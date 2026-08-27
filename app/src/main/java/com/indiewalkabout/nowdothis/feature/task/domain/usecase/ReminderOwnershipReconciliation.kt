package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

internal suspend fun reconcileCurrentReminderOwner(
    repository: TaskRepository,
    scheduler: ReminderScheduler,
    taskId: Int,
    now: Long
) {
    repeat(MAX_REMINDER_RECONCILIATION_ATTEMPTS) { attempt ->
        val current = repository.getTask(taskId)
        val expectedVersion = current?.snapshotVersion()
        val reminderAt = current.eligibleReminderAt(now)
        val converged = if (reminderAt == null) {
            scheduler.cancel(taskId)
            val verified = repository.getTask(taskId)
            verified?.snapshotVersion() == expectedVersion &&
                verified.eligibleReminderAt(now) == null
        } else {
            val status = scheduler.schedule(taskId, reminderAt).toReminderStatus()
            repository.updateReminderStatusIfCurrent(
                requireNotNull(expectedVersion),
                status
            ) && repository.getTask(taskId).let { verified ->
                val expectedPostUpdate = expectedVersion.copy(reminderStatus = status)
                verified?.snapshotVersion() == expectedPostUpdate &&
                    verified.eligibleReminderAt(now) == reminderAt &&
                    verified?.reminderStatus == status
            }
        }
        if (converged) return
        if (attempt < MAX_REMINDER_RECONCILIATION_ATTEMPTS - 1) yield()
    }
    terminalReminderReconciliation(scheduler, taskId)
}

internal fun Task?.eligibleReminderAt(now: Long): Long? = this
    ?.takeUnless(Task::isCompleted)
    ?.reminderAt
    ?.takeIf { it > now }

internal fun TaskSnapshotVersion.eligibleReminderAt(now: Long): Long? = this
    .takeUnless(TaskSnapshotVersion::isCompleted)
    ?.reminderAt
    ?.takeIf { it > now }

private suspend fun terminalReminderReconciliation(
    scheduler: ReminderScheduler,
    taskId: Int
) {
    runSchedulerBestEffort { scheduler.cancel(taskId) }
    runSchedulerBestEffort { scheduler.reconcile() }
}

private suspend fun runSchedulerBestEffort(action: suspend () -> Unit) {
    try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Unit
    }
}

private const val MAX_REMINDER_RECONCILIATION_ATTEMPTS = 8
