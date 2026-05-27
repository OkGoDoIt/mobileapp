package coredevices.pebble.services.backgroundaudio

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class BackgroundAudioTranscriptionPolicy(
    val enabled: Boolean = true,
    val chunkSizeBytes: Int = 16_000,
    val minTimeout: Duration = 60.seconds,
    val maxTimeout: Duration = 5.minutes,
    val retryDelay: Duration = 1.minutes,
    val maxAttempts: Int = 3,
)
