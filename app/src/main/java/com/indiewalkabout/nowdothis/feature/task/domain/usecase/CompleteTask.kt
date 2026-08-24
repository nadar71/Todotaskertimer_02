package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository

sealed interface CompleteTaskResult {
    data object NotFound : CompleteTaskResult
    data object AlreadyCompleted : CompleteTaskResult

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
        val result = repository.completeAtomically(taskId, now) { current ->
            calculateNextOccurrence(current)?.let { nextDueAt ->
                current.toNextOccurrence(nextDueAt, now)
            }
        }
        when (result) {
            AtomicCompletionResult.NotFound -> return CompleteTaskResult.NotFound
            AtomicCompletionResult.AlreadyCompleted -> return CompleteTaskResult.AlreadyCompleted
            is AtomicCompletionResult.Completed -> Unit
        }

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
                    scheduler.cancel(persistedNext.id)
                    persistedNext
                }
            }
        } else {
            persistedNext
        }
        return CompleteTaskResult.Completed(result.completed, finalNext)
    }

    private fun Task.toNextOccurrence(nextDueAt: Long, now: Long): Task {
        val nextReminderAt = reminderAt?.plus(nextDueAt - requireNotNull(dueAt))
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
}
