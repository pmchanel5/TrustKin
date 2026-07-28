package org.brotherhood.app.transport

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TransportRuntimeController(
    private val lan: MessageTransport,
    private val tor: MessageTransport,
) {
    private val mutex = Mutex()
    private val owners = linkedSetOf<String>()

    suspend fun acquire(owner: String) = mutex.withLock {
        val wasEmpty = owners.isEmpty()
        owners += owner
        try {
            // Healthy transports return immediately. Repeating start for an
            // existing owner also recovers a transport that exited or failed
            // after the owner was first registered.
            lan.start()
            tor.start()
        } catch (t: Throwable) {
            if (wasEmpty) {
                owners -= owner
                withContext(NonCancellable) {
                    tor.stop()
                    lan.stop()
                }
            }
            throw t
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
