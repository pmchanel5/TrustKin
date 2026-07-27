package org.brotherhood.app.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalIdentity(
    val id: String,
    val displayName: String,
    val fingerprint: String,
    val encryptionPrivateKeyset: String,
    val encryptionPublicKeyset: String,
    val signingPrivateKeyset: String,
    val signingPublicKeyset: String,
    val createdAt: Long,
)

@Serializable
data class Contact(
    val id: String,
    val displayName: String,
    val localAlias: String = "",
    val fingerprint: String,
    val encryptionPublicKeyset: String,
    val signingPublicKeyset: String,
    val endpointHost: String,
    val endpointPort: Int,
    val torOnion: String = "",
    val torPort: Int = 80,
    val endpointRevision: Int = 1,
    val torEndpointRevoked: Boolean = false,
    val blocked: Boolean = false,
    val verified: Boolean = false,
    val addedAt: Long,
) {
    val effectiveName: String
        get() = localAlias.ifBlank { displayName }
}

@Serializable
enum class MessageKind {
    TEXT,
    IMAGE,
    VOICE,
    SYSTEM,
}

@Serializable
enum class DeliveryStatus {
    PREPARING,
    QUEUED,
    SENDING,
    DELIVERED,
    TEMPORARY_FAILURE,
    PERMANENT_FAILURE,
    EXPIRED,
}

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val kind: MessageKind = MessageKind.TEXT,
    val attachmentBase64: String = "",
    val attachmentMime: String = "",
    val attachmentName: String = "",
    val attachmentSha256: String = "",
    val durationMillis: Long = 0,
    val sentAt: Long,
    val status: DeliveryStatus,
    val replyTo: String = "",
    val groupId: String = "",
)

@Serializable
data class OutboundItem(
    val id: String,
    val messageId: String,
    val contactId: String,
    val chunkIndex: Int = 0,
    val chunkCount: Int = 1,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val createdAt: Long,
    val expiresAt: Long,
    val lastError: String = "",
)

@Serializable
data class PrivateGroup(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val ownerId: String,
    val revision: Int = 1,
    val createdAt: Long,
)

@Serializable
data class AppPreferences(
    val confidentialPreviews: Boolean = true,
    val readReceipts: Boolean = false,
    val availabilityMode: AvailabilityMode = AvailabilityMode.BALANCED,
)

@Serializable
enum class AvailabilityMode {
    ALWAYS,
    BALANCED,
    WHEN_OPEN,
}

@Serializable
data class AppState(
    val schemaVersion: Int = 2,
    val identity: LocalIdentity? = null,
    val pinSalt: String = "",
    val pinHash: String = "",
    val contacts: List<Contact> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val outbound: List<OutboundItem> = emptyList(),
    val receivedMessageIds: Set<String> = emptySet(),
    val groups: List<PrivateGroup> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val torIdentity: TorIdentity? = null,
    val torEndpointRevision: Int = 1,
    val incomingVoiceTransfers: List<IncomingVoiceTransfer> = emptyList(),
)

@Serializable
data class TorIdentity(
    val onionAddress: String,
    val privateKey: String,
    val revision: Int = 1,
    val createdAt: Long,
)

@Serializable
data class ContactCard(
    val version: Int = 1,
    val id: String,
    val displayName: String,
    val fingerprint: String,
    val encryptionPublicKeyset: String,
    val signingPublicKeyset: String,
    val endpointHost: String,
    val endpointPort: Int,
    val torOnion: String = "",
    val torPort: Int = 80,
    val endpointRevision: Int = 1,
    val issuedAt: Long,
    val expiresAt: Long,
    val nonce: String,
)

@Serializable
data class SignedInvite(
    val card: ContactCard,
    val signature: String,
)

@Serializable
data class MessagePayload(
    val body: String,
    val kind: MessageKind,
    val attachmentBase64: String = "",
    val attachmentMime: String = "",
    val attachmentName: String = "",
    val attachmentSha256: String = "",
    val durationMillis: Long = 0,
    val logicalMessageId: String = "",
    val attachmentChunkIndex: Int = 0,
    val attachmentChunkCount: Int = 1,
    val attachmentTotalBytes: Int = 0,
    val replyTo: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val groupMemberIds: List<String> = emptyList(),
    val groupRevision: Int = 0,
)

@Serializable
data class IncomingVoiceTransfer(
    val senderId: String,
    val logicalMessageId: String,
    val template: MessagePayload,
    val chunks: Map<Int, String>,
    val updatedAt: Long,
)

@Serializable
data class WireEnvelope(
    val version: Int = 1,
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val sentAt: Long,
    val ciphertext: String,
    val signature: String,
)

@Serializable
data class NetworkFrame(
    val version: Int = 2,
    val senderId: String,
    val recipientId: String,
    val nonce: String,
    val timestamp: Long,
    val envelope: WireEnvelope,
    val signature: String,
)

@Serializable
data class DeliveryReceipt(
    val version: Int = 1,
    val messageId: String,
    val recipientId: String,
    val receivedAt: Long,
    val signature: String,
)

sealed interface ImportInviteResult {
    data class Success(val contact: Contact) : ImportInviteResult
    data class Failure(val reason: String) : ImportInviteResult
}
