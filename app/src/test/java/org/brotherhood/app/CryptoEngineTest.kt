package org.brotherhood.app

import java.security.GeneralSecurityException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.brotherhood.app.crypto.CryptoEngine
import org.brotherhood.app.model.MessageKind
import org.brotherhood.app.model.MessagePayload
import org.junit.Test

class CryptoEngineTest {
    private val crypto = CryptoEngine()

    @Test
    fun identityInviteSignatureAndEncryptedMessageRoundTrip() {
        val now = 1_800_000_000_000L
        val alice = crypto.generateIdentity("Alice", now)
        val bob = crypto.generateIdentity("Bob", now)
        val aliceCard = crypto.parseAndVerifyInvite(
            crypto.createInvite(alice, "192.0.2.10", 42337, now),
            now,
        )
        val bobCard = crypto.parseAndVerifyInvite(
            crypto.createInvite(bob, "192.0.2.11", 42337, now),
            now,
        )
        val aliceContact = crypto.contactFrom(aliceCard, verified = true)
        val bobContact = crypto.contactFrom(bobCard, verified = true)

        val envelope = crypto.createEnvelope(
            identity = alice,
            contact = bobContact,
            payload = MessagePayload("ciao", MessageKind.TEXT),
            sentAt = now,
        )
        val decrypted = crypto.verifyAndDecrypt(bob, aliceContact, envelope, now)

        assertEquals("ciao", decrypted.body)
        assertEquals(MessageKind.TEXT, decrypted.kind)
        assertTrue(alice.fingerprint.matches(Regex("([0-9A-F]{2}:){11}[0-9A-F]{2}")))
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val now = 1_800_000_000_000L
        val alice = crypto.generateIdentity("Alice", now)
        val bob = crypto.generateIdentity("Bob", now)
        val aliceContact = crypto.contactFrom(
            crypto.parseAndVerifyInvite(crypto.createInvite(alice, "192.0.2.10", 42337, now), now),
        )
        val bobContact = crypto.contactFrom(
            crypto.parseAndVerifyInvite(crypto.createInvite(bob, "192.0.2.11", 42337, now), now),
        )
        val envelope = crypto.createEnvelope(
            alice,
            bobContact,
            MessagePayload("messaggio autentico", MessageKind.TEXT),
            sentAt = now,
        )
        val tampered = envelope.copy(ciphertext = envelope.ciphertext.reversed())

        assertFailsWith<GeneralSecurityException> {
            crypto.verifyAndDecrypt(bob, aliceContact, tampered, now)
        }
    }

    @Test
    fun modelSerializationIsStable() {
        val payload = MessagePayload(
            body = "test",
            kind = MessageKind.TEXT,
            groupId = "g1",
            groupName = "Amici",
            groupMemberIds = listOf("a", "b", "c"),
            groupRevision = 2,
        )
        val json = Json { encodeDefaults = true }
        assertEquals(payload, json.decodeFromString(json.encodeToString(payload)))
    }
}
