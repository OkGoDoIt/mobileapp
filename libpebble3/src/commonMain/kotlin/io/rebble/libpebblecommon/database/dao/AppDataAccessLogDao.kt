package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.rebble.libpebblecommon.database.entity.AppDataAccessLog
import kotlin.uuid.Uuid

@Dao
interface AppDataAccessLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(log: AppDataAccessLog)

    @Query("SELECT * FROM AppDataAccessLog WHERE appUuid = :appUuid ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun recentForApp(appUuid: Uuid, limit: Int = 50): List<AppDataAccessLog>

    @Query("SELECT * FROM AppDataAccessLog WHERE active = 1 ORDER BY startedAtEpochMs DESC")
    suspend fun active(): List<AppDataAccessLog>

    @Query("SELECT * FROM AppDataAccessLog ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<AppDataAccessLog>

    @Query("UPDATE AppDataAccessLog SET endedAtEpochMs = :endedAtEpochMs, active = 0 WHERE id = :id")
    suspend fun finish(id: String, endedAtEpochMs: Long)
}
