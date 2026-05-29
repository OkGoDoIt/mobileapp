package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AppAudioContextTest {
    @Test
    fun statusRequestRoundTripsThroughRegistry() {
        val appUuid = Uuid.parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val original = AppAudioContext.StatusRequest(
            requestId = 42u,
            appUuid = appUuid,
            flags = 7u,
        )
        val bytes = original.serialize()
        val decoded = PebblePacket.deserialize(bytes) as AppAudioContext.StatusRequest

        assertEquals(ProtocolEndpoint.APP_AUDIO_CONTEXT, decoded.endpoint)
        assertEquals(AppAudioContext.Command.StatusRequest.value, decoded.command.get())
        assertEquals(42u, decoded.requestId.get())
        assertEquals(appUuid, decoded.appUuid.get())
        assertEquals(7u, decoded.flags.get())
    }

    @Test
    fun transcriptResponsePreservesPayloadLength() {
        val payload = "hello".encodeToByteArray().toUByteArray()
        val packet = AppAudioContext.TranscriptResponse(
            requestId = 1u,
            responseStatus = 0u,
            partIndexValue = 0u,
            partCountValue = 1u,
            payload = payload,
        )
        assertEquals(payload.size.toUShort(), packet.payloadLength.get())
        assertEquals(payload.size, packet.payloadBytes.get().size)
    }

    @Test
    fun commandIdsAreUnique() {
        val values = AppAudioContext.Command.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
    }
}
