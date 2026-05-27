package coredevices.pebble.services.backgroundaudio

import coredevices.util.queue.QueueTask
import coredevices.util.queue.TaskStatus
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class BackgroundAudioTranscriptionTask(
    override val id: Long = 0,
    override val created: Instant = Clock.System.now(),
    override val lastAttempt: Instant? = null,
    override val attempts: Int = 0,
    override val status: TaskStatus = TaskStatus.Pending,
    val segmentId: String,
    val lastSuccessfulStage: String? = null,
) : QueueTask
