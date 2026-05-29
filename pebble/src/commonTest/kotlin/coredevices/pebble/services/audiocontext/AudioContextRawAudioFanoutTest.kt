package coredevices.pebble.services.audiocontext

import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioChunk
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextRawEncoding
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AudioContextRawAudioFanoutTest {
    @Test
    fun publishesChunksWithoutBlockingStoragePath() = runBlocking {
        val fanout = AudioContextRawAudioFanout()
        val received = async {
            fanout.subscribe(Uuid.random(), AudioContextRawAudioOptions()).collect { chunk ->
                return@collect
            }
        }
        val firstChunk = async {
            fanout.subscribe(Uuid.random(), AudioContextRawAudioOptions()).first()
        }

        delay(20)
        fanout.publish(chunk(sequence = 7))

        assertEquals(7, firstChunk.await().sequenceStart)
        received.cancel()
    }

    @Test
    fun clampsChunksToSubscriberMaxChunkBytes() = runBlocking {
        val fanout = AudioContextRawAudioFanout()
        val firstChunk = async {
            fanout.subscribe(
                Uuid.random(),
                AudioContextRawAudioOptions(maxChunkBytes = 2),
            ).first()
        }

        delay(20)
        fanout.publish(chunk(sequence = 9))

        assertEquals(byteArrayOf(1, 2).toList(), firstChunk.await().bytes.toList())
    }

    private fun chunk(sequence: Long) = AudioContextRawAudioChunk(
        streamId = 1,
        sequenceStart = sequence,
        sampleIndexStart = sequence * 320,
        timestampEpochMs = null,
        sampleRateHz = 16_000,
        channels = 1,
        encoding = AudioContextRawEncoding.Pcm16Le,
        bytes = byteArrayOf(1, 2, 3, 4),
        gapCountSinceLastChunk = 0,
    )
}
