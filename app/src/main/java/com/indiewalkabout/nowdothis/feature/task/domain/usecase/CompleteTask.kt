package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionDecision
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.NextOccurrenceResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

sealed interface CompleteTaskResult {
    data object NotFound : CompleteTaskResult
    data object AlreadyCompleted : CompleteTaskResult
    data class Invalid(val reason: NextOccurrenceResult.Reason) : CompleteTaskResult

    data class Completed(
        val completed: Task,
        val nextOccurrence: Task?
    ) : CompleteTaskResult
}

class CompleteTask(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val calculateNextOccurrence: CalculateNextOccurrence,
    private val clock: AppClock
) {
    suspend operator fun invoke(taskId: Int): CompleteTaskResult {
        val now = clock.nowMillis()
        val result = repository.completeAtomically(taskId, now) { current, completedAt ->
            when (
                val occurrence = calculateNextOccurrence(
                    current,
                    completedAt = completedAt,
                    referenceAt = completedAt
                )
            ) {
                is NextOccurrenceResult.Next -> AtomicCompletionDecision.Create(
                    current.toNextOccurrence(
                        nextDueAt = occurrence.dueAt,
                        nextReminderAt = calculateNextOccurrence.nextReminderAt(
                            current,
                            occurrence.dueAt
                        ),
                        now = completedAt
                    )
                )
                NextOccurrenceResult.Ended -> AtomicCompletionDecision.CompleteOnly
                is NextOccurrenceResult.Invalid -> {
                    AtomicCompletionDecision.Invalid(occurrence.reason)
                }
            }
        }
        when (result) {
            AtomicCompletionResult.NotFound -> return CompleteTaskResult.NotFound
            AtomicCompletionResult.AlreadyCompleted -> return CompleteTaskResult.AlreadyCompleted
            is AtomicCompletionResult.Invalid -> return CompleteTaskResult.Invalid(result.reason)
            is AtomicCompletionResult.Completed -> Unit
        }

        return try {
            scheduler.cancel(result.completed.id)
            val persistedNext = result.nextOccurrence
            val finalNext = if (persistedNext?.reminderAt?.let { it > now } == true) {
                val expectedVersion = persistedNext.snapshotVersion()
                val isCurrent = repository.getTask(persistedNext.id)?.snapshotVersion() == expectedVersion
                if (!isCurrent) {
                    persistedNext
                } else {
                    val status = scheduler.schedule(
                        persistedNext.id,
                        requireNotNull(persistedNext.reminderAt)
                    ).toReminderStatus()
                    val statusUpdated = repository.updateReminderStatusIfCurrent(expectedVersion, status)
                    if (statusUpdated) {
                        persistedNext.copy(reminderStatus = status)
                    } else {
                        reconcileCurrentReminder(persistedNext.id, now)
                        persistedNext
                    }
                }
            } else {
                persistedNext
            }
            CompleteTaskResult.Completed(result.completed, finalNext)
        } catch (failure: Exception) {
            cleanupAfterPostCommitFailure(result)
            throw failure
        }
    }

    private suspend fun cleanupAfterPostCommitFailure(result: AtomicCompletionResult.Completed) {
        withContext(NonCancellable) {
            runCleanupBestEffort { scheduler.cancel(result.completed.id) }
            result.nextOccurrence?.id
                ?.takeIf { it != result.completed.id }
                ?.let { taskId -> runCleanupBestEffort { scheduler.cancel(taskId) } }
            runCleanupBestEffort { scheduler.reconcile() }
        }
    }

    private suspend fun runCleanupBestEffort(action: suspend () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            Unit
        }
    }

    private suspend fun reconcileCurrentReminder(taskId: Int, now: Long) {
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
        terminalReminderReconciliation(taskId)
    }

    private suspend fun terminalReminderReconciliation(taskId: Int) {
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

    private fun Task?.eligibleReminderAt(now: Long): Long? = this
        ?.takeUnless(Task::isCompleted)
        ?.reminderAt
        ?.takeIf { it > now }

    private fun Task.toNextOccurrence(
        nextDueAt: Long,
        nextReminderAt: Long?,
        now: Long
    ): Task {
        return copy(
            id = 0,
            isCompleted = false,
            completedAt = null,
            dueAt = nextDueAt,
            reminderAt = nextReminderAt,
            reminderStatus = if (nextReminderAt?.let { it > now } == true) {
                ReminderStatus.REQUESTED
            } else {
                ReminderStatus.NONE
            },
            createdAt = now,
            updatedAt = now,
            subtasks = subtasks.sortedBy { it.position }.map { subtask ->
                subtask.copy(
                    id = 0,
                    taskId = 0,
                    isCompleted = false,
                    completedAt = null
                )
            }
        )
    }

    private companion object {
        const val MAX_REMINDER_RECONCILIATION_ATTEMPTS = 8
    }
}
