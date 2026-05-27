package coredevices.pebble.services.backgroundaudio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber

@OptIn(ExperimentalForeignApi::class)
internal actual fun backgroundAudioAvailableBytes(): Long? {
    val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(
        path = backgroundAudioStorageDirectory().toString(),
        error = null,
    ) ?: return null
    return (attributes[NSFileSystemFreeSize] as? NSNumber)?.longLongValue
}
