package io.rebble.libpebblecommon.connection.endpointmanager.audio.background

/** Optional handler capability for durable persistence checkpointing. */
interface BackgroundAudioCheckpointSource {
    val lastPersistedSequence: UInt
    val lastPersistedSampleIndex: ULong
}
