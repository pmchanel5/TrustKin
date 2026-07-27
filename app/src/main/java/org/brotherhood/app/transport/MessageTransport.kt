package org.brotherhood.app.transport

import kotlinx.coroutines.flow.StateFlow
import org.brotherhood.app.model.DeliveryReceipt
import org.brotherhood.app.model.NetworkFrame

enum class TransportType {
    LAN,
    TOR,
}

enum class TransportPhase {
    STOPPED,
    STARTING,
    CONNECTING,
    ONLINE,
    DEGRADED,
    ERROR,
}

data class TransportState(
    val phase: TransportPhase = TransportPhase.STOPPED,
    val bootstrapPercent: Int = 0,
    val listeningAddress: String = "",
    val listeningPort: Int = 0,
    val onionServiceReady: Boolean = false,
    val deviceVerified: Boolean = false,
    val lastError: String = "",
)

data class RecipientEndpoint(
    val contactId: String,
    val lanHost: String = "",
    val lanPort: Int = 0,
    val torOnion: String = "",
    val torPort: Int = 80,
    val torRevoked: Boolean = false,
)

sealed interface TransportResult {
    data class Delivered(
        val receipt: DeliveryReceipt,
        val transport: TransportType,
    ) : TransportResult

    data class Unavailable(
        val transport: TransportType,
        val reason: String,
    ) : TransportResult

    data class Failed(
        val transport: TransportType,
        val reason: String,
        val retryable: Boolean = true,
    ) : TransportResult
}

interface MessageTransport {
    val type: TransportType
    val state: StateFlow<TransportState>

    suspend fun start()
    suspend fun stop()
    suspend fun send(
        recipient: RecipientEndpoint,
        frame: NetworkFrame,
    ): TransportResult
}

object TransportPolicy {
    fun preferredOrder(endpoint: RecipientEndpoint): List<TransportType> = buildList {
        if (endpoint.lanHost.isNotBlank() && endpoint.lanPort in 1..65535) add(TransportType.LAN)
        if (!endpoint.torRevoked && endpoint.torOnion.isNotBlank()) add(TransportType.TOR)
    }
}
