package coredevices.pebble.services.backgroundaudio

import coredevices.util.queue.QueueTaskRepository
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

class BackgroundAudioTranscriptionTaskRepository(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val root: Path = backgroundAudioStorageDirectory(),
) : QueueTaskRepository<BackgroundAudioTranscriptionTask> {
    private val mutex = Mutex()
    private val tasksDirectory = Path(root, "tasks")
    private val nextIdPath = Path(tasksDirectory, "next-id.txt")

    init {
        SystemFileSystem.createDirectories(tasksDirectory, mustCreate = false)
    }

    suspend fun insertOrReusePendingTask(segmentId: String): Long = mutex.withLock {
        val existing = readAllTasksLocked()
            .firstOrNull { it.segmentId == segmentId && it.status == TaskStatus.Pending }
        if (existing != null) {
            return@withLock existing.id
        }
        val id = nextIdLocked()
        writeTaskLocked(BackgroundAudioTranscriptionTask(id = id, segmentId = segmentId))
        id
    }

    override suspend fun insertTask(task: BackgroundAudioTranscriptionTask): Long = mutex.withLock {
        val id = if (task.id == 0L) nextIdLocked() else task.id
        writeTaskLocked(task.copy(id = id))
        id
    }

    override suspend fun getPendingTasks(): List<BackgroundAudioTranscriptionTask> = mutex.withLock {
        readAllTasksLocked()
            .filter { it.status == TaskStatus.Pending }
            .sortedBy { it.created }
    }

    override suspend fun updateLastSuccessfulStage(taskId: Long, stage: String) = mutex.withLock {
        updateTaskLocked(taskId) { it.copy(lastSuccessfulStage = stage) }
    }

    override suspend fun updateStatus(taskId: Long, status: TaskStatus) = mutex.withLock {
        updateTaskLocked(taskId) { it.copy(status = status) }
    }

    override suspend fun deleteTask(taskId: Long) = mutex.withLock {
        SystemFileSystem.delete(taskPath(taskId), mustExist = false)
    }

    override suspend fun deleteCompletedTasksAttemptedBefore(before: Instant) = mutex.withLock {
        readAllTasksLocked()
            .filter { it.status != TaskStatus.Pending && (it.lastAttempt ?: it.created) < before }
            .forEach { SystemFileSystem.delete(taskPath(it.id), mustExist = false) }
    }

    override suspend fun getTaskById(taskId: Long): BackgroundAudioTranscriptionTask? = mutex.withLock {
        readTaskLocked(taskId)
    }

    override suspend fun incrementAttempts(taskId: Long, currentTime: Instant) = mutex.withLock {
        updateTaskLocked(taskId) {
            it.copy(attempts = it.attempts + 1, lastAttempt = currentTime)
        }
    }

    private fun taskPath(taskId: Long): Path = Path(tasksDirectory, "$taskId.json")

    private fun nextIdLocked(): Long {
        val next = if (SystemFileSystem.exists(nextIdPath)) {
            SystemFileSystem.source(nextIdPath).buffered().use {
                it.readByteArray().decodeToString().trim().toLongOrNull()
            } ?: 1L
        } else {
            1L
        }
        writeAtomically(nextIdPath, (next + 1).toString())
        return next
    }

    private fun readAllTasksLocked(): List<BackgroundAudioTranscriptionTask> {
        return SystemFileSystem.list(tasksDirectory)
            .filter { it.name.endsWith(".json") }
            .mapNotNull { path ->
                runCatching {
                    json.decodeFromString<BackgroundAudioTranscriptionTask>(
                        SystemFileSystem.source(path).buffered().use { it.readByteArray().decodeToString() },
                    )
                }.getOrNull()
            }
    }

    private fun readTaskLocked(taskId: Long): BackgroundAudioTranscriptionTask? {
        val path = taskPath(taskId)
        if (!SystemFileSystem.exists(path)) {
            return null
        }
        return runCatching {
            json.decodeFromString<BackgroundAudioTranscriptionTask>(
                SystemFileSystem.source(path).buffered().use { it.readByteArray().decodeToString() },
            )
        }.getOrNull()
    }

    private fun updateTaskLocked(taskId: Long, transform: (BackgroundAudioTranscriptionTask) -> BackgroundAudioTranscriptionTask) {
        val current = readTaskLocked(taskId) ?: return
        writeTaskLocked(transform(current))
    }

    private fun writeTaskLocked(task: BackgroundAudioTranscriptionTask) {
        writeAtomically(taskPath(task.id), json.encodeToString(task))
    }

    private fun writeAtomically(path: Path, contents: String) {
        val tmp = Path(tasksDirectory, "${path.name}.tmp")
        SystemFileSystem.sink(tmp).buffered().use { sink ->
            sink.writeString(contents)
            sink.flush()
        }
        SystemFileSystem.atomicMove(tmp, path)
    }
}
