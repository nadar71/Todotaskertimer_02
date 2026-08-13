package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupValidationResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentTooLarge
import com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityError
import com.indiewalkabout.nowdothis.feature.portability.domain.model.UnsupportedFutureVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority

class BackupValidator {
    fun validate(
        backup: PlanningBackup,
        documentSizeBytes: Long
    ): BackupValidationResult {
        val error = validateError(backup, documentSizeBytes)
        return if (error == null) {
            BackupValidationResult.Valid(
                BackupSummary(
                    createdAtEpochMillis = backup.createdAtEpochMillis,
                    categoryCount = backup.categories.size,
                    taskCount = backup.tasks.size,
                    completedTaskCount = backup.tasks.count(PlanningTask::isCompleted),
                    subtaskCount = backup.tasks.sumOf { it.subtasks.size }
                )
            )
        } else {
            BackupValidationResult.Invalid(error)
        }
    }

    private fun validateError(backup: PlanningBackup, documentSizeBytes: Long): PortabilityError? {
        if (documentSizeBytes > MAX_DOCUMENT_SIZE_BYTES) return DocumentTooLarge
        if (backup.format != BackupDocumentV1.FORMAT || backup.version <= 0) return InvalidBackup
        if (backup.version > BackupDocumentV1.VERSION) return UnsupportedFutureVersion(backup.version)

        val categoryIds = mutableSetOf<Int>()
        val categoryPositions = mutableSetOf<Int>()
        if (backup.categories.any { category ->
                category.id <= 0 ||
                    !categoryIds.add(category.id) ||
                    category.position < 0 ||
                    !categoryPositions.add(category.position) ||
                    !categoryNameIsValid(category) ||
                    !category.colorToken.isStableName(CategoryColor.entries) ||
                    (category.defaultKey != null && !category.defaultKey.isStableName(DefaultCategoryKey.entries))
            }) {
            return InvalidBackup
        }

        val taskIds = mutableSetOf<Int>()
        val subtaskIds = mutableSetOf<Int>()
        for (task in backup.tasks) {
            if (task.id <= 0 ||
                !taskIds.add(task.id) ||
                task.title.isBlank() ||
                task.categoryId != null && task.categoryId !in categoryIds ||
                !task.priority.isStableName(TaskPriority.entries) ||
                !task.reminderStatus.isStableName(ReminderStatus.entries) ||
                !task.recurrence.isStableName(RecurrenceType.entries) ||
                !completionIsConsistent(task.isCompleted, task.completedAt) ||
                task.recurrenceEndAt != null && task.dueAt != null && task.recurrenceEndAt < task.dueAt
            ) {
                return InvalidBackup
            }

            val subtaskPositions = mutableSetOf<Int>()
            for (subtask in task.subtasks) {
                if (subtask.id <= 0 ||
                    !subtaskIds.add(subtask.id) ||
                    subtask.taskId != task.id ||
                    subtask.title.isBlank() ||
                    subtask.position < 0 ||
                    !subtaskPositions.add(subtask.position) ||
                    !completionIsConsistent(subtask.isCompleted, subtask.completedAt)
                ) {
                    return InvalidBackup
                }
            }
        }
        return null
    }

    private fun categoryNameIsValid(category: PlanningCategory): Boolean = when {
        category.defaultKey != null -> category.customName == null
        else -> !category.customName.isNullOrBlank()
    }

    private fun completionIsConsistent(isCompleted: Boolean, completedAt: Long?): Boolean =
        (isCompleted && completedAt != null) || (!isCompleted && completedAt == null)

    private fun String.isStableName(values: Iterable<Enum<*>>): Boolean =
        values.any { value -> value.name == this }

    companion object {
        const val MAX_DOCUMENT_SIZE_BYTES: Long = 10L * 1024 * 1024
    }
}
