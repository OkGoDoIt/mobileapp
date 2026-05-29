package io.rebble.libpebblecommon.connection.endpointmanager.audio.context

import io.rebble.libpebblecommon.PacketPriority
import io.rebble.libpebblecommon.audiocontext.AudioContextAvailability
import io.rebble.libpebblecommon.audiocontext.AudioContextPermission
import io.rebble.libpebblecommon.audiocontext.AudioContextPermissionException
import io.rebble.libpebblecommon.audiocontext.AudioContextPromptResult
import io.rebble.libpebblecommon.audiocontext.AudioContextProvider
import io.rebble.libpebblecommon.audiocontext.AudioContextQueryWindow
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioChunk
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextStatus
import io.rebble.libpebblecommon.audiocontext.AudioContextSubscriptionOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextTriggerMetadata
import io.rebble.libpebblecommon.audiocontext.AudioContextTranscriptSegment
import io.rebble.libpebblecommon.audiocontext.AudioContextSourceMetadata
import io.rebble.libpebblecommon.audiocontext.AudioContextTimeWindow
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.AppAudioContext
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AppAudioContextManagerTest {
    @Test
    fun statusRequestReturnsStatusResponse() = runBlocking {
        val handler = RecordingProtocolHandler()
        val manager = AppAudioContextManager(
            protocolHandler = handler,
            provider = FakeProvider(),
            watchScope = ConnectionCoroutineScope(SupervisorJob()),
        )
        manager.init()

        val appUuid = Uuid.random()
        handler.emit(
            AppAudioContext.StatusRequest(requestId = 9u, appUuid = appUuid),
        )
        delay(100)

        val response = handler.sent.single() as AppAudioContext.StatusResponse
        assertEquals(9u, response.requestId.get())
        assertEquals(appUuid, response.appUuid.get())
        assertEquals(AudioContextAvailability.Available.ordinal.toUByte(), response.availability.get())
    }

    @Test
    fun permissionFailureReturnsErrorResponse() = runBlocking {
        val handler = RecordingProtocolHandler()
        val manager = AppAudioContextManager(
            protocolHandler = handler,
            provider = object : FakeProvider() {
                override suspend fun recentTranscript(
                    appUuid: Uuid,
                    window: AudioContextQueryWindow,
                ): List<AudioContextTranscriptSegment> {
                    throw AudioContextPermissionException(
                        AudioContextAvailability.PermissionDenied,
                        "denied",
                    )
                }
            },
            watchScope = ConnectionCoroutineScope(SupervisorJob()),
        )
        manager.init()

        handler.emit(
            AppAudioContext.TranscriptRequest(requestId = 3u, appUuid = Uuid.random()),
        )
        delay(100)

        val response = handler.sent.single() as AppAudioContext.ErrorResponse
        assertEquals(AppAudioContext.ErrorCode.PermissionDenied.value, response.errorCode.get())
    }

    @Test
    fun bindsRequestToRunningAppUuidInsteadOfPacketUuid() = runBlocking {
        val handler = RecordingProtocolHandler()
        val runningAppUuid = Uuid.random()
        val packetAppUuid = Uuid.random()
        val provider = FakeProvider()
        val manager = AppAudioContextManager(
            protocolHandler = handler,
            provider = provider,
            watchScope = ConnectionCoroutineScope(SupervisorJob()),
            runningAppUuid = { runningAppUuid },
        )
        manager.init()

        handler.emit(
            AppAudioContext.StatusRequest(requestId = 11u, appUuid = packetAppUuid),
        )
        delay(100)

        val response = handler.sent.single() as AppAudioContext.StatusResponse
        assertEquals(runningAppUuid, provider.lastStatusAppUuid)
        assertEquals(runningAppUuid, response.appUuid.get())
    }

    @Test
    fun oversizedTranscriptPayloadIsChunkedAcrossResponses() = runBlocking {
        val handler = RecordingProtocolHandler()
        val manager = AppAudioContextManager(
            protocolHandler = handler,
            provider = object : FakeProvider() {
                override suspend fun recentTranscript(
                    appUuid: Uuid,
                    window: AudioContextQueryWindow,
                ): List<AudioContextTranscriptSegment> = listOf(segment("x".repeat(5000)))
            },
            watchScope = ConnectionCoroutineScope(SupervisorJob()),
        )
        manager.init()

        handler.emit(
            AppAudioContext.TranscriptRequest(requestId = 12u, appUuid = Uuid.random()),
        )
        delay(100)

        val responses = handler.sent.filterIsInstance<AppAudioContext.TranscriptResponse>()
        assertTrue(responses.size > 1)
        assertEquals(0u, responses.first().partIndex.get())
        assertEquals(responses.size.toUShort(), responses.first().partCount.get())
    }

    private class RecordingProtocolHandler : PebbleProtocolHandler {
        private val inbound = MutableSharedFlow<PebblePacket>(replay = 8)
        val sent = mutableListOf<PebblePacket>()
        override val inboundMessages: SharedFlow<PebblePacket> = inbound
        override val rawInboundMessages = emptyFlow<ByteArray>()

        suspend fun emit(packet: PebblePacket) {
            inbound.emit(packet)
        }

        override suspend fun send(message: PebblePacket, priority: PacketPriority) {
            sent.add(message)
        }

        override suspend fun send(message: ByteArray, priority: PacketPriority) = Unit
    }

    private open class FakeProvider : AudioContextProvider {
        var lastStatusAppUuid: Uuid? = null

        override suspend fun status(appUuid: Uuid): AudioContextStatus = AudioContextStatus(
            availability = AudioContextAvailability.Available,
            backgroundAudioEnabled = true,
            streamState = "Receiving",
            transcriptionEnabled = true,
            storageState = "Accepting",
            currentLiveSubscribers = 0,
            currentRawSubscribers = 0,
        ).also {
            lastStatusAppUuid = appUuid
        }

        override fun statusUpdates(appUuid: Uuid): Flow<AudioContextStatus> = flowOf(runBlocking { status(appUuid) })

        override suspend fun triggerInfo(appUuid: Uuid): AudioContextTriggerMetadata =
            AudioContextTriggerMetadata(
                launchReason = "unknown",
                triggerTimestampEpochMs = null,
                sourceType = null,
                sourceAction = null,
                button = null,
                args = null,
            )

        override suspend fun requestEnablePrompt(appUuid: Uuid): AudioContextPromptResult =
            AudioContextPromptResult.Granted

        override suspend fun requestPermissionPrompt(
            appUuid: Uuid,
            permissions: Set<AudioContextPermission>,
        ): AudioContextPromptResult = AudioContextPromptResult.Granted

        override suspend fun recentTranscript(
            appUuid: Uuid,
            window: AudioContextQueryWindow,
        ): List<AudioContextTranscriptSegment> = emptyList()

        override suspend fun transcriptHistory(
            appUuid: Uuid,
            window: AudioContextQueryWindow,
        ): List<AudioContextTranscriptSegment> = emptyList()

        override fun liveTranscript(
            appUuid: Uuid,
            options: AudioContextSubscriptionOptions,
        ): Flow<AudioContextTranscriptSegment> = emptyFlow()

        override fun rawAudio(
            appUuid: Uuid,
            options: AudioContextRawAudioOptions,
        ): Flow<AudioContextRawAudioChunk> = emptyFlow()
    }

    private fun segment(text: String) = AudioContextTranscriptSegment(
        id = "seg-1",
        text = text,
        isFinal = true,
        window = AudioContextTimeWindow(1000, 2000),
        language = null,
        provider = null,
        modelUsed = null,
        source = AudioContextSourceMetadata(
            sourceType = "watch",
            sourceDeviceId = null,
            sourceAction = null,
            streamId = 1,
            segmentId = "seg-1",
            gapCount = 2,
        ),
        warnings = emptyList(),
    )
}
