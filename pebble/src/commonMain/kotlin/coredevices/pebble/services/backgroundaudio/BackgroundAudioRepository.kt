package coredevices.pebble.services.backgroundaudio

import coredevices.util.queue.TaskStatus

class BackgroundAudioRepository(
    private val segmentStore: BackgroundAudioSegmentStore,
    private val taskRepository: BackgroundAudioTranscriptionTaskRepository,
    private val retentionManager: BackgroundAudioRetentionManager,
) {
    suspend fun snapshot(): BackgroundAudioSnapshot {
        val segments = segmentStore.listMetadata().sortedByDescending { it.startedAtEpochMs }
        val pendingTasks = taskRepository.getPendingTasks()
        val stats = segmentStore.storageStats()
        return BackgroundAudioSnapshot(
            segments = segments,
            storageStats = stats,
            storageState = retentionManager.storageState(),
            pendingTranscriptions = pendingTasks.count { it.status == TaskStatus.Pending },
        )
    }

    fun failedSegments(): List<BackgroundAudioSegmentMetadata> {
        return segmentStore.listMetadata()
            .filter { it.status == SegmentStatus.Closed && it.transcriptionStatus == TranscriptionStatus.Failed }
    }

    fun markPending(segmentId: String) {
        segmentStore.updateMetadata(segmentId) {
            it.copy(transcriptionStatus = TranscriptionStatus.Pending, transcriptionError = null)
        }
    }

    fun enforceRetention(): BackgroundAudioReceiverStorageState = retentionManager.enforceRetention()
}

data class BackgroundAudioSnapshot(
    val segments: List<BackgroundAudioSegmentMetadata>,
    val storageStats: BackgroundAudioStorageStats,
    val storageState: BackgroundAudioReceiverStorageState,
    val pendingTranscriptions: Int,
)
