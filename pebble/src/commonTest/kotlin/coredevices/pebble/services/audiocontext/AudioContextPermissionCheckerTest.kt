package coredevices.pebble.services.audiocontext

import io.rebble.libpebblecommon.audiocontext.AudioContextAvailability
import io.rebble.libpebblecommon.audiocontext.AudioContextPermission
import io.rebble.libpebblecommon.database.entity.LockerAppPermissionType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class AudioContextPermissionCheckerTest {
    @Test
    fun deniesWhenCapabilityIsNotDeclared() = runBlocking {
        val checker = checker(
            declared = emptyList(),
            grants = mapOf(LockerAppPermissionType.AudioTranscript to true),
        )

        val error = assertFailsWith<AudioContextPermissionDenied> {
            checker.require(Uuid.random(), AudioContextPermission.RecentTranscript)
        }

        assertEquals(AudioContextAvailability.CapabilityNotDeclared, error.availability)
    }

    @Test
    fun deniesByDefaultWhenGrantIsMissing() = runBlocking {
        val checker = checker(declared = listOf("audio_transcript"), grants = emptyMap())

        val error = assertFailsWith<AudioContextPermissionDenied> {
            checker.require(Uuid.random(), AudioContextPermission.RecentTranscript)
        }

        assertEquals(AudioContextAvailability.PermissionDenied, error.availability)
    }

    @Test
    fun allowsDeclaredAndGrantedPermission() = runBlocking {
        val checker = checker(
            declared = listOf("audio_raw"),
            grants = mapOf(LockerAppPermissionType.AudioRaw to true),
        )

        checker.require(Uuid.random(), AudioContextPermission.RawAudio)
    }

    private fun checker(
        declared: List<String>,
        grants: Map<LockerAppPermissionType, Boolean>,
    ) = AudioContextPermissionChecker(
        permissionGranted = { _, permission -> grants[permission] },
        declaredCapabilities = { declared },
    )
}
