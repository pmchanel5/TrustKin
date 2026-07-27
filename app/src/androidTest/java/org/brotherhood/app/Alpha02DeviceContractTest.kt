package org.brotherhood.app

import android.content.ComponentName
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.brotherhood.app.background.BrotherhoodForegroundService
import org.brotherhood.app.media.VoicePlaybackController
import org.brotherhood.app.media.VoiceMessageRecorder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Alpha02DeviceContractTest {
    @Test
    fun playbackControllerRemovesAbandonedPrivateTempFiles() {
        val context = ApplicationProvider.getApplicationContext<BrotherhoodApplication>()
        val abandoned = File(context.cacheDir, "voice-playback-abandoned.ogg")
        val abandonedRecording = File(context.cacheDir, "voice-recording-abandoned.ogg")
        abandoned.writeBytes(byteArrayOf(1, 2, 3))
        abandonedRecording.writeBytes(byteArrayOf(1, 2, 3))

        val controller = VoicePlaybackController(context)
        val recorder = VoiceMessageRecorder(context)

        assertFalse(abandoned.exists())
        assertFalse(abandonedRecording.exists())
        controller.close()
        recorder.close()
    }

    @Test
    @Suppress("DEPRECATION")
    fun persistentModeServiceIsPrivateAndDeclaresRemoteMessagingType() {
        val context = ApplicationProvider.getApplicationContext<BrotherhoodApplication>()
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, BrotherhoodForegroundService::class.java),
            0,
        )

        assertFalse(info.exported)
        assertTrue(
            info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING != 0,
        )
    }
}
