package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.NextOccurrenceResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

class CalculateNextOccurrence(
    private val zoneIdProvider: ZoneIdProvider
) {
    operator fun invoke(
        task: Task,
        completedAt: Long,
        referenceAt: Long,
    ): NextOccurrenceResult {
        val dueAt = task.dueAt ?: return NextOccurrenceResult.Invalid(
            NextOccurrenceResult.Reason.MISSING_DUE_DATE
        )
        if (task.recurrenceRule is RecurrenceRule.None) return NextOccurrenceResult.Ended

        return try {
            val zone = zoneIdProvider.zoneId()
            val dueDateTime = dueAt.asDateTime(zone)
            val referenceDateTime = referenceAt.asDateTime(zone)
            val completedDateTime = completedAt.asDateTime(zone)
            val nextDateTime = when (val rule = task.recurrenceRule) {
                is RecurrenceRule.Interval -> nextInterval(
                    base = rule.baseDateTime(dueDateTime, completedDateTime, zone),
                    reference = referenceDateTime,
                    rule = rule
                )

                is RecurrenceRule.SelectedWeekdays -> nextSelectedWeekday(
                    dueDateTime = dueDateTime,
                    completedDateTime = completedDateTime,
                    reference = referenceDateTime,
                    rule = rule,
                    zone = zone
                )

                is RecurrenceRule.MonthlyDay -> nextMonthlyDay(
                    base = rule.baseDateTime(dueDateTime, completedDateTime, zone),
                    reference = referenceDateTime,
                    rule = rule,
                    zone = zone
                )

                is RecurrenceRule.MonthlyOrdinal -> nextMonthlyOrdinal(
                    base = rule.baseDateTime(dueDateTime, completedDateTime, zone),
                    reference = referenceDateTime,
                    rule = rule,
                    zone = zone
                )

                RecurrenceRule.None -> return NextOccurrenceResult.Ended
            }
            val nextDueAt = nextDateTime.toInstant().toEpochMilli()
            if (task.recurrenceEndAt != null && nextDueAt > task.recurrenceEndAt) {
                NextOccurrenceResult.Ended
            } else {
                NextOccurrenceResult.Next(nextDueAt)
            }
        } catch (_: DateTimeException) {
            NextOccurrenceResult.Invalid(NextOccurrenceResult.Reason.OVERFLOW)
        } catch (_: ArithmeticException) {
            NextOccurrenceResult.Invalid(NextOccurrenceResult.Reason.OVERFLOW)
        }
    }

    operator fun invoke(task: Task): Long? {
        val dueAt = task.dueAt ?: return null
        return (invoke(task, dueAt, dueAt) as? NextOccurrenceResult.Next)?.dueAt
    }

    private fun RecurrenceRule.Interval.baseDateTime(
        dueDateTime: ZonedDateTime,
        completedDateTime: ZonedDateTime,
        zone: ZoneId
    ): ZonedDateTime = when (basis) {
        RecurrenceBasis.SCHEDULED_DATE -> dueDateTime
        RecurrenceBasis.COMPLETION_DATE -> completedDateTime.withDueTime(dueDateTime.toLocalTime(), zone)
    }

    private fun RecurrenceRule.MonthlyDay.baseDateTime(
        dueDateTime: ZonedDateTime,
        completedDateTime: ZonedDateTime,
        zone: ZoneId
    ): ZonedDateTime = when (basis) {
        RecurrenceBasis.SCHEDULED_DATE -> dueDateTime
        RecurrenceBasis.COMPLETION_DATE -> completedDateTime.withDueTime(dueDateTime.toLocalTime(), zone)
    }

    private fun RecurrenceRule.MonthlyOrdinal.baseDateTime(
        dueDateTime: ZonedDateTime,
        completedDateTime: ZonedDateTime,
        zone: ZoneId
    ): ZonedDateTime = when (basis) {
        RecurrenceBasis.SCHEDULED_DATE -> dueDateTime
        RecurrenceBasis.COMPLETION_DATE -> completedDateTime.withDueTime(dueDateTime.toLocalTime(), zone)
    }

    private fun nextInterval(
        base: ZonedDateTime,
        reference: ZonedDateTime,
        rule: RecurrenceRule.Interval
    ): ZonedDateTime {
        val unitsBetween = when (rule.unit) {
            IntervalUnit.DAYS -> ChronoUnit.DAYS.between(base.toLocalDate(), reference.toLocalDate())
            IntervalUnit.WEEKS -> ChronoUnit.WEEKS.between(base.toLocalDate(), reference.toLocalDate())
        }
        var steps = max(1, unitsBetween / rule.every)
        var candidate = base.advance(rule.unit, Math.multiplyExact(steps, rule.every.toLong()))
        if (candidate <= reference) {
            steps = Math.addExact(steps, 1)
            candidate = base.advance(rule.unit, Math.multiplyExact(steps, rule.every.toLong()))
        }
        return candidate
    }

    private fun nextSelectedWeekday(
        dueDateTime: ZonedDateTime,
        completedDateTime: ZonedDateTime,
        reference: ZonedDateTime,
        rule: RecurrenceRule.SelectedWeekdays,
        zone: ZoneId
    ): ZonedDateTime {
        val base = when (rule.basis) {
            RecurrenceBasis.SCHEDULED_DATE -> dueDateTime
            RecurrenceBasis.COMPLETION_DATE -> completedDateTime
        }
        val threshold = maxOf(base.toInstant(), reference.toInstant()).atZone(zone)
        val dueTime = dueDateTime.toLocalTime()
        return (0L..DAYS_IN_WEEK).asSequence()
            .map { offset -> threshold.toLocalDate().plusDays(offset).atTime(dueTime).atZone(zone) }
            .first { candidate -> candidate > threshold && candidate.dayOfWeek in rule.weekdays }
    }

    private fun nextMonthlyDay(
        base: ZonedDateTime,
        reference: ZonedDateTime,
        rule: RecurrenceRule.MonthlyDay,
        zone: ZoneId
    ): ZonedDateTime {
        val baseMonth = YearMonth.from(base)
        val monthsBetween = ChronoUnit.MONTHS.between(baseMonth.atDay(1), reference.toLocalDate().withDayOfMonth(1))
        var steps = max(1, monthsBetween / rule.everyMonths)
        var candidate = monthlyDayAt(baseMonth.plusMonths(Math.multiplyExact(steps, rule.everyMonths.toLong())), base.toLocalTime(), rule.anchorDay, zone)
        if (candidate <= reference) {
            steps = Math.addExact(steps, 1)
            candidate = monthlyDayAt(baseMonth.plusMonths(Math.multiplyExact(steps, rule.everyMonths.toLong())), base.toLocalTime(), rule.anchorDay, zone)
        }
        return candidate
    }

    private fun nextMonthlyOrdinal(
        base: ZonedDateTime,
        reference: ZonedDateTime,
        rule: RecurrenceRule.MonthlyOrdinal,
        zone: ZoneId
    ): ZonedDateTime {
        val baseMonth = YearMonth.from(base)
        val monthsBetween = ChronoUnit.MONTHS.between(baseMonth.atDay(1), reference.toLocalDate().withDayOfMonth(1))
        var steps = max(1, monthsBetween / rule.everyMonths)
        var candidate = monthlyOrdinalAt(baseMonth.plusMonths(Math.multiplyExact(steps, rule.everyMonths.toLong())), base.toLocalTime(), rule, zone)
        if (candidate <= reference) {
            steps = Math.addExact(steps, 1)
            candidate = monthlyOrdinalAt(baseMonth.plusMonths(Math.multiplyExact(steps, rule.everyMonths.toLong())), base.toLocalTime(), rule, zone)
        }
        return candidate
    }

    private fun monthlyDayAt(month: YearMonth, time: LocalTime, anchorDay: Int, zone: ZoneId): ZonedDateTime =
        month.atDay(min(anchorDay, month.lengthOfMonth())).atTime(time).atZone(zone)

    private fun monthlyOrdinalAt(
        month: YearMonth,
        time: LocalTime,
        rule: RecurrenceRule.MonthlyOrdinal,
        zone: ZoneId
    ): ZonedDateTime {
        val firstDay = month.atDay(1)
        val date = when (rule.ordinal) {
            MonthlyOrdinalValue.FIRST -> firstDay.with(TemporalAdjusters.firstInMonth(rule.weekday))
            MonthlyOrdinalValue.SECOND -> firstDay.with(TemporalAdjusters.dayOfWeekInMonth(2, rule.weekday))
            MonthlyOrdinalValue.THIRD -> firstDay.with(TemporalAdjusters.dayOfWeekInMonth(3, rule.weekday))
            MonthlyOrdinalValue.FOURTH -> firstDay.with(TemporalAdjusters.dayOfWeekInMonth(4, rule.weekday))
            MonthlyOrdinalValue.LAST -> firstDay.with(TemporalAdjusters.lastInMonth(rule.weekday))
        }
        return date.atTime(time).atZone(zone)
    }

    private fun ZonedDateTime.advance(unit: IntervalUnit, amount: Long): ZonedDateTime = when (unit) {
        IntervalUnit.DAYS -> plusDays(amount)
        IntervalUnit.WEEKS -> plusWeeks(amount)
    }

    private fun ZonedDateTime.withDueTime(time: LocalTime, zone: ZoneId): ZonedDateTime =
        toLocalDate().atTime(time).atZone(zone)

    private fun Long.asDateTime(zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(this).atZone(zone)

    private companion object {
        const val DAYS_IN_WEEK = 6L
    }
}
