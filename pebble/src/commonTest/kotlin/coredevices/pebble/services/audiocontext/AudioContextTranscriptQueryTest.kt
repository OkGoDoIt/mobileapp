package coredevices.pebble.services.audiocontext

import coredevices.pebble.services.backgroundaudio.BackgroundAudioSegmentMetadata
import coredevices.pebble.services.backgroundaudio.BackgroundAudioSegmentStore
import coredevices.pebble.services.backgroundaudio.BackgroundAudioTranscript
import coredevices.pebble.services.backgroundaudio.SegmentClosedReason
import coredevices.pebble.services.backgroundaudio.SegmentStatus
import coredevices.util.recording.RecordingSourceType
import io.rebble.libpebblecommon.audiocontext.AudioContextQueryWindow
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class AudioContextTranscriptQueryTest {
    @Test
    fun recentTranscriptFiltersByWindowAndMapsWarnings() {
        val root = testRoot("recent")
        val store = BackgroundAudioSegmentStore(root = root)
        val now = Clock.System.now().toEpochMilliseconds()
        writeTranscript(store, "inside", now - 20_000, now - 10_000, gapCount = 2, text = "inside window")
        writeTranscript(store, "outside", now - 120_000, now - 110_000, gapCount = 0, text = "outside window")

        val results = AudioContextTranscriptQuery(store).recent(
            AudioContextQueryWindow(anchorEpochMs = now, beforeSeconds = 60),
        )

        assertEquals(listOf("inside window"), results.map { it.text })
        assertEquals("inside", results.single().source.segmentId)
        assertTrue(results.single().warnings.any { it.contains("2 audio gap") })
    }

    @Test
    fun historyQueryRequiresExplicitWindowAndBoundsToOneDay() {
        val root = testRoot("history")
        val store = BackgroundAudioSegmentStore(root = root)
        val now = Clock.System.now().toEpochMilliseconds()
        writeTranscript(store, "history", now - 2_000, now - 1_000, gapCount = 0, text = "history window")

        val results = AudioContextTranscriptQuery(store).history(
            AudioContextQueryWindow(startedAtEpochMs = now - 10_000, endedAtEpochMs = now),
        )

        assertEquals("history window", results.single().text)
    }

    private fun writeTranscript(
        store: BackgroundAudioSegmentStore,
        segmentId: String,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
        gapCount: Int,
        text: String,
    ) {
        val metadata = BackgroundAudioSegmentMetadata(
            segmentId = segmentId,
            watchIdentifier = "watch-test",
            streamId = 42,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            sampleRateHz = 16_000,
            channels = 1,
            codecId = 1,
            codecProfile = "speex-wideband",
            firstSequence = 0,
            lastSequence = 1,
            firstSampleIndex = 0,
            lastSampleIndex = 320,
            pcmPath = store.segmentPcmPath(segmentId).toString(),
            status = SegmentStatus.Closed,
            gapCount = gapCount,
            closedReason = SegmentClosedReason.StreamStopped,
        )
        store.writeMetadata(metadata)
        store.writeTranscript(
            BackgroundAudioTranscript(
                segmentId = segmentId,
                watchIdentifier = "watch-test",
                sourceType = RecordingSourceType.PebbleWatchContinuous,
                startedAtEpochMs = startedAtEpochMs,
                endedAtEpochMs = endedAtEpochMs,
                sampleRateHz = 16_000,
                language = "en",
                provider = "fake",
                modelUsed = "test-model",
                finalText = text,
                createdAtEpochMs = endedAtEpochMs,
                updatedAtEpochMs = endedAtEpochMs,
                gapCount = gapCount,
            ),
        )
    }

    private fun testRoot(name: String): Path {
        val root = Path("build/audio-context-tests/$name-${Clock.System.now().toEpochMilliseconds()}")
        SystemFileSystem.createDirectories(root, mustCreate = false)
        return root
    }
}
