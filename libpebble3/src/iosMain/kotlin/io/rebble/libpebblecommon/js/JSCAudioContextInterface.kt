package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.js.AudioContextInterface
import io.rebble.libpebblecommon.js.JsRunner
import io.rebble.libpebblecommon.js.RegisterableJsInterface
import kotlinx.coroutines.CoroutineScope

class JSCAudioContextInterface(
    scope: CoroutineScope,
    jsRunner: JsRunner,
) : AudioContextInterface(scope, jsRunner), RegisterableJsInterface {
    override val interf = mapOf(
        "getStatus" to this::getStatus,
        "requestEnable" to this::requestEnable,
        "requestPermission" to this::requestPermission,
        "recentTranscript" to this::recentTranscript,
        "transcriptHistory" to this::transcriptHistory,
        "subscribeTranscript" to this::subscribeTranscript,
        "subscribeRawAudio" to this::subscribeRawAudio,
        "unsubscribe" to this::unsubscribe,
    )

    override val name = "_PebbleAudioContext"

    override fun dispatch(method: String, args: List<Any?>): Any? = when (method) {
        "getStatus" -> {
            getStatus(args[0].toString())
            null
        }
        "requestEnable" -> {
            requestEnable(args[0].toString())
            null
        }
        "requestPermission" -> {
            requestPermission(args[0].toString(), args[1].toString())
            null
        }
        "recentTranscript" -> {
            recentTranscript(args[0].toString(), args[1].toString())
            null
        }
        "transcriptHistory" -> {
            transcriptHistory(args[0].toString(), args[1].toString())
            null
        }
        "subscribeTranscript" -> subscribeTranscript(args[0].toString(), args[1].toString())
        "subscribeRawAudio" -> subscribeRawAudio(args[0].toString(), args[1].toString())
        "unsubscribe" -> unsubscribe(args[0].toString())
        else -> error("Unknown method: $method")
    }

    override fun close() {
        super.close()
    }
}
