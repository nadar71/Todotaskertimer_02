package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository

data class RestoreDeletedTaskResult(
    val taskId: Int,
    val reminderStatus: ReminderStatus
)

class RestoreDeletedTask(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val clock: AppClock
) {
    suspend operator fun invoke(snapshot: DeletedTaskSnapshot): RestoreDeletedTaskResult {
        val restoredId = repository.restore(snapshot)
        val task = snapshot.task
        val reminderAt = task.reminderAt
        val shouldSchedule =
            !task.isCompleted &&
                task.reminderStatus != ReminderStatus.NONE &&
                reminderAt != null &&
                reminderAt > clock.nowMillis()
        if (!shouldSchedule) {
            if (task.reminderStatus != ReminderStatus.NONE) {
                repository.updateReminderStatus(restoredId, ReminderStatus.NONE)
            }
            return RestoreDeletedTaskResult(restoredId, ReminderStatus.NONE)
        }

        val status = scheduler.schedule(restoredId, requireNotNull(reminderAt)).toReminderStatus()
        repository.updateReminderStatus(restoredId, status)
        return RestoreDeletedTaskResult(restoredId, status)
    }
}
