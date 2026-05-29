package coredevices.pebble.services.audiocontext

import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioChunk
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioOptions
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlin.uuid.Uuid

class AudioContextRawAudioFanout {
    private val chunks = MutableSharedFlow<AudioContextRawAudioChunk>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    var subscriberCount: Int = 0
        private set

    fun publish(chunk: AudioContextRawAudioChunk) {
        chunks.tryEmit(chunk)
    }

    fun subscribe(
        appUuid: Uuid,
        options: AudioContextRawAudioOptions,
    ): Flow<AudioContextRawAudioChunk> {
        return chunks.asSharedFlow()
            .map { chunk -> chunk.clampedTo(options.maxChunkBytes) }
            .onStart { subscriberCount++ }
            .onCompletion { subscriberCount-- }
    }

    private fun AudioContextRawAudioChunk.clampedTo(maxChunkBytes: Int): AudioContextRawAudioChunk {
        if (maxChunkBytes <= 0 || bytes.size <= maxChunkBytes) {
            return this
        }
        return copy(bytes = bytes.copyOf(maxChunkBytes))
    }
}
