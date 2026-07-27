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
import org.brotherhood.app.core.PayloadValidator
import org.brotherhood.app.core.AttachmentChunks
import org.brotherhood.app.core.VoiceTransferAssembler
import org.brotherhood.app.crypto.CryptoEngine
import org.brotherhood.app.model.AppState
import org.brotherhood.app.model.ChatMessage
import org.brotherhood.app.model.Contact
import org.brotherhood.app.model.DeliveryReceipt
import org.brotherhood.app.model.DeliveryStatus
import org.brotherhood.app.model.ImportInviteResult
import org.brotherhood.app.model.MessagePayload
import org.brotherhood.app.model.MessageKind
import org.brotherhood.app.model.NetworkFrame
import org.brotherhood.app.model.OutboundItem
import org.brotherhood.app.model.PrivateGroup
import org.brotherhood.app.model.AvailabilityMode
import org.brotherhood.app.model.TorIdentity
import org.brotherhood.app.model.WireEnvelope
import org.brotherhood.app.storage.PinHasher
import org.brotherhood.app.storage.SecureStateStore
import org.brotherhood.app.storage.StateMigrations
import org.brotherhood.app.transport.RecipientEndpoint

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
        val migrated = StateMigrations.migrate(loaded)
        mutableState.value = migrated
        replay = ReplayProtector(migrated.receivedMessageIds)
        if (migrated != loaded) store.save(migrated)
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
        val snapshot = mutableState.value
        val identity = requireNotNull(snapshot.identity) { "Identità non disponibile" }
        val tor = snapshot.torIdentity
        return crypto.createInvite(
            identity = identity,
            endpointHost = host,
            endpointPort = port,
            torOnion = tor?.onionAddress.orEmpty(),
            torPort = 80,
            endpointRevision = tor?.revision ?: 1,
        )
    }

    suspend fun importInvite(raw: String): ImportInviteResult = mutex.withLock {
        runCatching {
            val card = crypto.parseAndVerifyInvite(raw)
            val identity = requireNotNull(mutableState.value.identity)
            require(card.id != identity.id) { "Non puoi aggiungere la tua identità" }
            val incoming = crypto.contactFrom(card)
            val existing = mutableState.value.contacts.firstOrNull { it.id == incoming.id }
            if (existing != null) {
                require(incoming.endpointRevision >= existing.endpointRevision) {
                    "Aggiornamento endpoint obsoleto"
                }
                if (incoming.endpointRevision == existing.endpointRevision) {
                    require(
                        incoming.torOnion == existing.torOnion &&
                            incoming.torPort == existing.torPort,
                    ) { "Endpoint Tor modificato senza nuova revisione" }
                }
            }
            val contact = if (existing == null) incoming else incoming.copy(
                localAlias = existing.localAlias,
                blocked = existing.blocked,
                verified = existing.verified,
                addedAt = existing.addedAt,
                torEndpointRevoked =
                    existing.torEndpointRevoked &&
                        incoming.endpointRevision <= existing.endpointRevision,
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

    suspend fun setContactTorRevoked(contactId: String, revoked: Boolean) = mutate {
        copy(
            contacts = contacts.map {
                if (it.id == contactId) it.copy(torEndpointRevoked = revoked) else it
            },
        )
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
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val chunkCount = voiceChunkCount(payload)
        require(state.outbound.size + chunkCount <= MAX_QUEUE) { "Coda di invio piena" }
        val validatedPayload = preparePayloadForValidation(payload, messageId)
        PayloadValidator.validate(validatedPayload)
        val message = ChatMessage(
            id = messageId,
            conversationId = payload.groupId.ifBlank { contactId },
            senderId = identity.id,
            recipientId = contactId,
            body = validatedPayload.body,
            kind = validatedPayload.kind,
            attachmentBase64 = validatedPayload.attachmentBase64,
            attachmentMime = validatedPayload.attachmentMime,
            attachmentName = validatedPayload.attachmentName,
            attachmentSha256 = validatedPayload.attachmentSha256,
            durationMillis = validatedPayload.durationMillis,
            sentAt = now,
            status = DeliveryStatus.QUEUED,
            replyTo = validatedPayload.replyTo,
            groupId = validatedPayload.groupId,
        )
        val outbound = (0 until chunkCount).map { chunkIndex ->
            OutboundItem(
                id = UUID.randomUUID().toString(),
                messageId = messageId,
                contactId = contactId,
                chunkIndex = chunkIndex,
                chunkCount = chunkCount,
                createdAt = now,
                expiresAt = now + MESSAGE_TTL_MS,
            )
        }
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
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val chunkCount = voiceChunkCount(payload)
        require(state.outbound.size + recipients.size * chunkCount <= MAX_QUEUE) {
            "Coda di invio piena"
        }
        val validatedPayload = preparePayloadForValidation(
            payload.copy(groupId = groupId),
            messageId,
        )
        PayloadValidator.validate(validatedPayload)
        val message = ChatMessage(
            id = messageId,
            conversationId = group.id,
            senderId = identity.id,
            recipientId = "",
            body = validatedPayload.body,
            kind = validatedPayload.kind,
            attachmentBase64 = validatedPayload.attachmentBase64,
            attachmentMime = validatedPayload.attachmentMime,
            attachmentName = validatedPayload.attachmentName,
            attachmentSha256 = validatedPayload.attachmentSha256,
            durationMillis = validatedPayload.durationMillis,
            sentAt = now,
            status = DeliveryStatus.QUEUED,
            replyTo = validatedPayload.replyTo,
            groupId = group.id,
        )
        val items = recipients.flatMap { contact ->
            (0 until chunkCount).map { chunkIndex ->
                OutboundItem(
                    id = UUID.randomUUID().toString(),
                    messageId = messageId,
                    contactId = contact.id,
                    chunkIndex = chunkIndex,
                    chunkCount = chunkCount,
                    createdAt = now,
                    expiresAt = now + MESSAGE_TTL_MS,
                )
            }
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
        val attachment = if (message.kind == MessageKind.VOICE) {
            AttachmentChunks.chunk(message.attachmentBase64, item.chunkIndex)
        } else {
            message.attachmentBase64 to
                message.attachmentBase64.takeIf(String::isNotBlank)
                    ?.let(AttachmentChunks::totalBytes)
                    .orZero()
        }
        val payload = MessagePayload(
            body = message.body,
            kind = message.kind,
            attachmentBase64 = attachment.first,
            attachmentMime = message.attachmentMime,
            attachmentName = message.attachmentName,
            attachmentSha256 = message.attachmentSha256,
            durationMillis = message.durationMillis,
            logicalMessageId = message.id,
            attachmentChunkIndex = item.chunkIndex,
            attachmentChunkCount = item.chunkCount,
            attachmentTotalBytes = attachment.second,
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

    fun frameFor(item: OutboundItem): NetworkFrame {
        val identity = requireNotNull(mutableState.value.identity)
        return crypto.createNetworkFrame(identity, envelopeFor(item))
    }

    fun endpointFor(item: OutboundItem): RecipientEndpoint {
        val contact = mutableState.value.contacts.first { it.id == item.contactId }
        return RecipientEndpoint(
            contactId = contact.id,
            lanHost = contact.endpointHost,
            lanPort = contact.endpointPort,
            torOnion = contact.torOnion,
            torPort = contact.torPort,
            torRevoked = contact.torEndpointRevoked,
        )
    }

    suspend fun receiveFrame(frame: NetworkFrame): DeliveryReceipt {
        val snapshot = mutableState.value
        val identity = requireNotNull(snapshot.identity)
        val contact = snapshot.contacts.firstOrNull { it.id == frame.senderId }
            ?: throw SecurityException("Mittente non autorizzato")
        require(!contact.blocked) { "Mittente bloccato" }
        crypto.verifyNetworkFrame(identity, contact, frame)
        return receiveEnvelope(frame.envelope)
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
        var payload = crypto.verifyAndDecrypt(identity, contact, envelope)
        PayloadValidator.validate(payload)
        val now = System.currentTimeMillis()
        var incomingTransfers = state.incomingVoiceTransfers
            .filter { now - it.updatedAt <= MESSAGE_TTL_MS }
        if (payload.kind == MessageKind.VOICE && payload.attachmentChunkCount > 1) {
            val existing = incomingTransfers.firstOrNull {
                it.senderId == contact.id && it.logicalMessageId == payload.logicalMessageId
            }
            require(existing != null || incomingTransfers.size < MAX_INCOMING_TRANSFERS) {
                "Troppi trasferimenti in corso"
            }
            val progress = VoiceTransferAssembler.accept(existing, contact.id, payload, now)
            incomingTransfers = incomingTransfers.filterNot {
                it.senderId == contact.id && it.logicalMessageId == payload.logicalMessageId
            } + listOfNotNull(progress.transfer)
            if (progress.completedPayload == null) {
                require(replay.accept(envelope.messageId)) { "Messaggio duplicato" }
                update(
                    state.copy(
                        receivedMessageIds = replay.snapshot(),
                        incomingVoiceTransfers = incomingTransfers,
                    ),
                )
                return@withLock crypto.createReceipt(identity, envelope.messageId)
            }
            payload = progress.completedPayload
        }
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
        require(replay.accept(envelope.messageId)) { "Messaggio duplicato" }
        val logicalMessageId = payload.logicalMessageId.ifBlank { envelope.messageId }
        if (state.messages.any { it.id == logicalMessageId && it.senderId == contact.id }) {
            update(
                state.copy(
                    receivedMessageIds = replay.snapshot(),
                    incomingVoiceTransfers = incomingTransfers,
                    groups = groups,
                ),
            )
            return@withLock crypto.createReceipt(identity, envelope.messageId)
        }
        val message = ChatMessage(
            id = logicalMessageId,
            conversationId = payload.groupId.ifBlank { contact.id },
            senderId = contact.id,
            recipientId = identity.id,
            body = payload.body,
            kind = payload.kind,
            attachmentBase64 = payload.attachmentBase64,
            attachmentMime = payload.attachmentMime,
            attachmentName = payload.attachmentName,
            attachmentSha256 = payload.attachmentSha256,
            durationMillis = payload.durationMillis,
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
                incomingVoiceTransfers = incomingTransfers,
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

    suspend fun saveTorIdentity(torIdentity: TorIdentity) = mutate {
        copy(
            torIdentity = torIdentity,
            torEndpointRevision = maxOf(torEndpointRevision, torIdentity.revision),
        )
    }

    suspend fun revokeTorIdentity() = mutate {
        copy(
            torIdentity = null,
            torEndpointRevision = torEndpointRevision + 1,
        )
    }

    suspend fun setAvailabilityMode(mode: AvailabilityMode) = mutate {
        copy(preferences = preferences.copy(availabilityMode = mode))
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

    private fun voiceChunkCount(payload: MessagePayload): Int =
        if (payload.kind == MessageKind.VOICE) AttachmentChunks.count(payload.attachmentBase64)
        else 1

    private fun preparePayloadForValidation(
        payload: MessagePayload,
        logicalMessageId: String,
    ): MessagePayload {
        if (payload.kind != MessageKind.VOICE) return payload
        return payload.copy(
            logicalMessageId = logicalMessageId,
            attachmentChunkIndex = 0,
            attachmentChunkCount = 1,
            attachmentTotalBytes = AttachmentChunks.totalBytes(payload.attachmentBase64),
        )
    }

    private fun Int?.orZero(): Int = this ?: 0

    companion object {
        private const val MAX_QUEUE = 1_000
        private const val MAX_MESSAGES = 5_000
        private const val MAX_INCOMING_TRANSFERS = 8
        private const val MESSAGE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
