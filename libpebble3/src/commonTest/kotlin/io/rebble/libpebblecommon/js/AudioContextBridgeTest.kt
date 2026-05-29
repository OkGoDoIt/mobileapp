package io.rebble.libpebblecommon.js

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
import io.rebble.libpebblecommon.audiocontext.AudioContextTranscriptSegment
import io.rebble.libpebblecommon.audiocontext.AudioContextTimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    private fun bridge(
        provider: AudioContextProvider,
        signalResult: suspend (String, String) -> Unit = { _, _ -> },
    ) = AudioContextBridge(
        provider = provider,
        appUuid = Uuid.random(),
        scope = CoroutineScope(Dispatchers.Default),
        signalResult = signalResult,
        signalEvent = { _, _ -> },
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
        ): Flow<AudioContextRawAudioChunk> = emptyFlow()
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
}
