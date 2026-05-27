package io.rebble.libpebblecommon.connection.endpointmanager.audio

import io.rebble.libpebblecommon.packets.Result
import io.rebble.libpebblecommon.packets.SessionType
import io.rebble.libpebblecommon.services.VoiceService
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import io.rebble.libpebblecommon.voice.VoiceSessionIntent
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DefaultTranscriptionVoiceSessionHandlerTest {
    @Test
    fun doesNotHandleIndexMemoSessions() = runTest {
        val handler = DefaultTranscriptionVoiceSessionHandler(FakeTranscriptionProvider())
        assertFalse(handler.canHandle(request(VoiceSessionIntent.IndexMemo)))
        assertTrue(handler.canHandle(request(VoiceSessionIntent.Default)))
    }

    @Test
    fun cannotServeWhenTranscriptionUnavailable() = runTest {
        val handler = DefaultTranscriptionVoiceSessionHandler(
            FakeTranscriptionProvider(canServe = false),
        )
        assertFalse(handler.canServe(request(VoiceSessionIntent.Default)))
    }

    @Test
    fun transcribesDefaultSessions() = runTest {
        val provider = FakeTranscriptionProvider(
            result = TranscriptionResult.Success(listOf(TranscriptionWord("Hi", 0.9f))),
        )
        val handler = DefaultTranscriptionVoiceSessionHandler(provider)
        val result = handler.handle(request(VoiceSessionIntent.Default), emptyFlow())
        assertEquals(Result.Success, result.protocolResult)
        assertEquals(listOf(TranscriptionWord("Hi", 0.9f)), result.wordsForWatch)
        assertTrue(provider.transcribeInvoked)
    }

    private fun request(intent: VoiceSessionIntent) = VoiceService.SessionSetupRequest(
        appUuid = Uuid.NIL,
        sessionId = 1,
        sessionType = SessionType.Dictation,
        encoderInfo = VoiceEncoderInfo.Speex(
            sampleRate = 16000,
            version = "1.2rc1",
            bitRate = 12800,
            bitstreamVersion = 4,
            frameSize = 320,
        ),
        sessionIntent = intent,
    )

    private class FakeTranscriptionProvider(
        private val canServe: Boolean = true,
        val result: TranscriptionResult = TranscriptionResult.Success(emptyList()),
    ) : TranscriptionProvider {
        var transcribeInvoked = false

        override suspend fun canServeSession(): Boolean = canServe

        override suspend fun transcribe(
            encoderInfo: VoiceEncoderInfo,
            audioFrames: kotlinx.coroutines.flow.Flow<UByteArray>,
            isNotificationReply: Boolean,
        ): TranscriptionResult {
            transcribeInvoked = true
            return result
        }
    }
}
