package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import java.util.UUID

sealed interface SaveTaskResult {
    data class Saved(
        val taskId: Int,
        val reminderStatus: ReminderStatus
    ) : SaveTaskResult

    data class Invalid(val errors: List<TaskValidationError>) : SaveTaskResult
}

class SaveTask(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val validateTask: ValidateTask,
    private val clock: AppClock,
    private val seriesIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    suspend operator fun invoke(task: Task): SaveTaskResult {
        val now = clock.nowMillis()
        val errors = validateTask(task, now)
        if (errors.isNotEmpty()) return SaveTaskResult.Invalid(errors)

        val existing = task.id.takeIf { it != 0 }?.let { repository.getTask(it) }
        val hasFutureReminder = task.reminderAt?.let { it > now } == true
        val saved = task.copy(
            createdAt = if (task.id == 0) now else existing?.createdAt ?: task.createdAt,
            updatedAt = now,
            seriesId = existing?.seriesId
                ?: task.seriesId
                ?: if (task.recurrence != RecurrenceType.NONE) seriesIdFactory() else null,
            reminderStatus = if (hasFutureReminder) {
                ReminderStatus.REQUESTED
            } else {
                ReminderStatus.NONE
            }
        )
        val taskId = repository.upsert(saved)

        if (!hasFutureReminder) {
            scheduler.cancel(taskId)
            return SaveTaskResult.Saved(taskId, ReminderStatus.NONE)
        }

        val status = scheduler.schedule(taskId, requireNotNull(saved.reminderAt))
            .toReminderStatus()
        repository.updateReminderStatus(taskId, status)
        return SaveTaskResult.Saved(taskId, status)
    }
}

internal fun ReminderScheduleResult.toReminderStatus(): ReminderStatus = when (this) {
    ReminderScheduleResult.EXACT,
    ReminderScheduleResult.INEXACT -> ReminderStatus.SCHEDULED
    ReminderScheduleResult.FAILED -> ReminderStatus.UNAVAILABLE
}
