package coredevices.pebble.services.backgroundaudio

import android.os.StatFs

internal actual fun backgroundAudioAvailableBytes(): Long? {
    val path = backgroundAudioStorageDirectory().toString()
    return runCatching { StatFs(path).availableBytes }.getOrNull()
}
