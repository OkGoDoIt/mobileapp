package coredevices.pebble.services.audiocontext

import io.rebble.libpebblecommon.database.dao.AppDataAccessLogDao
import io.rebble.libpebblecommon.database.entity.AppDataAccessLog
import io.rebble.libpebblecommon.database.entity.AppDataAccessMode
import io.rebble.libpebblecommon.database.entity.AppDataAccessType
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AudioContextAccessAuditor(
    private val dao: AppDataAccessLogDao,
) {
    suspend fun recordQuery(
        appUuid: Uuid,
        dataType: AppDataAccessType,
        sourceSummary: String?,
        transcriptSegmentCount: Int? = null,
        byteCount: Long? = null,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        dao.insertOrReplace(
            AppDataAccessLog(
                id = "audio-query-$appUuid-$dataType-$now",
                appUuid = appUuid,
                dataType = dataType,
                accessMode = AppDataAccessMode.Query,
                startedAtEpochMs = now,
                endedAtEpochMs = now,
                sourceSummary = sourceSummary,
                byteCount = byteCount,
                transcriptSegmentCount = transcriptSegmentCount,
                active = false,
            ),
        )
    }

    suspend fun startSubscription(
        appUuid: Uuid,
        dataType: AppDataAccessType,
        sourceSummary: String?,
    ): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "audio-sub-$appUuid-$dataType-$now"
        dao.insertOrReplace(
            AppDataAccessLog(
                id = id,
                appUuid = appUuid,
                dataType = dataType,
                accessMode = AppDataAccessMode.LiveSubscription,
                startedAtEpochMs = now,
                endedAtEpochMs = null,
                sourceSummary = sourceSummary,
                byteCount = null,
                transcriptSegmentCount = null,
                active = true,
            ),
        )
        return id
    }

    suspend fun finishSubscription(id: String) {
        dao.finish(id, Clock.System.now().toEpochMilliseconds())
    }

    suspend fun activeAccesses(): List<AppDataAccessLog> = dao.active()

    suspend fun recentAccesses(limit: Int = 100): List<AppDataAccessLog> = dao.recent(limit)
}
