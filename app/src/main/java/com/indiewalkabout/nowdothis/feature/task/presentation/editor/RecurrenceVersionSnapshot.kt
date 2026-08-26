package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import java.time.DayOfWeek
import kotlinx.serialization.Serializable

@Serializable
internal data class RecurrenceVersionSnapshot(
    val kind: Kind,
    val basis: Basis? = null,
    val intervalUnit: Unit? = null,
    val every: Int? = null,
    val weekdayIsoValues: List<Int> = emptyList(),
    val anchorDay: Int? = null,
    val ordinal: Ordinal? = null,
    val ordinalWeekdayIsoValue: Int? = null,
    val endAt: Long? = null
) {
    @Serializable
    enum class Kind { NONE, INTERVAL, SELECTED_WEEKDAYS, MONTHLY_DAY, MONTHLY_ORDINAL }

    @Serializable
    enum class Basis { SCHEDULED_DATE, COMPLETION_DATE }

    @Serializable
    enum class Unit { DAYS, WEEKS }

    @Serializable
    enum class Ordinal { FIRST, SECOND, THIRD, FOURTH, LAST }

    fun restore(): RestoredRecurrenceVersion? {
        val restoredBasis = basis?.let { RecurrenceBasis.valueOf(it.name) }
        val rule = when (kind) {
            Kind.NONE -> {
                if (
                    basis != null || intervalUnit != null || every != null ||
                    weekdayIsoValues.isNotEmpty() || anchorDay != null || ordinal != null ||
                    ordinalWeekdayIsoValue != null
                ) return null
                RecurrenceRule.None
            }
            Kind.INTERVAL -> {
                if (
                    restoredBasis == null || intervalUnit == null || every == null ||
                    every !in 1..999 ||
                    weekdayIsoValues.isNotEmpty() || anchorDay != null || ordinal != null ||
                    ordinalWeekdayIsoValue != null
                ) return null
                RecurrenceRule.Interval(
                    unit = IntervalUnit.valueOf(intervalUnit.name),
                    every = requireNotNull(every),
                    basis = restoredBasis
                )
            }
            Kind.SELECTED_WEEKDAYS -> {
                if (
                    restoredBasis == null || intervalUnit != null || every != null ||
                    weekdayIsoValues.isEmpty() || anchorDay != null || ordinal != null ||
                    ordinalWeekdayIsoValue != null ||
                    weekdayIsoValues.any { it !in 1..7 } ||
                    weekdayIsoValues.size != weekdayIsoValues.distinct().size
                ) return null
                RecurrenceRule.SelectedWeekdays(
                    weekdaySnapshot = weekdayIsoValues.mapTo(linkedSetOf()) { DayOfWeek.of(it) },
                    basis = restoredBasis
                )
            }
            Kind.MONTHLY_DAY -> {
                if (
                    restoredBasis == null || intervalUnit != null || every == null ||
                    every !in 1..999 || weekdayIsoValues.isNotEmpty() || anchorDay == null ||
                    anchorDay !in 1..31 || ordinal != null ||
                    ordinalWeekdayIsoValue != null
                ) return null
                RecurrenceRule.MonthlyDay(
                    anchorDay = requireNotNull(anchorDay),
                    everyMonths = requireNotNull(every),
                    basis = restoredBasis
                )
            }
            Kind.MONTHLY_ORDINAL -> {
                if (
                    restoredBasis == null || intervalUnit != null || every == null ||
                    every !in 1..999 ||
                    weekdayIsoValues.isNotEmpty() || anchorDay != null || ordinal == null ||
                    ordinalWeekdayIsoValue == null || ordinalWeekdayIsoValue !in 1..7
                ) return null
                RecurrenceRule.MonthlyOrdinal(
                    ordinal = MonthlyOrdinalValue.valueOf(ordinal.name),
                    weekday = DayOfWeek.of(requireNotNull(ordinalWeekdayIsoValue)),
                    everyMonths = requireNotNull(every),
                    basis = restoredBasis
                )
            }
        }
        return RestoredRecurrenceVersion(rule, endAt)
    }

    companion object {
        fun from(version: TaskSnapshotVersion): RecurrenceVersionSnapshot =
            when (val rule = version.recurrenceRule) {
                RecurrenceRule.None -> RecurrenceVersionSnapshot(
                    kind = Kind.NONE,
                    endAt = version.recurrenceEndAt
                )
                is RecurrenceRule.Interval -> RecurrenceVersionSnapshot(
                    kind = Kind.INTERVAL,
                    basis = Basis.valueOf(rule.basis.name),
                    intervalUnit = Unit.valueOf(rule.unit.name),
                    every = rule.every,
                    endAt = version.recurrenceEndAt
                )
                is RecurrenceRule.SelectedWeekdays -> RecurrenceVersionSnapshot(
                    kind = Kind.SELECTED_WEEKDAYS,
                    basis = Basis.valueOf(rule.basis.name),
                    weekdayIsoValues = rule.weekdays.map { it.value },
                    endAt = version.recurrenceEndAt
                )
                is RecurrenceRule.MonthlyDay -> RecurrenceVersionSnapshot(
                    kind = Kind.MONTHLY_DAY,
                    basis = Basis.valueOf(rule.basis.name),
                    every = rule.everyMonths,
                    anchorDay = rule.anchorDay,
                    endAt = version.recurrenceEndAt
                )
                is RecurrenceRule.MonthlyOrdinal -> RecurrenceVersionSnapshot(
                    kind = Kind.MONTHLY_ORDINAL,
                    basis = Basis.valueOf(rule.basis.name),
                    every = rule.everyMonths,
                    ordinal = Ordinal.valueOf(rule.ordinal.name),
                    ordinalWeekdayIsoValue = rule.weekday.value,
                    endAt = version.recurrenceEndAt
                )
            }
    }
}

internal data class RestoredRecurrenceVersion(
    val rule: RecurrenceRule,
    val endAt: Long?
)
