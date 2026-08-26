package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import java.util.UUID

sealed interface SaveTaskResult {
    data class Saved(
        val taskId: Int,
        val reminderStatus: ReminderStatus,
        val version: TaskSnapshotVersion
    ) : SaveTaskResult

    data class Invalid(val errors: List<TaskValidationError>) : SaveTaskResult
    data object Conflict : SaveTaskResult
}

class SaveTask(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val validateTask: ValidateTask,
    private val clock: AppClock,
    private val seriesIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    suspend operator fun invoke(
        task: Task,
        expectedVersion: TaskSnapshotVersion? = task
            .takeIf { it.id != 0 }
            ?.snapshotVersion()
    ): SaveTaskResult {
        val now = clock.nowMillis()
        val errors = validateTask(task, now)
        if (errors.isNotEmpty()) return SaveTaskResult.Invalid(errors)

        val existing = task.id.takeIf { it != 0 }?.let { repository.getTask(it) }
        if (
            task.id != 0 &&
            (expectedVersion == null || existing?.snapshotVersion() != expectedVersion)
        ) {
            return SaveTaskResult.Conflict
        }
        val hasFutureReminder = task.reminderAt?.let { it > now } == true
        val saved = task.copy(
            createdAt = if (task.id == 0) now else existing?.createdAt ?: task.createdAt,
            updatedAt = existing?.updatedAt?.let { maxOf(now, it + 1) } ?: now,
            seriesId = existing?.seriesId
                ?: task.seriesId
                ?: if (task.recurrenceRule !is RecurrenceRule.None) seriesIdFactory() else null,
            reminderStatus = if (hasFutureReminder) {
                ReminderStatus.REQUESTED
            } else {
                ReminderStatus.NONE
            }
        )
        val taskId = if (existing == null) {
            repository.upsert(saved)
        } else {
            val updated = repository.updateIfUnchanged(saved, requireNotNull(expectedVersion))
            if (!updated) return SaveTaskResult.Conflict
            saved.id
        }

        if (!hasFutureReminder) {
            scheduler.cancel(taskId)
            return SaveTaskResult.Saved(
                taskId = taskId,
                reminderStatus = ReminderStatus.NONE,
                version = saved.copy(id = taskId).snapshotVersion()
            )
        }

        val status = scheduler.schedule(taskId, requireNotNull(saved.reminderAt))
            .toReminderStatus()
        repository.updateReminderStatus(taskId, status)
        return SaveTaskResult.Saved(
            taskId = taskId,
            reminderStatus = status,
            version = saved.copy(
                id = taskId,
                reminderStatus = status
            ).snapshotVersion()
        )
    }
}

internal fun ReminderScheduleResult.toReminderStatus(): ReminderStatus = when (this) {
    ReminderScheduleResult.EXACT,
    ReminderScheduleResult.INEXACT -> ReminderStatus.SCHEDULED
    ReminderScheduleResult.FAILED -> ReminderStatus.UNAVAILABLE
}
