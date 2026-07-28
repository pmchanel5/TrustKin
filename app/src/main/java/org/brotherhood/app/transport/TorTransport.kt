package org.brotherhood.app.transport

import android.app.Application
import android.os.Build
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManagerFactory
import org.briarproject.onionwrapper.AndroidTorWrapper
import org.briarproject.onionwrapper.TorWrapper
import org.brotherhood.app.data.BrotherhoodRepository
import org.brotherhood.app.model.DeliveryReceipt
import org.brotherhood.app.model.NetworkFrame
import org.brotherhood.app.model.TorIdentity

class TorTransport(
    private val application: Application,
    private val repository: BrotherhoodRepository,
    private val localServicePort: Int = LanTransport.DEFAULT_PORT,
) : MessageTransport {
    override val type = TransportType.TOR
    private val mutableState = MutableStateFlow(TransportState())
    override val state: StateFlow<TransportState> = mutableState.asStateFlow()
    private val lifecycleMutex = Mutex()
    private val ioExecutor = Executors.newCachedThreadPool()
    private val eventExecutor = Executors.newSingleThreadExecutor()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private var wrapper: TorWrapper? = null
    private var socksPort: Int = 0
    private var publishedOnion = ""
    private var descriptorUploaded = false
    @Volatile
    private var stopRequested = false
    @Volatile
    private var startupFailure = ""

    override suspend fun start() {
        lifecycleMutex.withLock {
            if (wrapper?.isTorRunning == true) return@withLock
            stopRequested = false
            startupFailure = ""
            mutableState.value = TransportState(phase = TransportPhase.STARTING)
            withContext(Dispatchers.IO) {
                runCatching {
                    val architecture = supportedArchitecture()
                        ?: throw IllegalStateException("Architettura Android non supportata")
                    socksPort = findFreePort()
                    val controlPort = findFreePort(excluding = socksPort)
                    val torDirectory = application.noBackupFilesDir.resolve("tor-runtime").apply {
                        if (!exists()) check(mkdirs()) { "Directory Tor non disponibile" }
                    }
                    val wakeLocks = AndroidWakeLockManagerFactory
                        .createAndroidWakeLockManager(application)
                    val tor = AndroidTorWrapper(
                        application,
                        wakeLocks,
                        ioExecutor,
                        eventExecutor,
                        architecture,
                        torDirectory,
                        socksPort,
                        controlPort,
                    )
                    tor.setObserver(TorObserver())
                    wrapper = tor
                    tor.start()
                    tor.enableConnectionPadding(true)
                    val previous = repository.state.value.torIdentity
                    val hiddenService = tor.publishHiddenService(
                        localServicePort,
                        REMOTE_SERVICE_PORT,
                        previous?.privateKey?.takeIf(String::isNotBlank),
                    )
                    publishedOnion = normalizeOnion(hiddenService.onion)
                    repository.saveTorIdentity(
                        TorIdentity(
                            onionAddress = publishedOnion,
                            privateKey = hiddenService.privKey,
                            revision = repository.state.value.torEndpointRevision,
                            createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    tor.enableNetwork(true)
                }.onFailure { error ->
                    startupFailure = failureLabel(error)
                    runCatching { wrapper?.stop() }
                    wrapper = null
                    mutableState.value = TransportState(
                        phase = if (stopRequested) TransportPhase.STOPPED else TransportPhase.ERROR,
                        lastError = if (stopRequested) "" else startupFailure,
                        deviceVerified = false,
                    )
                }
            }
        }
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        stopRequested = true
        val tor = wrapper ?: run {
            mutableState.value = TransportState(phase = TransportPhase.STOPPED)
            return
        }
        mutableState.value = mutableState.value.copy(phase = TransportPhase.STOPPED)
        withContext(Dispatchers.IO) {
            runCatching { tor.enableNetwork(false) }
            runCatching { tor.stop() }
        }
        wrapper = null
        descriptorUploaded = false
        mutableState.value = TransportState(phase = TransportPhase.STOPPED)
    }

    suspend fun rotateIdentity() {
        val expectedRevision = repository.state.value.torEndpointRevision + 1
        stop()
        repository.revokeTorIdentity()
        start()
        val rotated = repository.state.value.torIdentity
        check(
            rotated != null &&
                rotated.revision == expectedRevision &&
                ONION_V3.matches(rotated.onionAddress),
        ) { "Rotazione Tor non riuscita" }
    }

    suspend fun deleteRuntimeData() = withContext(Dispatchers.IO) {
        val directory = File(application.noBackupFilesDir, "tor-runtime")
        check(directory.parentFile == application.noBackupFilesDir) {
            "Directory Tor non valida"
        }
        if (directory.exists()) check(directory.deleteRecursively()) {
            "Dati runtime Tor non eliminati"
        }
    }

    override suspend fun send(
        recipient: RecipientEndpoint,
        frame: NetworkFrame,
    ): TransportResult = withContext(Dispatchers.IO) {
        if (recipient.torRevoked || !ONION_V3.matches(recipient.torOnion.lowercase())) {
            return@withContext TransportResult.Unavailable(type, "Endpoint Tor assente")
        }
        if (mutableState.value.phase != TransportPhase.ONLINE || socksPort == 0) {
            return@withContext TransportResult.Unavailable(type, "Tor non connesso")
        }
        runCatching {
            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress("127.0.0.1", socksPort),
            )
            val socket = Socket(proxy)
            exchange(
                socket,
                InetSocketAddress.createUnresolved(recipient.torOnion, recipient.torPort),
                frame,
            )
        }.fold(
            onSuccess = { TransportResult.Delivered(it, type) },
            onFailure = {
                TransportResult.Failed(type, it.javaClass.simpleName, retryable = true)
            },
        )
    }

    private fun exchange(
        socket: Socket,
        address: InetSocketAddress,
        frame: NetworkFrame,
    ): DeliveryReceipt {
        val bytes = json.encodeToString(frame).encodeToByteArray()
        require(bytes.size <= LanTransport.MAX_WIRE_BYTES) { "Pacchetto troppo grande" }
        socket.use {
            socket.connect(address, CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val output = DataOutputStream(socket.getOutputStream().buffered())
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
            val input = DataInputStream(socket.getInputStream().buffered())
            val responseSize = input.readInt()
            require(responseSize in 1..MAX_RECEIPT_BYTES) { "Ricevuta non valida" }
            val response = ByteArray(responseSize)
            input.readFully(response)
            return json.decodeFromString(response.decodeToString())
        }
    }

    private inner class TorObserver : TorWrapper.Observer {
        override fun onState(state: TorWrapper.TorState) {
            val reportedPhase = when (state) {
                TorWrapper.TorState.NOT_STARTED,
                TorWrapper.TorState.STOPPED,
                TorWrapper.TorState.DISABLED,
                TorWrapper.TorState.STOPPING -> TransportPhase.STOPPED
                TorWrapper.TorState.STARTING,
                TorWrapper.TorState.STARTED -> TransportPhase.STARTING
                TorWrapper.TorState.CONNECTING -> TransportPhase.CONNECTING
                TorWrapper.TorState.CONNECTED -> TransportPhase.ONLINE
            }
            val failed = startupFailure.isNotBlank()
            val unexpectedlyStopped =
                reportedPhase == TransportPhase.STOPPED && !stopRequested && !failed
            val phase = when {
                stopRequested -> TransportPhase.STOPPED
                failed || unexpectedlyStopped -> TransportPhase.ERROR
                else -> reportedPhase
            }
            val error = when {
                stopRequested -> ""
                failed -> startupFailure
                unexpectedlyStopped -> "TorStoppedUnexpectedly"
                phase == TransportPhase.ONLINE -> ""
                else -> mutableState.value.lastError
            }
            mutableState.value = mutableState.value.copy(
                phase = phase,
                onionServiceReady = descriptorUploaded,
                deviceVerified = false,
                lastError = error,
            )
        }

        override fun onBootstrapPercentage(percentage: Int) {
            mutableState.value = mutableState.value.copy(
                bootstrapPercent = percentage.coerceIn(0, 100),
            )
        }

        override fun onHsDescriptorUpload(onion: String) {
            if (normalizeOnion(onion) == publishedOnion) {
                descriptorUploaded = true
                mutableState.value = mutableState.value.copy(onionServiceReady = true)
            }
        }

        override fun onClockSkewDetected(skewSeconds: Long) {
            mutableState.value = mutableState.value.copy(
                phase = TransportPhase.DEGRADED,
                lastError = "ClockSkew",
            )
        }
    }

    private fun supportedArchitecture(): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            when {
                abi.startsWith("x86_64") -> return "x86_64_pie"
                abi.startsWith("x86") -> return "x86_pie"
                abi.startsWith("arm64") -> return "arm64_pie"
                abi.startsWith("armeabi") -> return "arm_pie"
            }
        }
        return null
    }

    private fun findFreePort(excluding: Int = -1): Int {
        repeat(8) {
            val port = ServerSocket(0).use { it.localPort }
            if (port != excluding) return port
        }
        throw IllegalStateException("Porte locali non disponibili")
    }

    private fun normalizeOnion(value: String): String =
        value.lowercase().let { if (it.endsWith(".onion")) it else "$it.onion" }

    private fun failureLabel(error: Throwable): String {
        val type = error.javaClass.simpleName.ifBlank { "TorStartFailure" }
        val detail = error.message
            ?.replace(Regex("[\\r\\n\\t]+"), " ")
            ?.trim()
            ?.take(120)
            .orEmpty()
        return if (detail.isBlank()) type else "$type: $detail"
    }

    companion object {
        private const val REMOTE_SERVICE_PORT = 80
        private const val CONNECT_TIMEOUT_MS = 45_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val MAX_RECEIPT_BYTES = 32_000
        private val ONION_V3 = Regex("^[a-z2-7]{56}\\.onion$")
    }
}
