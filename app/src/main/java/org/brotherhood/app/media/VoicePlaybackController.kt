package org.brotherhood.app.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.brotherhood.app.model.ChatMessage
import org.brotherhood.app.model.MessageKind

data class VoicePlaybackState(
    val messageId: String = "",
    val playing: Boolean = false,
    val positionMillis: Int = 0,
    val durationMillis: Int = 0,
)

class VoicePlaybackController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutableState = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = mutableState.asStateFlow()
    private var player: MediaPlayer? = null
    private var tempFile: File? = null
    private var ticker: Job? = null

    init {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith(TEMP_PREFIX) }
            ?.forEach { it.delete() }
    }

    fun playOrPause(message: ChatMessage) {
        require(message.kind == MessageKind.VOICE) { "Messaggio non vocale" }
        val current = player
        if (mutableState.value.messageId == message.id && current != null) {
            if (current.isPlaying) {
                current.pause()
                mutableState.value = mutableState.value.copy(playing = false)
            } else {
                current.start()
                mutableState.value = mutableState.value.copy(playing = true)
                startTicker()
            }
            return
        }
        stop()
        val bytes = Base64.getDecoder().decode(message.attachmentBase64)
        require(bytes.size <= VoiceMessageRecorder.MAX_FILE_BYTES) { "Vocale troppo grande" }
        if (message.attachmentSha256.isNotBlank()) {
            require(sha256(bytes) == message.attachmentSha256.lowercase()) {
                "Integrità vocale non valida"
            }
        }
        val suffix = if (message.attachmentMime == "audio/ogg") ".ogg" else ".m4a"
        val file = File(context.cacheDir, "$TEMP_PREFIX${message.id.hashCode()}$suffix")
        try {
            file.writeBytes(bytes)
        } finally {
            bytes.fill(0)
        }
        tempFile = file
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnCompletionListener { this@VoicePlaybackController.stop() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                this@VoicePlaybackController.stop()
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (t: Throwable) {
            runCatching { mediaPlayer.release() }
            file.delete()
            tempFile = null
            throw t
        }
        player = mediaPlayer
        mutableState.value = VoicePlaybackState(
            messageId = message.id,
            playing = true,
            durationMillis = mediaPlayer.duration,
        )
        startTicker()
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        tempFile?.delete()
        tempFile = null
        mutableState.value = VoicePlaybackState()
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val current = player ?: break
                mutableState.value = mutableState.value.copy(
                    positionMillis = runCatching { current.currentPosition }.getOrDefault(0),
                    playing = runCatching { current.isPlaying }.getOrDefault(false),
                )
                delay(200)
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TEMP_PREFIX = "voice-playback-"
    }
}
