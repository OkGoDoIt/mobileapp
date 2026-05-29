package io.rebble.libpebblecommon.audiocontext

/**
 * Raised when an app lacks a declared capability or an explicit audio grant.
 */
class AudioContextPermissionException(
    val availability: AudioContextAvailability,
    message: String,
) : Exception(message)
