package coredevices.pebble.services.backgroundaudio

import co.touchlab.kermit.Logger
import coredevices.util.AudioEncoding
import coredevices.util.queue.PersistentQueueScheduler
import coredevices.util.queue.RecoverableTaskException
import coredevices.util.transcription.STTLanguage
import coredevices.util.transcription.TranscriptionException
import coredevices.util.transcription.TranscriptionService
import coredevices.util.transcription.TranscriptionSessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Queues bounded transcription windows for closed background audio segments.
 */
class ContinuousTranscriptionCoordinator(
    private val segmentStore: BackgroundAudioSegmentStore,
    private val taskRepository: BackgroundAudioTranscriptionTaskRepository,
    private val transcriptionService: TranscriptionService,
    private val policy: BackgroundAudioTranscriptionPolicy,
    private val scope: BackgroundAudioScope,
) : PersistentQueueScheduler<BackgroundAudioTranscriptionTask>(
    repository = taskRepository,
    scope = scope,
    label = "BgAudioTranscription",
    rescheduleDelay = policy.retryDelay,
    maxAttempts = policy.maxAttempts,
    maxConcurrency = 1,
) {
    private val logger = Logger.withTag("BgAudioTranscription")
    private val _transcripts = MutableSharedFlow<BackgroundAudioTranscript>(extraBufferCapacity = 8)
    val transcripts: SharedFlow<BackgroundAudioTranscript> = _transcripts.asSharedFlow()

    fun resumePending() = resumePendingTasks()

    suspend fun enqueueClosedSegment(metadata: BackgroundAudioSegmentMetadata) {
        if (!policy.enabled) {
            segmentStore.updateMetadata(metadata.segmentId) {
                it.copy(
                    transcriptionStatus = TranscriptionStatus.Disabled,
                    transcriptionUpdatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                )
            }
            return
        }
        if (metadata.status != SegmentStatus.Closed || metadata.transcriptionStatus == TranscriptionStatus.Complete) {
            return
        }
        val id = taskRepository.insertOrReusePendingTask(metadata.segmentId)
        logger.d { "Scheduled background transcription task $id for segment ${metadata.segmentId}" }
        scheduleTask(id)
    }

    override suspend fun processTask(task: BackgroundAudioTranscriptionTask) {
        val metadata = segmentStore.readMetadata(task.segmentId) ?: return
        if (!metadata.shouldTranscribe()) {
            return
        }
        val pcmPath = Path(metadata.pcmPath)
        if (!SystemFileSystem.exists(pcmPath)) {
            markFailed(metadata.segmentId, "PCM segment is missing", task.attempts + 1)
            throw IllegalStateException("PCM segment ${metadata.segmentId} is missing")
        }
        if (!transcriptionService.isAvailable()) {
            markRetrying(metadata.segmentId, "Transcription service unavailable", task.attempts + 1)
            throw RecoverableTaskException("Transcription service unavailable")
        }

        segmentStore.updateMetadata(metadata.segmentId) {
            it.copy(
                transcriptionStatus = TranscriptionStatus.InProgress,
                transcriptionAttemptCount = task.attempts + 1,
                transcriptionUpdatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                transcriptionError = null,
            )
        }

        val reader = BackgroundAudioPcmReader(policy.chunkSizeBytes)
        val timeout = metadata.transcriptionTimeout()
        var finalText = ""
        var partialText: String? = null
        var modelUsed: String? = null
        try {
            transcriptionService.transcribe(
                audioStreamFrames = reader.readChunks(pcmPath),
                sampleRate = metadata.sampleRateHz,
                language = STTLanguage.Automatic,
                encoding = AudioEncoding.PCM_16BIT,
                timeout = timeout,
            ).collect { status ->
                when (status) {
                    TranscriptionSessionStatus.Open -> Unit
                    is TranscriptionSessionStatus.Partial -> partialText = status.text
                    is TranscriptionSessionStatus.Transcription -> {
                        finalText = status.text.trim()
                        modelUsed = status.modelUsed
                    }
                }
            }
        } catch (e: TranscriptionException.NoSpeechDetected) {
            writeTranscript(metadata, "", partialText, e.modelUsed, listOf(e.message ?: "No speech detected"))
            updateTerminal(metadata.segmentId, TranscriptionStatus.NoSpeech, task.attempts + 1)
            return
        } catch (e: TranscriptionException.TranscriptionNetworkError) {
            markRetrying(metadata.segmentId, e.message ?: "Network error", task.attempts + 1)
            throw RecoverableTaskException("Network transcription error", e)
        } catch (e: TranscriptionException.TranscriptionServiceUnavailable) {
            markRetrying(metadata.segmentId, e.message ?: "Service unavailable", task.attempts + 1)
            throw RecoverableTaskException("Transcription service unavailable", e)
        } catch (e: TranscriptionException.TranscriptionRequiresDownload) {
            markFailed(metadata.segmentId, e.message ?: "Model download required", task.attempts + 1)
            throw e
        } catch (e: TranscriptionException) {
            markFailed(metadata.segmentId, e.message ?: "Transcription failed", task.attempts + 1)
            throw e
        }

        if (finalText.isBlank()) {
            writeTranscript(metadata, "", partialText, modelUsed, listOf("Provider returned no final speech"))
            updateTerminal(metadata.segmentId, TranscriptionStatus.NoSpeech, task.attempts + 1)
        } else {
            writeTranscript(metadata, finalText, partialText, modelUsed, emptyList())
            updateTerminal(metadata.segmentId, TranscriptionStatus.Complete, task.attempts + 1)
        }
    }

    private fun BackgroundAudioSegmentMetadata.shouldTranscribe(): Boolean {
        return status == SegmentStatus.Closed &&
            transcriptionStatus != TranscriptionStatus.Disabled &&
            transcriptionStatus != TranscriptionStatus.Complete &&
            transcriptionStatus != TranscriptionStatus.NoSpeech
    }

    private fun BackgroundAudioSegmentMetadata.transcriptionTimeout() =
        (((endedAtEpochMs ?: Clock.System.now().toEpochMilliseconds()) - startedAtEpochMs).milliseconds +
            policy.minTimeout)
            .coerceAtLeast(policy.minTimeout)
            .coerceAtMost(policy.maxTimeout)

    private fun writeTranscript(
        metadata: BackgroundAudioSegmentMetadata,
        finalText: String,
        partialText: String?,
        modelUsed: String?,
        warnings: List<String>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val transcript = BackgroundAudioTranscript(
            segmentId = metadata.segmentId,
            watchIdentifier = metadata.watchIdentifier,
            sourceType = coredevices.util.recording.RecordingSourceType.valueOf(metadata.sourceType),
            startedAtEpochMs = metadata.startedAtEpochMs,
            endedAtEpochMs = metadata.endedAtEpochMs,
            sampleRateHz = metadata.sampleRateHz,
            language = null,
            provider = "default",
            modelUsed = modelUsed,
            finalText = finalText,
            partialText = partialText,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            gapCount = metadata.gapCount,
            warnings = warnings,
        )
        val transcriptPath = segmentStore.writeTranscript(transcript)
        _transcripts.tryEmit(transcript)
        segmentStore.updateMetadata(metadata.segmentId) {
            it.copy(transcriptPath = transcriptPath.toString())
        }
    }

    private fun updateTerminal(segmentId: String, status: TranscriptionStatus, attempts: Int) {
        segmentStore.updateMetadata(segmentId) {
            it.copy(
                transcriptionStatus = status,
                transcriptionAttemptCount = attempts,
                transcriptionUpdatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                transcriptionError = null,
            )
        }
    }

    private fun markRetrying(segmentId: String, error: String, attempts: Int) {
        segmentStore.updateMetadata(segmentId) {
            it.copy(
                transcriptionStatus = TranscriptionStatus.Retrying,
                transcriptionAttemptCount = attempts,
                transcriptionUpdatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                transcriptionError = error.take(200),
            )
        }
    }

    private fun markFailed(segmentId: String, error: String, attempts: Int) {
        segmentStore.updateMetadata(segmentId) {
            it.copy(
                transcriptionStatus = TranscriptionStatus.Failed,
                transcriptionAttemptCount = attempts,
                transcriptionUpdatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                transcriptionError = error.take(200),
            )
        }
    }
}
