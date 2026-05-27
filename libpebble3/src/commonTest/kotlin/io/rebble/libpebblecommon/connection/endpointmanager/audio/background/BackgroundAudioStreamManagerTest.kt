package io.rebble.libpebblecommon.connection.endpointmanager.audio.background

import io.rebble.libpebblecommon.PacketPriority
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.packets.BackgroundAudioStream
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.services.AudioStreamService
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class BackgroundAudioStreamManagerTest {
    @Test
    fun forwardsStartDataGapStopAndSendsCheckpoint() = runBlocking {
        val protocolHandler = FakeProtocolHandler()
        val handler = CapturingHandler()
        val scope = ConnectionCoroutineScope(SupervisorJob())
        val manager = BackgroundAudioStreamManager(
            audioStreamService = AudioStreamService(protocolHandler),
            watchScope = scope,
            handler = handler,
        )

        manager.init(watchInfo())
        delay(10)
        protocolHandler.receive(startPacket(streamId = 7u))
        protocolHandler.receive(dataPacket(streamId = 7u, firstSequence = 0u, frames = listOf(ubyteArrayOf(0xAAu))))
        protocolHandler.receive(dataPacket(streamId = 7u, firstSequence = 2u, frames = listOf(ubyteArrayOf(0xBBu))))
        protocolHandler.receive(gapPacket(streamId = 7u))
        protocolHandler.receive(stopPacket(streamId = 7u))
        delay(50)

        assertEquals(1, handler.starts.size)
        assertEquals(2, handler.batches.size)
        assertTrue(handler.gaps.any { it.synthetic })
        assertTrue(handler.gaps.any { !it.synthetic })
        assertEquals(1, handler.stops.size)
        val checkpoint = protocolHandler.sent.filterIsInstance<BackgroundAudioStream.BackgroundStreamCheckpoint>().first()
        assertEquals(0x04u, checkpoint.receiverFlags.get())
        assertEquals(1234u, checkpoint.freeStorageHintKb.get())
        scope.cancel()
    }

    @Test
    fun unsupportedWatchCapabilityDisablesManager() = runBlocking {
        val scope = ConnectionCoroutineScope(SupervisorJob())
        val manager = BackgroundAudioStreamManager(
            audioStreamService = AudioStreamService(FakeProtocolHandler()),
            watchScope = scope,
        )

        manager.init(watchInfo(capabilities = emptySet()))

        assertEquals(BackgroundAudioStreamState.UnsupportedWatch, manager.state.value)
        scope.cancel()
    }

    @Test
    fun activeDisconnectInterruptsStreamWithoutStopCallback() = runBlocking {
        val protocolHandler = FakeProtocolHandler()
        val handler = CapturingHandler()
        val scope = ConnectionCoroutineScope(SupervisorJob())
        val manager = BackgroundAudioStreamManager(
            audioStreamService = AudioStreamService(protocolHandler),
            watchScope = scope,
            handler = handler,
        )

        manager.init(watchInfo())
        delay(10)
        protocolHandler.receive(startPacket(streamId = 11u))
        protocolHandler.receive(dataPacket(streamId = 11u, firstSequence = 3u, frames = listOf(ubyteArrayOf(0xCCu))))
        delay(10)

        manager.onDisconnected("test")
        delay(50)

        assertEquals(BackgroundAudioStreamState.PausedDisconnected, manager.state.value)
        assertEquals(1, handler.interruptions.size)
        assertEquals(11u, handler.interruptions.single().streamId)
        assertEquals(3u, handler.interruptions.single().lastReceivedSequence)
        assertEquals(960u, handler.interruptions.single().lastReceivedSampleIndex)
        assertEquals(emptyList(), handler.stops)
        scope.cancel()
    }

    private class CapturingHandler : BackgroundAudioStreamHandler, BackgroundAudioReceiverHealthSource {
        val starts = mutableListOf<BackgroundAudioStreamConfig>()
        val batches = mutableListOf<BackgroundAudioFrameBatch>()
        val gaps = mutableListOf<BackgroundAudioGap>()
        val stops = mutableListOf<BackgroundAudioStopSummary>()
        val interruptions = mutableListOf<BackgroundAudioInterruption>()

        override val receiverFlags: UInt = 0x04u
        override val freeStorageHintKb: UInt = 1234u

        override suspend fun onStreamStarted(config: BackgroundAudioStreamConfig) {
            starts.add(config)
        }

        override suspend fun onFrameBatch(batch: BackgroundAudioFrameBatch) {
            batches.add(batch)
        }

        override suspend fun onGap(gap: BackgroundAudioGap) {
            gaps.add(gap)
        }

        override suspend fun onStreamStopped(summary: BackgroundAudioStopSummary) {
            stops.add(summary)
        }

        override suspend fun onStreamInterrupted(interruption: BackgroundAudioInterruption) {
            interruptions.add(interruption)
        }
    }

    private class FakeProtocolHandler : PebbleProtocolHandler {
        private val inbound = MutableSharedFlow<PebblePacket>()
        val sent = mutableListOf<PebblePacket>()

        override val inboundMessages: SharedFlow<PebblePacket> = inbound
        override val rawInboundMessages: Flow<ByteArray> = emptyFlow()

        suspend fun receive(packet: PebblePacket) {
            inbound.emit(packet)
        }

        override suspend fun send(message: PebblePacket, priority: PacketPriority) {
            sent.add(message)
        }

        override suspend fun send(message: ByteArray, priority: PacketPriority) {}
    }

    private fun watchInfo(
        capabilities: Set<ProtocolCapsFlag> = setOf(ProtocolCapsFlag.SupportsBackgroundAudioStreaming),
    ) = WatchInfo(
        runningFwVersion = FirmwareVersion(
            stringVersion = "v1.0.0",
            timestamp = Instant.fromEpochMilliseconds(0),
            major = 1,
            minor = 0,
            patch = 0,
            suffix = null,
            gitHash = "",
            isRecovery = false,
            isDualSlot = false,
            isSlot0 = false,
        ),
        recoveryFwVersion = null,
        platform = WatchHardwarePlatform.CORE_GETAFIX_DVT,
        bootloaderTimestamp = Instant.fromEpochMilliseconds(0),
        board = "qemu_gabbro",
        serial = "test",
        btAddress = "00:00:00:00:00:00",
        resourceCrc = 0,
        resourceTimestamp = Instant.fromEpochMilliseconds(0),
        language = "en_US",
        languageVersion = 0,
        capabilities = capabilities,
        isUnfaithful = false,
        healthInsightsVersion = null,
        javascriptVersion = null,
        color = WatchColor.TimeBlack,
    )

    private fun startPacket(streamId: UInt) = BackgroundAudioStream.BackgroundStreamStart().apply {
        protocolVersion.set(1u)
        this.streamId.set(streamId)
        codecId.set(BackgroundAudioStream.CODEC_SPEEX_WIDEBAND)
        channels.set(1u)
        frameSamples.set(320u)
        sampleRateHz.set(16000u)
        bitRateBps.set(9800u)
        frameDurationMs.set(20u)
    }

    private fun dataPacket(
        streamId: UInt,
        firstSequence: UInt,
        frames: List<UByteArray>,
    ) = BackgroundAudioStream.BackgroundStreamData().apply {
        this.streamId.set(streamId)
        this.firstSequence.set(firstSequence)
        firstSampleIndex.set(firstSequence.toULong() * 320u)
        frameCount.set(frames.size.toUByte())
        encodedFramesPayload.set(frames.toLengthPrefixedPayload())
    }

    private fun gapPacket(streamId: UInt) = BackgroundAudioStream.BackgroundStreamGap().apply {
        this.streamId.set(streamId)
        firstMissingSequence.set(10u)
        missingFrameCount.set(2u)
        firstMissingSampleIndex.set(3200u)
        reason.set(1u)
        watchDropCounter.set(2u)
    }

    private fun stopPacket(streamId: UInt) = BackgroundAudioStream.BackgroundStreamStop().apply {
        this.streamId.set(streamId)
        reason.set(1u)
        finalSequence.set(12u)
        finalSampleIndex.set(3840u)
    }

    private fun List<UByteArray>.toLengthPrefixedPayload(): UByteArray = buildList {
        for (frame in this@toLengthPrefixedPayload) {
            add((frame.size and 0xFF).toUByte())
            add(((frame.size shr 8) and 0xFF).toUByte())
            addAll(frame)
        }
    }.toUByteArray()
}
