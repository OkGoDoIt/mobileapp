package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.audiocontext.AudioContextAvailability
import io.rebble.libpebblecommon.audiocontext.AudioContextPermission
import io.rebble.libpebblecommon.audiocontext.AudioContextPermissionException
import io.rebble.libpebblecommon.audiocontext.AudioContextPromptResult
import io.rebble.libpebblecommon.audiocontext.AudioContextProvider
import io.rebble.libpebblecommon.audiocontext.AudioContextQueryWindow
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioChunk
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextRawEncoding
import io.rebble.libpebblecommon.audiocontext.AudioContextStatus
import io.rebble.libpebblecommon.audiocontext.AudioContextSubscriptionOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextTriggerMetadata
import io.rebble.libpebblecommon.audiocontext.AudioContextTranscriptSegment
import io.rebble.libpebblecommon.audiocontext.AudioContextTimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AudioContextBridgeTest {
    @Test
    fun getStatusReturnsEncodedPayload() = runBlocking {
        val results = mutableListOf<String>()
        val provider = FakeProvider().apply {
            status = AudioContextStatus(
                availability = AudioContextAvailability.Available,
                backgroundAudioEnabled = true,
                streamState = "Receiving",
                transcriptionEnabled = true,
                storageState = "Accepting",
                currentLiveSubscribers = 0,
                currentRawSubscribers = 0,
            )
        }
        val bridge = bridge(
            provider = provider,
            signalResult = { _, payload -> results.add(payload) },
        )

        bridge.getStatus("cb-1")
        delay(50)

        val payload = Json.decodeFromString<StatusJson>(results.single())
        assertEquals("Available", payload.availability)
        assertEquals("Receiving", payload.streamState)
        assertTrue(payload.backgroundAudioEnabled)
    }

    @Test
    fun permissionFailureReturnsErrorPayload() = runBlocking {
        val results = mutableListOf<String>()
        val bridge = bridge(
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
            signalResult = { _, payload -> results.add(payload) },
        )

        bridge.recentTranscript("cb-2", "{}")
        delay(50)

        val payload = Json.decodeFromString<ErrorJson>(results.single())
        assertEquals("PermissionDenied", payload.availability)
        assertTrue(payload.message.contains("denied"))
    }

    @Test
    fun unsubscribeCancelsSubscriptionJob() = runBlocking {
        val bridge = bridge(provider = FakeProvider())
        val subscriptionId = bridge.subscribeTranscript("cb-3", "{}")
        delay(20)
        assertTrue(bridge.unsubscribe(subscriptionId))
    }

    @Test
    fun rawAudioSubscriptionEmitsBase64Payload() = runBlocking {
        val events = mutableListOf<String>()
        val bridge = bridge(
            provider = FakeProvider(),
            signalEvent = { _, payload -> events.add(payload) },
        )

        bridge.subscribeRawAudio("cb-4", "{}")
        delay(50)

        val payload = Json.decodeFromString<RawAudioJson>(events.single())
        assertEquals(7L, payload.streamId)
        assertEquals("AQIDBA==", payload.base64)
        assertEquals("Pcm16Le", payload.encoding)
    }

    @Test
    fun statusSubscriptionEmitsStatusEvent() = runBlocking {
        val events = mutableListOf<String>()
        val bridge = bridge(
            provider = FakeProvider(),
            signalEvent = { _, payload -> events.add(payload) },
        )

        bridge.subscribeStatus("cb-5")
        delay(50)

        val payload = Json.decodeFromString<StatusEventJson>(events.single())
        assertEquals("Available", payload.status.availability)
        assertEquals("Idle", payload.status.streamState)
    }

    @Test
    fun triggerInfoReturnsMetadata() = runBlocking {
        val results = mutableListOf<String>()
        val bridge = bridge(
            provider = FakeProvider(),
            signalResult = { _, payload -> results.add(payload) },
        )

        bridge.triggerInfo("cb-6")
        delay(50)

        val payload = Json.decodeFromString<TriggerInfoJson>(results.single())
        assertEquals("quick_launch", payload.launchReason)
        assertEquals(1234L, payload.triggerTimestampEpochMs)
        assertEquals("watch", payload.sourceType)
    }

    private fun bridge(
        provider: AudioContextProvider,
        signalResult: suspend (String, String) -> Unit = { _, _ -> },
        signalEvent: suspend (String, String) -> Unit = { _, _ -> },
    ) = AudioContextBridge(
        provider = provider,
        appUuid = Uuid.random(),
        scope = CoroutineScope(Dispatchers.Default),
        signalResult = signalResult,
        signalEvent = signalEvent,
    )

  private open class FakeProvider : AudioContextProvider {
        var status: AudioContextStatus = AudioContextStatus(
            availability = AudioContextAvailability.Available,
            backgroundAudioEnabled = true,
            streamState = "Idle",
            transcriptionEnabled = true,
            storageState = "Accepting",
            currentLiveSubscribers = 0,
            currentRawSubscribers = 0,
        )

        override suspend fun status(appUuid: Uuid): AudioContextStatus = status

        override fun statusUpdates(appUuid: Uuid): Flow<AudioContextStatus> = flowOf(status)

        override suspend fun triggerInfo(appUuid: Uuid): AudioContextTriggerMetadata =
            AudioContextTriggerMetadata(
                launchReason = "quick_launch",
                triggerTimestampEpochMs = 1234,
                sourceType = "watch",
                sourceAction = "long_press",
                button = "select",
                args = 99,
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
        ): Flow<AudioContextTranscriptSegment> = flowOf(
            AudioContextTranscriptSegment(
                id = "seg-1",
                text = "hello",
                isFinal = true,
                window = AudioContextTimeWindow(0, 1000),
                language = null,
                provider = null,
                modelUsed = null,
                source = io.rebble.libpebblecommon.audiocontext.AudioContextSourceMetadata(
                    sourceType = "watch",
                    sourceDeviceId = null,
                    sourceAction = null,
                    streamId = null,
                    segmentId = "seg-1",
                    gapCount = 0,
                ),
                warnings = emptyList(),
            ),
        )

        override fun rawAudio(
            appUuid: Uuid,
            options: AudioContextRawAudioOptions,
        ): Flow<AudioContextRawAudioChunk> = flowOf(
            AudioContextRawAudioChunk(
                streamId = 7,
                sequenceStart = 1,
                sampleIndexStart = 2,
                timestampEpochMs = 3,
                sampleRateHz = 16_000,
                channels = 1,
                encoding = AudioContextRawEncoding.Pcm16Le,
                bytes = byteArrayOf(1, 2, 3, 4),
                gapCountSinceLastChunk = 0,
            ),
        )
    }

    @kotlinx.serialization.Serializable
    private data class StatusJson(
        val availability: String,
        val backgroundAudioEnabled: Boolean,
        val streamState: String,
        val transcriptionEnabled: Boolean,
        val storageState: String,
        val currentLiveSubscribers: Int,
        val currentRawSubscribers: Int,
    )

    @kotlinx.serialization.Serializable
    private data class ErrorJson(val availability: String, val message: String)

    @kotlinx.serialization.Serializable
    private data class StatusEventJson(val status: StatusJson)

    @kotlinx.serialization.Serializable
    private data class RawAudioJson(
        val streamId: Long,
        val sequenceStart: Long,
        val sampleIndexStart: Long,
        val timestampEpochMs: Long?,
        val sampleRateHz: Int,
        val channels: Int,
        val encoding: String,
        val base64: String,
        val gapCountSinceLastChunk: Int,
    )

    @kotlinx.serialization.Serializable
    private data class TriggerInfoJson(
        val launchReason: String,
        val triggerTimestampEpochMs: Long?,
        val sourceType: String?,
        val sourceAction: String?,
        val button: String?,
        val args: Long?,
    )
}
