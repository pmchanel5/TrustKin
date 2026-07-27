package org.brotherhood.app.data

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brotherhood.app.core.ReplayProtector
import org.brotherhood.app.core.RetryPolicy
import org.brotherhood.app.core.GroupPolicy
import org.brotherhood.app.crypto.CryptoEngine
import org.brotherhood.app.model.AppState
import org.brotherhood.app.model.ChatMessage
import org.brotherhood.app.model.Contact
import org.brotherhood.app.model.DeliveryReceipt
import org.brotherhood.app.model.DeliveryStatus
import org.brotherhood.app.model.ImportInviteResult
import org.brotherhood.app.model.MessagePayload
import org.brotherhood.app.model.OutboundItem
import org.brotherhood.app.model.PrivateGroup
import org.brotherhood.app.model.WireEnvelope
import org.brotherhood.app.storage.PinHasher
import org.brotherhood.app.storage.SecureStateStore

class BrotherhoodRepository(
    private val store: SecureStateStore,
    private val crypto: CryptoEngine,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private var replay = ReplayProtector()

    suspend fun initialize() = mutex.withLock {
        val loaded = store.load()
        mutableState.value = loaded
        replay = ReplayProtector(loaded.receivedMessageIds)
    }

    suspend fun createIdentity(displayName: String, pin: CharArray) = mutex.withLock {
        check(mutableState.value.identity == null) { "Esiste già un'identità" }
        val pinRecord = PinHasher.create(pin)
        val identity = crypto.generateIdentity(displayName)
        update(
            mutableState.value.copy(
                identity = identity,
                pinSalt = pinRecord.salt,
                pinHash = pinRecord.hash,
            ),
        )
    }

    fun verifyPin(pin: CharArray): Boolean {
        val snapshot = mutableState.value
        return PinHasher.verify(pin, snapshot.pinSalt, snapshot.pinHash)
    }

    fun createInvite(host: String, port: Int): String {
        val identity = requireNotNull(mutableState.value.identity) { "Identità non disponibile" }
        return crypto.createInvite(identity, host, port)
    }

    suspend fun importInvite(raw: String): ImportInviteResult = mutex.withLock {
        runCatching {
            val card = crypto.parseAndVerifyInvite(raw)
            val identity = requireNotNull(mutableState.value.identity)
            require(card.id != identity.id) { "Non puoi aggiungere la tua identità" }
            val incoming = crypto.contactFrom(card)
            val existing = mutableState.value.contacts.firstOrNull { it.id == incoming.id }
            val contact = if (existing == null) incoming else incoming.copy(
                localAlias = existing.localAlias,
                blocked = existing.blocked,
                verified = existing.verified,
                addedAt = existing.addedAt,
            )
            val contacts = mutableState.value.contacts.filterNot { it.id == contact.id } + contact
            update(mutableState.value.copy(contacts = contacts.sortedBy { it.effectiveName.lowercase() }))
            ImportInviteResult.Success(contact)
        }.getOrElse {
            ImportInviteResult.Failure(it.message ?: "Invito non valido")
        }
    }

    suspend fun setContactVerified(contactId: String, verified: Boolean) = mutate {
        copy(contacts = contacts.map { if (it.id == contactId) it.copy(verified = verified) else it })
    }

    suspend fun renameContact(contactId: String, alias: String) = mutate {
        copy(contacts = contacts.map {
            if (it.id == contactId) it.copy(localAlias = alias.trim().take(40)) else it
        })
    }

    suspend fun setContactBlocked(contactId: String, blocked: Boolean) = mutate {
        copy(contacts = contacts.map { if (it.id == contactId) it.copy(blocked = blocked) else it })
    }

    suspend fun removeContact(contactId: String) = mutate {
        copy(
            contacts = contacts.filterNot { it.id == contactId },
            outbound = outbound.filterNot { it.contactId == contactId },
        )
    }

    suspend fun enqueueMessage(contactId: String, payload: MessagePayload): String = mutex.withLock {
        val state = mutableState.value
        val identity = requireNotNull(state.identity)
        val contact = state.contacts.firstOrNull { it.id == contactId }
            ?: throw IllegalArgumentException("Contatto non trovato")
        require(!contact.blocked) { "Il contatto è bloccato" }
        require(state.outbound.size < MAX_QUEUE) { "Coda di invio piena" }
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val message = ChatMessage(
            id = messageId,
            conversationId = payload.groupId.ifBlank { contactId },
            senderId = identity.id,
            recipientId = contactId,
            body = payload.body,
            kind = payload.kind,
            attachmentBase64 = payload.attachmentBase64,
            attachmentMime = payload.attachmentMime,
            attachmentName = payload.attachmentName,
            sentAt = now,
            status = DeliveryStatus.QUEUED,
            replyTo = payload.replyTo,
            groupId = payload.groupId,
        )
        val outbound = OutboundItem(
            id = UUID.randomUUID().toString(),
            messageId = messageId,
            contactId = contactId,
            createdAt = now,
            expiresAt = now + MESSAGE_TTL_MS,
        )
        update(
            state.copy(
                messages = (state.messages + message).takeLast(MAX_MESSAGES),
                outbound = state.outbound + outbound,
            ),
        )
        messageId
    }

    suspend fun enqueueGroupMessage(groupId: String, payload: MessagePayload): String = mutex.withLock {
        val state = mutableState.value
        val identity = requireNotNull(state.identity)
        val group = state.groups.firstOrNull { it.id == groupId }
            ?: throw IllegalArgumentException("Gruppo non trovato")
        val recipients = group.memberIds
            .filter { it != identity.id }
            .mapNotNull { id -> state.contacts.firstOrNull { it.id == id && !it.blocked } }
        require(recipients.isNotEmpty()) { "Il gruppo non ha membri raggiungibili" }
        require(state.outbound.size + recipients.size <= MAX_QUEUE) { "Coda di invio piena" }
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val message = ChatMessage(
            id = messageId,
            conversationId = group.id,
            senderId = identity.id,
            recipientId = "",
            body = payload.body,
            kind = payload.kind,
            attachmentBase64 = payload.attachmentBase64,
            attachmentMime = payload.attachmentMime,
            attachmentName = payload.attachmentName,
            sentAt = now,
            status = DeliveryStatus.QUEUED,
            replyTo = payload.replyTo,
            groupId = group.id,
        )
        val items = recipients.map { contact ->
            OutboundItem(
                id = UUID.randomUUID().toString(),
                messageId = messageId,
                contactId = contact.id,
                createdAt = now,
                expiresAt = now + MESSAGE_TTL_MS,
            )
        }
        update(
            state.copy(
                messages = (state.messages + message).takeLast(MAX_MESSAGES),
                outbound = state.outbound + items,
            ),
        )
        messageId
    }

    fun envelopeFor(item: OutboundItem): WireEnvelope {
        val state = mutableState.value
        val identity = requireNotNull(state.identity)
        val contact = state.contacts.first { it.id == item.contactId }
        val message = state.messages.first { it.id == item.messageId }
        val group = message.groupId.takeIf(String::isNotBlank)
            ?.let { id -> state.groups.firstOrNull { it.id == id } }
        val payload = MessagePayload(
            body = message.body,
            kind = message.kind,
            attachmentBase64 = message.attachmentBase64,
            attachmentMime = message.attachmentMime,
            attachmentName = message.attachmentName,
            replyTo = message.replyTo,
            groupId = message.groupId,
            groupName = group?.name.orEmpty(),
            groupMemberIds = group?.memberIds.orEmpty(),
            groupRevision = group?.revision ?: 0,
        )
        return crypto.createEnvelope(
            identity = identity,
            contact = contact,
            payload = payload,
            messageId = item.id,
            sentAt = message.sentAt,
        )
    }

    suspend fun receiveEnvelope(envelope: WireEnvelope): DeliveryReceipt = mutex.withLock {
        val state = mutableState.value
        val identity = requireNotNull(state.identity)
        val contact = state.contacts.firstOrNull { it.id == envelope.senderId }
            ?: throw SecurityException("Mittente non autorizzato")
        require(!contact.blocked) { "Mittente bloccato" }
        if (envelope.messageId in state.receivedMessageIds) {
            return@withLock crypto.createReceipt(identity, envelope.messageId)
        }
        val payload = crypto.verifyAndDecrypt(identity, contact, envelope)
        require(replay.accept(envelope.messageId)) { "Messaggio duplicato" }
        var groups = state.groups
        if (payload.groupId.isNotBlank()) {
            val existing = groups.firstOrNull { it.id == payload.groupId }
            GroupPolicy.validateIncoming(
                existing = existing,
                senderId = contact.id,
                recipientId = identity.id,
                incomingMembers = payload.groupMemberIds,
                incomingRevision = payload.groupRevision,
            )
            if (existing == null || payload.groupRevision > existing.revision) {
                val receivedGroup = PrivateGroup(
                    id = payload.groupId,
                    name = payload.groupName.take(60).ifBlank { "Gruppo privato" },
                    memberIds = payload.groupMemberIds.distinct(),
                    ownerId = existing?.ownerId ?: contact.id,
                    revision = payload.groupRevision.coerceAtLeast(1),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
                groups = groups.filterNot { it.id == receivedGroup.id } + receivedGroup
            }
        }
        val message = ChatMessage(
            id = envelope.messageId,
            conversationId = payload.groupId.ifBlank { contact.id },
            senderId = contact.id,
            recipientId = identity.id,
            body = payload.body,
            kind = payload.kind,
            attachmentBase64 = payload.attachmentBase64,
            attachmentMime = payload.attachmentMime,
            attachmentName = payload.attachmentName,
            sentAt = envelope.sentAt,
            status = DeliveryStatus.DELIVERED,
            replyTo = payload.replyTo,
            groupId = payload.groupId,
        )
        update(
            state.copy(
                messages = (state.messages + message).takeLast(MAX_MESSAGES),
                receivedMessageIds = replay.snapshot(),
                groups = groups,
            ),
        )
        crypto.createReceipt(identity, envelope.messageId)
    }

    fun dueOutbound(now: Long = System.currentTimeMillis()): List<OutboundItem> =
        mutableState.value.outbound.filter { it.nextAttemptAt <= now }.take(20)

    suspend fun markSending(itemId: String) = mutate {
        val messageId = outbound.firstOrNull { it.id == itemId }?.messageId
        copy(messages = messages.map {
            if (it.id == messageId) it.copy(status = DeliveryStatus.SENDING) else it
        })
    }

    suspend fun markDelivered(itemId: String, receipt: DeliveryReceipt) = mutex.withLock {
        val state = mutableState.value
        val item = state.outbound.firstOrNull { it.id == itemId } ?: return@withLock
        val contact = state.contacts.first { it.id == item.contactId }
        crypto.verifyReceipt(contact, receipt, item.id)
        val remaining = state.outbound.filterNot { it.id == itemId }
        val allDelivered = remaining.none { it.messageId == item.messageId }
        update(
            state.copy(
                outbound = remaining,
                messages = state.messages.map {
                    if (it.id == item.messageId && allDelivered) {
                        it.copy(status = DeliveryStatus.DELIVERED)
                    } else it
                },
            ),
        )
    }

    suspend fun markTemporaryFailure(itemId: String, error: String) = mutex.withLock {
        val state = mutableState.value
        val item = state.outbound.firstOrNull { it.id == itemId } ?: return@withLock
        val now = System.currentTimeMillis()
        val attempts = item.attempts + 1
        val expired = item.expiresAt <= now
        val updatedItem = item.copy(
            attempts = attempts,
            nextAttemptAt = now + RetryPolicy.delayForAttempt(attempts),
            lastError = error.take(160),
        )
        update(
            state.copy(
                outbound = if (expired) {
                    state.outbound.filterNot { it.id == itemId }
                } else {
                    state.outbound.map { if (it.id == itemId) updatedItem else it }
                },
                messages = state.messages.map {
                    if (it.id == item.messageId) {
                        it.copy(
                            status = if (expired) {
                                DeliveryStatus.EXPIRED
                            } else {
                                DeliveryStatus.TEMPORARY_FAILURE
                            },
                        )
                    } else it
                },
            ),
        )
    }

    suspend fun createGroup(name: String, memberIds: List<String>): PrivateGroup = mutex.withLock {
        val state = mutableState.value
        val identity = requireNotNull(state.identity)
        val validMembers = memberIds.distinct().filter { id -> state.contacts.any { it.id == id } }
        require(name.trim().length in 2..60) { "Nome gruppo non valido" }
        require(validMembers.size in 1..19) { "Seleziona da 1 a 19 contatti" }
        val group = PrivateGroup(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            memberIds = listOf(identity.id) + validMembers,
            ownerId = identity.id,
            createdAt = System.currentTimeMillis(),
        )
        update(state.copy(groups = state.groups + group))
        group
    }

    suspend fun removeGroupMember(groupId: String, memberId: String) = mutex.withLock {
        val state = mutableState.value
        val identity = requireNotNull(state.identity)
        val group = state.groups.first { it.id == groupId }
        require(group.ownerId == identity.id) { "Solo il proprietario può rimuovere membri" }
        require(memberId != identity.id) { "Il proprietario non può rimuovere sé stesso" }
        update(
            state.copy(
                groups = state.groups.map {
                    if (it.id == groupId) {
                        it.copy(memberIds = it.memberIds - memberId, revision = it.revision + 1)
                    } else it
                },
            ),
        )
    }

    suspend fun deleteMessageLocally(messageId: String) = mutate {
        copy(
            messages = messages.filterNot { it.id == messageId },
            outbound = outbound.filterNot { it.messageId == messageId },
        )
    }

    suspend fun deleteAll() = mutex.withLock {
        store.deleteIdentityAndData()
        mutableState.value = AppState()
        replay = ReplayProtector()
    }

    private suspend fun mutate(block: AppState.() -> AppState) = mutex.withLock {
        update(mutableState.value.block())
    }

    private suspend fun update(state: AppState) {
        mutableState.value = state
        store.save(state)
    }

    companion object {
        private const val MAX_QUEUE = 1_000
        private const val MAX_MESSAGES = 5_000
        private const val MESSAGE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
