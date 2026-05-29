package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.audiocontext.AudioContextProvider
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/**
 * Native bridge for PKJS `Pebble.audioContext` APIs.
 */
abstract class AudioContextInterface(
    private val scope: CoroutineScope,
    private val jsRunner: JsRunner,
) : LibPebbleKoinComponent {
    private val provider: AudioContextProvider by inject()
    private val appUuid: Uuid by lazy { Uuid.parse(jsRunner.appInfo.uuid) }

    private val bridge = AudioContextBridge(
        provider = provider,
        appUuid = appUuid,
        scope = scope,
        signalResult = { callbackId, payloadJson ->
            scope.launch {
                val escaped = Json.encodeToString(payloadJson)
                jsRunner.eval(
                    "_PebbleAudioContextCB._resultSuccess(${callbackId.toJsNumber()}, $escaped)",
                )
            }
        },
        signalEvent = { subscriptionId, payloadJson ->
            scope.launch {
                val escaped = Json.encodeToString(payloadJson)
                jsRunner.eval(
                    "_PebbleAudioContextCB._event(${subscriptionId.toJsNumber()}, $escaped)",
                )
            }
        },
    )

    open fun getStatus(callbackId: String) = bridge.getStatus(callbackId)

    open fun requestEnable(callbackId: String) = bridge.requestEnable(callbackId)

    open fun triggerInfo(callbackId: String) = bridge.triggerInfo(callbackId)

    open fun requestPermission(callbackId: String, permissionsJson: String) =
        bridge.requestPermission(callbackId, permissionsJson)

    open fun recentTranscript(callbackId: String, optionsJson: String) =
        bridge.recentTranscript(callbackId, optionsJson)

    open fun transcriptHistory(callbackId: String, optionsJson: String) =
        bridge.transcriptHistory(callbackId, optionsJson)

    open fun subscribeTranscript(callbackId: String, optionsJson: String): String =
        bridge.subscribeTranscript(callbackId, optionsJson)

    open fun subscribeStatus(callbackId: String): String =
        bridge.subscribeStatus(callbackId)

    open fun subscribeRawAudio(callbackId: String, optionsJson: String): String =
        bridge.subscribeRawAudio(callbackId, optionsJson)

    open fun unsubscribe(subscriptionId: String): Boolean = bridge.unsubscribe(subscriptionId)

    open fun close() {
        bridge.close()
    }

    private fun String.toJsNumber(): String = Json.encodeToString(this)
}
