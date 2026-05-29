package coredevices.pebble.services.audiocontext

import io.rebble.libpebblecommon.audiocontext.AudioContextAvailability
import io.rebble.libpebblecommon.audiocontext.AudioContextPermission
import io.rebble.libpebblecommon.audiocontext.AudioContextPermissionException
import io.rebble.libpebblecommon.database.dao.LockerAppPermissionDao
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.database.entity.LockerAppPermissionType
import kotlin.uuid.Uuid

class AudioContextPermissionChecker(
    private val permissionGranted: suspend (Uuid, LockerAppPermissionType) -> Boolean?,
    private val declaredCapabilities: suspend (Uuid) -> List<String>?,
) {
    constructor(
        permissionDao: LockerAppPermissionDao,
        lockerEntryDao: LockerEntryRealDao,
    ) : this(
        permissionGranted = { appUuid, permission ->
            permissionDao.getByAppUuidAndPermission(appUuid, permission)?.granted
        },
        declaredCapabilities = { appUuid ->
            lockerEntryDao.getAll().firstOrNull { it.id == appUuid }?.capabilities
        },
    )

    suspend fun require(appUuid: Uuid, permission: AudioContextPermission) {
        val declared = hasDeclaredCapability(appUuid, permission)
        if (!declared) {
            throw AudioContextPermissionException(
                AudioContextAvailability.CapabilityNotDeclared,
                "App has not declared ${permission.capability}",
            )
        }
        val granted = permissionGranted(appUuid, permission.toLockerPermission()) == true
        if (!granted) {
            throw AudioContextPermissionException(
                AudioContextAvailability.PermissionDenied,
                "Audio context permission is not granted",
            )
        }
    }

    suspend fun hasDeclaredCapability(appUuid: Uuid, permission: AudioContextPermission): Boolean {
        return permission.capability in declaredCapabilities(appUuid).orEmpty()
    }
}

fun AudioContextPermission.toLockerPermission(): LockerAppPermissionType = when (this) {
    AudioContextPermission.Status -> LockerAppPermissionType.AudioStatus
    AudioContextPermission.RecentTranscript -> LockerAppPermissionType.AudioTranscript
    AudioContextPermission.TranscriptHistory -> LockerAppPermissionType.AudioHistory
    AudioContextPermission.LiveTranscript -> LockerAppPermissionType.AudioTranscript
    AudioContextPermission.RawAudio -> LockerAppPermissionType.AudioRaw
}
