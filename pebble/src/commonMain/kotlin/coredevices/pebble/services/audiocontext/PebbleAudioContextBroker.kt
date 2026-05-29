package coredevices.pebble.services.audiocontext

import coredevices.pebble.services.backgroundaudio.BackgroundAudioRepository
import coredevices.pebble.services.backgroundaudio.ContinuousTranscriptionCoordinator
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
import kotlinx.coroutines.delay
import io.rebble.libpebblecommon.database.entity.AppDataAccessType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class PebbleAudioContextBroker(
    private val permissionChecker: AudioContextPermissionChecker,
    private val accessAuditor: AudioContextAccessAuditor,
    private val transcriptQuery: AudioContextTranscriptQuery,
    private val rawAudioFanout: AudioContextRawAudioFanout,
    private val promptController: AudioContextPromptController,
    private val backgroundAudioRepository: BackgroundAudioRepository,
    private val transcriptionCoordinator: ContinuousTranscriptionCoordinator,
    private val phoneSupported: Boolean,
) : AudioContextProvider {
    private var liveSubscribers = 0

    override suspend fun status(appUuid: Uuid): AudioContextStatus {
        val availability = runCatching {
            permissionChecker.require(appUuid, AudioContextPermission.Status)
            if (phoneSupported) AudioContextAvailability.Available else AudioContextAvailability.UnsupportedPhone
        }.getOrElse { error ->
            (error as? AudioContextPermissionException)?.availability ?: AudioContextAvailability.Error
        }
        val snapshot = backgroundAudioRepository.snapshot()
        val hasOpenStream = snapshot.segments.any { it.endedAtEpochMs == null }
        return AudioContextStatus(
            availability = availability,
            backgroundAudioEnabled = hasOpenStream || snapshot.segments.isNotEmpty(),
            streamState = when {
                hasOpenStream -> "receiving"
                snapshot.segments.isNotEmpty() -> "idle"
                else -> "not_receiving"
            },
            transcriptionEnabled = true,
            storageState = snapshot.storageState.name,
            currentLiveSubscribers = liveSubscribers,
            currentRawSubscribers = rawAudioFanout.subscriberCount,
        )
    }

    override fun statusUpdates(appUuid: Uuid): Flow<AudioContextStatus> = flow {
        var previous: AudioContextStatus? = null
        while (true) {
            val next = status(appUuid)
            if (next != previous) {
                emit(next)
                previous = next
            }
            delay(1.seconds)
        }
    }

    override suspend fun triggerInfo(appUuid: Uuid): AudioContextTriggerMetadata {
        permissionChecker.require(appUuid, AudioContextPermission.Status)
        val latestSegment = backgroundAudioRepository.snapshot().segments.maxByOrNull { it.startedAtEpochMs }
        return AudioContextTriggerMetadata(
            launchReason = "unknown",
            triggerTimestampEpochMs = latestSegment?.startedAtEpochMs,
            sourceType = latestSegment?.sourceType ?: "watch",
            sourceAction = latestSegment?.closedReason?.name,
            button = null,
            args = null,
        )
    }

    override suspend fun requestEnablePrompt(appUuid: Uuid): AudioContextPromptResult {
        return withTimeoutOrNull(30.seconds) {
            promptController.request(appUuid, AudioContextPromptKind.EnableBackgroundAudio).await()
        } ?: AudioContextPromptResult.Unavailable
    }

    override suspend fun requestPermissionPrompt(
        appUuid: Uuid,
        permissions: Set<AudioContextPermission>,
    ): AudioContextPromptResult {
        val undeclared = permissions.firstOrNull { !permissionChecker.hasDeclaredCapability(appUuid, it) }
        if (undeclared != null) {
            return AudioContextPromptResult.Denied
        }
        return withTimeoutOrNull(30.seconds) {
            promptController.request(
                appUuid = appUuid,
                kind = AudioContextPromptKind.GrantPermission,
                permissions = permissions,
            ).await()
        } ?: AudioContextPromptResult.Unavailable
    }

    override suspend fun recentTranscript(
        appUuid: Uuid,
        window: AudioContextQueryWindow,
    ): List<AudioContextTranscriptSegment> {
        permissionChecker.require(appUuid, AudioContextPermission.RecentTranscript)
        val results = transcriptQuery.recent(window)
        accessAuditor.recordQuery(
            appUuid = appUuid,
            dataType = AppDataAccessType.AudioTranscript,
            sourceSummary = "recent",
            transcriptSegmentCount = results.size,
        )
        return results
    }

    override suspend fun transcriptHistory(
        appUuid: Uuid,
        window: AudioContextQueryWindow,
    ): List<AudioContextTranscriptSegment> {
        permissionChecker.require(appUuid, AudioContextPermission.TranscriptHistory)
        val results = transcriptQuery.history(window)
        accessAuditor.recordQuery(
            appUuid = appUuid,
            dataType = AppDataAccessType.AudioHistory,
            sourceSummary = "history",
            transcriptSegmentCount = results.size,
        )
        return results
    }

    override fun liveTranscript(
        appUuid: Uuid,
        options: AudioContextSubscriptionOptions,
    ): Flow<AudioContextTranscriptSegment> = flow {
        permissionChecker.require(appUuid, AudioContextPermission.LiveTranscript)
        liveSubscribers++
        val auditId = accessAuditor.startSubscription(
            appUuid = appUuid,
            dataType = AppDataAccessType.AudioTranscript,
            sourceSummary = "live transcript",
        )
        try {
            transcriptionCoordinator.transcripts
                .mapNotNull { transcript ->
                    val metadata = backgroundAudioRepository.snapshot()
                        .segments
                        .firstOrNull { it.segmentId == transcript.segmentId }
                    with(transcriptQuery) { transcript.toAudioContext(metadata) }
                }
                .collect { emit(it) }
        } finally {
            liveSubscribers--
            accessAuditor.finishSubscription(auditId)
        }
    }

    override fun rawAudio(
        appUuid: Uuid,
        options: AudioContextRawAudioOptions,
    ): Flow<AudioContextRawAudioChunk> = flow {
        permissionChecker.require(appUuid, AudioContextPermission.RawAudio)
        val auditId = accessAuditor.startSubscription(
            appUuid = appUuid,
            dataType = AppDataAccessType.AudioRaw,
            sourceSummary = "live raw audio",
        )
        try {
            rawAudioFanout.subscribe(appUuid, options).collect { emit(it) }
        } finally {
            accessAuditor.finishSubscription(auditId)
        }
    }
}
