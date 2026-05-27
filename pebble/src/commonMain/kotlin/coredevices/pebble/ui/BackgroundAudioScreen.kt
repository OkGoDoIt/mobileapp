package coredevices.pebble.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coredevices.pebble.Platform
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.backgroundaudio.BackgroundAudioSegmentMetadata
import coredevices.pebble.services.backgroundaudio.TranscriptionStatus
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.endpointmanager.audio.background.BackgroundAudioStreamState
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BackgroundAudioScreen(navBarNav: NavBarNav, topBarParams: TopBarParams) {
    val viewModel: BackgroundAudioViewModel = koinViewModel()
    val platform = koinInject<Platform>()
    val uiState by viewModel.uiState.collectAsState()
    val libPebble = rememberLibPebble()
    val watches by libPebble.watches.collectAsState()
    val watchPrefs by libPebble.watchPrefs.collectAsState(emptyList())
    val connectedWatch = watches.filterIsInstance<ConnectedPebbleDevice>().firstOrNull()
    val streamStateFlow = connectedWatch?.backgroundAudio?.state
    val streamState by streamStateFlow?.collectAsState(BackgroundAudioStreamState.Idle)
        ?: remember { mutableStateOf(BackgroundAudioStreamState.Idle) }
    val backgroundAudioEnabled = watchPrefs
        .firstOrNull { it.pref == BoolWatchPref.BackgroundAudioEnabled }
        ?.value as? Boolean ?: false
    val watchSupported = connectedWatch?.watchInfo?.capabilities
        ?.contains(ProtocolCapsFlag.SupportsBackgroundAudioStreaming) == true

    LaunchedEffect(Unit) {
        topBarParams.title("Background Audio")
        topBarParams.searchAvailable(null)
        topBarParams.actions {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        viewModel.refresh()
    }

    LaunchedEffect(streamState, watchSupported) {
        viewModel.updateStreamState(
            streamState = streamState,
            watchSupported = watchSupported,
            phoneSupported = platform == Platform.Android,
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        item {
            ListItem(
                headlineContent = { Text("Status") },
                supportingContent = {
                    Column {
                        Text("Watch setting: ${if (backgroundAudioEnabled) "On" else "Off"}")
                        Text("Stream: ${uiState.streamState.displayName()}")
                        Text("Watch support: ${if (uiState.watchSupported) "Supported" else "Unsupported or disconnected"}")
                        Text("Phone support: ${if (uiState.phoneSupported) "Supported" else "Unsupported"}")
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Storage") },
                supportingContent = {
                    Text(
                        "${uiState.storageStats.audioBytes.formatBytes()} audio, " +
                            "${uiState.storageStats.segmentCount} segments, ${uiState.storageState}",
                    )
                },
                trailingContent = {
                    TextButton(onClick = { viewModel.enforceRetention() }) {
                        Text("Clean Up")
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Transcription") },
                supportingContent = {
                    Text("${uiState.pendingTranscriptions} pending")
                },
                trailingContent = {
                    TextButton(onClick = { viewModel.retryFailedTranscriptions() }) {
                        Text("Retry Failed")
                    }
                },
            )
        }
        item {
            Text(
                text = "Recent Segments",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (uiState.recentSegments.isEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("No segments yet") },
                    supportingContent = { Text("Audio segments will appear here after the watch streams data.") },
                )
            }
        } else {
            items(uiState.recentSegments, key = { it.segmentId }) { segment ->
                BackgroundAudioSegmentRow(segment)
            }
        }
    }
}

@Composable
private fun BackgroundAudioSegmentRow(segment: BackgroundAudioSegmentMetadata) {
    ListItem(
        headlineContent = {
            Text(segment.startedAt.toString())
        },
        supportingContent = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(segment.status.name)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("  ${segment.transcriptionStatus.displayName()}")
                }
                Text("${segment.durationLabel()} • ${segment.bytesWritten.formatBytes()}")
                if (segment.gapCount > 0) {
                    Text("${segment.gapCount} gap${if (segment.gapCount == 1) "" else "s"}")
                }
                segment.transcriptionError?.let { Text(it) }
            }
        },
    )
}

private fun BackgroundAudioStreamState.displayName(): String = when (this) {
    BackgroundAudioStreamState.Idle -> "Idle"
    BackgroundAudioStreamState.Receiving -> "Receiving"
    BackgroundAudioStreamState.PausedDisconnected -> "Disconnected"
    BackgroundAudioStreamState.UnsupportedWatch -> "Unsupported watch"
    BackgroundAudioStreamState.UnsupportedCodec -> "Unsupported codec"
    BackgroundAudioStreamState.PhoneCapabilityDisabled -> "Phone disabled"
    BackgroundAudioStreamState.LowStoragePaused -> "Low storage"
    BackgroundAudioStreamState.Error -> "Error"
}

private fun TranscriptionStatus.displayName(): String = when (this) {
    TranscriptionStatus.Pending -> "Pending"
    TranscriptionStatus.Disabled -> "Disabled"
    TranscriptionStatus.InProgress -> "Transcribing"
    TranscriptionStatus.Complete -> "Complete"
    TranscriptionStatus.NoSpeech -> "No speech"
    TranscriptionStatus.Retrying -> "Retrying"
    TranscriptionStatus.Failed -> "Failed"
}

private fun BackgroundAudioSegmentMetadata.durationLabel(): String {
    val ended = endedAtEpochMs ?: return "Open"
    val duration = (ended - startedAtEpochMs).milliseconds
    return duration.toString()
}

private fun Long.formatBytes(): String = when {
    this >= 1024L * 1024L -> "${this / (1024L * 1024L)} MB"
    this >= 1024L -> "${this / 1024L} KB"
    else -> "$this B"
}
