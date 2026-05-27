package coredevices.pebble.services.backgroundaudio

import android.content.Context
import kotlinx.io.files.Path
import org.koin.mp.KoinPlatform

internal actual fun backgroundAudioStorageDirectory(): Path {
    val context: Context = KoinPlatform.getKoin().get()
    return Path(context.filesDir.path, "background-audio")
}
