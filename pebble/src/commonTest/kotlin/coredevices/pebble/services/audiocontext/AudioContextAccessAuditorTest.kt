package coredevices.pebble.services.audiocontext

import io.rebble.libpebblecommon.database.dao.AppDataAccessLogDao
import io.rebble.libpebblecommon.database.entity.AppDataAccessLog
import io.rebble.libpebblecommon.database.entity.AppDataAccessMode
import io.rebble.libpebblecommon.database.entity.AppDataAccessType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AudioContextAccessAuditorTest {
    @Test
    fun recordsQueryAndSubscriptionLifecycle() = runBlocking {
        val dao = InMemoryAppDataAccessLogDao()
        val auditor = AudioContextAccessAuditor(dao)
        val appUuid = Uuid.random()

        auditor.recordQuery(
            appUuid = appUuid,
            dataType = AppDataAccessType.AudioTranscript,
            sourceSummary = "recent",
            transcriptSegmentCount = 2,
        )
        val subscriptionId = auditor.startSubscription(
            appUuid = appUuid,
            dataType = AppDataAccessType.AudioRaw,
            sourceSummary = "live raw audio",
        )

        assertEquals(1, dao.active().size)
        auditor.finishSubscription(subscriptionId)

        val logs = dao.recentForApp(appUuid)
        assertEquals(2, logs.size)
        assertTrue(logs.any { it.accessMode == AppDataAccessMode.Query && !it.active })
        assertTrue(logs.any { it.accessMode == AppDataAccessMode.LiveSubscription && !it.active })
    }

    private class InMemoryAppDataAccessLogDao : AppDataAccessLogDao {
        private val logs = mutableListOf<AppDataAccessLog>()

        override suspend fun insertOrReplace(log: AppDataAccessLog) {
            logs.removeAll { it.id == log.id }
            logs.add(log)
        }

        override suspend fun recentForApp(appUuid: Uuid, limit: Int): List<AppDataAccessLog> =
            logs.filter { it.appUuid == appUuid }.sortedByDescending { it.startedAtEpochMs }.take(limit)

        override suspend fun active(): List<AppDataAccessLog> = logs.filter { it.active }

        override suspend fun recent(limit: Int): List<AppDataAccessLog> =
            logs.sortedByDescending { it.startedAtEpochMs }.take(limit)

        override suspend fun finish(id: String, endedAtEpochMs: Long) {
            val index = logs.indexOfFirst { it.id == id }
            if (index >= 0) {
                logs[index] = logs[index].copy(active = false, endedAtEpochMs = endedAtEpochMs)
            }
        }
    }
}
