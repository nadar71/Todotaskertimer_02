package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository

class DeleteAllTasks(
    private val repository: TaskRepository,
    private val scheduler: ReminderScheduler
) {
    suspend operator fun invoke() {
        repository.deleteAll().forEach { taskId -> scheduler.cancel(taskId) }
    }
}
