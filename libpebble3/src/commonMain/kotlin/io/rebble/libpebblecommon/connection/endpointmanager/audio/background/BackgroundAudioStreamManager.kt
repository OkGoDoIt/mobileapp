package io.rebble.libpebblecommon.connection.endpointmanager.audio.background

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.BackgroundAudioStream
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.services.AudioStreamService
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class BackgroundAudioStreamManager(
    private val audioStreamService: AudioStreamService,
    private val watchScope: ConnectionCoroutineScope,
    private val handler: BackgroundAudioStreamHandler = NoOpBackgroundAudioStreamHandler,
) {
    companion object {
        private val logger = Logger.withTag("BackgroundAudio")
    }

    private var watchInfo: WatchInfo? = null
    private var activeConfig: BackgroundAudioStreamConfig? = null
    private var lastReceivedSequence: UInt? = null

    private val _state = MutableStateFlow(BackgroundAudioStreamState.Idle)
    val state = _state.asStateFlow()

    fun init(watchInfo: WatchInfo) {
        this.watchInfo = watchInfo
        if (!isSupported(watchInfo)) {
            _state.value = BackgroundAudioStreamState.UnsupportedCodec
            return
        }
        audioStreamService.inboundMessages
            .filterIsInstance<BackgroundAudioStream>()
            .onEach { handlePacket(it) }
            .launchIn(watchScope)
        _state.value = BackgroundAudioStreamState.Idle
    }

    fun isSupported(watchInfo: WatchInfo): Boolean {
        return watchInfo.capabilities.contains(ProtocolCapsFlag.SupportsBackgroundAudioStreaming)
    }

    private suspend fun handlePacket(packet: BackgroundAudioStream) {
        when (packet) {
            is BackgroundAudioStream.BackgroundStreamStart -> handleStart(packet)
            is BackgroundAudioStream.BackgroundStreamData -> handleData(packet)
            is BackgroundAudioStream.BackgroundStreamGap -> handleGap(packet)
            is BackgroundAudioStream.BackgroundStreamStop -> handleStop(packet)
            else -> Unit
        }
    }

    private suspend fun handleStart(packet: BackgroundAudioStream.BackgroundStreamStart) {
        val config = BackgroundAudioStreamConfig(
            streamId = packet.streamId.get(),
            protocolVersion = packet.protocolVersion.get(),
            codecId = packet.codecId.get(),
            channels = packet.channels.get(),
            frameSamples = packet.frameSamples.get(),
            sampleRateHz = packet.sampleRateHz.get(),
            bitRateBps = packet.bitRateBps.get(),
            frameDurationMs = packet.frameDurationMs.get(),
            startTimeMs = packet.startTimeMs.get(),
            startMonotonicMs = packet.startMonotonicMs.get(),
            flags = packet.flags.get(),
        )
        if (config.codecId != BackgroundAudioStream.CODEC_SPEEX_WIDEBAND) {
            logger.w { "Unsupported background codec ${config.codecId}" }
            _state.value = BackgroundAudioStreamState.UnsupportedCodec
            return
        }
        if (!handler.canAccept(config)) {
            _state.value = BackgroundAudioStreamState.Error
            return
        }
        activeConfig = config
        lastReceivedSequence = null
        _state.value = BackgroundAudioStreamState.Receiving
        handler.onStreamStarted(config)
    }

    private suspend fun handleData(packet: BackgroundAudioStream.BackgroundStreamData) {
        val config = activeConfig
        if (config == null || packet.streamId.get() != config.streamId) {
            logger.w { "Background data before start or for wrong stream" }
            return
        }
        val frames = BackgroundAudioStream.parseLengthPrefixedFrames(packet.encodedFramesPayload.get())
        if (frames.isEmpty()) {
            return
        }
        val firstSequence = packet.firstSequence.get()
        detectSequenceGap(config.streamId, firstSequence)
        lastReceivedSequence = firstSequence + frames.size.toUInt() - 1u
        handler.onFrameBatch(
            BackgroundAudioFrameBatch(
                streamId = config.streamId,
                firstSequence = firstSequence,
                firstSampleIndex = packet.firstSampleIndex.get(),
                frames = frames,
            ),
        )
        val checkpointSequence = (handler as? BackgroundAudioCheckpointSource)?.lastPersistedSequence
            ?: lastReceivedSequence!!
        val checkpointSample = (handler as? BackgroundAudioCheckpointSource)?.lastPersistedSampleIndex
            ?: packet.firstSampleIndex.get()
        maybeSendCheckpoint(config.streamId, checkpointSequence, checkpointSample)
    }

    private suspend fun detectSequenceGap(streamId: UInt, firstSequence: UInt) {
        val expected = lastReceivedSequence?.plus(1u)
        if (expected != null && firstSequence > expected) {
            handler.onGap(
                BackgroundAudioGap(
                    streamId = streamId,
                    firstMissingSequence = expected,
                    missingFrameCount = firstSequence - expected,
                    firstMissingSampleIndex = 0u,
                    reason = 0u,
                    watchDropCounter = 0u,
                    synthetic = true,
                ),
            )
        }
    }

    private suspend fun handleGap(packet: BackgroundAudioStream.BackgroundStreamGap) {
        val config = activeConfig ?: return
        handler.onGap(
            BackgroundAudioGap(
                streamId = packet.streamId.get(),
                firstMissingSequence = packet.firstMissingSequence.get(),
                missingFrameCount = packet.missingFrameCount.get(),
                firstMissingSampleIndex = packet.firstMissingSampleIndex.get(),
                reason = packet.reason.get(),
                watchDropCounter = packet.watchDropCounter.get(),
            ),
        )
    }

    private suspend fun handleStop(packet: BackgroundAudioStream.BackgroundStreamStop) {
        val config = activeConfig ?: return
        handler.onStreamStopped(
            BackgroundAudioStopSummary(
                streamId = packet.streamId.get(),
                reason = packet.reason.get(),
                finalSequence = packet.finalSequence.get(),
                finalSampleIndex = packet.finalSampleIndex.get(),
            ),
        )
        activeConfig = null
        lastReceivedSequence = null
        _state.value = BackgroundAudioStreamState.Idle
    }

    private fun maybeSendCheckpoint(streamId: UInt, highestSequence: UInt, sampleIndex: ULong) {
        watchScope.launch {
            val checkpoint = BackgroundAudioStream.BackgroundStreamCheckpoint(
                streamId = streamId,
                highestContiguousSequencePersisted = highestSequence,
                persistedSampleIndex = sampleIndex,
            )
            audioStreamService.sendBackground(checkpoint)
        }
    }

    fun onDisconnected() {
        if (activeConfig != null) {
            _state.value = BackgroundAudioStreamState.PausedDisconnected
        }
    }
}
