package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import kotlinx.serialization.Serializable

@Serializable
internal data class BackupDocumentV1(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAtEpochMillis: Long,
    val categories: List<BackupCategoryV1>,
    val tasks: List<BackupTaskV1>
) {
    fun toDomain(): PlanningBackup = PlanningBackup(
        format = format,
        version = version,
        createdAtEpochMillis = createdAtEpochMillis,
        categories = categories.map(BackupCategoryV1::toDomain),
        tasks = tasks.map(BackupTaskV1::toDomain)
    )

    companion object {
        const val FORMAT = "now-do-this-backup"
        const val VERSION = 1

        fun fromDomain(backup: PlanningBackup): BackupDocumentV1 = BackupDocumentV1(
            format = backup.format,
            version = backup.version,
            createdAtEpochMillis = backup.createdAtEpochMillis,
            categories = backup.categories
                .sortedWith(compareBy(PlanningCategory::position, PlanningCategory::id))
                .map(BackupCategoryV1::fromDomain),
            tasks = backup.tasks
                .sortedBy(PlanningTask::id)
                .map(BackupTaskV1::fromDomain)
        )
    }
}

@Serializable
internal data class BackupCategoryV1(
    val id: Int,
    val customName: String?,
    val defaultKey: String?,
    val colorToken: String,
    val position: Int,
    val createdAt: Long
) {
    fun toDomain(): PlanningCategory = PlanningCategory(id, customName, defaultKey, colorToken, position, createdAt)

    companion object {
        fun fromDomain(category: PlanningCategory) = BackupCategoryV1(
            id = category.id,
            customName = category.customName,
            defaultKey = category.defaultKey,
            colorToken = category.colorToken,
            position = category.position,
            createdAt = category.createdAt
        )
    }
}

@Serializable
internal data class BackupTaskV1(
    val id: Int,
    val title: String,
    val description: String,
    val priority: String,
    val categoryId: Int?,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val dueAt: Long?,
    val reminderAt: Long?,
    val reminderStatus: String,
    val recurrence: String,
    val recurrenceEndAt: Long?,
    val seriesId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val subtasks: List<BackupSubtaskV1>
) {
    fun toDomain(): PlanningTask = PlanningTask(
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
        subtasks = subtasks.map(BackupSubtaskV1::toDomain)
    )

    companion object {
        fun fromDomain(task: PlanningTask) = BackupTaskV1(
            id = task.id,
            title = task.title,
            description = task.description,
            priority = task.priority,
            categoryId = task.categoryId,
            isCompleted = task.isCompleted,
            completedAt = task.completedAt,
            dueAt = task.dueAt,
            reminderAt = task.reminderAt,
            reminderStatus = task.reminderStatus,
            recurrence = task.recurrence,
            recurrenceEndAt = task.recurrenceEndAt,
            seriesId = task.seriesId,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            subtasks = task.subtasks
                .sortedWith(compareBy(PlanningSubtask::position, PlanningSubtask::id))
                .map(BackupSubtaskV1::fromDomain)
        )
    }
}

@Serializable
internal data class BackupSubtaskV1(
    val id: Int,
    val taskId: Int,
    val title: String,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val position: Int
) {
    fun toDomain(): PlanningSubtask = PlanningSubtask(id, taskId, title, isCompleted, completedAt, position)

    companion object {
        fun fromDomain(subtask: PlanningSubtask) = BackupSubtaskV1(
            id = subtask.id,
            taskId = subtask.taskId,
            title = subtask.title,
            isCompleted = subtask.isCompleted,
            completedAt = subtask.completedAt,
            position = subtask.position
        )
    }
}
