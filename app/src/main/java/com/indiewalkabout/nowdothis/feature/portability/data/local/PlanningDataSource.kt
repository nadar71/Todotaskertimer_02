package com.indiewalkabout.nowdothis.feature.portability.data.local

import androidx.room.withTransaction
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupDocumentV1
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity

interface PlanningDataStore {
    suspend fun snapshot(createdAtEpochMillis: Long): PlanningBackup

    suspend fun replaceAll(backup: PlanningBackup): Set<Int>
}

class PlanningDataSource(
    private val database: AppDatabase
) : PlanningDataStore {
    override suspend fun snapshot(createdAtEpochMillis: Long): PlanningBackup = database.withTransaction {
        val subtasksByTaskId = database.taskDao().getAllSubtaskEntities()
            .groupBy(SubtaskEntity::taskId)

        PlanningBackup(
            format = BackupDocumentV1.FORMAT,
            version = BackupDocumentV1.VERSION,
            createdAtEpochMillis = createdAtEpochMillis,
            categories = database.categoryDao().getAll().map(CategoryEntity::toPlanningCategory),
            tasks = database.taskDao().getAllTaskEntities().map { task ->
                task.toPlanningTask(
                    subtasks = subtasksByTaskId[task.id].orEmpty().map(SubtaskEntity::toPlanningSubtask)
                )
            }
        )
    }

    override suspend fun replaceAll(backup: PlanningBackup): Set<Int> = database.withTransaction {
        val taskDao = database.taskDao()
        val categoryDao = database.categoryDao()
        val preRestoreTaskIds = taskDao.getAllTaskIds().toSet()

        taskDao.deleteAllTasks()
        categoryDao.deleteAll()

        if (backup.categories.isNotEmpty()) {
            categoryDao.insertAll(backup.categories.map(PlanningCategory::toCategoryEntity))
        }
        if (backup.tasks.isNotEmpty()) {
            taskDao.insertTasks(backup.tasks.map(PlanningTask::toTaskEntity))
        }
        val subtasks = backup.tasks.flatMap { task -> task.subtasks.map(PlanningSubtask::toSubtaskEntity) }
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

private fun TaskEntity.toPlanningTask(subtasks: List<PlanningSubtask>) = PlanningTask(
    id = id,
    title = title,
    description = description,
    priority = priority,
    categoryId = categoryId,
    isCompleted = isCompleted,
    completedAt = completedAt,
    dueAt = dueAt,
    reminderAt = reminderAt,
    reminderStatus = reminderStatus,
    recurrence = recurrence,
    recurrenceEndAt = recurrenceEndAt,
    seriesId = seriesId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subtasks = subtasks
)

private fun SubtaskEntity.toPlanningSubtask() = PlanningSubtask(
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

private fun PlanningTask.toTaskEntity() = TaskEntity(
    id = id,
    title = title,
    description = description,
    priority = priority,
    categoryId = categoryId,
    isCompleted = isCompleted,
    completedAt = completedAt,
    dueAt = dueAt,
    reminderAt = reminderAt,
    reminderStatus = reminderStatus,
    recurrence = recurrence,
    recurrenceEndAt = recurrenceEndAt,
    seriesId = seriesId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun PlanningSubtask.toSubtaskEntity() = SubtaskEntity(
    id = id,
    taskId = taskId,
    title = title,
    isCompleted = isCompleted,
    completedAt = completedAt,
    position = position
)
