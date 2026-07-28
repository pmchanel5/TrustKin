package org.brotherhood.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.brotherhood.app.BrotherhoodApplication
import org.brotherhood.app.transport.TransportPhase

class BalancedDeliveryWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BrotherhoodApplication
        return runCatching {
            app.ensureInitialized()
            if (app.repository.state.value.identity == null) return Result.success()
            app.runtimeController.acquire(OWNER)
            try {
                app.deliveryEngine.drainDueQueue()
                if (
                    app.repository.dueOutbound().isNotEmpty() &&
                    app.torTransport.state.value.phase in setOf(
                        TransportPhase.STARTING,
                        TransportPhase.CONNECTING,
                    )
                ) {
                    withTimeoutOrNull(TOR_WAIT_MS) {
                        while (
                            app.torTransport.state.value.phase in setOf(
                                TransportPhase.STARTING,
                                TransportPhase.CONNECTING,
                            )
                        ) {
                            delay(1_000)
                        }
                    }
                    app.deliveryEngine.drainDueQueue()
                }
            } finally {
                withContext(NonCancellable) {
                    app.runtimeController.release(OWNER)
                }
            }
            Result.success()
        }.getOrElse {
            if (it is CancellationException) throw it
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val OWNER = "balanced-worker"
        private const val TOR_WAIT_MS = 90_000L
    }
}
