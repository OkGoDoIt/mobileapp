package io.rebble.libpebblecommon.packets

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundAudioStreamTest {
    @Test
    fun parseLengthPrefixedFrames() {
        val payload = ubyteArrayOf(
            0x02u, 0x00u, 0xAAu, 0xBBu,
            0x01u, 0x00u, 0xCCu,
        )
        val frames = BackgroundAudioStream.parseLengthPrefixedFrames(payload)
        assertEquals(2, frames.size)
        assertEquals(2, frames[0].size)
        assertEquals(1, frames[1].size)
    }

    @Test
    fun backgroundCommandIdsDoNotCollideWithDictation() {
        assertEquals(0x02u, AudioStream.Command.DataTransfer.value)
        assertEquals(0x10u, BackgroundAudioStream.Command.StreamStart.value)
    }

    @Test
    fun checkpointSerializes64BitSampleIndexLittleEndian() {
        val packet = BackgroundAudioStream.BackgroundStreamCheckpoint(
            streamId = 0x01020304u,
            highestContiguousSequencePersisted = 0x05060708u,
            persistedSampleIndex = 0x1122334455667788u,
            receiverFlags = 0x0A0B0C0Du,
            freeStorageHintKb = 0x01000000u,
        )

        val bytes = packet.m.toBytes()

        assertEquals(0x88u, bytes[9])
        assertEquals(0x77u, bytes[10])
        assertEquals(0x66u, bytes[11])
        assertEquals(0x55u, bytes[12])
        assertEquals(0x44u, bytes[13])
        assertEquals(0x33u, bytes[14])
        assertEquals(0x22u, bytes[15])
        assertEquals(0x11u, bytes[16])
    }
}
