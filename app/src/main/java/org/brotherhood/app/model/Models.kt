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
    val availabilityMode: AvailabilityMode = AvailabilityMode.WHEN_OPEN,
)

@Serializable
enum class AvailabilityMode {
    ALWAYS,
    BALANCED,
    WHEN_OPEN,
}

@Serializable
data class AppState(
    val schemaVersion: Int = 1,
    val identity: LocalIdentity? = null,
    val pinSalt: String = "",
    val pinHash: String = "",
    val contacts: List<Contact> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val outbound: List<OutboundItem> = emptyList(),
    val receivedMessageIds: Set<String> = emptySet(),
    val groups: List<PrivateGroup> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
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
    val replyTo: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val groupMemberIds: List<String> = emptyList(),
    val groupRevision: Int = 0,
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
