package org.brotherhood.app

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.brotherhood.app.model.DeliveryReceipt
import org.brotherhood.app.model.NetworkFrame
import org.brotherhood.app.model.OutboundItem
import org.brotherhood.app.model.WireEnvelope
import org.brotherhood.app.transport.MessageTransport
import org.brotherhood.app.transport.RecipientEndpoint
import org.brotherhood.app.transport.TransportResult
import org.brotherhood.app.transport.TransportRouter
import org.brotherhood.app.transport.TransportState
import org.brotherhood.app.transport.TransportType
import org.junit.Test

class TransportRouterTest {
    @Test
    fun fallbackReusesExactlyTheSameSignedFrameAcrossLanAndTor() = runTest {
        val frame = frame()
        val receipt = receipt()
        val lan = FakeTransport(
            TransportType.LAN,
            TransportResult.Failed(TransportType.LAN, "timeout"),
        )
        val tor = FakeTransport(
            TransportType.TOR,
            TransportResult.Delivered(receipt, TransportType.TOR),
        )
        val router = TransportRouter(
            endpointProvider = { endpoint() },
            frameProvider = { frame },
            transports = listOf(lan, tor),
        )

        val result = router.send(item())

        assertIs<TransportResult.Delivered>(result)
        assertEquals(TransportType.TOR, result.transport)
        assertEquals(listOf(frame), lan.frames)
        assertEquals(listOf(frame), tor.frames)
    }

    @Test
    fun validLanReceiptStopsBeforeTorAndPreventsSecondVisibleDelivery() = runTest {
        val frame = frame()
        val receipt = receipt()
        val lan = FakeTransport(
            TransportType.LAN,
            TransportResult.Delivered(receipt, TransportType.LAN),
        )
        val tor = FakeTransport(
            TransportType.TOR,
            TransportResult.Delivered(receipt, TransportType.TOR),
        )
        val router = TransportRouter(
            endpointProvider = { endpoint() },
            frameProvider = { frame },
            transports = listOf(lan, tor),
        )

        val result = router.send(item())

        assertIs<TransportResult.Delivered>(result)
        assertEquals(TransportType.LAN, result.transport)
        assertEquals(1, lan.frames.size)
        assertEquals(0, tor.frames.size)
    }

    private fun endpoint() = RecipientEndpoint(
        contactId = "contact",
        lanHost = "192.0.2.5",
        lanPort = 42337,
        torOnion = "a".repeat(56) + ".onion",
    )

    private fun item() = OutboundItem(
        id = "outbound-id",
        messageId = "message-id",
        contactId = "contact",
        createdAt = 1,
        expiresAt = Long.MAX_VALUE,
    )

    private fun frame() = NetworkFrame(
        senderId = "sender",
        recipientId = "recipient",
        nonce = "bm9uY2Utbm9uY2Utbm9uY2Utbm9uY2U=",
        timestamp = 1,
        envelope = WireEnvelope(
            messageId = "outbound-id",
            senderId = "sender",
            recipientId = "recipient",
            sentAt = 1,
            ciphertext = "ciphertext",
            signature = "signature",
        ),
        signature = "frame-signature",
    )

    private fun receipt() = DeliveryReceipt(
        messageId = "outbound-id",
        recipientId = "recipient",
        receivedAt = 2,
        signature = "receipt-signature",
    )

    private class FakeTransport(
        override val type: TransportType,
        private val result: TransportResult,
    ) : MessageTransport {
        override val state: StateFlow<TransportState> = MutableStateFlow(TransportState())
        val frames = mutableListOf<NetworkFrame>()

        override suspend fun start() = Unit

        override suspend fun stop() = Unit

        override suspend fun send(
            recipient: RecipientEndpoint,
            frame: NetworkFrame,
        ): TransportResult {
            frames += frame
            return result
        }
    }
}
