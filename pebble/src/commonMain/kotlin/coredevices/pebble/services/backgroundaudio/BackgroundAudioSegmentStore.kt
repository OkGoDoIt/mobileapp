package coredevices.pebble.services.backgroundaudio

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Durable metadata + PCM segment files for watch background audio. */
class BackgroundAudioSegmentStore(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val root: Path = backgroundAudioStorageDirectory(),
) {
    init {
        SystemFileSystem.createDirectories(root, mustCreate = false)
        SystemFileSystem.createDirectories(metadataDirectory(), mustCreate = false)
        SystemFileSystem.createDirectories(segmentsDirectory(), mustCreate = false)
        SystemFileSystem.createDirectories(transcriptsDirectory(), mustCreate = false)
    }

    fun metadataDirectory(): Path = Path(root, "metadata")

    fun segmentsDirectory(): Path = Path(root, "segments")

    fun transcriptsDirectory(): Path = Path(root, "transcripts")

    fun segmentPcmPath(segmentId: String): Path = Path(segmentsDirectory(), "$segmentId.pcm")

    fun transcriptPath(segmentId: String): Path = Path(transcriptsDirectory(), "$segmentId.json")

    fun writeMetadata(metadata: BackgroundAudioSegmentMetadata) {
        val path = Path(metadataDirectory(), "${metadata.segmentId}.json")
        writeAtomically(metadataDirectory(), path, json.encodeToString(metadata))
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

    fun listMetadata(): List<BackgroundAudioSegmentMetadata> {
        return SystemFileSystem.list(metadataDirectory())
            .filter { it.name.endsWith(".json") }
            .mapNotNull { path ->
                runCatching {
                    json.decodeFromString<BackgroundAudioSegmentMetadata>(
                        SystemFileSystem.source(path).buffered().use { it.readByteArray().decodeToString() },
                    )
                }.getOrNull()
            }
            .sortedBy { it.startedAtEpochMs }
    }

    fun listSegmentIds(): List<String> {
        return SystemFileSystem.list(segmentsDirectory())
            .map { it.name.removeSuffix(".pcm") }
            .sorted()
    }

    fun updateMetadata(
        segmentId: String,
        transform: (BackgroundAudioSegmentMetadata) -> BackgroundAudioSegmentMetadata,
    ): BackgroundAudioSegmentMetadata? {
        val current = readMetadata(segmentId) ?: return null
        val updated = transform(current)
        writeMetadata(updated)
        return updated
    }

    fun writeTranscript(transcript: BackgroundAudioTranscript): Path {
        val path = transcriptPath(transcript.segmentId)
        writeAtomically(transcriptsDirectory(), path, json.encodeToString(transcript))
        return path
    }

    fun readTranscript(segmentId: String): BackgroundAudioTranscript? {
        val path = transcriptPath(segmentId)
        if (!SystemFileSystem.exists(path)) {
            return null
        }
        return runCatching {
            SystemFileSystem.source(path).buffered().use { source ->
                json.decodeFromString<BackgroundAudioTranscript>(source.readByteArray().decodeToString())
            }
        }.getOrNull()
    }

    fun deleteSegment(segmentId: String, deleteTranscript: Boolean = false) {
        SystemFileSystem.delete(segmentPcmPath(segmentId), mustExist = false)
        SystemFileSystem.delete(Path(metadataDirectory(), "$segmentId.json"), mustExist = false)
        if (deleteTranscript) {
            SystemFileSystem.delete(transcriptPath(segmentId), mustExist = false)
        }
    }

    fun segmentSizeBytes(segmentId: String): Long {
        return SystemFileSystem.metadataOrNull(segmentPcmPath(segmentId))?.size ?: 0L
    }

    fun storageStats(): BackgroundAudioStorageStats {
        val metadata = listMetadata()
        val audioBytes = metadata.sumOf { segmentSizeBytes(it.segmentId) }
        val transcriptBytes = SystemFileSystem.list(transcriptsDirectory()).sumOf {
            SystemFileSystem.metadataOrNull(it)?.size ?: 0L
        }
        val metadataBytes = SystemFileSystem.list(metadataDirectory()).sumOf {
            SystemFileSystem.metadataOrNull(it)?.size ?: 0L
        }
        return BackgroundAudioStorageStats(
            audioBytes = audioBytes,
            transcriptBytes = transcriptBytes,
            metadataBytes = metadataBytes,
            segmentCount = metadata.size,
            oldestSegmentStartedAtEpochMs = metadata.minOfOrNull { it.startedAtEpochMs },
        )
    }

    fun isOpen(segmentId: String): Boolean {
        return readMetadata(segmentId)?.status == SegmentStatus.Open
    }

    private fun writeAtomically(directory: Path, path: Path, contents: String) {
        val tmp = Path(directory, "${path.name}.tmp")
        SystemFileSystem.sink(tmp).buffered().use { sink ->
            sink.writeString(contents)
            sink.flush()
        }
        SystemFileSystem.atomicMove(tmp, path)
    }
}
