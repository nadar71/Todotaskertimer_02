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
    fun toEntities(task: Task): Pair<TaskEntity, List<SubtaskEntity>> {
        require(task.recurrenceRule is RecurrenceRule.None || task.dueAt != null) {
            "Active recurrence persistence requires a due time"
        }
        require(task.recurrenceRule !is RecurrenceRule.None || task.recurrenceEndAt == null) {
            "A non-recurring task cannot have a recurrence end"
        }
        return TaskEntity(
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
            recurrence = task.recurrenceRule.toLegacyRecurrenceType(task.dueAt).name,
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
    }

    fun toDomain(relation: TaskWithSubtasks): Task = relation.task.run {
        val recurrenceType = enumValueOf<RecurrenceType>(recurrence)
        require(recurrenceType != RecurrenceType.NONE || recurrenceEndAt == null) {
            "A legacy non-recurring task cannot have a recurrence end"
        }
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
            recurrenceRule = recurrenceType.toLegacyRecurrenceRule(dueAt),
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

private fun RecurrenceRule.toLegacyRecurrenceType(dueAt: Long?): RecurrenceType = when (this) {
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
    is RecurrenceRule.MonthlyDay -> {
        require(
            everyMonths == 1 &&
                basis == RecurrenceBasis.SCHEDULED_DATE &&
                anchorDay == dueAt.localDayOfMonth("Monthly recurrence persistence")
        ) {
            "Advanced recurrence persistence is not available until Room v3"
        }
        RecurrenceType.MONTHLY
    }
    is RecurrenceRule.SelectedWeekdays,
    is RecurrenceRule.MonthlyOrdinal -> error("Advanced recurrence persistence is not available until Room v3")
}

private fun RecurrenceType.toLegacyRecurrenceRule(dueAt: Long?): RecurrenceRule = when (this) {
    RecurrenceType.NONE -> RecurrenceRule.None
    RecurrenceType.DAILY -> {
        dueAt.requireForActiveRecurrence("Legacy daily recurrence")
        RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
    }
    RecurrenceType.WEEKLY -> {
        dueAt.requireForActiveRecurrence("Legacy weekly recurrence")
        RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
    }
    RecurrenceType.MONTHLY -> RecurrenceRule.MonthlyDay(
        anchorDay = dueAt.localDayOfMonth("Legacy monthly recurrence"),
        everyMonths = 1,
        basis = RecurrenceBasis.SCHEDULED_DATE
    )
}

private fun Long?.localDayOfMonth(boundary: String): Int =
    Instant.ofEpochMilli(requireForActiveRecurrence(boundary))
        .atZone(ZoneId.systemDefault())
        .dayOfMonth

private fun Long?.requireForActiveRecurrence(boundary: String): Long =
    requireNotNull(this) { "$boundary requires a due time" }
