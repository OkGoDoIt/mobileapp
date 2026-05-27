package io.rebble.libpebblecommon.connection.endpointmanager.audio.background

/** Watch-reported stream configuration from [BackgroundAudioStreamStart]. */
data class BackgroundAudioStreamConfig(
    val streamId: UInt,
    val protocolVersion: UByte,
    val codecId: UByte,
    val channels: UByte,
    val frameSamples: UShort,
    val sampleRateHz: UInt,
    val bitRateBps: UInt,
    val frameDurationMs: UShort,
    val startTimeMs: ULong,
    val startMonotonicMs: ULong,
    val flags: UInt,
)

data class BackgroundAudioFrameBatch(
    val streamId: UInt,
    val firstSequence: UInt,
    val firstSampleIndex: ULong,
    val frames: List<UByteArray>,
)

data class BackgroundAudioGap(
    val streamId: UInt,
    val firstMissingSequence: UInt,
    val missingFrameCount: UInt,
    val firstMissingSampleIndex: ULong,
    val reason: UByte,
    val watchDropCounter: UInt,
    val synthetic: Boolean = false,
)

data class BackgroundAudioStopSummary(
    val streamId: UInt,
    val reason: UByte,
    val finalSequence: UInt,
    val finalSampleIndex: ULong,
)

data class BackgroundAudioInterruption(
    val streamId: UInt,
    val reason: String,
    val lastReceivedSequence: UInt?,
    val lastReceivedSampleIndex: ULong?,
)

enum class BackgroundAudioStreamState {
    Idle,
    Receiving,
    PausedDisconnected,
    UnsupportedWatch,
    UnsupportedCodec,
    PhoneCapabilityDisabled,
    LowStoragePaused,
    Error,
}
