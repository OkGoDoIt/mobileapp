package io.rebble.libpebblecommon.audiocontext

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface AudioContextProvider {
    suspend fun status(appUuid: Uuid): AudioContextStatus

    fun statusUpdates(appUuid: Uuid): Flow<AudioContextStatus>

    suspend fun triggerInfo(appUuid: Uuid): AudioContextTriggerMetadata

    suspend fun requestEnablePrompt(appUuid: Uuid): AudioContextPromptResult

    suspend fun requestPermissionPrompt(
        appUuid: Uuid,
        permissions: Set<AudioContextPermission>,
    ): AudioContextPromptResult

    suspend fun recentTranscript(
        appUuid: Uuid,
        window: AudioContextQueryWindow = AudioContextQueryWindow(),
    ): List<AudioContextTranscriptSegment>

    suspend fun transcriptHistory(
        appUuid: Uuid,
        window: AudioContextQueryWindow,
    ): List<AudioContextTranscriptSegment>

    fun liveTranscript(
        appUuid: Uuid,
        options: AudioContextSubscriptionOptions = AudioContextSubscriptionOptions(),
    ): Flow<AudioContextTranscriptSegment>

    fun rawAudio(
        appUuid: Uuid,
        options: AudioContextRawAudioOptions = AudioContextRawAudioOptions(),
    ): Flow<AudioContextRawAudioChunk>
}
