package org.brotherhood.app.transport

import org.brotherhood.app.data.BrotherhoodRepository

class DeliveryEngine(
    private val repository: BrotherhoodRepository,
    private val router: TransportRouter,
) {
    suspend fun drainDueQueue(limit: Int = 20): Int {
        var delivered = 0
        repository.dueOutbound().take(limit).forEach { item ->
            repository.markSending(item.id)
            when (val result = router.send(item)) {
                is TransportResult.Delivered -> {
                    repository.markDelivered(item.id, result.receipt)
                    delivered++
                }
                is TransportResult.Failed -> {
                    repository.markTemporaryFailure(item.id, result.reason)
                }
                is TransportResult.Unavailable -> {
                    repository.markTemporaryFailure(item.id, result.reason)
                }
            }
        }
        return delivered
    }
}
