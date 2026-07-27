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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.brotherhood.app.model.OutboundItem
import org.brotherhood.app.model.WireEnvelope

data class LanStatus(
    val listening: Boolean = false,
    val address: String = "",
    val port: Int = LanTransport.DEFAULT_PORT,
    val lastError: String = "",
)

class LanTransport(
    private val context: Context,
    private val repository: BrotherhoodRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private val mutableStatus = MutableStateFlow(LanStatus())
    val status: StateFlow<LanStatus> = mutableStatus.asStateFlow()

    fun start() {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch {
            runCatching {
                ServerSocket().also { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(DEFAULT_PORT))
                    server = socket
                    mutableStatus.value = LanStatus(
                        listening = true,
                        address = currentIpv4Address(),
                        port = socket.localPort,
                    )
                    while (isActive && !socket.isClosed) {
                        val client = socket.accept()
                        launch { handleClient(client) }
                    }
                }
            }.onFailure {
                mutableStatus.value = LanStatus(
                    listening = false,
                    address = currentIpv4Address(),
                    lastError = it.javaClass.simpleName,
                )
            }
        }
    }

    suspend fun send(item: OutboundItem): DeliveryReceipt = withContext(Dispatchers.IO) {
        val state = repository.state.value
        val contact = state.contacts.first { it.id == item.contactId }
        require(!contact.blocked) { "Contatto bloccato" }
        val envelope = repository.envelopeFor(item)
        val bytes = json.encodeToString(envelope).encodeToByteArray()
        require(bytes.size <= MAX_WIRE_BYTES) { "Pacchetto troppo grande" }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(contact.endpointHost, contact.endpointPort), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            DataOutputStream(socket.getOutputStream().buffered()).use { output ->
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
                val input = DataInputStream(socket.getInputStream().buffered())
                val responseSize = input.readInt()
                require(responseSize in 1..MAX_RECEIPT_BYTES) { "Ricevuta non valida" }
                val response = ByteArray(responseSize)
                input.readFully(response)
                json.decodeFromString<DeliveryReceipt>(response.decodeToString())
            }
        }
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

    fun close() {
        runCatching { server?.close() }
        acceptJob?.cancel()
        scope.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream().buffered())
            val size = input.readInt()
            require(size in 1..MAX_WIRE_BYTES) { "Dimensione pacchetto non valida" }
            val bytes = ByteArray(size)
            input.readFully(bytes)
            val envelope = json.decodeFromString<WireEnvelope>(bytes.decodeToString())
            val receipt = repository.receiveEnvelope(envelope)
            val response = json.encodeToString(receipt).encodeToByteArray()
            val output = DataOutputStream(socket.getOutputStream().buffered())
            output.writeInt(response.size)
            output.write(response)
            output.flush()
        }
    }

    companion object {
        const val DEFAULT_PORT = 42337
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_WIRE_BYTES = 5_000_000
        private const val MAX_RECEIPT_BYTES = 32_000
    }
}
