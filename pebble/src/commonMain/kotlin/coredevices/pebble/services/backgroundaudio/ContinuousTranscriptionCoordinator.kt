package coredevices.pebble.services.backgroundaudio

import co.touchlab.kermit.Logger

/**
 * Queues bounded transcription windows for closed background audio segments.
 * Full provider integration is deferred until the stream path is validated on hardware.
 */
class ContinuousTranscriptionCoordinator(
    private val segmentStore: BackgroundAudioSegmentStore = BackgroundAudioSegmentStore(),
) {
    private val logger = Logger.withTag("BgAudioTranscription")

    fun resumePending() {
        val pending = segmentStore.listSegmentIds().mapNotNull { segmentStore.readMetadata(it) }
            .filter { it.status == SegmentStatus.Closed && it.transcriptionStatus == TranscriptionStatus.Pending }
        if (pending.isNotEmpty()) {
            logger.i { "Found ${pending.size} background segments pending transcription" }
        }
    }

    fun enqueueClosedSegment(metadata: BackgroundAudioSegmentMetadata) {
        logger.d { "Transcription enqueue for segment ${metadata.segmentId} (stub)" }
    }
}
