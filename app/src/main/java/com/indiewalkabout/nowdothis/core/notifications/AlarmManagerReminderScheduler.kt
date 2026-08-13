package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderReconcileResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class AlarmManagerReminderScheduler @Inject constructor(
    private val gateway: AlarmGateway,
    private val taskRepository: TaskRepository,
    private val clock: AppClock
) : ReminderScheduler {
    override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
        if (gateway.canScheduleExact && gateway.setExact(taskId, triggerAt)) {
            return ReminderScheduleResult.EXACT
        }
        return if (gateway.setInexact(taskId, triggerAt)) {
            ReminderScheduleResult.INEXACT
        } else {
            ReminderScheduleResult.FAILED
        }
    }

    override suspend fun cancel(taskId: Int) = gateway.cancel(taskId)

    override suspend fun reconcile() {
        reconcileWithResult()
    }

    override suspend fun reconcileWithResult(): ReminderReconcileResult {
        val now = clock.nowMillis()
        val statuses = taskRepository.futureReminders(now)
            .filter { it.isEligibleAt(now) }
            .map { task -> reconcile(task) }
        return if (ReminderStatus.UNAVAILABLE in statuses) {
            ReminderReconcileResult.SOME_UNAVAILABLE
        } else {
            ReminderReconcileResult.SUCCESS
        }
    }

    private suspend fun reconcile(task: Task): ReminderStatus {
        val status = try {
            when (schedule(task.id, requireNotNull(task.reminderAt))) {
                ReminderScheduleResult.EXACT,
                ReminderScheduleResult.INEXACT -> ReminderStatus.SCHEDULED
                ReminderScheduleResult.FAILED -> ReminderStatus.UNAVAILABLE
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ReminderStatus.UNAVAILABLE
        }

        try {
            taskRepository.updateReminderStatus(task.id, status)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A single stale or failed row must not block the remaining reminders.
        }
        return status
    }

    private fun Task.isEligibleAt(now: Long): Boolean =
        !isCompleted &&
            reminderAt?.let { it > now } == true &&
            reminderStatus in RECONCILABLE_STATUSES

    private companion object {
        val RECONCILABLE_STATUSES = setOf(
            ReminderStatus.REQUESTED,
            ReminderStatus.SCHEDULED,
            ReminderStatus.UNAVAILABLE
        )
    }
}
