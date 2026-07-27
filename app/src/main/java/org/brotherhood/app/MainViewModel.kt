package org.brotherhood.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.brotherhood.app.media.ImageSanitizer
import org.brotherhood.app.media.VoiceMessageRecorder
import org.brotherhood.app.media.VoicePlaybackController
import org.brotherhood.app.model.ChatMessage
import org.brotherhood.app.model.ImportInviteResult
import org.brotherhood.app.model.MessageKind
import org.brotherhood.app.model.MessagePayload
import org.brotherhood.app.model.AvailabilityMode
import org.brotherhood.app.transport.TransportResult
import org.brotherhood.app.background.BackgroundModeManager

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrotherhoodApplication
    val state = app.repository.state
    val lanStatus = app.lanTransport.state
    val torStatus = app.torTransport.state
    val routerDiagnostics = app.transportRouter.diagnostics

    private val mutableInitialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()
    private val mutableUnlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = mutableUnlocked.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()
    private val mutablePendingInvite = MutableStateFlow<String?>(null)
    val pendingInvite: StateFlow<String?> = mutablePendingInvite.asStateFlow()
    private val retryRunning = AtomicBoolean(false)
    private val voiceFinalizing = AtomicBoolean(false)
    private val voiceRecorder = VoiceMessageRecorder(application)
    private val voicePlayback = VoicePlaybackController(application)
    val voiceRecording = voiceRecorder.state
    val voicePlaybackState = voicePlayback.state
    private var voiceTarget: VoiceTarget? = null
    private var voiceStartJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { app.ensureInitialized() }
                .onFailure { mutableNotice.value = "Archivio locale non leggibile" }
            mutableInitialized.value = true
            if (app.repository.state.value.identity != null) {
                app.runtimeController.acquire(UI_OWNER)
                BackgroundModeManager.configure(
                    app,
                    app.repository.state.value.preferences.availabilityMode,
                )
            }
            while (true) {
                retryQueue()
                delay(15_000)
            }
        }
        viewModelScope.launch {
            voiceRecording.collect { recording ->
                if (recording.limitReached && voiceTarget != null) finishVoiceRecording(cancel = false)
                else if (recording.interrupted && voiceTarget != null) {
                    mutableNotice.value = "Registrazione interrotta"
                    finishVoiceRecording(cancel = true)
                }
            }
        }
    }

    fun createIdentity(name: String, pin: String, confirmation: String) {
        if (pin != confirmation) {
            mutableNotice.value = "I PIN non coincidono"
            return
        }
        viewModelScope.launch {
            runBusy {
                val chars = pin.toCharArray()
                try {
                    app.repository.createIdentity(name, chars)
                    app.runtimeController.acquire(UI_OWNER)
                    BackgroundModeManager.configure(
                        app,
                        app.repository.state.value.preferences.availabilityMode,
                    )
                    mutableUnlocked.value = true
                } finally {
                    chars.fill('\u0000')
                }
            }
        }
    }

    fun unlock(pin: String) {
        val chars = pin.toCharArray()
        mutableUnlocked.value = try {
            app.repository.verifyPin(chars)
        } finally {
            chars.fill('\u0000')
        }
        if (!mutableUnlocked.value) mutableNotice.value = "PIN non corretto"
    }

    fun lock() {
        mutableUnlocked.value = false
    }

    fun createInvite(): String = runCatching {
        val status = lanStatus.value
        val host = status.listeningAddress.ifBlank { app.lanTransport.currentIpv4Address() }
        app.repository.createInvite(host, status.listeningPort.takeIf { it > 0 } ?: 42337)
    }.getOrElse {
        mutableNotice.value = it.message ?: "Invito non disponibile"
        ""
    }

    fun importInvite(raw: String, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            when (val result = app.repository.importInvite(raw)) {
                is ImportInviteResult.Success -> {
                    mutableNotice.value = "Contatto ${result.contact.displayName} aggiunto"
                    onSuccess(result.contact.id)
                }
                is ImportInviteResult.Failure -> mutableNotice.value = result.reason
            }
        }
    }

    fun acceptDeepLink(raw: String) {
        if (raw.startsWith("brotherhood://invite?data=")) {
            mutablePendingInvite.value = raw
        }
    }

    fun consumePendingInvite() {
        mutablePendingInvite.value = null
    }

    fun sendText(contactId: String, body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            runCatching {
                app.repository.enqueueMessage(
                    contactId,
                    MessagePayload(body = body.trim(), kind = MessageKind.TEXT),
                )
                retryQueue()
            }.onFailure { mutableNotice.value = it.message ?: "Invio non riuscito" }
        }
    }

    fun sendGroupText(groupId: String, body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            runCatching {
                app.repository.enqueueGroupMessage(
                    groupId,
                    MessagePayload(body = body.trim(), kind = MessageKind.TEXT, groupId = groupId),
                )
                retryQueue()
            }.onFailure { mutableNotice.value = it.message ?: "Invio non riuscito" }
        }
    }

    fun sendImage(contactId: String, uri: Uri) {
        viewModelScope.launch {
            runBusy {
                val image = ImageSanitizer.sanitize(getApplication(), uri)
                app.repository.enqueueMessage(
                    contactId,
                    MessagePayload(
                        body = "Immagine",
                        kind = MessageKind.IMAGE,
                        attachmentBase64 = image.base64,
                        attachmentMime = image.mimeType,
                        attachmentName = "immagine-${System.currentTimeMillis()}.jpg",
                    ),
                )
                mutableNotice.value = "Immagine ripulita: ${image.byteSize / 1024} KiB"
                retryQueue()
            }
        }
    }

    fun sendGroupImage(groupId: String, uri: Uri) {
        viewModelScope.launch {
            runBusy {
                val image = ImageSanitizer.sanitize(getApplication(), uri)
                app.repository.enqueueGroupMessage(
                    groupId,
                    MessagePayload(
                        body = "Immagine",
                        kind = MessageKind.IMAGE,
                        attachmentBase64 = image.base64,
                        attachmentMime = image.mimeType,
                        attachmentName = "immagine-${System.currentTimeMillis()}.jpg",
                        groupId = groupId,
                    ),
                )
                retryQueue()
            }
        }
    }

    fun startVoiceRecording(targetId: String, group: Boolean) {
        if (voiceTarget != null) return
        val target = VoiceTarget(targetId, group)
        voiceTarget = target
        voiceStartJob = viewModelScope.launch {
            try {
                voiceRecorder.start()
                if (voiceTarget != target) voiceRecorder.cancel()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (voiceTarget == target) voiceTarget = null
                mutableNotice.value = t.message ?: "Registrazione non disponibile"
            }
        }
    }

    fun finishVoiceRecording(cancel: Boolean) {
        val target = voiceTarget ?: return
        if (!voiceFinalizing.compareAndSet(false, true)) return
        voiceTarget = null
        viewModelScope.launch {
            try {
                voiceStartJob?.join()
                val voice = voiceRecorder.finish(cancel) ?: return@launch
                val encoded = Base64.getEncoder().encodeToString(voice.bytes)
                voice.bytes.fill(0)
                val payload = MessagePayload(
                    body = "Messaggio vocale",
                    kind = MessageKind.VOICE,
                    attachmentBase64 = encoded,
                    attachmentMime = voice.mimeType,
                    attachmentName = voice.fileName,
                    attachmentSha256 = voice.sha256,
                    durationMillis = voice.durationMillis,
                    groupId = if (target.group) target.id else "",
                )
                if (target.group) app.repository.enqueueGroupMessage(target.id, payload)
                else app.repository.enqueueMessage(target.id, payload)
                retryQueue()
            } catch (t: Throwable) {
                mutableNotice.value = t.message ?: "Vocale non salvato"
            } finally {
                voiceFinalizing.set(false)
            }
        }
    }

    fun playOrPauseVoice(message: ChatMessage) {
        runCatching { voicePlayback.playOrPause(message) }
            .onFailure { mutableNotice.value = it.message ?: "Vocale non riproducibile" }
    }

    fun createGroup(name: String, members: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { app.repository.createGroup(name, members) }
                .onSuccess { onCreated(it.id) }
                .onFailure { mutableNotice.value = it.message ?: "Gruppo non creato" }
        }
    }

    fun verifyContact(id: String, verified: Boolean) {
        viewModelScope.launch { app.repository.setContactVerified(id, verified) }
    }

    fun blockContact(id: String, blocked: Boolean) {
        viewModelScope.launch { app.repository.setContactBlocked(id, blocked) }
    }

    fun setContactTorRevoked(id: String, revoked: Boolean) {
        viewModelScope.launch { app.repository.setContactTorRevoked(id, revoked) }
    }

    fun renameContact(id: String, alias: String) {
        viewModelScope.launch { app.repository.renameContact(id, alias) }
    }

    fun removeContact(id: String) {
        viewModelScope.launch { app.repository.removeContact(id) }
    }

    fun removeGroupMember(groupId: String, memberId: String) {
        viewModelScope.launch {
            runCatching { app.repository.removeGroupMember(groupId, memberId) }
                .onFailure { mutableNotice.value = it.message ?: "Membro non rimosso" }
        }
    }

    fun setAvailabilityMode(mode: AvailabilityMode) {
        viewModelScope.launch {
            app.repository.setAvailabilityMode(mode)
            BackgroundModeManager.configure(app, mode)
        }
    }

    fun rotateTorIdentity() {
        viewModelScope.launch {
            runBusy {
                app.torTransport.rotateIdentity()
                mutableNotice.value =
                    "Nuovo endpoint Tor creato. Condividi un nuovo invito con i contatti."
            }
        }
    }

    fun onAppForeground() {
        viewModelScope.launch {
            app.ensureInitialized()
            if (app.repository.state.value.identity != null) {
                app.runtimeController.acquire(UI_OWNER)
            }
        }
    }

    fun onAppBackground() {
        viewModelScope.launch { app.runtimeController.release(UI_OWNER) }
    }

    fun clearNotice() {
        mutableNotice.value = null
    }

    fun deleteIdentity() {
        viewModelScope.launch {
            voiceTarget = null
            voiceStartJob?.join()
            voiceRecorder.cancel()
            voicePlayback.stop()
            BackgroundModeManager.configure(app, AvailabilityMode.WHEN_OPEN)
            app.runtimeController.shutdownAll()
            app.torTransport.deleteRuntimeData()
            app.repository.deleteAll()
            mutableUnlocked.value = false
            mutableNotice.value = "Identità e dati locali eliminati"
        }
    }

    private suspend fun retryQueue() {
        if (!retryRunning.compareAndSet(false, true)) return
        try {
            app.deliveryEngine.drainDueQueue()
        } finally {
            retryRunning.set(false)
        }
    }

    private suspend fun runBusy(block: suspend () -> Unit) {
        mutableBusy.value = true
        runCatching { block() }
            .onFailure { mutableNotice.value = it.message ?: "Operazione non riuscita" }
        mutableBusy.value = false
    }

    override fun onCleared() {
        voiceStartJob?.cancel()
        voiceRecorder.close()
        voicePlayback.close()
        kotlinx.coroutines.runBlocking { app.runtimeController.release(UI_OWNER) }
        super.onCleared()
    }

    private data class VoiceTarget(val id: String, val group: Boolean)

    companion object {
        private const val UI_OWNER = "ui"
    }
}
