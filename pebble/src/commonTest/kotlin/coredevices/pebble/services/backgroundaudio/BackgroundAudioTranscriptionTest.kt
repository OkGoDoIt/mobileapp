package coredevices.pebble.services.backgroundaudio

import coredevices.util.AudioEncoding
import coredevices.util.queue.TaskStatus
import coredevices.util.recording.RecordingSourceType
import coredevices.util.transcription.STTLanguage
import coredevices.util.transcription.TranscriptionService
import coredevices.util.transcription.TranscriptionSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration

class BackgroundAudioTranscriptionTest {
    @Test
    fun taskRepositoryDedupesPendingSegmentAndPersists() = runBlocking {
        val root = testRoot("tasks")
        val repository = BackgroundAudioTranscriptionTaskRepository(root = root)

        val firstId = repository.insertOrReusePendingTask("segment-a")
        val secondId = repository.insertOrReusePendingTask("segment-a")

        assertEquals(firstId, secondId)
        assertEquals(1, repository.getPendingTasks().size)

        val reopened = BackgroundAudioTranscriptionTaskRepository(root = root)
        assertEquals("segment-a", reopened.getPendingTasks().single().segmentId)
    }

    @Test
    fun segmentStoreWritesTranscriptStatsAndDeletesAudio() {
        val root = testRoot("store")
        val store = BackgroundAudioSegmentStore(root = root)
        val metadata = closedMetadata(store, "segment-store").copy(transcriptionStatus = TranscriptionStatus.Complete)

        store.writeMetadata(metadata)
        writePcm(store, metadata.segmentId, byteArrayOf(1, 2, 3, 4))
        store.writeTranscript(
            BackgroundAudioTranscript(
                segmentId = metadata.segmentId,
                watchIdentifier = metadata.watchIdentifier,
                sourceType = RecordingSourceType.PebbleWatchContinuous,
                startedAtEpochMs = metadata.startedAtEpochMs,
                endedAtEpochMs = metadata.endedAtEpochMs,
                sampleRateHz = metadata.sampleRateHz,
                language = null,
                provider = "fake",
                modelUsed = "fake-model",
                finalText = "hello",
                createdAtEpochMs = metadata.endedAtEpochMs ?: metadata.startedAtEpochMs,
                updatedAtEpochMs = metadata.endedAtEpochMs ?: metadata.startedAtEpochMs,
                gapCount = 0,
            ),
        )

        assertEquals(4, store.segmentSizeBytes(metadata.segmentId))
        assertEquals(1, store.storageStats().segmentCount)

        store.deleteSegment(metadata.segmentId, deleteTranscript = false)

        assertEquals(0, store.segmentSizeBytes(metadata.segmentId))
        assertNotNull(store.readTranscript(metadata.segmentId))
    }

    @Test
    fun coordinatorTranscribesClosedSegmentAndWritesTranscript() = runBlocking {
        val root = testRoot("coordinator")
        val store = BackgroundAudioSegmentStore(root = root)
        val taskRepository = BackgroundAudioTranscriptionTaskRepository(root = root)
        val metadata = closedMetadata(store, "segment-transcribe")
        store.writeMetadata(metadata)
        writePcm(store, metadata.segmentId, ByteArray(32) { it.toByte() })

        val job = SupervisorJob()
        val coordinator = ContinuousTranscriptionCoordinator(
            segmentStore = store,
            taskRepository = taskRepository,
            transcriptionService = FakeTranscriptionService("background transcript"),
            policy = BackgroundAudioTranscriptionPolicy(retryDelay = Duration.ZERO),
            scope = BackgroundAudioScope(CoroutineScope(Dispatchers.Default + job)),
        )

        coordinator.enqueueClosedSegment(metadata)

        val completed = waitForMetadata(store, metadata.segmentId) {
            it.transcriptionStatus == TranscriptionStatus.Complete
        }
        assertEquals("background transcript", store.readTranscript(metadata.segmentId)?.finalText)
        assertEquals(1, completed.transcriptionAttemptCount)
        assertEquals(TaskStatus.Success, taskRepository.getPendingTasks().firstOrNull()?.status ?: TaskStatus.Success)

        coordinator.close()
        job.cancel()
    }

    private class FakeTranscriptionService(
        private val text: String,
    ) : TranscriptionService {
        override val onInitialized: Channel<Boolean> = Channel(Channel.CONFLATED)

        override suspend fun isAvailable(): Boolean = true

        override suspend fun transcribe(
            audioStreamFrames: Flow<ByteArray>?,
            sampleRate: Int,
            language: STTLanguage,
            conversationContext: coredevices.util.transcription.STTConversationContext?,
            dictionaryContext: List<String>?,
            contentContext: String?,
            encoding: AudioEncoding,
            timeout: Duration,
        ): Flow<TranscriptionSessionStatus> = flow {
            var bytes = 0
            audioStreamFrames?.collect { bytes += it.size }
            assertTrue(bytes > 0)
            emit(TranscriptionSessionStatus.Open)
            emit(TranscriptionSessionStatus.Partial("background"))
            emit(TranscriptionSessionStatus.Transcription(text, modelUsed = "fake-model"))
        }
    }

    private companion object {
        fun testRoot(name: String): Path {
            val root = Path("build/background-audio-tests/$name-${Clock.System.now().toEpochMilliseconds()}")
            SystemFileSystem.createDirectories(root, mustCreate = false)
            return root
        }

        fun closedMetadata(store: BackgroundAudioSegmentStore, segmentId: String): BackgroundAudioSegmentMetadata {
            val now = Clock.System.now().toEpochMilliseconds()
            return BackgroundAudioSegmentMetadata(
                segmentId = segmentId,
                watchIdentifier = "watch-test",
                streamId = 7,
                startedAtEpochMs = now - 1_000,
                endedAtEpochMs = now,
                sampleRateHz = 16_000,
                channels = 1,
                codecId = 1,
                codecProfile = "speex-wideband",
                firstSequence = 0,
                lastSequence = 2,
                firstSampleIndex = 0,
                lastSampleIndex = 640,
                pcmPath = store.segmentPcmPath(segmentId).toString(),
                status = SegmentStatus.Closed,
                closedReason = SegmentClosedReason.StreamStopped,
            )
        }

        fun writePcm(store: BackgroundAudioSegmentStore, segmentId: String, bytes: ByteArray) {
            SystemFileSystem.sink(store.segmentPcmPath(segmentId)).buffered().use { sink ->
                sink.write(bytes)
            }
        }

        suspend fun waitForMetadata(
            store: BackgroundAudioSegmentStore,
            segmentId: String,
            predicate: (BackgroundAudioSegmentMetadata) -> Boolean,
        ): BackgroundAudioSegmentMetadata {
            repeat(50) {
                val metadata = store.readMetadata(segmentId)
                if (metadata != null && predicate(metadata)) {
                    return metadata
                }
                delay(50)
            }
            error("Timed out waiting for background audio metadata update")
        }
    }
}
