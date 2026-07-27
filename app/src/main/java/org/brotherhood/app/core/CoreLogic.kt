package org.brotherhood.app.core

import java.security.MessageDigest
import kotlin.math.min
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
