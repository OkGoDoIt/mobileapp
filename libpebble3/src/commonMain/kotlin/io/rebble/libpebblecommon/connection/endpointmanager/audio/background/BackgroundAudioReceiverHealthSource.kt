package io.rebble.libpebblecommon.connection.endpointmanager.audio.background

interface BackgroundAudioReceiverHealthSource {
    val receiverFlags: UInt
    val freeStorageHintKb: UInt
}
