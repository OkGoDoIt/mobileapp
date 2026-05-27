package coredevices.pebble.services.backgroundaudio

import co.touchlab.kermit.Logger
import coredevices.pebble.services.PebbleSpeexFrameDecoder
import coredevices.pebble.services.SpeexFrameDecoder
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioFrameBatch
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioGap
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioInterruption
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioStopSummary
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioStreamConfig
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioCheckpointSource
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioStreamHandler
import io.rebble.libpebblecommon.packets.BackgroundAudioStream
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo

/**
 * Phone-side handler that decodes watch background Speex streams into rotated PCM segments.
 */
class PebbleBackgroundAudioHandler(
    private val watchIdentifier: String,
    private val segmentStore: BackgroundAudioSegmentStore = BackgroundAudioSegmentStore(),
    private val transcriptionCoordinator: ContinuousTranscriptionCoordinator? = null,
    private val speexDecoderFactory: (VoiceEncoderInfo.Speex) -> SpeexFrameDecoder =
        { PebbleSpeexFrameDecoder(it) },
) : BackgroundAudioStreamHandler, BackgroundAudioCheckpointSource {
    private val logger = Logger.withTag("PebbleBgAudio")
    private var writer: BackgroundAudioSegmentWriter? = null

    override suspend fun canAccept(config: BackgroundAudioStreamConfig): Boolean {
        return config.codecId == BackgroundAudioStream.CODEC_SPEEX_WIDEBAND
    }

    override suspend fun onStreamStarted(config: BackgroundAudioStreamConfig) {
        writer = BackgroundAudioSegmentWriter(
            store = segmentStore,
            watchIdentifier = watchIdentifier,
            speexDecoderFactory = speexDecoderFactory,
        ).also { it.beginStream(config) }
        logger.i { "Background stream started id=${config.streamId}" }
    }

    override suspend fun onFrameBatch(batch: BackgroundAudioFrameBatch) {
        writer?.writeBatch(batch)?.forEach { enqueueClosedSegment(it) }
    }

    override suspend fun onGap(gap: BackgroundAudioGap) {
        writer?.recordGap()
        logger.w { "Background gap stream=${gap.streamId} seq=${gap.firstMissingSequence} count=${gap.missingFrameCount}" }
    }

    override suspend fun onStreamStopped(summary: BackgroundAudioStopSummary) {
        writer?.finishStream(SegmentClosedReason.StreamStopped)?.let { enqueueClosedSegment(it) }
        writer = null
        logger.i { "Background stream stopped id=${summary.streamId}" }
    }

    override suspend fun onStreamInterrupted(interruption: BackgroundAudioInterruption) {
        writer?.finishStream(SegmentClosedReason.Disconnected)?.let { enqueueClosedSegment(it) }
        writer = null
        logger.w { "Background stream interrupted id=${interruption.streamId} reason=${interruption.reason}" }
    }

    override val lastPersistedSequence: UInt
        get() = writer?.lastPersistedSequence ?: 0u

    override val lastPersistedSampleIndex: ULong
        get() = writer?.lastPersistedSampleIndex ?: 0u

    private suspend fun enqueueClosedSegment(metadata: BackgroundAudioSegmentMetadata) {
        try {
            transcriptionCoordinator?.enqueueClosedSegment(metadata)
        } catch (e: Exception) {
            logger.w(e) { "Failed to enqueue background segment ${metadata.segmentId}" }
        }
    }
}
