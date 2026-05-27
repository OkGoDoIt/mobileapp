package coredevices.pebble.services.backgroundaudio

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Durable metadata + PCM segment files for watch background audio. */
class BackgroundAudioSegmentStore(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val root: Path = backgroundAudioStorageDirectory()

    init {
        SystemFileSystem.createDirectories(root, mustCreate = false)
        SystemFileSystem.createDirectories(metadataDirectory(), mustCreate = false)
        SystemFileSystem.createDirectories(segmentsDirectory(), mustCreate = false)
    }

    fun metadataDirectory(): Path = Path(root, "metadata")

    fun segmentsDirectory(): Path = Path(root, "segments")

    fun segmentPcmPath(segmentId: String): Path = Path(segmentsDirectory(), "$segmentId.pcm")

    fun writeMetadata(metadata: BackgroundAudioSegmentMetadata) {
        val path = Path(metadataDirectory(), "${metadata.segmentId}.json")
        SystemFileSystem.sink(path).buffered().use { sink ->
            sink.writeString(json.encodeToString(metadata))
        }
    }

    fun readMetadata(segmentId: String): BackgroundAudioSegmentMetadata? {
        val path = Path(metadataDirectory(), "$segmentId.json")
        if (!SystemFileSystem.exists(path)) {
            return null
        }
        return SystemFileSystem.source(path).buffered().use { source ->
            json.decodeFromString<BackgroundAudioSegmentMetadata>(source.readByteArray().decodeToString())
        }
    }

    fun listSegmentIds(): List<String> {
        return SystemFileSystem.list(segmentsDirectory())
            .map { it.name.removeSuffix(".pcm") }
            .sorted()
    }
}
