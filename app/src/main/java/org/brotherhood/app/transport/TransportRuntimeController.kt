package org.brotherhood.app.transport

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TransportRuntimeController(
    private val lan: LanTransport,
    private val tor: TorTransport,
) {
    private val mutex = Mutex()
    private val owners = linkedSetOf<String>()

    suspend fun acquire(owner: String) = mutex.withLock {
        val wasEmpty = owners.isEmpty()
        owners += owner
        if (wasEmpty) {
            try {
                lan.start()
                tor.start()
            } catch (t: Throwable) {
                owners -= owner
                withContext(NonCancellable) {
                    tor.stop()
                    lan.stop()
                }
                throw t
            }
        }
    }

    suspend fun release(owner: String) = mutex.withLock {
        owners -= owner
        if (owners.isEmpty()) {
            withContext(NonCancellable) {
                tor.stop()
                lan.stop()
            }
        }
    }

    suspend fun shutdownAll() = mutex.withLock {
        owners.clear()
        withContext(NonCancellable) {
            tor.stop()
            lan.stop()
        }
    }
}
