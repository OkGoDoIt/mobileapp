package io.rebble.libpebblecommon.audiocontext

import kotlinx.serialization.Serializable

@Serializable
enum class AudioContextPermission(val capability: String) {
    Status("audio_status"),
    RecentTranscript("audio_transcript"),
    TranscriptHistory("audio_history"),
    LiveTranscript("audio_transcript"),
    RawAudio("audio_raw"),
}

@Serializable
enum class AudioContextAvailability {
    Available,
    UnsupportedWatch,
    UnsupportedPhone,
    DisabledByUser,
    PermissionDenied,
    CapabilityNotDeclared,
    TranscriptionUnavailable,
    NoData,
    Error,
}

@Serializable
enum class AudioContextPromptResult {
    Granted,
    Denied,
    Dismissed,
    Unavailable,
}

@Serializable
enum class AudioContextRawEncoding {
    Pcm16Le,
}

@Serializable
data class AudioContextStatus(
    val availability: AudioContextAvailability,
    val backgroundAudioEnabled: Boolean,
    val streamState: String,
    val transcriptionEnabled: Boolean,
    val storageState: String,
    val currentLiveSubscribers: Int,
    val currentRawSubscribers: Int,
)

@Serializable
data class AudioContextTimeWindow(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
)

@Serializable
data class AudioContextQueryWindow(
    val beforeSeconds: Int = 60,
    val afterSeconds: Int = 0,
    val anchorEpochMs: Long? = null,
    val startedAtEpochMs: Long? = null,
    val endedAtEpochMs: Long? = null,
)

@Serializable
data class AudioContextSourceMetadata(
    val sourceType: String,
    val sourceDeviceId: String?,
    val sourceAction: String?,
    val streamId: Long?,
    val segmentId: String?,
    val gapCount: Int,
)

@Serializable
data class AudioContextTriggerMetadata(
    val launchReason: String,
    val triggerTimestampEpochMs: Long?,
    val sourceType: String?,
    val sourceAction: String?,
    val button: String?,
    val args: Long?,
)

@Serializable
data class AudioContextTranscriptSegment(
    val id: String,
    val text: String,
    val isFinal: Boolean,
    val window: AudioContextTimeWindow,
    val language: String?,
    val provider: String?,
    val modelUsed: String?,
    val source: AudioContextSourceMetadata,
    val warnings: List<String>,
)

@Serializable
data class AudioContextSubscriptionOptions(
    val includePartial: Boolean = true,
    val startedAtEpochMs: Long? = null,
)

@Serializable
data class AudioContextRawAudioOptions(
    val maxChunkBytes: Int = 16_000,
    val includeBackfill: Boolean = false,
)

@Serializable
data class AudioContextRawAudioChunk(
    val streamId: Long,
    val sequenceStart: Long,
    val sampleIndexStart: Long,
    val timestampEpochMs: Long?,
    val sampleRateHz: Int,
    val channels: Int,
    val encoding: AudioContextRawEncoding,
    val bytes: ByteArray,
    val gapCountSinceLastChunk: Int,
)
