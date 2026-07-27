package org.brotherhood.app.core

import java.security.MessageDigest
import java.util.Base64
import kotlin.math.min
import org.brotherhood.app.model.MessageKind
import org.brotherhood.app.model.MessagePayload
import org.brotherhood.app.model.IncomingVoiceTransfer
import org.brotherhood.app.model.PrivateGroup

object RetryPolicy {
    private const val BASE_DELAY_MS = 5_000L
    private const val MAX_DELAY_MS = 30L * 60 * 1000

    fun delayForAttempt(attempt: Int): Long {
        if (attempt <= 0) return 0
        val shift = min(attempt - 1, 16)
        return min(BASE_DELAY_MS * (1L shl shift), MAX_DELAY_MS)
    }
}

class ReplayProtector(
    existingIds: Collection<String> = emptyList(),
    private val capacity: Int = 10_000,
) {
    private val ids: LinkedHashSet<String> = LinkedHashSet<String>().apply {
        addAll(existingIds.toList().takeLast(capacity))
    }

    @Synchronized
    fun accept(id: String): Boolean {
        if (id in ids) return false
        ids += id
        while (ids.size > capacity) ids.remove(ids.first())
        return true
    }

    @Synchronized
    fun snapshot(): Set<String> = ids.toSet()
}

object FileChunks {
    const val DEFAULT_CHUNK_SIZE = 64 * 1024

    fun split(bytes: ByteArray, chunkSize: Int = DEFAULT_CHUNK_SIZE): List<ByteArray> {
        require(chunkSize in 1024..256 * 1024)
        if (bytes.isEmpty()) return listOf(ByteArray(0))
        return buildList {
            var offset = 0
            while (offset < bytes.size) {
                val end = min(offset + chunkSize, bytes.size)
                add(bytes.copyOfRange(offset, end))
                offset = end
            }
        }
    }

    fun join(chunks: List<ByteArray>, expectedSha256: ByteArray): ByteArray {
        require(chunks.isNotEmpty())
        val total = chunks.sumOf { it.size }
        require(total <= 10 * 1024 * 1024) { "File troppo grande" }
        val rebuilt = ByteArray(total)
        var offset = 0
        chunks.forEach {
            it.copyInto(rebuilt, offset)
            offset += it.size
        }
        require(MessageDigest.isEqual(sha256(rebuilt), expectedSha256)) { "Integrità file non valida" }
        return rebuilt
    }

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}

object GroupPolicy {
    fun validateIncoming(
        existing: PrivateGroup?,
        senderId: String,
        recipientId: String,
        incomingMembers: List<String>,
        incomingRevision: Int,
    ) {
        require(incomingMembers.distinct().size in 2..20) { "Gruppo non valido" }
        require(senderId in incomingMembers && recipientId in incomingMembers) {
            "Composizione gruppo non valida"
        }
        if (existing == null) return
        require(senderId in existing.memberIds) { "Mittente rimosso dal gruppo" }
        require(incomingRevision >= existing.revision) { "Revisione gruppo obsoleta" }
        if (incomingRevision > existing.revision) {
            require(senderId == existing.ownerId) { "Solo il proprietario può aggiornare i membri" }
        }
    }
}

object PayloadValidator {
    private const val MAX_BODY_CHARS = 8_000
    private const val MAX_IMAGE_BYTES = 2_300_000
    private const val MAX_VOICE_BYTES = 1_500_000
    private const val MAX_ATTACHMENT_NAME_CHARS = 120
    private const val MAX_IDENTIFIER_CHARS = 128
    private const val MAX_VOICE_CHUNKS = 24
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun validate(payload: MessagePayload) {
        require(payload.body.length <= MAX_BODY_CHARS) { "Testo troppo lungo" }
        require(payload.replyTo.length <= MAX_IDENTIFIER_CHARS) { "Riferimento non valido" }
        require(payload.logicalMessageId.length <= MAX_IDENTIFIER_CHARS) {
            "Identificativo messaggio non valido"
        }
        require(payload.groupId.length <= MAX_IDENTIFIER_CHARS) { "Gruppo non valido" }
        require(payload.groupName.length <= 60) { "Nome gruppo non valido" }
        require(payload.groupMemberIds.size <= 20) { "Troppi membri nel gruppo" }
        require(payload.groupMemberIds.all { it.isNotBlank() && it.length <= MAX_IDENTIFIER_CHARS }) {
            "Identificativo membro non valido"
        }
        require(payload.attachmentName.length <= MAX_ATTACHMENT_NAME_CHARS) {
            "Nome allegato troppo lungo"
        }
        require('/' !in payload.attachmentName && '\\' !in payload.attachmentName) {
            "Nome allegato non valido"
        }

        when (payload.kind) {
            MessageKind.TEXT, MessageKind.SYSTEM -> {
                require(payload.attachmentChunkIndex == 0 && payload.attachmentChunkCount == 1) {
                    "Frammentazione inattesa"
                }
                require(payload.attachmentBase64.isBlank()) { "Allegato inatteso" }
                require(payload.attachmentMime.isBlank()) { "Tipo allegato inatteso" }
                require(payload.attachmentSha256.isBlank()) { "Hash allegato inatteso" }
                require(payload.durationMillis == 0L) { "Durata inattesa" }
            }

            MessageKind.IMAGE -> {
                require(payload.attachmentChunkIndex == 0 && payload.attachmentChunkCount == 1) {
                    "Frammentazione immagine non supportata"
                }
                require(payload.attachmentMime == "image/jpeg") { "Formato immagine non supportato" }
                val bytes = decodeAttachment(payload.attachmentBase64, MAX_IMAGE_BYTES)
                try {
                    validateOptionalHash(bytes, payload.attachmentSha256)
                    require(payload.durationMillis == 0L) { "Durata inattesa" }
                } finally {
                    bytes.fill(0)
                }
            }

            MessageKind.VOICE -> {
                require(payload.attachmentMime in setOf("audio/ogg", "audio/mp4")) {
                    "Formato vocale non supportato"
                }
                require(payload.durationMillis in 1..60_000) { "Durata vocale non valida" }
                require(payload.logicalMessageId.length in 16..MAX_IDENTIFIER_CHARS) {
                    "Identificativo vocale non valido"
                }
                require(payload.attachmentChunkCount in 1..MAX_VOICE_CHUNKS) {
                    "Numero blocchi non valido"
                }
                require(payload.attachmentChunkIndex in 0 until payload.attachmentChunkCount) {
                    "Indice blocco non valido"
                }
                require(payload.attachmentTotalBytes in 1..MAX_VOICE_BYTES) {
                    "Dimensione vocale non valida"
                }
                require(sha256Pattern.matches(payload.attachmentSha256.lowercase())) {
                    "Hash vocale non valido"
                }
                val perChunkLimit =
                    if (payload.attachmentChunkCount == 1) MAX_VOICE_BYTES
                    else FileChunks.DEFAULT_CHUNK_SIZE
                val bytes = decodeAttachment(payload.attachmentBase64, perChunkLimit)
                try {
                    if (payload.attachmentChunkCount == 1) {
                        require(bytes.size == payload.attachmentTotalBytes) {
                            "Dimensione vocale incoerente"
                        }
                        require(
                            MessageDigest.isEqual(
                                hash(bytes),
                                payload.attachmentSha256.lowercase().hexToBytes(),
                            ),
                        ) { "Integrità vocale non valida" }
                    }
                } finally {
                    bytes.fill(0)
                }
            }
        }
    }

    private fun decodeAttachment(encoded: String, maxBytes: Int): ByteArray {
        require(encoded.isNotBlank()) { "Allegato assente" }
        require(encoded.length <= ((maxBytes + 2) / 3) * 4) { "Allegato troppo grande" }
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Allegato non valido") }
        require(decoded.isNotEmpty() && decoded.size <= maxBytes) { "Allegato troppo grande" }
        return decoded
    }

    private fun validateOptionalHash(bytes: ByteArray, expectedHash: String) {
        if (expectedHash.isBlank()) return
        require(sha256Pattern.matches(expectedHash.lowercase())) { "Hash allegato non valido" }
        require(MessageDigest.isEqual(hash(bytes), expectedHash.lowercase().hexToBytes())) {
            "Integrità allegato non valida"
        }
    }

    private fun hash(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

object AttachmentChunks {
    fun count(encoded: String, chunkSize: Int = FileChunks.DEFAULT_CHUNK_SIZE): Int {
        require(chunkSize in 1024..256 * 1024)
        val bytes = decode(encoded)
        return try {
            (bytes.size + chunkSize - 1) / chunkSize
        } finally {
            bytes.fill(0)
        }
    }

    fun totalBytes(encoded: String): Int {
        val bytes = decode(encoded)
        return try {
            bytes.size
        } finally {
            bytes.fill(0)
        }
    }

    fun chunk(
        encoded: String,
        index: Int,
        chunkSize: Int = FileChunks.DEFAULT_CHUNK_SIZE,
    ): Pair<String, Int> {
        require(chunkSize in 1024..256 * 1024)
        val bytes = decode(encoded)
        return try {
            val chunkCount = (bytes.size + chunkSize - 1) / chunkSize
            require(index in 0 until chunkCount) { "Blocco non valido" }
            val start = index * chunkSize
            val chunk = bytes.copyOfRange(start, min(start + chunkSize, bytes.size))
            try {
                Base64.getEncoder().encodeToString(chunk) to bytes.size
            } finally {
                chunk.fill(0)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun decode(encoded: String): ByteArray =
        runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Allegato non valido") }
}

data class VoiceTransferProgress(
    val transfer: IncomingVoiceTransfer?,
    val completedPayload: MessagePayload?,
)

object VoiceTransferAssembler {
    fun accept(
        existing: IncomingVoiceTransfer?,
        senderId: String,
        payload: MessagePayload,
        now: Long,
    ): VoiceTransferProgress {
        require(payload.kind == MessageKind.VOICE && payload.attachmentChunkCount > 1)
        val normalized = payload.copy(
            attachmentBase64 = "",
            attachmentChunkIndex = 0,
        )
        if (existing != null) {
            require(existing.senderId == senderId) { "Mittente trasferimento non valido" }
            require(existing.logicalMessageId == payload.logicalMessageId) {
                "Trasferimento non valido"
            }
            require(existing.template == normalized) { "Metadati vocali incoerenti" }
        }
        val chunks = (existing?.chunks.orEmpty() +
            (payload.attachmentChunkIndex to payload.attachmentBase64))
        val transfer = IncomingVoiceTransfer(
            senderId = senderId,
            logicalMessageId = payload.logicalMessageId,
            template = normalized,
            chunks = chunks,
            updatedAt = now,
        )
        if (chunks.size < payload.attachmentChunkCount) {
            return VoiceTransferProgress(transfer = transfer, completedPayload = null)
        }
        require(chunks.keys == (0 until payload.attachmentChunkCount).toSet()) {
            "Sequenza blocchi non valida"
        }
        val decoded = mutableListOf<ByteArray>()
        try {
            (0 until payload.attachmentChunkCount).forEach { index ->
                decoded += runCatching { Base64.getDecoder().decode(chunks.getValue(index)) }
                    .getOrElse { throw IllegalArgumentException("Blocco vocale non valido") }
            }
        } catch (t: Throwable) {
            decoded.forEach { it.fill(0) }
            throw t
        }
        val rebuilt = try {
            FileChunks.join(decoded, payload.attachmentSha256.lowercase().hexToBytes())
        } finally {
            decoded.forEach { it.fill(0) }
        }
        return try {
            require(rebuilt.size == payload.attachmentTotalBytes) {
                "Dimensione vocale incoerente"
            }
            val completed = normalized.copy(
                attachmentBase64 = Base64.getEncoder().encodeToString(rebuilt),
                attachmentChunkIndex = 0,
                attachmentChunkCount = 1,
            )
            PayloadValidator.validate(completed)
            VoiceTransferProgress(transfer = null, completedPayload = completed)
        } finally {
            rebuilt.fill(0)
        }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
