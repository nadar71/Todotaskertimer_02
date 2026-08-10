package com.indiewalkabout.nowdothis.feature.task.data.mapper

import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority

object TaskEntityMapper {
    fun toEntities(task: Task): Pair<TaskEntity, List<SubtaskEntity>> =
        TaskEntity(
            id = task.id,
            title = task.title,
            description = task.description,
            priority = task.priority.name,
            categoryId = task.categoryId,
            isCompleted = task.isCompleted,
            completedAt = task.completedAt,
            dueAt = task.dueAt,
            reminderAt = task.reminderAt,
            reminderStatus = task.reminderStatus.name,
            recurrence = task.recurrence.name,
            recurrenceEndAt = task.recurrenceEndAt,
            seriesId = task.seriesId,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
        ) to task.subtasks.map { subtask ->
            SubtaskEntity(
                id = subtask.id,
                taskId = task.id,
                title = subtask.title,
                isCompleted = subtask.isCompleted,
                completedAt = subtask.completedAt,
                position = subtask.position
            )
        }

    fun toDomain(relation: TaskWithSubtasks): Task = relation.task.run {
        Task(
            id = id,
            title = title,
            description = description,
            priority = enumValueOf<TaskPriority>(priority),
            categoryId = categoryId,
            isCompleted = isCompleted,
            completedAt = completedAt,
            dueAt = dueAt,
            reminderAt = reminderAt,
            reminderStatus = enumValueOf<ReminderStatus>(reminderStatus),
            recurrence = enumValueOf<RecurrenceType>(recurrence),
            recurrenceEndAt = recurrenceEndAt,
            seriesId = seriesId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            subtasks = relation.subtasks
                .sortedWith(compareBy(SubtaskEntity::position, SubtaskEntity::id))
                .map { subtask ->
                    Subtask(
                        id = subtask.id,
                        taskId = subtask.taskId,
                        title = subtask.title,
                        isCompleted = subtask.isCompleted,
                        completedAt = subtask.completedAt,
                        position = subtask.position
                    )
                }
        )
    }
}
