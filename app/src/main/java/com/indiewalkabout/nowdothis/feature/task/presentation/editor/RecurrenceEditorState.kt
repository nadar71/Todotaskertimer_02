package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.time.DayOfWeek
import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceEditorKind {
    NONE,
    INTERVAL,
    SELECTED_WEEKDAYS,
    MONTHLY_DAY,
    MONTHLY_ORDINAL
}

@Serializable
enum class RecurrenceEditorBasis {
    SCHEDULED_DATE,
    COMPLETION_DATE
}

@Serializable
enum class RecurrenceEditorIntervalUnit {
    DAYS,
    WEEKS
}

@Serializable
enum class RecurrenceEditorWeekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

@Serializable
enum class RecurrenceEditorOrdinal {
    FIRST,
    SECOND,
    THIRD,
    FOURTH,
    LAST
}

@Serializable
data class RecurrenceEditorState(
    val kind: RecurrenceEditorKind = RecurrenceEditorKind.NONE,
    val basis: RecurrenceEditorBasis? = null,
    val intervalUnit: RecurrenceEditorIntervalUnit? = null,
    val intervalEvery: Int? = null,
    val selectedWeekdays: Set<RecurrenceEditorWeekday> = emptySet(),
    val monthlyEvery: Int? = null,
    val monthlyAnchorDay: Int? = null,
    val ordinal: RecurrenceEditorOrdinal? = null,
    val ordinalWeekday: RecurrenceEditorWeekday? = null,
    val endAt: Long? = null
) {
    companion object {
        fun forKind(kind: RecurrenceEditorKind, endAt: Long? = null): RecurrenceEditorState =
            when (kind) {
                RecurrenceEditorKind.NONE -> RecurrenceEditorState()
                RecurrenceEditorKind.INTERVAL -> RecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.COMPLETION_DATE,
                    intervalUnit = RecurrenceEditorIntervalUnit.DAYS,
                    intervalEvery = 1,
                    endAt = endAt
                )
                RecurrenceEditorKind.SELECTED_WEEKDAYS -> RecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                    endAt = endAt
                )
                RecurrenceEditorKind.MONTHLY_DAY -> RecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                    monthlyEvery = 1,
                    monthlyAnchorDay = 1,
                    endAt = endAt
                )
                RecurrenceEditorKind.MONTHLY_ORDINAL -> RecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                    monthlyEvery = 1,
                    ordinal = RecurrenceEditorOrdinal.FIRST,
                    ordinalWeekday = RecurrenceEditorWeekday.MONDAY,
                    endAt = endAt
                )
            }

        fun fromRule(rule: RecurrenceRule, endAt: Long?): RecurrenceEditorState = when (rule) {
            RecurrenceRule.None -> RecurrenceEditorState(endAt = endAt)
            is RecurrenceRule.Interval -> RecurrenceEditorState(
                kind = RecurrenceEditorKind.INTERVAL,
                basis = rule.basis.toEditorBasis(),
                intervalUnit = rule.unit.toEditorUnit(),
                intervalEvery = rule.every,
                endAt = endAt
            )
            is RecurrenceRule.SelectedWeekdays -> RecurrenceEditorState(
                kind = RecurrenceEditorKind.SELECTED_WEEKDAYS,
                basis = rule.basis.toEditorBasis(),
                selectedWeekdays = rule.weekdays.mapTo(linkedSetOf()) { it.toEditorWeekday() },
                endAt = endAt
            )
            is RecurrenceRule.MonthlyDay -> RecurrenceEditorState(
                kind = RecurrenceEditorKind.MONTHLY_DAY,
                basis = rule.basis.toEditorBasis(),
                monthlyEvery = rule.everyMonths,
                monthlyAnchorDay = rule.anchorDay,
                endAt = endAt
            )
            is RecurrenceRule.MonthlyOrdinal -> RecurrenceEditorState(
                kind = RecurrenceEditorKind.MONTHLY_ORDINAL,
                basis = rule.basis.toEditorBasis(),
                monthlyEvery = rule.everyMonths,
                ordinal = rule.ordinal.toEditorOrdinal(),
                ordinalWeekday = rule.weekday.toEditorWeekday(),
                endAt = endAt
            )
        }

        fun fromLegacyName(
            name: String,
            endAt: Long?,
            monthlyAnchorDay: Int = 1
        ): RecurrenceEditorState = when (name) {
            "DAILY" -> RecurrenceEditorState(
                kind = RecurrenceEditorKind.INTERVAL,
                basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                intervalUnit = RecurrenceEditorIntervalUnit.DAYS,
                intervalEvery = 1,
                endAt = endAt
            )
            "WEEKLY" -> RecurrenceEditorState(
                kind = RecurrenceEditorKind.INTERVAL,
                basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                intervalUnit = RecurrenceEditorIntervalUnit.WEEKS,
                intervalEvery = 1,
                endAt = endAt
            )
            "MONTHLY" -> RecurrenceEditorState(
                kind = RecurrenceEditorKind.MONTHLY_DAY,
                basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                monthlyEvery = 1,
                monthlyAnchorDay = monthlyAnchorDay,
                endAt = endAt
            )
            else -> RecurrenceEditorState()
        }
    }
}

internal sealed interface RecurrenceRuleDraftResult {
    data class Valid(val rule: RecurrenceRule) : RecurrenceRuleDraftResult
    data class Invalid(val error: TaskEditorFieldError) : RecurrenceRuleDraftResult
}

internal fun RecurrenceEditorState.toValidatedRule(): RecurrenceRuleDraftResult {
    if (kind == RecurrenceEditorKind.NONE) {
        return RecurrenceRuleDraftResult.Valid(RecurrenceRule.None)
    }
    val domainBasis = basis?.toDomainBasis()
        ?: return RecurrenceRuleDraftResult.Invalid(TaskEditorFieldError.RECURRENCE_INCOMPLETE)
    return when (kind) {
        RecurrenceEditorKind.NONE -> RecurrenceRuleDraftResult.Valid(RecurrenceRule.None)
        RecurrenceEditorKind.INTERVAL -> {
            val every = intervalEvery
            if (every == null || every !in 1..999) {
                RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE
                )
            } else {
                val unit = intervalUnit?.toDomainUnit()
                    ?: return RecurrenceRuleDraftResult.Invalid(
                        TaskEditorFieldError.RECURRENCE_INCOMPLETE
                    )
                RecurrenceRuleDraftResult.Valid(RecurrenceRule.Interval(unit, every, domainBasis))
            }
        }
        RecurrenceEditorKind.SELECTED_WEEKDAYS -> {
            if (selectedWeekdays.isEmpty()) {
                RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_WEEKDAY_REQUIRED
                )
            } else {
                RecurrenceRuleDraftResult.Valid(
                    RecurrenceRule.SelectedWeekdays(
                        selectedWeekdays.mapTo(linkedSetOf()) { it.toDayOfWeek() },
                        domainBasis
                    )
                )
            }
        }
        RecurrenceEditorKind.MONTHLY_DAY -> {
            val anchor = monthlyAnchorDay
            val every = monthlyEvery
            when {
                anchor == null || anchor !in 1..31 -> RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_ANCHOR_OUT_OF_RANGE
                )
                every == null || every !in 1..999 -> RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE
                )
                else -> RecurrenceRuleDraftResult.Valid(
                    RecurrenceRule.MonthlyDay(anchor, every, domainBasis)
                )
            }
        }
        RecurrenceEditorKind.MONTHLY_ORDINAL -> {
            val every = monthlyEvery
            if (every == null || every !in 1..999) {
                RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE
                )
            } else {
                val domainOrdinal = ordinal?.toDomainOrdinal()
                    ?: return RecurrenceRuleDraftResult.Invalid(
                        TaskEditorFieldError.RECURRENCE_INCOMPLETE
                    )
                val weekday = ordinalWeekday?.toDayOfWeek()
                    ?: return RecurrenceRuleDraftResult.Invalid(
                        TaskEditorFieldError.RECURRENCE_INCOMPLETE
                    )
                RecurrenceRuleDraftResult.Valid(
                    RecurrenceRule.MonthlyOrdinal(
                        domainOrdinal,
                        weekday,
                        every,
                        domainBasis
                    )
                )
            }
        }
    }
}

private fun RecurrenceEditorBasis.toDomainBasis(): RecurrenceBasis =
    RecurrenceBasis.valueOf(name)

private fun RecurrenceBasis.toEditorBasis(): RecurrenceEditorBasis =
    RecurrenceEditorBasis.valueOf(name)

private fun RecurrenceEditorIntervalUnit.toDomainUnit(): IntervalUnit = IntervalUnit.valueOf(name)

private fun IntervalUnit.toEditorUnit(): RecurrenceEditorIntervalUnit =
    RecurrenceEditorIntervalUnit.valueOf(name)

private fun RecurrenceEditorWeekday.toDayOfWeek(): DayOfWeek = DayOfWeek.valueOf(name)

private fun DayOfWeek.toEditorWeekday(): RecurrenceEditorWeekday =
    RecurrenceEditorWeekday.valueOf(name)

private fun RecurrenceEditorOrdinal.toDomainOrdinal(): MonthlyOrdinalValue =
    MonthlyOrdinalValue.valueOf(name)

private fun MonthlyOrdinalValue.toEditorOrdinal(): RecurrenceEditorOrdinal =
    RecurrenceEditorOrdinal.valueOf(name)
