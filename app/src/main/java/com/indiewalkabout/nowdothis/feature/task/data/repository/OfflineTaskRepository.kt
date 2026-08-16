package com.indiewalkabout.nowdothis.feature.task.data.repository

import androidx.room.withTransaction
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskDao
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks
import com.indiewalkabout.nowdothis.feature.task.data.mapper.TaskEntityMapper
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.repository.CompletionHistoryReader
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskScheduleReader
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.TaskSectionClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineTaskRepository @Inject constructor(
    private val database: AppDatabase,
    private val taskDao: TaskDao
) : TaskRepository, TaskScheduleReader, CompletionHistoryReader {
    override fun observeTask(taskId: Int): Flow<Task?> =
        taskDao.observeTask(taskId).map { it?.let(TaskEntityMapper::toDomain) }

    override fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections> =
        taskDao.observeSectionCandidates(
            bounds.startInclusive,
            bounds.endExclusive,
            filter.query,
            filter.categoryId
        )
            .map { relations ->
                TaskSectionClassifier.classify(
                    tasks = relations.toDomainTasks(),
                    bounds = bounds,
                    sort = filter.sort
                )
            }

    override suspend fun getTask(taskId: Int): Task? =
        taskDao.getTask(taskId)?.let(TaskEntityMapper::toDomain)

    override suspend fun upsert(task: Task): Int = database.withTransaction {
        upsertInTransaction(task)
    }

    override suspend fun completeAtomically(
        taskId: Int,
        completedAt: Long,
        next: Task?
    ): AtomicCompletionResult? = database.withTransaction {
        val current = taskDao.getTask(taskId) ?: return@withTransaction null
        if (current.task.isCompleted) return@withTransaction null
        val completedTask = current.task.copy(
            isCompleted = true,
            completedAt = completedAt,
            updatedAt = completedAt
        )
        val completedSubtasks = current.subtasks.map { subtask ->
            if (subtask.isCompleted) {
                subtask
            } else {
                subtask.copy(isCompleted = true, completedAt = completedAt)
            }
        }
        taskDao.updateTask(completedTask)
        taskDao.replaceSubtasks(taskId, completedSubtasks)
        val persistedNext = next?.let { nextOccurrence ->
            val nextId = upsertInTransaction(
                nextOccurrence.copy(
                    id = 0,
                    subtasks = nextOccurrence.subtasks.map { it.copy(id = 0, taskId = 0) }
                )
            )
            TaskEntityMapper.toDomain(requireNotNull(taskDao.getTask(nextId)))
        }
        AtomicCompletionResult(
            completed = TaskEntityMapper.toDomain(
                TaskWithSubtasks(completedTask, completedSubtasks)
            ),
            nextOccurrence = persistedNext
        )
    }

    override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot =
        database.withTransaction {
            val relation = requireNotNull(taskDao.getTask(taskId)) { "Task $taskId not found" }
            taskDao.deleteTaskById(taskId)
            DeletedTaskSnapshot(TaskEntityMapper.toDomain(relation))
        }

    override suspend fun deleteAll(): List<Int> = database.withTransaction {
        val reminderTaskIds = taskDao.observeAllTaskEntities().first()
            .filter { entity ->
                !entity.isCompleted &&
                    entity.reminderAt != null &&
                    entity.reminderStatus in CANCELLABLE_REMINDER_STATUSES
            }
            .map { it.id }
        taskDao.deleteAllTasks()
        reminderTaskIds
    }

    override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = database.withTransaction {
        val original = snapshot.task
        if (original.id != 0 && taskDao.getTask(original.id) == null) {
            val (task, subtasks) = TaskEntityMapper.toEntities(original)
            val requestedSubtaskIds = subtasks.map { it.id }.filter { it != 0 }
            val occupiedSubtaskIds = if (requestedSubtaskIds.isEmpty()) {
                emptySet()
            } else {
                taskDao.existingSubtaskIds(requestedSubtaskIds).toSet()
            }
            val restorableSubtasks = subtasks.map { subtask ->
                if (subtask.id in occupiedSubtaskIds) subtask.copy(id = 0) else subtask
            }
            taskDao.insertTask(task)
            if (restorableSubtasks.isNotEmpty()) {
                taskDao.insertRestoredSubtasks(restorableSubtasks)
            }
            original.id
        } else {
            upsertInTransaction(
                original.copy(
                    id = 0,
                    subtasks = original.subtasks.map { it.copy(id = 0, taskId = 0) }
                )
            )
        }
    }

    override suspend fun deleteCompleted(taskId: Int) = database.withTransaction {
        val task = taskDao.getTask(taskId)?.task
        if (task?.isCompleted == true) taskDao.deleteTaskById(taskId)
    }

    override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) =
        database.withTransaction {
            val task = taskDao.getTask(taskId)?.task ?: return@withTransaction
            taskDao.updateTask(task.copy(reminderStatus = status.name))
        }

    override suspend fun futureReminders(after: Long): List<Task> = database.withTransaction {
        taskDao.observeAllTaskEntities().first()
            .filter { entity ->
                !entity.isCompleted &&
                    entity.reminderAt?.let { it > after } == true &&
                    entity.reminderStatus in RECONCILABLE_REMINDER_STATUSES
            }
            .mapNotNull { taskDao.getTask(it.id) }
            .map(TaskEntityMapper::toDomain)
            .sortedBy { it.reminderAt }
    }

    override fun observeMonth(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<Task>> = taskDao.observeMonth(startInclusive, endExclusive)
        .map { it.toDomainTasks() }

    override fun observeDay(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<Task>> = taskDao.observeMonth(startInclusive, endExclusive)
        .map { it.toDomainTasks() }

    override fun observeHistory(before: Long, filter: TaskFilter): Flow<List<Task>> =
        taskDao.observeHistory(before, filter.query, filter.categoryId)
            .map { it.toDomainTasks() }

    private suspend fun upsertInTransaction(task: Task): Int {
        val (entity, subtasks) = TaskEntityMapper.toEntities(task)
        val taskId = if (entity.id == 0) {
            taskDao.insertTask(entity).toInt()
        } else if (taskDao.getTask(entity.id) == null) {
            taskDao.insertTask(entity).toInt()
        } else {
            taskDao.updateTask(entity)
            entity.id
        }
        taskDao.replaceSubtasks(taskId, subtasks)
        return taskId
    }

    private fun List<TaskWithSubtasks>.toDomainTasks(): List<Task> =
        map(TaskEntityMapper::toDomain)

    private companion object {
        val CANCELLABLE_REMINDER_STATUSES = setOf(
            ReminderStatus.REQUESTED.name,
            ReminderStatus.SCHEDULED.name
        )
        val RECONCILABLE_REMINDER_STATUSES =
            CANCELLABLE_REMINDER_STATUSES + ReminderStatus.UNAVAILABLE.name
    }
}
