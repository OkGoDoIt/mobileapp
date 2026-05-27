package coredevices.pebble.services.backgroundaudio

import coredevices.util.recording.RecordingSourceType
import kotlinx.serialization.Serializable

@Serializable
data class BackgroundAudioTranscript(
    val segmentId: String,
    val watchIdentifier: String,
    val sourceType: RecordingSourceType,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val sampleRateHz: Int,
    val language: String?,
    val provider: String?,
    val modelUsed: String?,
    val finalText: String,
    val partialText: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val gapCount: Int,
    val warnings: List<String> = emptyList(),
)
