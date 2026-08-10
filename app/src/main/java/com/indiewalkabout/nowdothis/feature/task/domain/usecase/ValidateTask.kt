package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task

enum class TaskValidationError {
    BLANK_TITLE,
    BLANK_DESCRIPTION,
    REMINDER_AFTER_DUE,
    RECURRENCE_WITHOUT_DUE_TIME,
    RECURRENCE_END_BEFORE_DUE,
    REMINDER_IN_PAST
}

class ValidateTask {
    operator fun invoke(task: Task, now: Long): List<TaskValidationError> = buildList {
        if (task.title.isBlank()) add(TaskValidationError.BLANK_TITLE)
        if (task.description.isBlank()) add(TaskValidationError.BLANK_DESCRIPTION)
        if (
            task.reminderAt != null &&
            task.dueAt != null &&
            task.reminderAt > task.dueAt
        ) {
            add(TaskValidationError.REMINDER_AFTER_DUE)
        }
        if (task.recurrence != RecurrenceType.NONE && task.dueAt == null) {
            add(TaskValidationError.RECURRENCE_WITHOUT_DUE_TIME)
        }
        if (
            task.recurrenceEndAt != null &&
            task.dueAt != null &&
            task.recurrenceEndAt < task.dueAt
        ) {
            add(TaskValidationError.RECURRENCE_END_BEFORE_DUE)
        }
        if (task.reminderAt != null && task.reminderAt < now) {
            add(TaskValidationError.REMINDER_IN_PAST)
        }
    }
}
