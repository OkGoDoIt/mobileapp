package io.rebble.libpebblecommon.voice

sealed class VoiceSessionIntent(val wireValue: UByte) {
    data object Default : VoiceSessionIntent(0x00u)
    data object IndexMemo : VoiceSessionIntent(0x01u)
    data class Unknown(val raw: UByte) : VoiceSessionIntent(raw)

    companion object {
        fun fromWireValue(value: UByte): VoiceSessionIntent =
            when (value) {
                Default.wireValue -> Default
                IndexMemo.wireValue -> IndexMemo
                else -> Unknown(value)
            }
    }
}
