package coredevices.pebble.services.audiocontext

import io.rebble.libpebblecommon.audiocontext.AudioContextPermission
import io.rebble.libpebblecommon.audiocontext.AudioContextPromptResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.Uuid

enum class AudioContextPromptKind {
    EnableBackgroundAudio,
    GrantPermission,
}

data class AudioContextPromptRequest(
    val appUuid: Uuid,
    val permissions: Set<AudioContextPermission>,
    val kind: AudioContextPromptKind,
    val completion: CompletableDeferred<AudioContextPromptResult>,
)

class AudioContextPromptController {
    private val _requests = MutableSharedFlow<AudioContextPromptRequest>(extraBufferCapacity = 1)
    val requests: SharedFlow<AudioContextPromptRequest> = _requests.asSharedFlow()

    fun request(
        appUuid: Uuid,
        kind: AudioContextPromptKind,
        permissions: Set<AudioContextPermission> = emptySet(),
    ): CompletableDeferred<AudioContextPromptResult> {
        val completion = CompletableDeferred<AudioContextPromptResult>()
        val emitted = _requests.tryEmit(
            AudioContextPromptRequest(
                appUuid = appUuid,
                permissions = permissions,
                kind = kind,
                completion = completion,
            ),
        )
        if (!emitted) {
            completion.complete(AudioContextPromptResult.Unavailable)
        }
        return completion
    }
}
