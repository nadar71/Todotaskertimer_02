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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
        val committedVersion = saved.copy(id = taskId).snapshotVersion()

        val status = try {
            finalizeReminder(committedVersion, now)
        } catch (failure: Exception) {
            cleanupAfterPostCommitFailure(taskId, now)
            throw failure
        }
        if (status == null) return SaveTaskResult.Conflict
        return SaveTaskResult.Saved(
            taskId = taskId,
            reminderStatus = status,
            version = committedVersion.copy(reminderStatus = status)
        )
    }

    private suspend fun finalizeReminder(
        committedVersion: TaskSnapshotVersion,
        now: Long
    ): ReminderStatus? {
        val taskId = committedVersion.id
        if (repository.getTask(taskId)?.snapshotVersion() != committedVersion) {
            cancelAndReconcileCurrentOwner(taskId, now)
            return null
        }

        val reminderAt = committedVersion.eligibleReminderAt(now)
        if (reminderAt == null) {
            scheduler.cancel(taskId)
            val verified = repository.getTask(taskId)
            if (
                verified?.snapshotVersion() == committedVersion &&
                verified.eligibleReminderAt(now) == null
            ) {
                return ReminderStatus.NONE
            }
            cancelAndReconcileCurrentOwner(taskId, now)
            return null
        }

        val status = scheduler.schedule(taskId, reminderAt).toReminderStatus()
        val statusUpdated = repository.updateReminderStatusIfCurrent(committedVersion, status)
        val expectedPostUpdate = committedVersion.copy(reminderStatus = status)
        val verified = repository.getTask(taskId)
        if (
            statusUpdated &&
            verified?.snapshotVersion() == expectedPostUpdate &&
            verified.eligibleReminderAt(now) == reminderAt
        ) {
            return status
        }
        cancelAndReconcileCurrentOwner(taskId, now)
        return null
    }

    private suspend fun cancelAndReconcileCurrentOwner(taskId: Int, now: Long) {
        scheduler.cancel(taskId)
        reconcileCurrentReminderOwner(repository, scheduler, taskId, now)
    }

    private suspend fun cleanupAfterPostCommitFailure(taskId: Int, now: Long) {
        withContext(NonCancellable) {
            runCleanupBestEffort { scheduler.cancel(taskId) }
            runCleanupBestEffort {
                reconcileCurrentReminderOwner(repository, scheduler, taskId, now)
            }
        }
    }

    private suspend fun runCleanupBestEffort(action: suspend () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            Unit
        }
    }
}

internal fun ReminderScheduleResult.toReminderStatus(): ReminderStatus = when (this) {
    ReminderScheduleResult.EXACT,
    ReminderScheduleResult.INEXACT -> ReminderStatus.SCHEDULED
    ReminderScheduleResult.FAILED -> ReminderStatus.UNAVAILABLE
}
