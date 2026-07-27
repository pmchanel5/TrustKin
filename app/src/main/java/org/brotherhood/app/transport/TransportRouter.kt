package org.brotherhood.app.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.brotherhood.app.data.BrotherhoodRepository
import org.brotherhood.app.model.NetworkFrame
import org.brotherhood.app.model.OutboundItem

data class RouterDiagnostics(
    val lastTransport: TransportType? = null,
    val lastError: String = "",
)

class TransportRouter internal constructor(
    private val endpointProvider: (OutboundItem) -> RecipientEndpoint,
    private val frameProvider: (OutboundItem) -> NetworkFrame,
    transports: List<MessageTransport>,
) {
    constructor(
        repository: BrotherhoodRepository,
        transports: List<MessageTransport>,
    ) : this(repository::endpointFor, repository::frameFor, transports)

    private val transportsByType = transports.associateBy { it.type }
    private val mutableDiagnostics = MutableStateFlow(RouterDiagnostics())
    val diagnostics: StateFlow<RouterDiagnostics> = mutableDiagnostics.asStateFlow()

    suspend fun send(item: OutboundItem): TransportResult {
        val endpoint = endpointProvider(item)
        val frame = frameProvider(item)
        val order = TransportPolicy.preferredOrder(endpoint)
        if (order.isEmpty()) {
            return TransportResult.Unavailable(TransportType.LAN, "Nessun endpoint disponibile")
        }
        var last: TransportResult = TransportResult.Unavailable(order.first(), "Non disponibile")
        for (type in order) {
            val transport = transportsByType[type] ?: continue
            val result = transport.send(endpoint, frame)
            when (result) {
                is TransportResult.Delivered -> {
                    mutableDiagnostics.value = RouterDiagnostics(lastTransport = result.transport)
                    return result
                }
                is TransportResult.Failed -> {
                    last = result
                    mutableDiagnostics.value = RouterDiagnostics(
                        lastTransport = type,
                        lastError = result.reason,
                    )
                    if (!result.retryable) return result
                }
                is TransportResult.Unavailable -> {
                    last = result
                    mutableDiagnostics.value = RouterDiagnostics(
                        lastTransport = type,
                        lastError = result.reason,
                    )
                }
            }
        }
        return last
    }

}
