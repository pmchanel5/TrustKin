package org.brotherhood.app

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.brotherhood.app.core.FileChunks
import org.brotherhood.app.core.GroupPolicy
import org.brotherhood.app.core.ReplayProtector
import org.brotherhood.app.core.RetryPolicy
import org.brotherhood.app.model.PrivateGroup
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
}
