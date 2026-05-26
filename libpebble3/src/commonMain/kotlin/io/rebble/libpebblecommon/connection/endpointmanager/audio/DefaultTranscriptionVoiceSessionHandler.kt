package io.rebble.libpebblecommon.connection.endpointmanager.audio

import io.rebble.libpebblecommon.SystemAppIDs
import io.rebble.libpebblecommon.packets.Result
import io.rebble.libpebblecommon.services.VoiceService
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.VoiceSessionIntent
import io.rebble.libpebblecommon.voice.toProtocol
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

internal class DefaultTranscriptionVoiceSessionHandler(
    private val transcriptionProvider: TranscriptionProvider,
) : VoiceSessionHandler {
    override suspend fun canHandle(request: VoiceService.SessionSetupRequest): Boolean {
        return request.sessionIntent !is VoiceSessionIntent.IndexMemo
    }

    override suspend fun canServe(request: VoiceService.SessionSetupRequest): Boolean {
        return transcriptionProvider.canServeSession()
    }

    override suspend fun handle(
        request: VoiceService.SessionSetupRequest,
        audioFrames: Flow<UByteArray>,
    ): VoiceSessionHandlerResult {
        val encoderInfo = request.encoderInfo
            ?: return VoiceSessionHandlerResult(Result.FailInvalidMessage, null)
        val result = transcriptionProvider.transcribe(
            encoderInfo,
            audioFrames,
            isNotificationReply = request.appUuid == Uuid.NIL ||
                request.appUuid == SystemAppIDs.NOTIFICATIONS_APP_UUID,
        )
        return when (result) {
            is TranscriptionResult.Success -> VoiceSessionHandlerResult(
                protocolResult = result.toProtocol(),
                wordsForWatch = result.words,
            )
            is TranscriptionResult.Disabled -> VoiceSessionHandlerResult(Result.FailDisabled, null)
            is TranscriptionResult.Error,
            is TranscriptionResult.Failed,
            is TranscriptionResult.ConnectionError,
            -> VoiceSessionHandlerResult(Result.FailRecognizerError, null)
        }
    }
}
