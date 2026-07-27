package org.brotherhood.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.brotherhood.app.BrotherhoodApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BrotherhoodApplication
                app.ensureInitialized()
                BackgroundModeManager.configure(
                    app,
                    app.repository.state.value.preferences.availabilityMode,
                )
            } catch (_: Throwable) {
                // Il boot non deve esporre dettagli dell'archivio nei log di sistema.
            } finally {
                pending.finish()
            }
        }
    }
}
