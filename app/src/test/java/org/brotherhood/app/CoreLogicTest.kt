package org.brotherhood.app

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.brotherhood.app.core.AttachmentChunks
import org.brotherhood.app.core.FileChunks
import org.brotherhood.app.core.GroupPolicy
import org.brotherhood.app.core.PayloadValidator
import org.brotherhood.app.core.ReplayProtector
import org.brotherhood.app.core.RetryPolicy
import org.brotherhood.app.core.VoiceTransferAssembler
import org.brotherhood.app.model.AppPreferences
import org.brotherhood.app.model.PrivateGroup
import org.brotherhood.app.model.AppState
import org.brotherhood.app.model.AvailabilityMode
import org.brotherhood.app.model.Contact
import org.brotherhood.app.model.MessageKind
import org.brotherhood.app.model.MessagePayload
import org.brotherhood.app.storage.StateMigrations
import org.brotherhood.app.transport.LanEndpointPolicy
import org.brotherhood.app.transport.RecipientEndpoint
import org.brotherhood.app.transport.RequestRateLimiter
import org.brotherhood.app.transport.TransportPolicy
import org.brotherhood.app.transport.TransportType
import org.junit.Test

class CoreLogicTest {
    @Test
    fun replayProtectorRejectsDuplicatesAndKeepsBoundedHistory() {
        val replay = ReplayProtector(capacity = 3)
        assertTrue(replay.accept("a"))
        assertFalse(replay.accept("a"))
        assertTrue(replay.accept("b"))
        assertTrue(replay.accept("c"))
        assertTrue(replay.accept("d"))
        assertTrue(replay.accept("a"))
        assertEquals(3, replay.snapshot().size)
    }

    @Test
    fun retryUsesCappedExponentialBackoff() {
        assertEquals(0, RetryPolicy.delayForAttempt(0))
        assertEquals(5_000, RetryPolicy.delayForAttempt(1))
        assertEquals(10_000, RetryPolicy.delayForAttempt(2))
        assertEquals(30L * 60 * 1000, RetryPolicy.delayForAttempt(30))
    }

    @Test
    fun chunksRebuildOnlyWithMatchingIntegrityHash() {
        val source = ByteArray(350_000) { (it % 251).toByte() }
        val chunks = FileChunks.split(source)
        val rebuilt = FileChunks.join(chunks, FileChunks.sha256(source))
        assertContentEquals(source, rebuilt)
    }

    @Test
    fun removedGroupMemberCannotSendWithOldRevision() {
        val group = PrivateGroup(
            id = "group",
            name = "Amici",
            memberIds = listOf("owner", "recipient"),
            ownerId = "owner",
            revision = 3,
            createdAt = 1,
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GroupPolicy.validateIncoming(
                existing = group,
                senderId = "removed",
                recipientId = "recipient",
                incomingMembers = listOf("owner", "recipient", "removed"),
                incomingRevision = 2,
            )
        }
    }

    @Test
    fun ordinaryMemberCannotReplaceGroupMembership() {
        val group = PrivateGroup(
            id = "group",
            name = "Amici",
            memberIds = listOf("owner", "member", "recipient"),
            ownerId = "owner",
            revision = 3,
            createdAt = 1,
        )
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GroupPolicy.validateIncoming(
                existing = group,
                senderId = "member",
                recipientId = "recipient",
                incomingMembers = listOf("member", "recipient"),
                incomingRevision = 4,
            )
        }
    }

    @Test
    fun alpha01StateMigratesWithoutInventingTorEndpoint() {
        val legacy = AppState(
            schemaVersion = 1,
            contacts = listOf(
                Contact(
                    id = "contact",
                    displayName = "Contatto",
                    fingerprint = "AA",
                    encryptionPublicKeyset = "enc",
                    signingPublicKeyset = "sig",
                    endpointHost = "192.0.2.5",
                    endpointPort = 42337,
                    addedAt = 1,
                ),
            ),
        )
        val migrated = StateMigrations.migrate(legacy)
        assertEquals(2, migrated.schemaVersion)
        assertEquals("", migrated.contacts.single().torOnion)
        assertEquals("192.0.2.5", migrated.contacts.single().endpointHost)
    }

    @Test
    fun transportPolicyPrefersLanThenTorAndHonoursRevocation() {
        val onion = "a".repeat(56) + ".onion"
        val both = RecipientEndpoint(
            contactId = "c",
            lanHost = "192.0.2.5",
            lanPort = 42337,
            torOnion = onion,
        )
        assertEquals(listOf(TransportType.LAN, TransportType.TOR), TransportPolicy.preferredOrder(both))
        assertEquals(
            listOf(TransportType.LAN),
            TransportPolicy.preferredOrder(both.copy(torRevoked = true)),
        )
    }

    @Test
    fun isolatedEmulatorLanEndpointIsNeverAdvertisedOrRouted() {
        assertFalse(LanEndpointPolicy.isAdvertisable("10.0.2.15"))
        assertFalse(LanEndpointPolicy.isAdvertisable("10.0.2.16"))
        assertTrue(LanEndpointPolicy.isAdvertisable("192.168.1.42"))
        assertEquals(
            listOf(TransportType.TOR),
            TransportPolicy.preferredOrder(
                RecipientEndpoint(
                    contactId = "bob",
                    lanHost = "10.0.2.15",
                    lanPort = 42337,
                    torOnion = "a".repeat(56) + ".onion",
                ),
            ),
        )
    }

    @Test
    fun requestRateLimiterRejectsBurstsAndRecoversAfterWindow() {
        var now = 0L
        val limiter = RequestRateLimiter(
            perSourceLimit = 2,
            globalLimit = 3,
            windowMillis = 1_000,
            clock = { now },
        )
        assertTrue(limiter.allow("source-a"))
        assertTrue(limiter.allow("source-a"))
        assertFalse(limiter.allow("source-a"))
        assertTrue(limiter.allow("source-b"))
        assertFalse(limiter.allow("source-c"))
        now = 1_001
        assertTrue(limiter.allow("source-a"))
    }

    @Test
    fun balancedAvailabilityIsThePrivacyPreservingDefault() {
        assertEquals(AvailabilityMode.BALANCED, AppPreferences().availabilityMode)
    }

    @Test
    fun allBackgroundModesRoundTripThroughPersistedState() {
        val json = Json { encodeDefaults = true }
        AvailabilityMode.entries.forEach { mode ->
            val state = AppState(
                preferences = AppPreferences(availabilityMode = mode),
            )
            assertEquals(
                mode,
                json.decodeFromString<AppState>(json.encodeToString(state))
                    .preferences
                    .availabilityMode,
            )
        }
    }

    @Test
    fun voicePayloadRequiresMatchingIntegrityHashAndBoundedDuration() {
        val bytes = ByteArray(4_096) { (it % 113).toByte() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val payload = MessagePayload(
            body = "Messaggio vocale",
            kind = MessageKind.VOICE,
            attachmentBase64 = Base64.getEncoder().encodeToString(bytes),
            attachmentMime = "audio/ogg",
            attachmentName = "vocale.ogg",
            attachmentSha256 = hash,
            durationMillis = 2_000,
            logicalMessageId = "voice-message-id-0001",
            attachmentTotalBytes = bytes.size,
        )

        PayloadValidator.validate(payload)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PayloadValidator.validate(payload.copy(attachmentSha256 = "0".repeat(64)))
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PayloadValidator.validate(payload.copy(durationMillis = 60_001))
        }
    }

    @Test
    fun voiceChunksResumeOutOfOrderRebuildAndSurviveSerializedRestart() {
        val bytes = ByteArray(180_000) { (it % 239).toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val count = AttachmentChunks.count(encoded)
        val base = MessagePayload(
            body = "Messaggio vocale",
            kind = MessageKind.VOICE,
            attachmentMime = "audio/ogg",
            attachmentName = "vocale.ogg",
            attachmentSha256 = hash,
            durationMillis = 8_000,
            logicalMessageId = "voice-message-id-0002",
            attachmentChunkCount = count,
            attachmentTotalBytes = bytes.size,
            groupId = "group-id",
            groupName = "Amici",
            groupMemberIds = listOf("sender", "recipient"),
            groupRevision = 2,
        )
        var progress = org.brotherhood.app.core.VoiceTransferProgress(null, null)
        listOf(1, 0).forEach { index ->
            val chunk = AttachmentChunks.chunk(encoded, index)
            val payload = base.copy(
                attachmentBase64 = chunk.first,
                attachmentChunkIndex = index,
            )
            PayloadValidator.validate(payload)
            progress = VoiceTransferAssembler.accept(
                progress.transfer,
                "sender",
                payload,
                100L + index,
            )
        }

        val persisted = AppState(incomingVoiceTransfers = listOf(requireNotNull(progress.transfer)))
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString<AppState>(json.encodeToString(persisted))
        progress = org.brotherhood.app.core.VoiceTransferProgress(
            restored.incomingVoiceTransfers.single(),
            null,
        )

        (2 until count).forEach { index ->
            val chunk = AttachmentChunks.chunk(encoded, index)
            progress = VoiceTransferAssembler.accept(
                progress.transfer,
                "sender",
                base.copy(
                    attachmentBase64 = chunk.first,
                    attachmentChunkIndex = index,
                ),
                200L + index,
            )
        }

        val completed = requireNotNull(progress.completedPayload)
        assertContentEquals(bytes, Base64.getDecoder().decode(completed.attachmentBase64))
        assertEquals("group-id", completed.groupId)
        assertEquals(1, completed.attachmentChunkCount)
    }

    @Test
    fun payloadValidatorRejectsUnexpectedOrOversizedContent() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PayloadValidator.validate(
                MessagePayload(
                    body = "test",
                    kind = MessageKind.TEXT,
                    attachmentBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1)),
                    attachmentMime = "application/octet-stream",
                ),
            )
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PayloadValidator.validate(
                MessagePayload(
                    body = "x".repeat(8_001),
                    kind = MessageKind.TEXT,
                ),
            )
        }
    }
}
