package coredevices.pebble.services.backgroundaudio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

class BackgroundAudioPcmReader(
    private val chunkSizeBytes: Int = 16_000,
) {
    fun readChunks(path: Path): Flow<ByteArray> = flow {
        SystemFileSystem.source(path).use { source ->
            val buffer = Buffer()
            while (true) {
                val bytesRead = source.readAtMostTo(buffer, chunkSizeBytes.toLong())
                if (bytesRead == -1L) {
                    break
                }
                emit(buffer.readByteArray())
            }
        }
    }.flowOn(Dispatchers.IO)
}
