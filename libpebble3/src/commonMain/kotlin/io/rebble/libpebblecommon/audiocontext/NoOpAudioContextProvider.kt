package io.rebble.libpebblecommon.audiocontext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

class NoOpAudioContextProvider(
    private val availability: AudioContextAvailability = AudioContextAvailability.UnsupportedPhone,
) : AudioContextProvider {
    override suspend fun status(appUuid: Uuid): AudioContextStatus = AudioContextStatus(
        availability = availability,
        backgroundAudioEnabled = false,
        streamState = "unsupported",
        transcriptionEnabled = false,
        storageState = "unavailable",
        currentLiveSubscribers = 0,
        currentRawSubscribers = 0,
    )

    override fun statusUpdates(appUuid: Uuid): Flow<AudioContextStatus> = flowOf(statusForNoOp())

    override suspend fun triggerInfo(appUuid: Uuid): AudioContextTriggerMetadata = AudioContextTriggerMetadata(
        launchReason = "unknown",
        triggerTimestampEpochMs = null,
        sourceType = null,
        sourceAction = null,
        button = null,
        args = null,
    )

    override suspend fun requestEnablePrompt(appUuid: Uuid): AudioContextPromptResult =
        AudioContextPromptResult.Unavailable

    override suspend fun requestPermissionPrompt(
        appUuid: Uuid,
        permissions: Set<AudioContextPermission>,
    ): AudioContextPromptResult = AudioContextPromptResult.Unavailable

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

    private fun statusForNoOp() = AudioContextStatus(
        availability = availability,
        backgroundAudioEnabled = false,
        streamState = "unsupported",
        transcriptionEnabled = false,
        storageState = "unavailable",
        currentLiveSubscribers = 0,
        currentRawSubscribers = 0,
    )
}
