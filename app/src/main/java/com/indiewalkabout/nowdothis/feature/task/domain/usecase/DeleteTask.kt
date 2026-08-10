package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository

class DeleteTask(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler
) {
    suspend operator fun invoke(taskId: Int): DeletedTaskSnapshot {
        val snapshot = repository.deleteWithSnapshot(taskId)
        scheduler.cancel(taskId)
        return snapshot
    }
}
