package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderReconcileResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

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
            .map { it.snapshotVersion() }
            .filter { it.eligibleReminderAt(now) != null }
            .map { version -> reconcile(version, now) }
        return if (ReminderStatus.UNAVAILABLE in statuses) {
            ReminderReconcileResult.SOME_UNAVAILABLE
        } else {
            ReminderReconcileResult.SUCCESS
        }
    }

    private suspend fun reconcile(
        initialVersion: TaskSnapshotVersion,
        now: Long
    ): ReminderStatus {
        val taskId = initialVersion.id
        return try {
            var expectedVersion: TaskSnapshotVersion? = initialVersion
            repeat(MAX_RECONCILIATION_ATTEMPTS) { attempt ->
                val authoritativeVersion = taskRepository.getTask(taskId)?.snapshotVersion()
                if (authoritativeVersion != expectedVersion) {
                    cancel(taskId)
                    expectedVersion = authoritativeVersion
                } else {
                    val reminderAt = expectedVersion.eligibleReminderAt(now)
                    if (reminderAt == null) {
                        cancel(taskId)
                        val verifiedVersion = taskRepository.getTask(taskId)?.snapshotVersion()
                        if (
                            verifiedVersion == expectedVersion &&
                            verifiedVersion.eligibleReminderAt(now) == null
                        ) {
                            return ReminderStatus.NONE
                        }
                        expectedVersion = verifiedVersion
                    } else {
                        val schedulingVersion = requireNotNull(expectedVersion)
                        val status = schedule(schedulingVersion)
                        val statusUpdated = taskRepository.updateReminderStatusIfCurrent(
                            schedulingVersion,
                            status
                        )
                        val expectedPostUpdate = schedulingVersion.copy(reminderStatus = status)
                        val verifiedVersion = taskRepository.getTask(taskId)?.snapshotVersion()
                        if (
                            statusUpdated &&
                            verifiedVersion == expectedPostUpdate &&
                            verifiedVersion.eligibleReminderAt(now) == reminderAt
                        ) {
                            return status
                        }
                        cancel(taskId)
                        expectedVersion = verifiedVersion
                    }
                }
                if (attempt < MAX_RECONCILIATION_ATTEMPTS - 1) yield()
            }
            cancel(taskId)
            ReminderStatus.UNAVAILABLE
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            cancelBestEffort(taskId)
            ReminderStatus.UNAVAILABLE
        }
    }

    private suspend fun schedule(version: TaskSnapshotVersion): ReminderStatus {
        return try {
            when (schedule(version.id, requireNotNull(version.reminderAt))) {
                ReminderScheduleResult.EXACT,
                ReminderScheduleResult.INEXACT -> ReminderStatus.SCHEDULED
                ReminderScheduleResult.FAILED -> ReminderStatus.UNAVAILABLE
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ReminderStatus.UNAVAILABLE
        }
    }

    private suspend fun cancelBestEffort(taskId: Int) {
        try {
            cancel(taskId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Unit
        }
    }

    private fun TaskSnapshotVersion?.eligibleReminderAt(now: Long): Long? = this
        ?.takeUnless { it.isCompleted }
        ?.takeIf { it.reminderStatus in RECONCILABLE_STATUSES }
        ?.reminderAt
        ?.takeIf { it > now }

    private companion object {
        const val MAX_RECONCILIATION_ATTEMPTS = 8

        val RECONCILABLE_STATUSES = setOf(
            ReminderStatus.REQUESTED,
            ReminderStatus.SCHEDULED,
            ReminderStatus.UNAVAILABLE
        )
    }
}
