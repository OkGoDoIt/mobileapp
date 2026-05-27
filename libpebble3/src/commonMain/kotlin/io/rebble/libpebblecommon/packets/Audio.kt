package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SFixedList
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SULong
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.SUnboundBytes
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.Endian

/**
 * Dictation audio streaming packets. Little endian.
 */
sealed class AudioStream(command: Command, sessionId: UShort = 0u) : PebblePacket(ProtocolEndpoint.AUDIO_STREAMING) {
    val command = SUByte(m, command.value)
    val sessionId = SUShort(m, sessionId, endianness = Endian.Little)

    class EncoderFrame : StructMappable() {
        val data = SUnboundBytes(m)
    }

    class DataTransfer : AudioStream(Command.DataTransfer) {
        val frameCount = SUByte(m)
        val frames = SFixedList(m, 0) {
            EncoderFrame()
        }
        init {
            frames.linkWithCount(frameCount)
        }
    }

    class StopTransfer(sessionId: UShort = 0u) : AudioStream(Command.StopTransfer, sessionId)

    enum class Command(val value: UByte) {
        DataTransfer(0x02u),
        StopTransfer(0x03u),
    }
}

/** Continuous background lifelogging audio packets on endpoint 10000. */
sealed class BackgroundAudioStream(command: Command) : PebblePacket(ProtocolEndpoint.AUDIO_STREAMING) {
    val command = SUByte(m, command.value)

    init {
        type = command.value
    }

    class BackgroundStreamStart : BackgroundAudioStream(Command.StreamStart) {
        val protocolVersion = SUByte(m)
        val streamId = SUInt(m, endianness = Endian.Little)
        val codecId = SUByte(m)
        val channels = SUByte(m)
        val frameSamples = SUShort(m, endianness = Endian.Little)
        val sampleRateHz = SUInt(m, endianness = Endian.Little)
        val bitRateBps = SUInt(m, endianness = Endian.Little)
        val frameDurationMs = SUShort(m, endianness = Endian.Little)
        val startTimeMs = SULong(m, endianness = Endian.Little)
        val startMonotonicMs = SULong(m, endianness = Endian.Little)
        val flags = SUInt(m, endianness = Endian.Little)
    }

    class BackgroundStreamData : BackgroundAudioStream(Command.StreamData) {
        val streamId = SUInt(m, endianness = Endian.Little)
        val firstSequence = SUInt(m, endianness = Endian.Little)
        val firstSampleIndex = SULong(m, endianness = Endian.Little)
        val frameCount = SUByte(m)
        val flags = SUShort(m, endianness = Endian.Little)
        val encodedFramesPayload = SUnboundBytes(m)
    }

    class BackgroundStreamGap : BackgroundAudioStream(Command.StreamGap) {
        val streamId = SUInt(m, endianness = Endian.Little)
        val firstMissingSequence = SUInt(m, endianness = Endian.Little)
        val missingFrameCount = SUInt(m, endianness = Endian.Little)
        val firstMissingSampleIndex = SULong(m, endianness = Endian.Little)
        val reason = SUByte(m)
        val watchDropCounter = SUInt(m, endianness = Endian.Little)
    }

    class BackgroundStreamCheckpoint(
        streamId: UInt = 0u,
        highestContiguousSequencePersisted: UInt = 0u,
        persistedSampleIndex: ULong = 0u,
        receiverFlags: UInt = 0u,
        freeStorageHintKb: UInt = 0u,
    ) : BackgroundAudioStream(Command.StreamCheckpoint) {
        val streamId = SUInt(m, streamId, endianness = Endian.Little)
        val highestContiguousSequencePersisted = SUInt(m, highestContiguousSequencePersisted, endianness = Endian.Little)
        val persistedSampleIndex = SULong(m, persistedSampleIndex, endianness = Endian.Little)
        val receiverFlags = SUInt(m, receiverFlags, endianness = Endian.Little)
        val freeStorageHintKb = SUInt(m, freeStorageHintKb, endianness = Endian.Little)
    }

    class BackgroundStreamStop : BackgroundAudioStream(Command.StreamStop) {
        val streamId = SUInt(m, endianness = Endian.Little)
        val reason = SUByte(m)
        val finalSequence = SUInt(m, endianness = Endian.Little)
        val finalSampleIndex = SULong(m, endianness = Endian.Little)
        val countersCrcOrZero = SUInt(m, endianness = Endian.Little)
    }

    enum class Command(val value: UByte) {
        StreamStart(0x10u),
        StreamData(0x11u),
        StreamGap(0x12u),
        StreamCheckpoint(0x13u),
        StreamStop(0x14u),
        StreamControl(0x15u),
    }

    companion object {
        const val CODEC_SPEEX_WIDEBAND: UByte = 0x01u

        fun parseLengthPrefixedFrames(payload: UByteArray): List<UByteArray> {
            val frames = mutableListOf<UByteArray>()
            var offset = 0
            while (offset + 2 <= payload.size) {
                val length = payload[offset].toInt() or (payload[offset + 1].toInt() shl 8)
                offset += 2
                if (length <= 0 || offset + length > payload.size) {
                    break
                }
                frames.add(payload.copyOfRange(offset, offset + length))
                offset += length
            }
            return frames
        }
    }
}

fun audioStreamPacketsRegister() {
    PacketRegistry.register(ProtocolEndpoint.AUDIO_STREAMING, AudioStream.Command.DataTransfer.value) {
        AudioStream.DataTransfer()
    }
    PacketRegistry.register(ProtocolEndpoint.AUDIO_STREAMING, AudioStream.Command.StopTransfer.value) {
        AudioStream.StopTransfer()
    }
    PacketRegistry.register(ProtocolEndpoint.AUDIO_STREAMING, BackgroundAudioStream.Command.StreamStart.value) {
        BackgroundAudioStream.BackgroundStreamStart()
    }
    PacketRegistry.register(ProtocolEndpoint.AUDIO_STREAMING, BackgroundAudioStream.Command.StreamData.value) {
        BackgroundAudioStream.BackgroundStreamData()
    }
    PacketRegistry.register(ProtocolEndpoint.AUDIO_STREAMING, BackgroundAudioStream.Command.StreamGap.value) {
        BackgroundAudioStream.BackgroundStreamGap()
    }
    PacketRegistry.register(ProtocolEndpoint.AUDIO_STREAMING, BackgroundAudioStream.Command.StreamStop.value) {
        BackgroundAudioStream.BackgroundStreamStop()
    }
}
