package io.rebble.libpebblecommon.connection.endpointmanager.audio

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.AudioStream
import io.rebble.libpebblecommon.packets.DictationResult
import io.rebble.libpebblecommon.packets.Result
import io.rebble.libpebblecommon.packets.Sentence
import io.rebble.libpebblecommon.packets.SessionSetupResult
import io.rebble.libpebblecommon.packets.SessionType
import io.rebble.libpebblecommon.packets.VoiceAttribute
import io.rebble.libpebblecommon.packets.VoiceAttributeType
import io.rebble.libpebblecommon.services.AudioStreamService
import io.rebble.libpebblecommon.services.VoiceService
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.toProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class VoiceSessionManager(
    private val voiceService: VoiceService,
    private val audioStreamService: AudioStreamService,
    private val watchScope: ConnectionCoroutineScope,
    private val transcriptionProvider: TranscriptionProvider,
    private val voiceSessionHandlers: List<VoiceSessionHandler> = emptyList(),
) {
    companion object Companion {
        private val logger = Logger.withTag("VoiceSession")
    }

    private val defaultHandler = DefaultTranscriptionVoiceSessionHandler(transcriptionProvider)

    private val _currentSession = MutableStateFlow<CurrentSession?>(null)
    val currentSession = _currentSession.asStateFlow()

    data class CurrentSession(
        val request: VoiceService.SessionSetupRequest,
        val result: CompletableDeferred<TranscriptionResult>,
    )

    private fun makeSetupResult(
        sessionType: SessionType,
        result: Result,
        appInitiated: Boolean,
    ): SessionSetupResult {
        val setupResult = SessionSetupResult(sessionType, result)
        if (appInitiated) {
            setupResult.flags.set(1u)
        }
        return setupResult
    }

    private fun makeDictationResult(
        sessionId: UShort,
        result: Result,
        words: Iterable<TranscriptionWord>?,
        appUuid: Uuid,
    ): DictationResult {
        return DictationResult(
            sessionId,
            result,
            buildList {
                words?.let {
                    add(
                        VoiceAttribute(
                            id = VoiceAttributeType.Transcription.value,
                            content = VoiceAttribute.Transcription(
                                sentences = listOf(
                                    Sentence(words.map { word -> word.toProtocol() }),
                                ),
                            ),
                        ),
                    )
                }
                if (appUuid != Uuid.NIL) {
                    add(
                        VoiceAttribute(
                            id = VoiceAttributeType.AppUuid.value,
                            content = VoiceAttribute.AppUuid().apply {
                                uuid.set(appUuid)
                            },
                        ),
                    )
                }
            },
        ).apply {
            if (appUuid != Uuid.NIL) {
                flags.set(1u)
            }
        }
    }

    private suspend fun selectHandler(
        request: VoiceService.SessionSetupRequest,
    ): VoiceSessionHandler? {
        voiceSessionHandlers.firstOrNull { it.canHandle(request) }?.let { return it }
        if (defaultHandler.canHandle(request)) {
            return defaultHandler
        }
        return null
    }

    fun init() {
        watchScope.launch {
            voiceService.sessionSetupRequests.flowOn(Dispatchers.IO).collectLatest { setupRequest ->
                logger.i { "New voice session started: $setupRequest" }
                var audioFrameFlowCollected = false
                val audioFrameFlow = audioStreamService.dataFlowForSession(setupRequest.sessionId.toUShort())
                    .transform { transfer ->
                        transfer.frames
                            .map { frame -> frame.data.get() }
                            .forEach { emit(it) }
                    }
                    .onStart {
                        audioFrameFlowCollected = true
                    }
                val appInitiated = setupRequest.appUuid != Uuid.NIL
                if (setupRequest.encoderInfo == null) {
                    logger.e { "Received voice session setup request without encoder info" }
                    voiceService.send(
                        makeSetupResult(
                            sessionType = setupRequest.sessionType,
                            result = Result.FailInvalidMessage,
                            appInitiated = appInitiated,
                        ),
                    )
                    return@collectLatest
                }

                val handler = selectHandler(setupRequest)
                if (handler == null) {
                    logger.w { "No voice session handler for $setupRequest" }
                    voiceService.send(
                        makeSetupResult(
                            sessionType = setupRequest.sessionType,
                            result = Result.FailDisabled,
                            appInitiated = appInitiated,
                        ),
                    )
                    return@collectLatest
                }

                if (!handler.canServe(setupRequest)) {
                    logger.w { "Voice session handler cannot serve $setupRequest" }
                    voiceService.send(
                        makeSetupResult(
                            sessionType = setupRequest.sessionType,
                            result = Result.FailDisabled,
                            appInitiated = appInitiated,
                        ),
                    )
                    return@collectLatest
                }

                voiceService.send(
                    makeSetupResult(
                        sessionType = setupRequest.sessionType,
                        result = Result.Success,
                        appInitiated = appInitiated,
                    ),
                )

                val resultCompletable = CompletableDeferred<TranscriptionResult>()
                _currentSession.value = CurrentSession(setupRequest, resultCompletable)
                logger.i { "Voice session initialized with ID: ${setupRequest.sessionId}" }

                val handlerResult = try {
                    handler.handle(setupRequest, audioFrameFlow)
                } catch (e: CancellationException) {
                    logger.d { "Voice session cancelled" }
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Voice session handler error: ${e.message}" }
                    VoiceSessionHandlerResult(Result.FailRecognizerError, null)
                }

                logger.i { "Voice session completed with result: ${handlerResult.protocolResult}" }
                if (!audioFrameFlowCollected) {
                    logger.w { "Audio frames not collected, sending audio stop packet" }
                    audioStreamService.send(AudioStream.StopTransfer(setupRequest.sessionId.toUShort()))
                }
                voiceService.send(
                    makeDictationResult(
                        sessionId = setupRequest.sessionId.toUShort(),
                        result = handlerResult.protocolResult,
                        words = handlerResult.wordsForWatch,
                        appUuid = setupRequest.appUuid,
                    ),
                )
                val transcriptionResult = when {
                    handlerResult.wordsForWatch != null ->
                        TranscriptionResult.Success(handlerResult.wordsForWatch)
                    handlerResult.protocolResult == Result.Success ->
                        TranscriptionResult.Success(emptyList())
                    handlerResult.protocolResult == Result.FailDisabled ->
                        TranscriptionResult.Disabled
                    else -> TranscriptionResult.Error("Voice session failed: ${handlerResult.protocolResult}")
                }
                resultCompletable.complete(transcriptionResult)
                _currentSession.value = null
            }
        }
    }
}
