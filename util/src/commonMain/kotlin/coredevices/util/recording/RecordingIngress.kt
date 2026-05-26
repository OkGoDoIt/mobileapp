package coredevices.util.recording

import kotlinx.io.Sink
import kotlin.time.Instant

enum class RecordingSourceType {
    PhoneMic,
    IndexRing,
    PebbleWatchMemo,
    PebbleWatchContinuous,
}

data class RecordingIngressMetadata(
    val sourceType: RecordingSourceType,
    val sourceDeviceId: String? = null,
    val transportSessionId: String? = null,
    val codec: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
)

interface RecordingIngress {
    suspend fun openRawPcmSink(
        fileId: String,
        sampleRate: Int,
        mimeType: String,
        metadata: RecordingIngressMetadata,
    ): Sink

    suspend fun finalizeLocalRecording(
        fileId: String,
        buttonSequence: String? = null,
        metadata: RecordingIngressMetadata,
    )

    suspend fun cancelLocalRecording(fileId: String)
}
