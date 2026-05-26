package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.util.recording.RecordingIngress
import coredevices.util.recording.RecordingIngressMetadata
import coredevices.util.recording.RecordingSourceType
import io.rebble.libpebblecommon.connection.endpointmanager.audio.VoiceSessionHandler
import io.rebble.libpebblecommon.connection.endpointmanager.audio.VoiceSessionHandlerResult
import io.rebble.libpebblecommon.packets.Result
import io.rebble.libpebblecommon.services.VoiceService
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import io.rebble.libpebblecommon.voice.VoiceSessionIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.io.buffered
import kotlinx.io.write
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Saves watch Index Memo voice sessions to [RecordingIngress] instead of dictation transcription.
 */
class WatchIndexMemoVoiceSessionHandler(
    private val recordingIngress: RecordingIngress?,
) : VoiceSessionHandler {
    private val logger = Logger.withTag("WatchIndexMemo")

    override suspend fun canHandle(request: VoiceService.SessionSetupRequest): Boolean {
        return request.sessionIntent is VoiceSessionIntent.IndexMemo
    }

    override suspend fun canServe(request: VoiceService.SessionSetupRequest): Boolean {
        return recordingIngress != null &&
            request.encoderInfo is VoiceEncoderInfo.Speex
    }

    override suspend fun handle(
        request: VoiceService.SessionSetupRequest,
        audioFrames: Flow<UByteArray>,
    ): VoiceSessionHandlerResult {
        val ingress = recordingIngress
            ?: return VoiceSessionHandlerResult(Result.FailDisabled, null)
        val encoderInfo = request.encoderInfo as? VoiceEncoderInfo.Speex
            ?: return VoiceSessionHandlerResult(Result.FailInvalidMessage, null)

        val fileId = "watch_index_memo-${Uuid.random()}"
        val startedAt = Clock.System.now()
        val metadata = RecordingIngressMetadata(
            sourceType = RecordingSourceType.PebbleWatchMemo,
            transportSessionId = request.sessionId.toString(),
            codec = "speex",
            startedAt = startedAt,
        )
        var finalized = false
        return try {
            PebbleSpeexFrameDecoder(encoderInfo).use { decoder ->
                ingress.openRawPcmSink(
                    fileId = fileId,
                    sampleRate = decoder.sampleRate,
                    mimeType = "audio/raw",
                    metadata = metadata,
                ).buffered().use { sink ->
                    audioFrames.collect { frame ->
                        sink.write(decoder.decode(frame))
                    }
                }
            }
            ingress.finalizeLocalRecording(
                fileId = fileId,
                buttonSequence = null,
                metadata = metadata.copy(endedAt = Clock.System.now()),
            )
            finalized = true
            logger.i { "Saved watch Index Memo recording $fileId" }
            VoiceSessionHandlerResult(
                protocolResult = Result.Success,
                wordsForWatch = listOf(TranscriptionWord("Saved", 1.0f)),
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to save watch Index Memo: ${e.message}" }
            if (!finalized) {
                ingress.cancelLocalRecording(fileId)
            }
            VoiceSessionHandlerResult(Result.FailRecognizerError, null)
        }
    }
}
