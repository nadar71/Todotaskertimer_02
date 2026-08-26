package com.indiewalkabout.nowdothis.feature.task.data.mapper

import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.ZoneId

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
            recurrence = task.recurrenceRule.toLegacyRecurrenceType().name,
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
            recurrenceRule = enumValueOf<RecurrenceType>(recurrence).toLegacyRecurrenceRule(dueAt),
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

private fun RecurrenceRule.toLegacyRecurrenceType(): RecurrenceType = when (this) {
    RecurrenceRule.None -> RecurrenceType.NONE
    is RecurrenceRule.Interval -> when {
        unit == IntervalUnit.DAYS && every == 1 && basis == RecurrenceBasis.SCHEDULED_DATE -> {
            RecurrenceType.DAILY
        }
        unit == IntervalUnit.WEEKS && every == 1 && basis == RecurrenceBasis.SCHEDULED_DATE -> {
            RecurrenceType.WEEKLY
        }
        else -> error("Advanced recurrence persistence is not available until Room v3")
    }
    is RecurrenceRule.MonthlyDay -> when {
        everyMonths == 1 && basis == RecurrenceBasis.SCHEDULED_DATE -> RecurrenceType.MONTHLY
        else -> error("Advanced recurrence persistence is not available until Room v3")
    }
    is RecurrenceRule.SelectedWeekdays,
    is RecurrenceRule.MonthlyOrdinal -> error("Advanced recurrence persistence is not available until Room v3")
}

private fun RecurrenceType.toLegacyRecurrenceRule(dueAt: Long?): RecurrenceRule = when (this) {
    RecurrenceType.NONE -> RecurrenceRule.None
    RecurrenceType.DAILY -> {
        RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
    }
    RecurrenceType.WEEKLY -> {
        RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
    }
    RecurrenceType.MONTHLY -> RecurrenceRule.MonthlyDay(
        anchorDay = dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).dayOfMonth }
            ?: error("Legacy monthly recurrence requires a due time"),
        everyMonths = 1,
        basis = RecurrenceBasis.SCHEDULED_DATE
    )
}
