package org.brotherhood.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.brotherhood.app.model.NetworkFrame
import org.brotherhood.app.transport.MessageTransport
import org.brotherhood.app.transport.RecipientEndpoint
import org.brotherhood.app.transport.TransportResult
import org.brotherhood.app.transport.TransportRuntimeController
import org.brotherhood.app.transport.TransportState
import org.brotherhood.app.transport.TransportType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransportRuntimeControllerTest {
    @Test
    fun reacquiringSameOwnerReconcilesTransportsThatMayHaveStopped() = runTest {
        val lan = CountingTransport(TransportType.LAN)
        val tor = CountingTransport(TransportType.TOR)
        val controller = TransportRuntimeController(lan, tor)

        controller.acquire("ui")
        controller.acquire("ui")

        assertEquals(2, lan.starts)
        assertEquals(2, tor.starts)
        controller.release("ui")
        assertEquals(1, lan.stops)
        assertEquals(1, tor.stops)
    }

    private class CountingTransport(
        override val type: TransportType,
    ) : MessageTransport {
        override val state: StateFlow<TransportState> = MutableStateFlow(TransportState())
        var starts = 0
        var stops = 0

        override suspend fun start() {
            starts++
        }

        override suspend fun stop() {
            stops++
        }

        override suspend fun send(
            recipient: RecipientEndpoint,
            frame: NetworkFrame,
        ): TransportResult = TransportResult.Unavailable(type, "unused")
    }
}
