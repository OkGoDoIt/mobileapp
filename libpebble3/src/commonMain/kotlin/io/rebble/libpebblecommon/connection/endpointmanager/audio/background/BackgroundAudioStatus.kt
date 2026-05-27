package io.rebble.libpebblecommon.connection.endpointmanager.audio.background

import kotlinx.coroutines.flow.StateFlow

interface BackgroundAudioStatus {
    val state: StateFlow<BackgroundAudioStreamState>
}
