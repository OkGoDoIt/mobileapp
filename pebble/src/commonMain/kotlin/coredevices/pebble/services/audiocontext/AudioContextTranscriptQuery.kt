package coredevices.pebble.services.audiocontext

import coredevices.pebble.services.backgroundaudio.BackgroundAudioSegmentMetadata
import coredevices.pebble.services.backgroundaudio.BackgroundAudioSegmentStore
import coredevices.pebble.services.backgroundaudio.BackgroundAudioTranscript
import io.rebble.libpebblecommon.audiocontext.AudioContextQueryWindow
import io.rebble.libpebblecommon.audiocontext.AudioContextSourceMetadata
import io.rebble.libpebblecommon.audiocontext.AudioContextTimeWindow
import io.rebble.libpebblecommon.audiocontext.AudioContextTranscriptSegment
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class AudioContextTranscriptQuery(
    private val segmentStore: BackgroundAudioSegmentStore,
) {
    fun recent(window: AudioContextQueryWindow, maxBytes: Int = 64 * 1024): List<AudioContextTranscriptSegment> {
        val resolved = window.resolveRecent()
        return query(resolved, maxBytes)
    }

    fun history(window: AudioContextQueryWindow, maxBytes: Int = 64 * 1024): List<AudioContextTranscriptSegment> {
        val now = Clock.System.now().toEpochMilliseconds()
        val start = window.startedAtEpochMs ?: (now - 24.hours.inWholeMilliseconds)
        val end = window.endedAtEpochMs ?: now
        val boundedStart = start.coerceAtLeast(end - 24.hours.inWholeMilliseconds)
        return query(ResolvedWindow(boundedStart, end), maxBytes)
    }

    private fun query(window: ResolvedWindow, maxBytes: Int): List<AudioContextTranscriptSegment> {
        var usedBytes = 0
        return segmentStore.listMetadata()
            .filter { it.overlaps(window) }
            .mapNotNull { metadata ->
                segmentStore.readTranscript(metadata.segmentId)?.toAudioContext(metadata)
            }
            .takeWhile { segment ->
                usedBytes += segment.text.encodeToByteArray().size
                usedBytes <= maxBytes
            }
    }

    private fun AudioContextQueryWindow.resolveRecent(): ResolvedWindow {
        val anchor = anchorEpochMs ?: Clock.System.now().toEpochMilliseconds()
        val start = startedAtEpochMs ?: (anchor - beforeSeconds.coerceAtLeast(0) * 1000L)
        val end = endedAtEpochMs ?: (anchor + afterSeconds.coerceAtLeast(0) * 1000L)
        return ResolvedWindow(start, end.coerceAtLeast(start))
    }

    private fun BackgroundAudioSegmentMetadata.overlaps(window: ResolvedWindow): Boolean {
        val end = endedAtEpochMs ?: Clock.System.now().toEpochMilliseconds()
        return startedAtEpochMs <= window.endEpochMs && end >= window.startEpochMs
    }

    fun BackgroundAudioTranscript.toAudioContext(metadata: BackgroundAudioSegmentMetadata?): AudioContextTranscriptSegment {
        val final = finalText.isNotBlank()
        val text = finalText.ifBlank { partialText.orEmpty() }
        return AudioContextTranscriptSegment(
            id = segmentId,
            text = text,
            isFinal = final,
            window = AudioContextTimeWindow(
                startedAtEpochMs = startedAtEpochMs,
                endedAtEpochMs = endedAtEpochMs ?: updatedAtEpochMs,
            ),
            language = language,
            provider = provider,
            modelUsed = modelUsed,
            source = AudioContextSourceMetadata(
                sourceType = sourceType.name,
                sourceDeviceId = watchIdentifier,
                sourceAction = metadata?.closedReason?.name,
                streamId = metadata?.streamId,
                segmentId = segmentId,
                gapCount = gapCount,
            ),
            warnings = warnings + if (gapCount > 0) listOf("Segment contains $gapCount audio gap(s)") else emptyList(),
        )
    }

    private data class ResolvedWindow(
        val startEpochMs: Long,
        val endEpochMs: Long,
    )
}
