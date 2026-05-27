package coredevices.pebble.services.backgroundaudio

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

data class BackgroundAudioRetentionPolicy(
    val maxAudioBytes: Long = 512L * 1024L * 1024L,
    val minFreeBytes: Long = 250L * 1024L * 1024L,
    val maxAudioAge: Duration = 24.hours,
    val deleteAudioAfterSuccessfulTranscription: Boolean = false,
)
