package com.indiewalkabout.nowdothis.feature.task.domain.repository

import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTask(taskId: Int): Flow<Task?>
    fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections>
    suspend fun getTask(taskId: Int): Task?
    suspend fun upsert(task: Task): Int
    suspend fun completeAtomically(
        taskId: Int,
        completedAt: Long,
        nextOccurrence: (Task) -> Task?
    ): AtomicCompletionResult
    suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot
    suspend fun deleteAll(): List<Int>
    suspend fun restore(snapshot: DeletedTaskSnapshot): Int
    suspend fun deleteCompleted(taskId: Int)
    suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus)
    suspend fun futureReminders(after: Long): List<Task>
}
