package coredevices.pebble.services

import coredevices.speex.SpeexCodec
import coredevices.speex.SpeexDecodeResult
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo

/**
 * Decodes a stream of Pebble watch Speex frames to PCM bytes.
 */
class PebbleSpeexFrameDecoder(
    encoderInfo: VoiceEncoderInfo.Speex,
) : AutoCloseable {
    val sampleRate: Int = encoderInfo.sampleRate.toInt()

    private val speex = SpeexCodec(
        sampleRate = encoderInfo.sampleRate,
        bitRate = encoderInfo.bitRate,
        frameSize = encoderInfo.frameSize,
    )
    private val pcm = ByteArray(encoderInfo.frameSize * Short.SIZE_BYTES)

    fun decode(frame: UByteArray): ByteArray {
        val result = speex.decodeFrame(frame.asByteArray(), pcm, hasHeaderByte = true)
        require(result == SpeexDecodeResult.Success) { "Failed to decode Speex frame: $result" }
        return pcm.copyOf()
    }

    override fun close() {}
}
