package coredevices.pebble.services.backgroundaudio

enum class BackgroundAudioReceiverStorageState {
    Accepting,
    RetentionRunning,
    LowStorage,
    PausedByPolicy,
}
