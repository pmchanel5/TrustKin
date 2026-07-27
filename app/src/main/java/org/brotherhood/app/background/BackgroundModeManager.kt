package org.brotherhood.app.background

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.brotherhood.app.model.AvailabilityMode

object BackgroundModeManager {
    private const val PERIODIC_WORK = "brotherhood-balanced-delivery"

    fun configure(context: Context, mode: AvailabilityMode) {
        val workManager = WorkManager.getInstance(context)
        when (mode) {
            AvailabilityMode.ALWAYS -> {
                workManager.cancelUniqueWork(PERIODIC_WORK)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BrotherhoodForegroundService::class.java),
                )
            }
            AvailabilityMode.BALANCED -> {
                context.stopService(Intent(context, BrotherhoodForegroundService::class.java))
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val work = PeriodicWorkRequestBuilder<BalancedDeliveryWorker>(
                    15,
                    TimeUnit.MINUTES,
                )
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    work,
                )
            }
            AvailabilityMode.WHEN_OPEN -> {
                context.stopService(Intent(context, BrotherhoodForegroundService::class.java))
                workManager.cancelUniqueWork(PERIODIC_WORK)
            }
        }
    }
}
