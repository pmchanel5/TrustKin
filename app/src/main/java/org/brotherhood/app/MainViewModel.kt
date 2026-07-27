package org.brotherhood.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.brotherhood.app.media.ImageSanitizer
import org.brotherhood.app.model.ImportInviteResult
import org.brotherhood.app.model.MessageKind
import org.brotherhood.app.model.MessagePayload

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrotherhoodApplication
    val state = app.repository.state
    val lanStatus = app.lanTransport.status

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

    init {
        viewModelScope.launch {
            runCatching { app.repository.initialize() }
                .onFailure { mutableNotice.value = "Archivio locale non leggibile" }
            mutableInitialized.value = true
            app.lanTransport.start()
            while (true) {
                retryQueue()
                delay(15_000)
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
        val host = status.address.ifBlank { app.lanTransport.currentIpv4Address() }
        app.repository.createInvite(host, status.port)
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

    fun clearNotice() {
        mutableNotice.value = null
    }

    fun deleteIdentity() {
        viewModelScope.launch {
            app.repository.deleteAll()
            mutableUnlocked.value = false
            mutableNotice.value = "Identità e dati locali eliminati"
        }
    }

    private suspend fun retryQueue() {
        if (!retryRunning.compareAndSet(false, true)) return
        try {
            app.repository.dueOutbound().forEach { item ->
                runCatching {
                    app.repository.markSending(item.id)
                    app.lanTransport.send(item)
                }.onSuccess { receipt ->
                    app.repository.markDelivered(item.id, receipt)
                }.onFailure {
                    app.repository.markTemporaryFailure(item.id, it.javaClass.simpleName)
                }
            }
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
}
