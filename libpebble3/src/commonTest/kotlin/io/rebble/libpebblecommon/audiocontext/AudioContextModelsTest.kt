package io.rebble.libpebblecommon.audiocontext

import io.rebble.libpebblecommon.locker.AppCapability
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class AudioContextModelsTest {
    @Test
    fun audioCapabilitiesParseFromManifestStrings() {
        val capabilities = AppCapability.fromString(
            listOf("audio_status", "audio_transcript", "audio_history", "audio_raw"),
        )

        assertEquals(
            listOf(
                AppCapability.AudioStatus,
                AppCapability.AudioTranscript,
                AppCapability.AudioHistory,
                AppCapability.AudioRaw,
            ),
            capabilities,
        )
    }

    @Test
    fun noOpProviderFailsClosed() = runBlocking {
        val provider = NoOpAudioContextProvider()
        val appUuid = Uuid.random()

        assertEquals(AudioContextAvailability.UnsupportedPhone, provider.status(appUuid).availability)
        assertEquals(AudioContextPromptResult.Unavailable, provider.requestEnablePrompt(appUuid))
        assertEquals(AudioContextPromptResult.Unavailable, provider.requestPermissionPrompt(appUuid, setOf(AudioContextPermission.RawAudio)))
        assertEquals(emptyList(), provider.recentTranscript(appUuid))
        assertEquals(emptyList(), provider.transcriptHistory(appUuid, AudioContextQueryWindow()))
        assertNull(provider.liveTranscript(appUuid).firstOrNull())
        assertNull(provider.rawAudio(appUuid).firstOrNull())
    }
}
