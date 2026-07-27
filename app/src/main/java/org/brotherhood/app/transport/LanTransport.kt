package org.brotherhood.app.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.brotherhood.app.data.BrotherhoodRepository
import org.brotherhood.app.model.DeliveryReceipt
import org.brotherhood.app.model.NetworkFrame

class LanTransport(
    private val context: Context,
    private val repository: BrotherhoodRepository,
) : MessageTransport {
    override val type = TransportType.LAN
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private val rateLimiter = RequestRateLimiter()
    private val connectionSlots = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private val mutableState = MutableStateFlow(TransportState())
    override val state: StateFlow<TransportState> = mutableState.asStateFlow()

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (acceptJob?.isActive == true) return@withContext
        mutableState.value = TransportState(phase = TransportPhase.STARTING)
        runCatching {
            ServerSocket().also { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(DEFAULT_PORT))
                server = socket
                mutableState.value = TransportState(
                    phase = TransportPhase.ONLINE,
                    listeningAddress = currentIpv4Address(),
                    listeningPort = socket.localPort,
                    deviceVerified = false,
                )
                acceptJob = scope.launch {
                    while (isActive && !socket.isClosed) {
                        val client = runCatching { socket.accept() }.getOrNull() ?: break
                        if (!connectionSlots.tryAcquire()) {
                            client.close()
                            continue
                        }
                        launch {
                            try {
                                runCatching { handleClient(client) }
                            } finally {
                                connectionSlots.release()
                            }
                        }
                    }
                }
            }
        }.onFailure {
            mutableState.value = TransportState(
                phase = TransportPhase.ERROR,
                listeningAddress = currentIpv4Address(),
                lastError = it.javaClass.simpleName,
            )
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        runCatching { server?.close() }
        server = null
        acceptJob?.cancel()
        acceptJob = null
        mutableState.value = TransportState(phase = TransportPhase.STOPPED)
    }

    override suspend fun send(
        recipient: RecipientEndpoint,
        frame: NetworkFrame,
    ): TransportResult = withContext(Dispatchers.IO) {
        if (recipient.lanHost.isBlank() || recipient.lanPort !in 1..65535) {
            return@withContext TransportResult.Unavailable(type, "Endpoint LAN assente")
        }
        runCatching {
            exchange(
                socket = Socket(),
                address = InetSocketAddress(recipient.lanHost, recipient.lanPort),
                frame = frame,
            )
        }.fold(
            onSuccess = { TransportResult.Delivered(it, type) },
            onFailure = {
                TransportResult.Failed(type, it.javaClass.simpleName, retryable = true)
            },
        )
    }

    fun currentIpv4Address(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return ""
        val properties = manager.getLinkProperties(network) ?: return ""
        return properties.linkAddresses
            .asSequence()
            .map(LinkAddress::getAddress)
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
            .orEmpty()
    }

    private fun exchange(
        socket: Socket,
        address: InetSocketAddress,
        frame: NetworkFrame,
    ): DeliveryReceipt {
        val bytes = json.encodeToString(frame).encodeToByteArray()
        require(bytes.size <= MAX_WIRE_BYTES) { "Pacchetto troppo grande" }
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

    private suspend fun handleClient(socket: Socket) {
        socket.use {
            val source = socket.inetAddress?.hostAddress.orEmpty()
            if (!rateLimiter.allow(source)) return
            socket.soTimeout = READ_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream().buffered())
            val size = input.readInt()
            require(size in 1..MAX_WIRE_BYTES) { "Dimensione pacchetto non valida" }
            val bytes = ByteArray(size)
            input.readFully(bytes)
            val frame = json.decodeFromString<NetworkFrame>(bytes.decodeToString())
            val receipt = repository.receiveFrame(frame)
            val response = json.encodeToString(receipt).encodeToByteArray()
            val output = DataOutputStream(socket.getOutputStream().buffered())
            output.writeInt(response.size)
            output.write(response)
            output.flush()
        }
    }

    companion object {
        const val DEFAULT_PORT = 42337
        const val MAX_WIRE_BYTES = 5_000_000
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_RECEIPT_BYTES = 32_000
        private const val MAX_CONCURRENT_CONNECTIONS = 8
    }
}

class RequestRateLimiter(
    private val perSourceLimit: Int = 60,
    private val globalLimit: Int = 180,
    private val windowMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val bySource = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val global = ArrayDeque<Long>()

    @Synchronized
    fun allow(source: String): Boolean {
        val now = clock()
        evict(global, now)
        if (global.isEmpty()) bySource.clear()
        val sourceEvents = bySource.getOrPut(source.take(64)) { ArrayDeque() }
        evict(sourceEvents, now)
        if (global.size >= globalLimit || sourceEvents.size >= perSourceLimit) return false
        global.addLast(now)
        sourceEvents.addLast(now)
        return true
    }

    private fun evict(events: ArrayDeque<Long>, now: Long) {
        while (events.isNotEmpty() && now - events.first() >= windowMillis) {
            events.removeFirst()
        }
    }
}
