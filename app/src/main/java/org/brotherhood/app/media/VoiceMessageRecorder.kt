package org.brotherhood.app.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.UUID
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
import kotlinx.coroutines.withContext

data class VoiceRecordingState(
    val active: Boolean = false,
    val elapsedMillis: Long = 0,
    val limitReached: Boolean = false,
    val interrupted: Boolean = false,
)

data class RecordedVoice(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
    val durationMillis: Long,
    val sha256: String,
)

class VoiceMessageRecorder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(VoiceRecordingState())
    val state: StateFlow<VoiceRecordingState> = mutableState.asStateFlow()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var mimeType = ""
    private var extension = ""
    private var timerJob: Job? = null
    private var startedAt = 0L

    init {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith(TEMP_PREFIX) }
            ?.forEach { it.delete() }
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        check(recorder == null) { "Registrazione già attiva" }
        requestAudioFocus()
        val useOpus = Build.VERSION.SDK_INT >= 29
        extension = if (useOpus) "ogg" else "m4a"
        mimeType = if (useOpus) "audio/ogg" else "audio/mp4"
        val file = File(context.cacheDir, "$TEMP_PREFIX${UUID.randomUUID()}.$extension")
        tempFile = file
        val mediaRecorder = runCatching { createRecorder() }.getOrElse {
            tempFile = null
            abandonAudioFocus()
            throw it
        }
        recorder = mediaRecorder
        runCatching {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (useOpus) {
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                mediaRecorder.setAudioEncodingBitRate(24_000)
                mediaRecorder.setAudioSamplingRate(48_000)
            } else {
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setAudioEncodingBitRate(32_000)
                mediaRecorder.setAudioSamplingRate(44_100)
            }
            mediaRecorder.setMaxDuration(MAX_DURATION_MS.toInt())
            mediaRecorder.setMaxFileSize(MAX_FILE_BYTES.toLong())
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.setOnInfoListener { _, what, _ ->
                if (
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    mutableState.value = mutableState.value.copy(limitReached = true)
                }
            }
            mediaRecorder.setOnErrorListener { _, _, _ ->
                mutableState.value = mutableState.value.copy(interrupted = true)
            }
            mediaRecorder.prepare()
            mediaRecorder.start()
            startedAt = System.currentTimeMillis()
            mutableState.value = VoiceRecordingState(active = true)
            timerJob = scope.launch {
                while (isActive && mutableState.value.active) {
                    val elapsed = (System.currentTimeMillis() - startedAt)
                        .coerceIn(0, MAX_DURATION_MS)
                    mutableState.value = mutableState.value.copy(elapsedMillis = elapsed)
                    delay(100)
                }
            }
        }.getOrElse {
            releaseRecorder()
            file.delete()
            abandonAudioFocus()
            throw it
        }
    }

    suspend fun finish(cancel: Boolean): RecordedVoice? = withContext(Dispatchers.IO) {
        val mediaRecorder = recorder ?: return@withContext null
        val file = tempFile
        timerJob?.cancel()
        runCatching { mediaRecorder.stop() }
        releaseRecorder()
        abandonAudioFocus()
        val elapsed = (System.currentTimeMillis() - startedAt).coerceIn(0, MAX_DURATION_MS)
        mutableState.value = VoiceRecordingState()
        if (cancel || file == null || !file.exists()) {
            file?.delete()
            return@withContext null
        }
        try {
            require(file.length() in MIN_FILE_BYTES..MAX_FILE_BYTES.toLong()) {
                "Registrazione troppo breve o troppo grande"
            }
            val bytes = file.readBytes()
            val duration = readDuration(file).takeIf { it > 0 } ?: elapsed
            RecordedVoice(
                bytes = bytes,
                mimeType = mimeType,
                fileName = "vocale-${System.currentTimeMillis()}.$extension",
                durationMillis = duration.coerceAtMost(MAX_DURATION_MS),
                sha256 = sha256(bytes),
            )
        } finally {
            file.delete()
            tempFile = null
        }
    }

    suspend fun cancel() {
        finish(cancel = true)
    }

    fun close() {
        timerJob?.cancel()
        runCatching { recorder?.stop() }
        releaseRecorder()
        abandonAudioFocus()
        tempFile?.delete()
        tempFile = null
        mutableState.value = VoiceRecordingState()
        scope.cancel()
    }

    private fun requestAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS) {
                    mutableState.value = mutableState.value.copy(interrupted = true)
                }
            }
            .build()
        focusRequest = request
        require(audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Microfono occupato da un'altra applicazione"
        }
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

    private fun releaseRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }

    private fun readDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } finally {
            retriever.release()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_DURATION_MS = 60_000L
        const val MAX_FILE_BYTES = 1_500_000
        private const val TEMP_PREFIX = "voice-recording-"
        private const val MIN_FILE_BYTES = 256L
    }
}
