package coredevices.pebble.services.backgroundaudio

import coredevices.pebble.services.SpeexFrameDecoder
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioFrameBatch
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioStreamConfig
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.write
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Writes decoded PCM for a single open segment, rotating when duration is exceeded.
 */
class BackgroundAudioSegmentWriter(
    private val store: BackgroundAudioSegmentStore,
    private val watchIdentifier: String,
    private val segmentDurationSeconds: Int = 30,
    private val speexDecoderFactory: (VoiceEncoderInfo.Speex) -> SpeexFrameDecoder,
) {
    private var config: BackgroundAudioStreamConfig? = null
    private var decoder: SpeexFrameDecoder? = null
    private var openSegment: OpenSegment? = null
    var lastPersistedSequence: UInt = 0u
        private set
    var lastPersistedSampleIndex: ULong = 0u
        private set

    private data class OpenSegment(
        val metadata: BackgroundAudioSegmentMetadata,
        val maxBytes: Int,
        var bytesWritten: Int = 0,
        var gapCount: Int = 0,
        var lastSequence: Long = 0,
        var lastSampleIndex: Long = 0,
    )

    fun beginStream(config: BackgroundAudioStreamConfig) {
        this.config = config
        decoder?.close()
        val encoderInfo = VoiceEncoderInfo.Speex(
            sampleRate = config.sampleRateHz.toLong(),
            version = "4",
            bitRate = config.bitRateBps.toInt(),
            bitstreamVersion = 4,
            frameSize = config.frameSamples.toInt(),
        )
        decoder = speexDecoderFactory(encoderInfo)
    }

    fun writeBatch(batch: BackgroundAudioFrameBatch): List<BackgroundAudioSegmentMetadata> {
        val dec = decoder ?: return emptyList()
        ensureOpenSegment(batch.firstSequence, batch.firstSampleIndex)
        val segment = openSegment ?: return emptyList()
        val pcmPath = store.segmentPcmPath(segment.metadata.segmentId)
        SystemFileSystem.sink(pcmPath, append = true).buffered().use { sink ->
            batch.frames.forEachIndexed { index, frame ->
                val pcm = dec.decode(frame)
                sink.write(pcm)
                segment.bytesWritten += pcm.size
                segment.lastSequence = batch.firstSequence.toLong() + index
                segment.lastSampleIndex = batch.firstSampleIndex.toLong() +
                    (index * config!!.frameSamples.toLong())
                lastPersistedSequence = segment.lastSequence.toUInt()
                lastPersistedSampleIndex = segment.lastSampleIndex.toULong()
            }
        }
        persistMetadata(segment)
        if (segment.bytesWritten >= segment.maxBytes) {
            return listOfNotNull(closeOpenSegment(SegmentClosedReason.DurationRotation))
        }
        return emptyList()
    }

    fun recordGap(): BackgroundAudioSegmentMetadata? {
        openSegment?.let {
            it.gapCount++
            persistMetadata(it)
        }
        return null
    }

    fun finishStream(reason: SegmentClosedReason = SegmentClosedReason.StreamStopped): BackgroundAudioSegmentMetadata? {
        val closed = closeOpenSegment(reason)
        decoder?.close()
        decoder = null
        config = null
        return closed
    }

    private fun ensureOpenSegment(firstSequence: UInt, firstSampleIndex: ULong) {
        if (openSegment != null) {
            return
        }
        val cfg = config ?: return
        val segmentId = "bg-${Uuid.random()}"
        val maxBytes = cfg.sampleRateHz.toInt() * Short.SIZE_BYTES * segmentDurationSeconds
        val metadata = BackgroundAudioSegmentMetadata(
            segmentId = segmentId,
            watchIdentifier = watchIdentifier,
            streamId = cfg.streamId.toLong(),
            startedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            sampleRateHz = cfg.sampleRateHz.toInt(),
            channels = cfg.channels.toInt(),
            codecId = cfg.codecId.toInt(),
            codecProfile = "speex-wideband",
            firstSequence = firstSequence.toLong(),
            firstSampleIndex = firstSampleIndex.toLong(),
            pcmPath = store.segmentPcmPath(segmentId).toString(),
            status = SegmentStatus.Open,
        )
        openSegment = OpenSegment(metadata = metadata, maxBytes = maxBytes)
        store.writeMetadata(metadata)
    }

    private fun closeOpenSegment(reason: SegmentClosedReason): BackgroundAudioSegmentMetadata? {
        val segment = openSegment ?: return null
        val closed = segment.metadata.copy(
            endedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            lastSequence = segment.lastSequence,
            lastSampleIndex = segment.lastSampleIndex,
            status = SegmentStatus.Closed,
            gapCount = segment.gapCount,
            bytesWritten = segment.bytesWritten.toLong(),
            closedReason = reason,
        )
        store.writeMetadata(closed)
        openSegment = null
        return closed
    }

    private fun persistMetadata(segment: OpenSegment) {
        store.writeMetadata(
            segment.metadata.copy(
                lastSequence = segment.lastSequence,
                lastSampleIndex = segment.lastSampleIndex,
                gapCount = segment.gapCount,
                bytesWritten = segment.bytesWritten.toLong(),
            ),
        )
    }
}
