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
        if (
            LanEndpointPolicy.isAdvertisable(endpoint.lanHost) &&
            endpoint.lanPort in 1..65535
        ) {
            add(TransportType.LAN)
        }
        if (!endpoint.torRevoked && endpoint.torOnion.isNotBlank()) add(TransportType.TOR)
    }
}

/**
 * Legacy Android emulator instances use these guest-only addresses behind
 * isolated virtual routers. Without an explicit host-side port forwarding
 * rule, another emulator or physical phone cannot reach them.
 */
object LanEndpointPolicy {
    private val isolatedEmulatorAddresses = setOf("10.0.2.15", "10.0.2.16")

    fun isAdvertisable(host: String): Boolean =
        host.isNotBlank() && !isIsolatedEmulatorAddress(host)

    fun isIsolatedEmulatorAddress(host: String): Boolean =
        host.trim() in isolatedEmulatorAddresses
}
