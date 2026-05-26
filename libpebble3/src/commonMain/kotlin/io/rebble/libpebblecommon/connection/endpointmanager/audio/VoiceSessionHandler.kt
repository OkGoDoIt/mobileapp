package io.rebble.libpebblecommon.connection.endpointmanager.audio

import io.rebble.libpebblecommon.packets.Result
import io.rebble.libpebblecommon.services.VoiceService
import io.rebble.libpebblecommon.voice.TranscriptionWord
import kotlinx.coroutines.flow.Flow

interface VoiceSessionHandler {
    suspend fun canHandle(request: VoiceService.SessionSetupRequest): Boolean

    suspend fun canServe(request: VoiceService.SessionSetupRequest): Boolean = true

    suspend fun handle(
        request: VoiceService.SessionSetupRequest,
        audioFrames: Flow<UByteArray>,
    ): VoiceSessionHandlerResult
}

data class VoiceSessionHandlerResult(
    val protocolResult: Result,
    val wordsForWatch: List<TranscriptionWord>?,
)
