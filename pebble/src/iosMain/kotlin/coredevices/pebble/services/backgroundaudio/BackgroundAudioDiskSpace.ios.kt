package coredevices.pebble.services.backgroundaudio

import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize

internal actual fun backgroundAudioAvailableBytes(): Long? {
    val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(
        path = backgroundAudioStorageDirectory().toString(),
        error = null,
    ) ?: return null
    return (attributes[NSFileSystemFreeSize] as? Number)?.toLong()
}
