package io.rebble.libpebblecommon.connection.endpointmanager.audio.context

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.audiocontext.AudioContextAvailability
import io.rebble.libpebblecommon.audiocontext.AudioContextPermission
import io.rebble.libpebblecommon.audiocontext.AudioContextPermissionException
import io.rebble.libpebblecommon.audiocontext.AudioContextPromptResult
import io.rebble.libpebblecommon.audiocontext.AudioContextProvider
import io.rebble.libpebblecommon.audiocontext.AudioContextQueryWindow
import io.rebble.libpebblecommon.audiocontext.AudioContextSubscriptionOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextTranscriptSegment
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.AppAudioContext
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Handles watch-app audio context requests on private endpoint 12000.
 */
class AppAudioContextManager(
    private val protocolHandler: PebbleProtocolHandler,
    private val provider: AudioContextProvider,
    private val watchScope: ConnectionCoroutineScope,
    private val runningAppUuid: () -> Uuid? = { null },
) {
    private val logger = Logger.withTag("AppAudioContext")
    private val json = Json { ignoreUnknownKeys = true }
    private val subscriptionJobs = mutableMapOf<UShort, Job>()

    fun init() {
        watchScope.launch {
            protocolHandler.inboundMessages
                .filterIsInstance<AppAudioContext>()
                .collect { packet -> handlePacket(packet) }
        }
    }

    fun onDisconnected() {
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
    }

    private suspend fun handlePacket(packet: AppAudioContext) {
        when (packet) {
            is AppAudioContext.StatusRequest -> handleStatus(packet)
            is AppAudioContext.EnablePromptRequest -> handleEnablePrompt(packet)
            is AppAudioContext.PermissionRequest -> handlePermissionPrompt(packet)
            is AppAudioContext.TranscriptRequest -> handleTranscript(packet)
            is AppAudioContext.SubscribeRequest -> handleSubscribe(packet)
            is AppAudioContext.CancelRequest -> handleCancel(packet)
            else -> Unit
        }
    }

    private suspend fun handleStatus(packet: AppAudioContext.StatusRequest) {
        val appUuid = resolveAppUuid(packet.appUuid.get())
        val requestId = packet.requestId.get()
        runCatching { provider.status(appUuid) }
            .onSuccess { status ->
                send(
                    AppAudioContext.StatusResponse(
                        requestId = requestId,
                        appUuid = appUuid,
                        availabilityCode = availabilityCode(status.availability),
                        backgroundAudioEnabledFlag = if (status.backgroundAudioEnabled) 1u else 0u,
                        streamStateCode = streamStateCode(status.streamState),
                        transcriptionEnabledFlag = if (status.transcriptionEnabled) 1u else 0u,
                        storageStateCode = storageStateCode(status.storageState),
                        liveSubscribers = status.currentLiveSubscribers.toUShort(),
                        rawSubscribers = status.currentRawSubscribers.toUShort(),
                    ),
                )
            }
            .onFailure { sendError(requestId, appUuid, it) }
    }

    private suspend fun handleEnablePrompt(packet: AppAudioContext.EnablePromptRequest) {
        val appUuid = resolveAppUuid(packet.appUuid.get())
        val requestId = packet.requestId.get()
        runCatching { provider.requestEnablePrompt(appUuid) }
            .onSuccess { result ->
                send(
                    AppAudioContext.PromptResponse(
                        requestId = requestId,
                        appUuid = appUuid,
                        promptResult = promptResultCode(result),
                    ),
                )
            }
            .onFailure { sendError(requestId, appUuid, it) }
    }

    private suspend fun handlePermissionPrompt(packet: AppAudioContext.PermissionRequest) {
        val appUuid = resolveAppUuid(packet.appUuid.get())
        val requestId = packet.requestId.get()
        val permissions = packet.permissionBytes.get().mapNotNull { wirePermission(it) }.toSet()
        runCatching { provider.requestPermissionPrompt(appUuid, permissions) }
            .onSuccess { result ->
                send(
                    AppAudioContext.PromptResponse(
                        requestId = requestId,
                        appUuid = appUuid,
                        promptResult = promptResultCode(result),
                    ),
                )
            }
            .onFailure { sendError(requestId, appUuid, it) }
    }

    private suspend fun handleTranscript(packet: AppAudioContext.TranscriptRequest) {
        val appUuid = resolveAppUuid(packet.appUuid.get())
        val requestId = packet.requestId.get()
        val window = AudioContextQueryWindow(
            beforeSeconds = packet.beforeSeconds.get().toInt(),
            afterSeconds = packet.afterSeconds.get().toInt(),
            anchorEpochMs = packet.anchorEpochMs.get().takeIf { it > 0uL }?.toLong(),
            startedAtEpochMs = packet.startedAtEpochMs.get().takeIf { it > 0uL }?.toLong(),
            endedAtEpochMs = packet.endedAtEpochMs.get().takeIf { it > 0uL }?.toLong(),
        )
        val history = packet.history.get() > 0.toUByte()
        runCatching {
            if (history) {
                provider.transcriptHistory(appUuid, window)
            } else {
                provider.recentTranscript(appUuid, window)
            }
        }.onSuccess { segments ->
            sendTranscriptChunks(requestId, appUuid, segments)
        }.onFailure { sendError(requestId, appUuid, it) }
    }

    private fun handleSubscribe(packet: AppAudioContext.SubscribeRequest) {
        val appUuid = resolveAppUuid(packet.appUuid.get())
        val requestId = packet.requestId.get()
        subscriptionJobs.remove(requestId)?.cancel()
        val options = AudioContextSubscriptionOptions(
            includePartial = packet.includePartial.get() > 0.toUByte(),
            startedAtEpochMs = packet.startedAtEpochMs.get().takeIf { it > 0uL }?.toLong(),
        )
        val job = watchScope.launch {
            runCatching {
                provider.liveTranscript(appUuid, options)
                    .catch { error ->
                        sendError(requestId, appUuid, error)
                    }
                    .collect { segment ->
                        sendEvent(requestId, appUuid, segment)
                    }
            }.onFailure { sendError(requestId, appUuid, it) }
        }
        subscriptionJobs[requestId] = job
    }

    private fun handleCancel(packet: AppAudioContext.CancelRequest) {
        subscriptionJobs.remove(packet.requestId.get())?.cancel()
    }

    private suspend fun sendTranscriptChunks(
        requestId: UShort,
        appUuid: Uuid,
        segments: List<AudioContextTranscriptSegment>,
    ) {
        val payloadJson = json.encodeToString(TranscriptPayload(segments))
        val payloadBytes = payloadJson.encodeToByteArray().toUByteArray()
        val chunks = payloadBytes.asIterable()
            .chunked(AppAudioContext.MAX_TRANSCRIPT_CHUNK_BYTES)
            .map { it.toUByteArray() }
            .ifEmpty { listOf(UByteArray(0)) }
        chunks.forEachIndexed { index, chunk ->
            send(
                AppAudioContext.TranscriptResponse(
                    requestId = requestId,
                    appUuid = appUuid,
                    responseStatus = 0u,
                    partIndexValue = index.toUShort(),
                    partCountValue = chunks.size.toUShort(),
                    payload = chunk,
                ),
            )
        }
    }

    private suspend fun sendEvent(
        requestId: UShort,
        appUuid: Uuid,
        segment: AudioContextTranscriptSegment,
    ) {
        val payloadBytes = json.encodeToString(TranscriptPayload(listOf(segment)))
            .encodeToByteArray()
            .toUByteArray()
        if (payloadBytes.size > AppAudioContext.MAX_TRANSCRIPT_CHUNK_BYTES) {
            logger.w { "Skipping oversized live transcript event for $appUuid" }
            return
        }
        send(
            AppAudioContext.Event(
                requestId = requestId,
                appUuid = appUuid,
                payload = payloadBytes,
            ),
        )
    }

    private suspend fun sendError(requestId: UShort, appUuid: Uuid, error: Throwable) {
        val availability = (error as? AudioContextPermissionException)?.availability
        val code = when (availability) {
            AudioContextAvailability.PermissionDenied ->
                AppAudioContext.ErrorCode.PermissionDenied
            AudioContextAvailability.CapabilityNotDeclared ->
                AppAudioContext.ErrorCode.CapabilityNotDeclared
            AudioContextAvailability.DisabledByUser ->
                AppAudioContext.ErrorCode.BackgroundAudioDisabled
            AudioContextAvailability.TranscriptionUnavailable ->
                AppAudioContext.ErrorCode.TranscriptionUnavailable
            AudioContextAvailability.NoData -> AppAudioContext.ErrorCode.NoData
            AudioContextAvailability.UnsupportedWatch,
            AudioContextAvailability.UnsupportedPhone,
            -> AppAudioContext.ErrorCode.Unavailable
            else -> AppAudioContext.ErrorCode.InternalError
        }
        send(
            AppAudioContext.ErrorResponse(
                requestId = requestId,
                appUuid = appUuid,
                errorCodeValue = code.value,
                message = error.message ?: "Audio context request failed",
            ),
        )
    }

    private suspend fun send(packet: PebblePacket) {
        protocolHandler.send(packet)
    }

    private fun resolveAppUuid(packetAppUuid: Uuid): Uuid {
        val running = runningAppUuid()
        if (running != null && running != packetAppUuid) {
            logger.w { "Ignoring AppAudioContext packet UUID $packetAppUuid; binding request to running app $running" }
        }
        return running ?: packetAppUuid
    }

    private fun wirePermission(value: UByte): AudioContextPermission? = when (value) {
        AppAudioContext.WirePermission.Status.value -> AudioContextPermission.Status
        AppAudioContext.WirePermission.RecentTranscript.value -> AudioContextPermission.RecentTranscript
        AppAudioContext.WirePermission.TranscriptHistory.value -> AudioContextPermission.TranscriptHistory
        AppAudioContext.WirePermission.LiveTranscript.value -> AudioContextPermission.LiveTranscript
        AppAudioContext.WirePermission.RawAudio.value -> AudioContextPermission.RawAudio
        else -> null
    }

    private fun availabilityCode(availability: AudioContextAvailability): UByte =
        availability.ordinal.toUByte()

    private fun streamStateCode(streamState: String): UByte = when (streamState.lowercase()) {
        "idle" -> AppAudioContext.StreamStateCode.Idle.value
        "receiving" -> AppAudioContext.StreamStateCode.Receiving.value
        else -> AppAudioContext.StreamStateCode.Unknown.value
    }

    private fun storageStateCode(storageState: String): UByte = when (storageState.lowercase()) {
        "accepting" -> AppAudioContext.StorageStateCode.Accepting.value
        "paused" -> AppAudioContext.StorageStateCode.Paused.value
        "full" -> AppAudioContext.StorageStateCode.Full.value
        else -> AppAudioContext.StorageStateCode.Unknown.value
    }

    private fun promptResultCode(result: AudioContextPromptResult): UByte = when (result) {
        AudioContextPromptResult.Granted -> 0u
        AudioContextPromptResult.Denied -> 1u
        AudioContextPromptResult.Dismissed -> 2u
        AudioContextPromptResult.Unavailable -> 3u
    }

    @Serializable
    private data class TranscriptPayload(val segments: List<AudioContextTranscriptSegment>)
}
