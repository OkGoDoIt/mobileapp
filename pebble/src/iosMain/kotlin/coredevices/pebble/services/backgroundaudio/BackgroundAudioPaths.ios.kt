package coredevices.pebble.services.backgroundaudio

import kotlinx.io.files.Path
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual fun backgroundAudioStorageDirectory(): Path {
    val urls = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask,
    )
    val base = urls.firstOrNull()?.path ?: error("No documents directory")
    return Path(base, "background-audio")
}
