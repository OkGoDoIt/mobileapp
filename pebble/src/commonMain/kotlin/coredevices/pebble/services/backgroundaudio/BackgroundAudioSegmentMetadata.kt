package coredevices.pebble.services.backgroundaudio

import coredevices.util.recording.RecordingSourceType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class SegmentStatus {
    Open,
    Closed,
    Failed,
}

@Serializable
enum class TranscriptionStatus {
    Pending,
    Disabled,
    InProgress,
    Complete,
    Failed,
}

@Serializable
data class BackgroundAudioSegmentMetadata(
    val segmentId: String,
    val watchIdentifier: String,
    val streamId: Long,
    val sourceType: String = RecordingSourceType.PebbleWatchContinuous.name,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val sampleRateHz: Int,
    val channels: Int,
    val codecId: Int,
    val codecProfile: String,
    val firstSequence: Long,
    val lastSequence: Long? = null,
    val firstSampleIndex: Long,
    val lastSampleIndex: Long? = null,
    val pcmPath: String,
    val status: SegmentStatus = SegmentStatus.Open,
    val gapCount: Int = 0,
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.Pending,
) {
    val startedAt: Instant get() = Instant.fromEpochMilliseconds(startedAtEpochMs)
    val endedAt: Instant? get() = endedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) }
}
