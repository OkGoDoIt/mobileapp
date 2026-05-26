package coredevices.ring.service.recordings

import co.touchlab.kermit.Logger
import coredevices.ring.storage.RecordingStorage
import coredevices.util.recording.RecordingIngress
import coredevices.util.recording.RecordingIngressMetadata
import kotlinx.io.Sink
import kotlinx.io.buffered

/**
 * Writes local PCM recordings through [RecordingStorage] and queues Index processing.
 */
class IndexRecordingIngress(
    private val recordingStorage: RecordingStorage,
    private val recordingProcessingQueue: RecordingProcessingQueue,
) : RecordingIngress {
    private val logger = Logger.withTag("IndexRecordingIngress")

    override suspend fun openRawPcmSink(
        fileId: String,
        sampleRate: Int,
        mimeType: String,
        metadata: RecordingIngressMetadata,
    ): Sink {
        logger.i { "Opening raw PCM sink for $fileId ($metadata)" }
        return recordingStorage.openRecordingSink(fileId, sampleRate, mimeType)
    }

    override suspend fun finalizeLocalRecording(
        fileId: String,
        buttonSequence: String?,
        metadata: RecordingIngressMetadata,
    ) {
        logger.i { "Finalizing local recording $fileId ($metadata)" }
        val (source, info) = recordingStorage.openRecordingSource(fileId)
        val cleanSink = recordingStorage.openCleanRecordingSink(
            fileId,
            info.cachedMetadata.sampleRate,
            info.cachedMetadata.mimeType,
        )
        source.use { src ->
            cleanSink.buffered().use { dst ->
                src.transferTo(dst)
            }
        }
        recordingProcessingQueue.queueLocalAudioProcessing(
            fileId = fileId,
            buttonSequence = buttonSequence,
        )
    }

    override suspend fun cancelLocalRecording(fileId: String) {
        logger.i { "Cancelling local recording $fileId" }
        recordingStorage.deleteRecordingFromCache(fileId)
        recordingStorage.deleteRecordingFromCache("$fileId-clean")
    }
}
