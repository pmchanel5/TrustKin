package org.brotherhood.app.crypto

import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.brotherhood.app.model.Contact
import org.brotherhood.app.model.ContactCard
import org.brotherhood.app.model.DeliveryReceipt
import org.brotherhood.app.model.LocalIdentity
import org.brotherhood.app.model.MessagePayload
import org.brotherhood.app.model.NetworkFrame
import org.brotherhood.app.model.SignedInvite
import org.brotherhood.app.model.WireEnvelope

class CryptoEngine {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    init {
        TinkConfig.register()
    }

    fun generateIdentity(displayName: String, now: Long = System.currentTimeMillis()): LocalIdentity {
        require(displayName.trim().length in 2..40) { "Il nome deve contenere da 2 a 40 caratteri" }
        val encryptionPrivate = KeysetHandle.generateNew(
            HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM,
        )
        val signingPrivate = KeysetHandle.generateNew(SignatureKeyTemplates.ECDSA_P256)
        val encryptionPublic = encryptionPrivate.publicKeysetHandle
        val signingPublic = signingPrivate.publicKeysetHandle
        val encryptionPublicBytes = serialize(encryptionPublic)
        val signingPublicBytes = serialize(signingPublic)
        val identityMaterial = encryptionPublicBytes + signingPublicBytes
        return LocalIdentity(
            id = stableId(identityMaterial),
            displayName = displayName.trim(),
            fingerprint = fingerprint(identityMaterial),
            encryptionPrivateKeyset = b64(serialize(encryptionPrivate)),
            encryptionPublicKeyset = b64(encryptionPublicBytes),
            signingPrivateKeyset = b64(serialize(signingPrivate)),
            signingPublicKeyset = b64(signingPublicBytes),
            createdAt = now,
        )
    }

    fun createInvite(
        identity: LocalIdentity,
        endpointHost: String,
        endpointPort: Int,
        torOnion: String = "",
        torPort: Int = 80,
        endpointRevision: Int = 1,
        now: Long = System.currentTimeMillis(),
        validityMillis: Long = 24 * 60 * 60 * 1000L,
    ): String {
        val hasLanEndpoint = endpointHost.length in 3..253 && endpointPort in 1..65535
        val hasTorEndpoint = isValidOnionV3(torOnion)
        require(hasLanEndpoint || hasTorEndpoint) { "Nessun endpoint disponibile" }
        require(endpointHost.isBlank() || hasLanEndpoint) { "Endpoint LAN non valido" }
        require(torOnion.isBlank() || isValidOnionV3(torOnion)) { "Endpoint Tor non valido" }
        require(torPort in 1..65535) { "Porta Tor non valida" }
        require(endpointRevision > 0) { "Revisione endpoint non valida" }
        val card = ContactCard(
            id = identity.id,
            displayName = identity.displayName,
            fingerprint = identity.fingerprint,
            encryptionPublicKeyset = identity.encryptionPublicKeyset,
            signingPublicKeyset = identity.signingPublicKeyset,
            endpointHost = endpointHost,
            endpointPort = endpointPort,
            torOnion = torOnion,
            torPort = torPort,
            endpointRevision = endpointRevision,
            issuedAt = now,
            expiresAt = now + validityMillis,
            nonce = b64(ByteArray(16).also(random::nextBytes)),
        )
        val cardBytes = canonicalCard(card)
        val signature = sign(identity.signingPrivateKeyset, cardBytes)
        val signed = SignedInvite(card, b64(signature))
        return "brotherhood://invite?data=${b64(json.encodeToString(signed).encodeToByteArray())}"
    }

    fun parseAndVerifyInvite(raw: String, now: Long = System.currentTimeMillis()): ContactCard {
        val encoded = raw.trim().substringAfter("data=", raw.trim())
        require(encoded.length in 32..32_000) { "Invito non valido" }
        val signed = runCatching {
            json.decodeFromString<SignedInvite>(decoder.decode(encoded).decodeToString())
        }.getOrElse { throw IllegalArgumentException("Invito non leggibile") }
        val card = signed.card
        require(card.version == 1) { "Versione invito non supportata" }
        require(card.expiresAt >= now) { "Invito scaduto" }
        require(card.issuedAt <= now + 5 * 60 * 1000L) { "Data invito non valida" }
        require(card.displayName.length in 2..40) { "Nome contatto non valido" }
        val hasLanEndpoint =
            card.endpointHost.length in 3..253 && card.endpointPort in 1..65535
        val hasTorEndpoint = isValidOnionV3(card.torOnion)
        require(hasLanEndpoint || hasTorEndpoint) { "Nessun endpoint disponibile" }
        require(card.endpointHost.isBlank() || hasLanEndpoint) { "Endpoint LAN non valido" }
        require(card.torOnion.isBlank() || isValidOnionV3(card.torOnion)) {
            "Endpoint Tor non valido"
        }
        require(card.torPort in 1..65535 && card.endpointRevision > 0) {
            "Revisione endpoint non valida"
        }
        require(card.encryptionPublicKeyset.length <= 16_384) { "Chiave troppo grande" }
        require(card.signingPublicKeyset.length <= 16_384) { "Chiave troppo grande" }
        verify(card.signingPublicKeyset, decoder.decode(signed.signature), canonicalCard(card))
        val material = decoder.decode(card.encryptionPublicKeyset) + decoder.decode(card.signingPublicKeyset)
        require(stableId(material) == card.id) { "Identità invito incoerente" }
        require(fingerprint(material) == card.fingerprint) { "Impronta invito incoerente" }
        return card
    }

    fun contactFrom(card: ContactCard, verified: Boolean = false): Contact = Contact(
        id = card.id,
        displayName = card.displayName,
        fingerprint = card.fingerprint,
        encryptionPublicKeyset = card.encryptionPublicKeyset,
        signingPublicKeyset = card.signingPublicKeyset,
        endpointHost = card.endpointHost,
        endpointPort = card.endpointPort,
        torOnion = card.torOnion,
        torPort = card.torPort,
        endpointRevision = card.endpointRevision,
        verified = verified,
        addedAt = System.currentTimeMillis(),
    )

    fun createEnvelope(
        identity: LocalIdentity,
        contact: Contact,
        payload: MessagePayload,
        messageId: String = UUID.randomUUID().toString(),
        sentAt: Long = System.currentTimeMillis(),
    ): WireEnvelope {
        val plaintext = json.encodeToString(payload).encodeToByteArray()
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Messaggio troppo grande" }
        val context = messageContext(contact.id)
        val encryptor = deserialize(contact.encryptionPublicKeyset)
            .getPrimitive(HybridEncrypt::class.java)
        val ciphertext = encryptor.encrypt(plaintext, context)
        val unsigned = WireEnvelope(
            messageId = messageId,
            senderId = identity.id,
            recipientId = contact.id,
            sentAt = sentAt,
            ciphertext = b64(ciphertext),
            signature = "",
        )
        return unsigned.copy(
            signature = b64(sign(identity.signingPrivateKeyset, canonicalEnvelope(unsigned))),
        )
    }

    fun verifyAndDecrypt(
        identity: LocalIdentity,
        contact: Contact,
        envelope: WireEnvelope,
        now: Long = System.currentTimeMillis(),
    ): MessagePayload {
        require(envelope.version == 1) { "Versione messaggio non supportata" }
        require(envelope.senderId == contact.id) { "Mittente non corrispondente" }
        require(envelope.recipientId == identity.id) { "Destinatario non corrispondente" }
        require(envelope.messageId.length in 16..80) { "ID messaggio non valido" }
        require(envelope.sentAt in (now - MAX_MESSAGE_AGE_MS)..(now + MAX_CLOCK_SKEW_MS)) {
            "Data messaggio non valida"
        }
        require(envelope.ciphertext.length <= MAX_CIPHERTEXT_BASE64_CHARS) { "Messaggio troppo grande" }
        require(envelope.signature.length in 32..MAX_SIGNATURE_BASE64_CHARS) {
            "Firma messaggio non valida"
        }
        verify(
            contact.signingPublicKeyset,
            decoder.decode(envelope.signature),
            canonicalEnvelope(envelope.copy(signature = "")),
        )
        val decryptor = deserialize(identity.encryptionPrivateKeyset)
            .getPrimitive(HybridDecrypt::class.java)
        val ciphertext = decoder.decode(envelope.ciphertext)
        val plaintext = try {
            decryptor.decrypt(ciphertext, messageContext(identity.id))
        } finally {
            ciphertext.fill(0)
        }
        return try {
            require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Messaggio decifrato troppo grande" }
            json.decodeFromString(plaintext.decodeToString())
        } finally {
            plaintext.fill(0)
        }
    }

    fun createReceipt(
        identity: LocalIdentity,
        messageId: String,
        receivedAt: Long = System.currentTimeMillis(),
    ): DeliveryReceipt {
        val unsigned = DeliveryReceipt(
            messageId = messageId,
            recipientId = identity.id,
            receivedAt = receivedAt,
            signature = "",
        )
        return unsigned.copy(
            signature = b64(sign(identity.signingPrivateKeyset, canonicalReceipt(unsigned))),
        )
    }

    fun createNetworkFrame(
        identity: LocalIdentity,
        envelope: WireEnvelope,
        now: Long = System.currentTimeMillis(),
    ): NetworkFrame {
        require(envelope.senderId == identity.id) { "Mittente envelope non valido" }
        val unsigned = NetworkFrame(
            senderId = identity.id,
            recipientId = envelope.recipientId,
            nonce = b64(ByteArray(24).also(random::nextBytes)),
            timestamp = now,
            envelope = envelope,
            signature = "",
        )
        return unsigned.copy(
            signature = b64(sign(identity.signingPrivateKeyset, canonicalFrame(unsigned))),
        )
    }

    fun verifyNetworkFrame(
        identity: LocalIdentity,
        contact: Contact,
        frame: NetworkFrame,
        now: Long = System.currentTimeMillis(),
    ) {
        require(frame.version == 2) { "Versione protocollo non supportata" }
        require(frame.senderId == contact.id && frame.envelope.senderId == contact.id) {
            "Mittente frame non valido"
        }
        require(frame.recipientId == identity.id && frame.envelope.recipientId == identity.id) {
            "Destinatario frame non valido"
        }
        require(frame.nonce.length in 24..64) { "Nonce non valido" }
        val decodedNonce = runCatching { decoder.decode(frame.nonce) }
            .getOrElse { throw IllegalArgumentException("Nonce non valido") }
        try {
            require(decodedNonce.size == 24) { "Nonce non valido" }
        } finally {
            decodedNonce.fill(0)
        }
        require(frame.signature.length in 32..MAX_SIGNATURE_BASE64_CHARS) {
            "Firma frame non valida"
        }
        require(frame.timestamp in (now - FRAME_WINDOW_MS)..(now + MAX_CLOCK_SKEW_MS)) {
            "Data handshake non valida"
        }
        verify(
            contact.signingPublicKeyset,
            decoder.decode(frame.signature),
            canonicalFrame(frame.copy(signature = "")),
        )
    }

    fun verifyReceipt(contact: Contact, receipt: DeliveryReceipt, messageId: String) {
        require(receipt.version == 1 && receipt.messageId == messageId) { "Ricevuta non valida" }
        require(receipt.recipientId == contact.id) { "Destinatario ricevuta non valido" }
        require(receipt.signature.length in 32..MAX_SIGNATURE_BASE64_CHARS) {
            "Firma ricevuta non valida"
        }
        verify(
            contact.signingPublicKeyset,
            decoder.decode(receipt.signature),
            canonicalReceipt(receipt.copy(signature = "")),
        )
    }

    private fun canonicalCard(card: ContactCard): ByteArray =
        json.encodeToString(ContactCard.serializer(), card).encodeToByteArray()

    private fun canonicalEnvelope(envelope: WireEnvelope): ByteArray = listOf(
        envelope.version.toString(),
        envelope.messageId,
        envelope.senderId,
        envelope.recipientId,
        envelope.sentAt.toString(),
        envelope.ciphertext,
    ).joinToString("\u001f").encodeToByteArray()

    private fun canonicalReceipt(receipt: DeliveryReceipt): ByteArray = listOf(
        receipt.version.toString(),
        receipt.messageId,
        receipt.recipientId,
        receipt.receivedAt.toString(),
    ).joinToString("\u001f").encodeToByteArray()

    private fun canonicalFrame(frame: NetworkFrame): ByteArray = listOf(
        frame.version.toString(),
        frame.senderId,
        frame.recipientId,
        frame.nonce,
        frame.timestamp.toString(),
        json.encodeToString(WireEnvelope.serializer(), frame.envelope),
    ).joinToString("\u001f").encodeToByteArray()

    private fun messageContext(recipientId: String): ByteArray =
        "brotherhood-message-v1|$recipientId".encodeToByteArray()

    private fun sign(privateKeyset: String, data: ByteArray): ByteArray =
        deserialize(privateKeyset).getPrimitive(PublicKeySign::class.java).sign(data)

    private fun verify(publicKeyset: String, signature: ByteArray, data: ByteArray) {
        deserialize(publicKeyset).getPrimitive(PublicKeyVerify::class.java).verify(signature, data)
    }

    private fun serialize(handle: KeysetHandle): ByteArray {
        val output = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(output))
        return output.toByteArray()
    }

    private fun deserialize(encoded: String): KeysetHandle =
        CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(decoder.decode(encoded)))

    private fun b64(bytes: ByteArray): String = encoder.encodeToString(bytes)

    private fun stableId(material: ByteArray): String =
        b64(MessageDigest.getInstance("SHA-256").digest(material).copyOfRange(0, 18))

    private fun fingerprint(material: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(material)
            .copyOfRange(0, 12)
            .joinToString(":") { "%02X".format(it) }

    private fun isValidOnionV3(value: String): Boolean =
        ONION_V3.matches(value.lowercase())

    companion object {
        const val MAX_PLAINTEXT_BYTES = 3_200_000
        const val MAX_CIPHERTEXT_BASE64_CHARS = 4_400_000
        const val MAX_MESSAGE_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_CLOCK_SKEW_MS = 10L * 60 * 1000
        const val FRAME_WINDOW_MS = 10L * 60 * 1000
        private const val MAX_SIGNATURE_BASE64_CHARS = 512
        private val ONION_V3 = Regex("^[a-z2-7]{56}\\.onion$")
    }
}
