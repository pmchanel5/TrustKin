package org.brotherhood.app.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.brotherhood.app.BrotherhoodApplication
import org.brotherhood.app.MainActivity
import org.brotherhood.app.model.AvailabilityMode
import org.brotherhood.app.transport.TransportPhase

class BrotherhoodForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val acquired = AtomicBoolean(false)
    private var loop: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification("Avvio della rete privata"))
        val app = application as BrotherhoodApplication
        loop = scope.launch {
            try {
                app.ensureInitialized()
                if (app.repository.state.value.identity == null) {
                    stopSelf()
                    return@launch
                }
                app.runtimeController.acquire(OWNER)
                acquired.set(true)
                while (isActive) {
                    app.runtimeController.acquire(OWNER)
                    app.deliveryEngine.drainDueQueue()
                    updateNotification(app)
                    delay(15_000)
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification("Rete in attesa"))
                }
            } finally {
                if (acquired.compareAndSet(true, false)) {
                    withContext(NonCancellable) {
                        app.runtimeController.release(OWNER)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val app = application as BrotherhoodApplication
            scope.launch {
                app.ensureInitialized()
                app.repository.setAvailabilityMode(AvailabilityMode.WHEN_OPEN)
                BackgroundModeManager.configure(this@BrotherhoodForegroundService, AvailabilityMode.WHEN_OPEN)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(notification: Notification) {
        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun updateNotification(app: BrotherhoodApplication) {
        val tor = app.torTransport.state.value
        val torText = when (tor.phase) {
            TransportPhase.ONLINE -> "Tor connesso"
            TransportPhase.CONNECTING -> "Tor ${tor.bootstrapPercent}%"
            TransportPhase.ERROR -> "Tor non disponibile"
            else -> "Tor in avvio"
        }
        val queued = app.repository.state.value.outbound.size
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("$torText · coda $queued"))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BrotherhoodForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Brotherhood è disponibile")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, "Arresta", stopIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Disponibilità Brotherhood",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Servizio visibile per ricevere messaggi peer-to-peer"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val OWNER = "foreground-service"
        private const val CHANNEL_ID = "brotherhood_availability"
        private const val NOTIFICATION_ID = 42337
        private const val ACTION_STOP = "org.brotherhood.app.STOP_AVAILABILITY"
    }
}
