package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.audiocontext.AudioContextPermission
import io.rebble.libpebblecommon.audiocontext.AudioContextProvider
import io.rebble.libpebblecommon.audiocontext.AudioContextQueryWindow
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioChunk
import io.rebble.libpebblecommon.audiocontext.AudioContextRawAudioOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextStatus
import io.rebble.libpebblecommon.audiocontext.AudioContextSubscriptionOptions
import io.rebble.libpebblecommon.audiocontext.AudioContextTranscriptSegment
import io.rebble.libpebblecommon.audiocontext.AudioContextPermissionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/**
 * Phone-side PKJS bridge over [AudioContextProvider].
 */
class AudioContextBridge(
    private val provider: AudioContextProvider,
    private val appUuid: Uuid,
    private val scope: CoroutineScope,
    private val signalResult: suspend (callbackId: String, payloadJson: String) -> Unit,
    private val signalEvent: suspend (subscriptionId: String, payloadJson: String) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val subscriptionJobs = mutableMapOf<String, Job>()
    private var nextSubscriptionId = 1

    fun close() {
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
    }

    fun getStatus(callbackId: String) {
        scope.launch {
            runCatching { provider.status(appUuid) }
                .onSuccess { signalResult(callbackId, json.encodeToString(StatusPayload.from(it))) }
                .onFailure { signalResult(callbackId, errorPayload(it)) }
        }
    }

    fun requestEnable(callbackId: String) {
        scope.launch {
            runCatching { provider.requestEnablePrompt(appUuid) }
                .onSuccess { signalResult(callbackId, json.encodeToString(PromptPayload(it.name))) }
                .onFailure { signalResult(callbackId, errorPayload(it)) }
        }
    }

    fun requestPermission(callbackId: String, permissionsJson: String) {
        scope.launch {
            val permissions = parsePermissions(permissionsJson)
            runCatching { provider.requestPermissionPrompt(appUuid, permissions) }
                .onSuccess { signalResult(callbackId, json.encodeToString(PromptPayload(it.name))) }
                .onFailure { signalResult(callbackId, errorPayload(it)) }
        }
    }

    fun recentTranscript(callbackId: String, optionsJson: String) {
        scope.launch {
            val window = parseWindow(optionsJson)
            runCatching { provider.recentTranscript(appUuid, window) }
                .onSuccess { signalResult(callbackId, json.encodeToString(TranscriptPayload.from(it))) }
                .onFailure { signalResult(callbackId, errorPayload(it)) }
        }
    }

    fun transcriptHistory(callbackId: String, optionsJson: String) {
        scope.launch {
            val window = parseWindow(optionsJson)
            runCatching { provider.transcriptHistory(appUuid, window) }
                .onSuccess { signalResult(callbackId, json.encodeToString(TranscriptPayload.from(it))) }
                .onFailure { signalResult(callbackId, errorPayload(it)) }
        }
    }

    fun subscribeTranscript(callbackId: String, optionsJson: String): String {
        val subscriptionId = nextSubscriptionId().toString()
        val options = parseSubscriptionOptions(optionsJson)
        val job = scope.launch {
            runCatching {
                provider.liveTranscript(appUuid, options).collect { segment ->
                    signalEvent(subscriptionId, json.encodeToString(EventPayload.from(segment)))
                }
            }.onFailure {
                signalResult(callbackId, errorPayload(it))
            }
        }
        subscriptionJobs[subscriptionId] = job
        scope.launch {
            signalResult(callbackId, json.encodeToString(SubscriptionPayload(subscriptionId)))
        }
        return subscriptionId
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun subscribeRawAudio(callbackId: String, optionsJson: String): String {
        val subscriptionId = nextSubscriptionId().toString()
        val options = parseRawOptions(optionsJson)
        val job = scope.launch {
            runCatching {
                provider.rawAudio(appUuid, options).collect { chunk ->
                    signalEvent(subscriptionId, json.encodeToString(RawAudioPayload.from(chunk)))
                }
            }.onFailure {
                signalResult(callbackId, errorPayload(it))
            }
        }
        subscriptionJobs[subscriptionId] = job
        scope.launch {
            signalResult(callbackId, json.encodeToString(SubscriptionPayload(subscriptionId)))
        }
        return subscriptionId
    }

    fun unsubscribe(subscriptionId: String): Boolean {
        return subscriptionJobs.remove(subscriptionId)?.let {
            it.cancel()
            true
        } ?: false
    }

    private fun nextSubscriptionId(): Int = synchronized(subscriptionJobs) {
        nextSubscriptionId++
    }

    private fun parsePermissions(permissionsJson: String): Set<AudioContextPermission> {
        if (permissionsJson.isBlank()) {
            return emptySet()
        }
        val names = json.decodeFromString<List<String>>(permissionsJson)
        return names.mapNotNull { name -> permissionFromCapability(name) }.toSet()
    }

    private fun permissionFromCapability(name: String): AudioContextPermission? = when (name) {
        AudioContextPermission.Status.capability -> AudioContextPermission.Status
        AudioContextPermission.RecentTranscript.capability -> AudioContextPermission.RecentTranscript
        AudioContextPermission.TranscriptHistory.capability -> AudioContextPermission.TranscriptHistory
        AudioContextPermission.LiveTranscript.capability -> AudioContextPermission.RecentTranscript
        AudioContextPermission.RawAudio.capability -> AudioContextPermission.RawAudio
        else -> null
    }

    private fun parseWindow(optionsJson: String): AudioContextQueryWindow {
        if (optionsJson.isBlank()) {
            return AudioContextQueryWindow()
        }
        return json.decodeFromString(optionsJson)
    }

    private fun parseSubscriptionOptions(optionsJson: String): AudioContextSubscriptionOptions {
        if (optionsJson.isBlank()) {
            return AudioContextSubscriptionOptions()
        }
        return json.decodeFromString(optionsJson)
    }

    private fun parseRawOptions(optionsJson: String): AudioContextRawAudioOptions {
        if (optionsJson.isBlank()) {
            return AudioContextRawAudioOptions()
        }
        return json.decodeFromString(optionsJson)
    }

    private fun errorPayload(error: Throwable): String {
        val availability = (error as? AudioContextPermissionException)?.availability?.name ?: "Error"
        return json.encodeToString(
            ErrorPayload(
                availability = availability,
                message = error.message ?: "Audio context request failed",
            ),
        )
    }

    @Serializable
    private data class StatusPayload(
        val availability: String,
        val backgroundAudioEnabled: Boolean,
        val streamState: String,
        val transcriptionEnabled: Boolean,
        val storageState: String,
        val currentLiveSubscribers: Int,
        val currentRawSubscribers: Int,
    ) {
        companion object {
            fun from(status: AudioContextStatus) = StatusPayload(
                availability = status.availability.name,
                backgroundAudioEnabled = status.backgroundAudioEnabled,
                streamState = status.streamState,
                transcriptionEnabled = status.transcriptionEnabled,
                storageState = status.storageState,
                currentLiveSubscribers = status.currentLiveSubscribers,
                currentRawSubscribers = status.currentRawSubscribers,
            )
        }
    }

    @Serializable
    private data class PromptPayload(val result: String)

    @Serializable
    private data class SubscriptionPayload(val subscriptionId: String)

    @Serializable
    private data class ErrorPayload(val availability: String, val message: String)

    @Serializable
    private data class TranscriptPayload(val segments: List<SegmentPayload>) {
        companion object {
            fun from(segments: List<AudioContextTranscriptSegment>) = TranscriptPayload(
                segments.map { SegmentPayload.from(it) },
            )
        }
    }

    @Serializable
    private data class SegmentPayload(
        val id: String,
        val text: String,
        val isFinal: Boolean,
        val startedAtEpochMs: Long,
        val endedAtEpochMs: Long,
        val language: String?,
        val provider: String?,
        val modelUsed: String?,
        val warnings: List<String>,
    ) {
        companion object {
            fun from(segment: AudioContextTranscriptSegment) = SegmentPayload(
                id = segment.id,
                text = segment.text,
                isFinal = segment.isFinal,
                startedAtEpochMs = segment.window.startedAtEpochMs,
                endedAtEpochMs = segment.window.endedAtEpochMs,
                language = segment.language,
                provider = segment.provider,
                modelUsed = segment.modelUsed,
                warnings = segment.warnings,
            )
        }
    }

    @Serializable
    private data class EventPayload(val segment: SegmentPayload) {
        companion object {
            fun from(segment: AudioContextTranscriptSegment) =
                EventPayload(SegmentPayload.from(segment))
        }
    }

    @Serializable
    private data class RawAudioPayload(
        val streamId: Long,
        val sequenceStart: Long,
        val sampleIndexStart: Long,
        val timestampEpochMs: Long?,
        val sampleRateHz: Int,
        val channels: Int,
        val encoding: String,
        val base64: String,
        val gapCountSinceLastChunk: Int,
    ) {
        companion object {
            @OptIn(ExperimentalEncodingApi::class)
            fun from(chunk: AudioContextRawAudioChunk) = RawAudioPayload(
                streamId = chunk.streamId,
                sequenceStart = chunk.sequenceStart,
                sampleIndexStart = chunk.sampleIndexStart,
                timestampEpochMs = chunk.timestampEpochMs,
                sampleRateHz = chunk.sampleRateHz,
                channels = chunk.channels,
                encoding = chunk.encoding.name,
                base64 = Base64.encode(chunk.bytes),
                gapCountSinceLastChunk = chunk.gapCountSinceLastChunk,
            )
        }
    }
}
