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
}
