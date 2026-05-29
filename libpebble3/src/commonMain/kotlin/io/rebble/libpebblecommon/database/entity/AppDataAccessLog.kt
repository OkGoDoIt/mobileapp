package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(indices = [Index("appUuid"), Index("startedAtEpochMs"), Index("active")])
data class AppDataAccessLog(
    @PrimaryKey val id: String,
    val appUuid: Uuid,
    val dataType: AppDataAccessType,
    val accessMode: AppDataAccessMode,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val sourceSummary: String?,
    val byteCount: Long?,
    val transcriptSegmentCount: Int?,
    val active: Boolean,
)

enum class AppDataAccessType {
    AudioStatus,
    AudioTranscript,
    AudioHistory,
    AudioRaw,
}

enum class AppDataAccessMode {
    Query,
    LiveSubscription,
}
