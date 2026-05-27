package io.rebble.libpebblecommon.connection.endpointmanager.audio.background

/** App-level handler for continuous watch background audio streams. */
interface BackgroundAudioStreamHandler {
    suspend fun canAccept(config: BackgroundAudioStreamConfig): Boolean = true

    suspend fun onStreamStarted(config: BackgroundAudioStreamConfig)

    suspend fun onFrameBatch(batch: BackgroundAudioFrameBatch)

    suspend fun onGap(gap: BackgroundAudioGap)

    suspend fun onStreamStopped(summary: BackgroundAudioStopSummary)

    /**
     * Called after audio and metadata for the given sequence range are durable on phone.
     * Return the highest contiguous sequence persisted for checkpointing.
     */
    suspend fun onPersistedThrough(streamId: UInt, highestSequence: UInt, sampleIndex: ULong) {}
}

/** No-op handler used when the app does not install lifelogging support. */
object NoOpBackgroundAudioStreamHandler : BackgroundAudioStreamHandler {
    override suspend fun onStreamStarted(config: BackgroundAudioStreamConfig) {}
    override suspend fun onFrameBatch(batch: BackgroundAudioFrameBatch) {}
    override suspend fun onGap(gap: BackgroundAudioGap) {}
    override suspend fun onStreamStopped(summary: BackgroundAudioStopSummary) {}
}
