package com.indiewalkabout.nowdothis.feature.task.data.mapper

import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.DayOfWeek

class InvalidRecurrenceRecord(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

object TaskEntityMapper {
    fun toEntities(task: Task): Pair<TaskEntity, List<SubtaskEntity>> {
        validateDomainRecurrence(task)
        val recurrence = task.recurrenceRule.toColumns()
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
            recurrence = task.recurrenceRule.toLegacyProjection(),
            recurrenceKind = recurrence.kind,
            recurrenceIntervalUnit = recurrence.intervalUnit,
            recurrenceIntervalCount = recurrence.intervalCount,
            recurrenceBasis = recurrence.basis,
            recurrenceWeekdayMask = recurrence.weekdayMask,
            recurrenceAnchorDay = recurrence.anchorDay,
            recurrenceOrdinal = recurrence.ordinal,
            recurrenceOrdinalWeekday = recurrence.ordinalWeekday,
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
        val rule = toRecurrenceRule()
        validateRecordBoundaries(rule)
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
            recurrenceRule = rule,
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

private data class RecurrenceColumns(
    val kind: String,
    val intervalUnit: String? = null,
    val intervalCount: Int? = null,
    val basis: String? = null,
    val weekdayMask: Int? = null,
    val anchorDay: Int? = null,
    val ordinal: String? = null,
    val ordinalWeekday: String? = null
)

private fun RecurrenceRule.toColumns(): RecurrenceColumns = when (this) {
    RecurrenceRule.None -> RecurrenceColumns(kind = "NONE")
    is RecurrenceRule.Interval -> RecurrenceColumns(
        kind = "INTERVAL",
        intervalUnit = unit.name,
        intervalCount = every,
        basis = basis.name
    )
    is RecurrenceRule.SelectedWeekdays -> RecurrenceColumns(
        kind = "SELECTED_WEEKDAYS",
        basis = basis.name,
        weekdayMask = weekdays.fold(0) { mask, weekday ->
            mask or (1 shl (weekday.value - 1))
        }
    )
    is RecurrenceRule.MonthlyDay -> RecurrenceColumns(
        kind = "MONTHLY_DAY",
        intervalCount = everyMonths,
        basis = basis.name,
        anchorDay = anchorDay
    )
    is RecurrenceRule.MonthlyOrdinal -> RecurrenceColumns(
        kind = "MONTHLY_ORDINAL",
        intervalCount = everyMonths,
        basis = basis.name,
        ordinal = ordinal.name,
        ordinalWeekday = weekday.name
    )
}

private fun TaskEntity.toRecurrenceRule(): RecurrenceRule = try {
    when (recurrenceKind) {
        "NONE" -> {
            rejectPresent(
                recurrenceIntervalUnit,
                recurrenceIntervalCount,
                recurrenceBasis,
                recurrenceWeekdayMask,
                recurrenceAnchorDay,
                recurrenceOrdinal,
                recurrenceOrdinalWeekday
            )
            RecurrenceRule.None
        }
        "INTERVAL" -> {
            rejectPresent(
                recurrenceWeekdayMask,
                recurrenceAnchorDay,
                recurrenceOrdinal,
                recurrenceOrdinalWeekday
            )
            RecurrenceRule.Interval(
                unit = requiredEnum(recurrenceIntervalUnit, "recurrence_interval_unit"),
                every = required(recurrenceIntervalCount, "recurrence_interval_count"),
                basis = requiredEnum(recurrenceBasis, "recurrence_basis")
            )
        }
        "SELECTED_WEEKDAYS" -> {
            rejectPresent(
                recurrenceIntervalUnit,
                recurrenceIntervalCount,
                recurrenceAnchorDay,
                recurrenceOrdinal,
                recurrenceOrdinalWeekday
            )
            val mask = required(recurrenceWeekdayMask, "recurrence_weekday_mask")
            if (mask !in MIN_WEEKDAY_MASK..MAX_WEEKDAY_MASK) {
                invalid("recurrence_weekday_mask must use the seven ISO weekday bits")
            }
            RecurrenceRule.SelectedWeekdays(
                DayOfWeek.entries.filterTo(linkedSetOf()) { weekday ->
                    mask and (1 shl (weekday.value - 1)) != 0
                },
                requiredEnum(recurrenceBasis, "recurrence_basis")
            )
        }
        "MONTHLY_DAY" -> {
            rejectPresent(
                recurrenceIntervalUnit,
                recurrenceWeekdayMask,
                recurrenceOrdinal,
                recurrenceOrdinalWeekday
            )
            RecurrenceRule.MonthlyDay(
                anchorDay = required(recurrenceAnchorDay, "recurrence_anchor_day"),
                everyMonths = required(
                    recurrenceIntervalCount,
                    "recurrence_interval_count"
                ),
                basis = requiredEnum(recurrenceBasis, "recurrence_basis")
            )
        }
        "MONTHLY_ORDINAL" -> {
            rejectPresent(
                recurrenceIntervalUnit,
                recurrenceWeekdayMask,
                recurrenceAnchorDay
            )
            RecurrenceRule.MonthlyOrdinal(
                ordinal = requiredEnum(recurrenceOrdinal, "recurrence_ordinal"),
                weekday = requiredEnum(
                    recurrenceOrdinalWeekday,
                    "recurrence_ordinal_weekday"
                ),
                everyMonths = required(
                    recurrenceIntervalCount,
                    "recurrence_interval_count"
                ),
                basis = requiredEnum(recurrenceBasis, "recurrence_basis")
            )
        }
        else -> invalid("Unknown recurrence_kind: $recurrenceKind")
    }
} catch (error: InvalidRecurrenceRecord) {
    throw error
} catch (error: IllegalArgumentException) {
    throw InvalidRecurrenceRecord(
        "Invalid recurrence record for task $id: ${error.message}",
        error
    )
}

private fun TaskEntity.validateRecordBoundaries(rule: RecurrenceRule) {
    if (rule is RecurrenceRule.None) {
        if (recurrenceEndAt != null) {
            invalid("NONE cannot have recurrence_end_at")
        }
        return
    }
    val firstDueAt = dueAt ?: invalid("Active recurrence requires due_at")
    if (recurrenceEndAt != null && recurrenceEndAt < firstDueAt) {
        invalid("recurrence_end_at cannot precede due_at")
    }
}

private fun validateDomainRecurrence(task: Task) {
    if (task.recurrenceRule is RecurrenceRule.None) {
        require(task.recurrenceEndAt == null) {
            "A non-recurring task cannot have a recurrence end"
        }
        return
    }
    val firstDueAt = requireNotNull(task.dueAt) {
        "Active recurrence persistence requires a due time"
    }
    require(task.recurrenceEndAt == null || task.recurrenceEndAt >= firstDueAt) {
        "A recurrence end cannot precede the first due time"
    }
}

private fun RecurrenceRule.toLegacyProjection(): String = when (this) {
    RecurrenceRule.None -> "NONE"
    is RecurrenceRule.Interval -> when {
        unit == IntervalUnit.DAYS && every == 1 && basis == RecurrenceBasis.SCHEDULED_DATE -> {
            "DAILY"
        }
        unit == IntervalUnit.WEEKS && every == 1 && basis == RecurrenceBasis.SCHEDULED_DATE -> {
            "WEEKLY"
        }
        else -> "NONE"
    }
    is RecurrenceRule.MonthlyDay -> if (
        everyMonths == 1 && basis == RecurrenceBasis.SCHEDULED_DATE
    ) {
        "MONTHLY"
    } else {
        "NONE"
    }
    is RecurrenceRule.SelectedWeekdays,
    is RecurrenceRule.MonthlyOrdinal -> "NONE"
}

private fun rejectPresent(vararg values: Any?) {
    if (values.any { it != null }) {
        invalid("recurrence_kind has unused recurrence parameters")
    }
}

private fun <T : Any> required(value: T?, column: String): T =
    value ?: invalid("$column is required")

private inline fun <reified T : Enum<T>> requiredEnum(value: String?, column: String): T {
    val stableName = required(value, column)
    return enumValues<T>().firstOrNull { it.name == stableName }
        ?: invalid("$column has unsupported value: $stableName")
}

private fun invalid(message: String): Nothing = throw InvalidRecurrenceRecord(message)

private const val MIN_WEEKDAY_MASK = 1
private const val MAX_WEEKDAY_MASK = 0b111_1111
