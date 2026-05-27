package coredevices.pebble.services.backgroundaudio

import co.touchlab.kermit.Logger
import kotlin.time.Clock

class BackgroundAudioRetentionManager(
    private val segmentStore: BackgroundAudioSegmentStore,
    private val policy: BackgroundAudioRetentionPolicy,
) {
    private val logger = Logger.withTag("BgAudioRetention")

    fun storageState(): BackgroundAudioReceiverStorageState {
        val stats = segmentStore.storageStats()
        val freeBytes = backgroundAudioAvailableBytes()
        return when {
            freeBytes != null && freeBytes < policy.minFreeBytes -> BackgroundAudioReceiverStorageState.LowStorage
            stats.audioBytes > policy.maxAudioBytes -> BackgroundAudioReceiverStorageState.PausedByPolicy
            else -> BackgroundAudioReceiverStorageState.Accepting
        }
    }

    fun freeStorageHintKb(): UInt {
        return ((backgroundAudioAvailableBytes() ?: 0L) / 1024L)
            .coerceIn(0L, UInt.MAX_VALUE.toLong())
            .toUInt()
    }

    fun enforceRetention(): BackgroundAudioReceiverStorageState {
        val initialState = storageState()
        if (initialState == BackgroundAudioReceiverStorageState.Accepting) {
            deleteExpiredAudio()
            return storageState()
        }

        logger.w { "Running background audio retention for state=$initialState" }
        deleteExpiredAudio()
        trimToQuota()
        return storageState()
    }

    fun canAcceptNewStream(): Boolean {
        val state = enforceRetention()
        return state == BackgroundAudioReceiverStorageState.Accepting
    }

    private fun deleteExpiredAudio() {
        val cutoff = Clock.System.now() - policy.maxAudioAge
        segmentStore.listMetadata()
            .filter { it.status != SegmentStatus.Open && it.startedAt < cutoff }
            .filterNot { it.transcriptionStatus == TranscriptionStatus.InProgress }
            .forEach { metadata ->
                logger.i { "Deleting expired background audio segment ${metadata.segmentId}" }
                segmentStore.deleteSegment(metadata.segmentId, deleteTranscript = false)
            }
    }

    private fun trimToQuota() {
        var stats = segmentStore.storageStats()
        if (stats.audioBytes <= policy.maxAudioBytes) {
            return
        }

        deletionCandidates().forEach { metadata ->
            if (stats.audioBytes <= policy.maxAudioBytes) {
                return
            }
            logger.i { "Deleting background audio segment ${metadata.segmentId} to enforce quota" }
            segmentStore.deleteSegment(metadata.segmentId, deleteTranscript = false)
            stats = segmentStore.storageStats()
        }
    }

    private fun deletionCandidates(): List<BackgroundAudioSegmentMetadata> {
        val metadata = segmentStore.listMetadata()
            .filter { it.status != SegmentStatus.Open }
            .filterNot { it.transcriptionStatus == TranscriptionStatus.InProgress }
        val terminalFirst = metadata.filter {
            it.transcriptionStatus == TranscriptionStatus.Complete ||
                it.transcriptionStatus == TranscriptionStatus.NoSpeech
        }
        val failedNext = metadata.filter { it.transcriptionStatus == TranscriptionStatus.Failed }
        return (terminalFirst + failedNext).distinctBy { it.segmentId }.sortedBy { it.startedAtEpochMs }
    }
}
