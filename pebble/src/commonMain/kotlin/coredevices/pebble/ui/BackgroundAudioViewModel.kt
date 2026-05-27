package coredevices.pebble.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.pebble.services.backgroundaudio.BackgroundAudioReceiverStorageState
import coredevices.pebble.services.backgroundaudio.BackgroundAudioRepository
import coredevices.pebble.services.backgroundaudio.BackgroundAudioSegmentMetadata
import coredevices.pebble.services.backgroundaudio.BackgroundAudioStorageStats
import coredevices.pebble.services.backgroundaudio.ContinuousTranscriptionCoordinator
import coredevices.pebble.services.backgroundaudio.TranscriptionStatus
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioStreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackgroundAudioUiState(
    val streamState: BackgroundAudioStreamState = BackgroundAudioStreamState.Idle,
    val watchSupported: Boolean = false,
    val phoneSupported: Boolean = true,
    val storageState: BackgroundAudioReceiverStorageState = BackgroundAudioReceiverStorageState.Accepting,
    val storageStats: BackgroundAudioStorageStats = BackgroundAudioStorageStats(0, 0, 0, 0, null),
    val pendingTranscriptions: Int = 0,
    val recentSegments: List<BackgroundAudioSegmentMetadata> = emptyList(),
)

class BackgroundAudioViewModel(
    private val repository: BackgroundAudioRepository,
    private val transcriptionCoordinator: ContinuousTranscriptionCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackgroundAudioUiState())
    val uiState: StateFlow<BackgroundAudioUiState> = _uiState.asStateFlow()

    fun updateStreamState(
        streamState: BackgroundAudioStreamState,
        watchSupported: Boolean,
        phoneSupported: Boolean,
    ) {
        _uiState.update {
            it.copy(
                streamState = streamState,
                watchSupported = watchSupported,
                phoneSupported = phoneSupported,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = repository.snapshot()
            _uiState.update {
                it.copy(
                    storageState = snapshot.storageState,
                    storageStats = snapshot.storageStats,
                    pendingTranscriptions = snapshot.pendingTranscriptions,
                    recentSegments = snapshot.segments.take(50),
                )
            }
        }
    }

    fun retryFailedTranscriptions() {
        viewModelScope.launch {
            repository.failedSegments().forEach { metadata ->
                repository.markPending(metadata.segmentId)
                transcriptionCoordinator.enqueueClosedSegment(
                    metadata.copy(transcriptionStatus = TranscriptionStatus.Pending),
                )
            }
            refresh()
        }
    }

    fun enforceRetention() {
        viewModelScope.launch {
            repository.enforceRetention()
            refresh()
        }
    }
}
