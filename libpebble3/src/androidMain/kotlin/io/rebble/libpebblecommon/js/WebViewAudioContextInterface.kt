package io.rebble.libpebblecommon.js

import android.webkit.JavascriptInterface
import io.rebble.libpebblecommon.js.AudioContextInterface
import io.rebble.libpebblecommon.js.JsRunner
import kotlinx.coroutines.CoroutineScope

class WebViewAudioContextInterface(
    scope: CoroutineScope,
    jsRunner: JsRunner,
) : AudioContextInterface(scope, jsRunner) {
    @JavascriptInterface
    override fun getStatus(callbackId: String) = super.getStatus(callbackId)

    @JavascriptInterface
    override fun requestEnable(callbackId: String) = super.requestEnable(callbackId)

    @JavascriptInterface
    override fun triggerInfo(callbackId: String) = super.triggerInfo(callbackId)

    @JavascriptInterface
    override fun requestPermission(callbackId: String, permissionsJson: String) =
        super.requestPermission(callbackId, permissionsJson)

    @JavascriptInterface
    override fun recentTranscript(callbackId: String, optionsJson: String) =
        super.recentTranscript(callbackId, optionsJson)

    @JavascriptInterface
    override fun transcriptHistory(callbackId: String, optionsJson: String) =
        super.transcriptHistory(callbackId, optionsJson)

    @JavascriptInterface
    override fun subscribeTranscript(callbackId: String, optionsJson: String): String =
        super.subscribeTranscript(callbackId, optionsJson)

    @JavascriptInterface
    override fun subscribeStatus(callbackId: String): String =
        super.subscribeStatus(callbackId)

    @JavascriptInterface
    override fun subscribeRawAudio(callbackId: String, optionsJson: String): String =
        super.subscribeRawAudio(callbackId, optionsJson)

    @JavascriptInterface
    override fun unsubscribe(subscriptionId: String): Boolean = super.unsubscribe(subscriptionId)
}
