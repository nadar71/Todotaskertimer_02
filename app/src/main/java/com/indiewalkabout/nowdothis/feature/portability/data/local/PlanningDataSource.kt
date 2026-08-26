package com.indiewalkabout.nowdothis.feature.portability.data.local

import androidx.room.withTransaction
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks
import com.indiewalkabout.nowdothis.feature.task.data.mapper.TaskEntityMapper
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority

interface PlanningDataStore {
    suspend fun snapshot(createdAtEpochMillis: Long): PlanningBackup

    suspend fun replaceAll(backup: PlanningBackup): Set<Int>
}

class PlanningDataSource(
    private val database: AppDatabase
) : PlanningDataStore {
    override suspend fun snapshot(createdAtEpochMillis: Long): PlanningBackup =
        database.withTransaction {
            val subtasksByTaskId = database.taskDao().getAllSubtaskEntities()
                .groupBy(SubtaskEntity::taskId)

            PlanningBackup(
                format = PlanningBackup.FORMAT,
                version = PlanningBackup.CURRENT_VERSION,
                createdAtEpochMillis = createdAtEpochMillis,
                categories = database.categoryDao().getAll()
                    .map(CategoryEntity::toPlanningCategory),
                tasks = database.taskDao().getAllTaskEntities().map { task ->
                    TaskEntityMapper.toDomain(
                        TaskWithSubtasks(task, subtasksByTaskId[task.id].orEmpty())
                    ).toPlanningTask()
                }
            )
        }

    override suspend fun replaceAll(backup: PlanningBackup): Set<Int> =
        database.withTransaction {
            val taskDao = database.taskDao()
            val categoryDao = database.categoryDao()
            val preRestoreTaskIds = taskDao.getAllTaskIds().toSet()

            val persistence = backup.tasks.map { task ->
                TaskEntityMapper.toEntities(task.toDomain())
            }

            taskDao.deleteAllTasks()
            categoryDao.deleteAll()

            if (backup.categories.isNotEmpty()) {
                categoryDao.insertAll(backup.categories.map(PlanningCategory::toCategoryEntity))
            }
            if (persistence.isNotEmpty()) {
                taskDao.insertTasks(persistence.map { (task, _) -> task })
            }
            val subtasks = persistence.flatMap { (_, subtasks) -> subtasks }
            if (subtasks.isNotEmpty()) {
                taskDao.insertRestoredSubtasks(subtasks)
            }

            preRestoreTaskIds
        }
}

private fun CategoryEntity.toPlanningCategory() = PlanningCategory(
    id = id,
    customName = customName,
    defaultKey = defaultKey,
    colorToken = colorToken,
    position = position,
    createdAt = createdAt
)

private fun Task.toPlanningTask() = PlanningTask(
    id = id,
    title = title,
    description = description,
    priority = priority.name,
    categoryId = categoryId,
    isCompleted = isCompleted,
    completedAt = completedAt,
    dueAt = dueAt,
    reminderAt = reminderAt,
    reminderStatus = reminderStatus.name,
    recurrenceRule = recurrenceRule,
    recurrenceEndAt = recurrenceEndAt,
    seriesId = seriesId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subtasks = subtasks.map(Subtask::toPlanningSubtask)
)

private fun Subtask.toPlanningSubtask() = PlanningSubtask(
    id = id,
    taskId = taskId,
    title = title,
    isCompleted = isCompleted,
    completedAt = completedAt,
    position = position
)

private fun PlanningCategory.toCategoryEntity() = CategoryEntity(
    id = id,
    customName = customName,
    defaultKey = defaultKey,
    colorToken = colorToken,
    position = position,
    createdAt = createdAt
)

private fun PlanningTask.toDomain() = Task(
    id = id,
    title = title,
    description = description,
    priority = enumValueOf<TaskPriority>(priority),
    categoryId = categoryId,
    isCompleted = isCompleted,
    completedAt = completedAt,
    dueAt = dueAt,
    reminderAt = reminderAt,
    reminderStatus = enumValueOf<ReminderStatus>(reminderStatus),
    recurrenceRule = recurrenceRule,
    recurrenceEndAt = recurrenceEndAt,
    seriesId = seriesId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subtasks = subtasks.map(PlanningSubtask::toDomain)
)

private fun PlanningSubtask.toDomain() = Subtask(
    id = id,
    taskId = taskId,
    title = title,
    isCompleted = isCompleted,
    completedAt = completedAt,
    position = position
)
