package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.packets.AudioStream
import io.rebble.libpebblecommon.packets.BackgroundAudioStream
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.takeWhile

class AudioStreamService(private val protocolHandler: PebbleProtocolHandler) : ProtocolService {
    val inboundMessages get() = protocolHandler.inboundMessages

    suspend fun send(packet: AudioStream) {
        protocolHandler.send(packet)
    }

    suspend fun sendBackground(packet: BackgroundAudioStream) {
        protocolHandler.send(packet)
    }

    suspend fun sendPacket(packet: PebblePacket) {
        protocolHandler.send(packet)
    }

    fun dataFlowForSession(sessionId: UShort) =
        protocolHandler.inboundMessages.filterIsInstance<AudioStream>()
            .filter { it.sessionId.get() == sessionId }
            .takeWhile { it !is AudioStream.StopTransfer }
            .filterIsInstance<AudioStream.DataTransfer>()
}