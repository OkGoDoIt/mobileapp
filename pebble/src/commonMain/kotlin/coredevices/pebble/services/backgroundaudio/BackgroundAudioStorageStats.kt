package coredevices.pebble.services.backgroundaudio

import kotlinx.serialization.Serializable

@Serializable
data class BackgroundAudioStorageStats(
    val audioBytes: Long,
    val transcriptBytes: Long,
    val metadataBytes: Long,
    val segmentCount: Int,
    val oldestSegmentStartedAtEpochMs: Long?,
)
