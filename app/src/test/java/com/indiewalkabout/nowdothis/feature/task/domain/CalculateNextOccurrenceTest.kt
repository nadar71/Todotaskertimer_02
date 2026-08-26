package com.indiewalkabout.nowdothis.feature.task.domain

import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.NextOccurrenceResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis.COMPLETION_DATE
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis.SCHEDULED_DATE
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateNextOccurrenceTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val calculator = CalculateNextOccurrence { zone }

    @Test
    fun noneRecurrence_endsSeries() {
        assertEquals(
            NextOccurrenceResult.Ended,
            calculator(task(), instant(2025, 1, 1, 10), instant(2025, 1, 1, 10))
        )
    }

    @Test
    fun recurringTaskWithoutDueTime_isInvalid() {
        assertEquals(
            NextOccurrenceResult.Invalid(NextOccurrenceResult.Reason.MISSING_DUE_DATE),
            calculator(task(dueAt = null, rule = dailyRule(SCHEDULED_DATE)), 0, 0)
        )
    }

    @Test
    fun scheduledDayInterval_advancesFromScheduledDate() {
        assertEquals(
            next(2025, 1, 3, 9),
            calculator(task(rule = dailyRule(SCHEDULED_DATE, every = 2)), instant(2025, 1, 1, 10), instant(2025, 1, 1, 10))
        )
    }

    @Test
    fun completionDayInterval_anchorsToCompletionDateAndKeepsDueTime() {
        assertEquals(
            next(2025, 1, 12, 9),
            calculator(task(rule = dailyRule(COMPLETION_DATE, every = 2)), instant(2025, 1, 10, 17), instant(2025, 1, 10, 17))
        )
    }

    @Test
    fun scheduledWeekInterval_advancesFromScheduledDate() {
        assertEquals(
            next(2025, 1, 15, 9),
            calculator(task(rule = weeklyRule(SCHEDULED_DATE, every = 2)), instant(2025, 1, 2, 10), instant(2025, 1, 2, 10))
        )
    }

    @Test
    fun completionWeekInterval_anchorsToCompletionDateAndKeepsDueTime() {
        assertEquals(
            next(2025, 1, 24, 9),
            calculator(task(rule = weeklyRule(COMPLETION_DATE, every = 2)), instant(2025, 1, 10, 17), instant(2025, 1, 10, 17))
        )
    }

    @Test
    fun selectedWeekdays_choosesTheNextConfiguredIsoWeekday() {
        val due = instant(2026, 8, 3, 9)

        assertEquals(
            next(2026, 8, 5, 9),
            calculator(
                task(
                    dueAt = due,
                    rule = RecurrenceRule.SelectedWeekdays(
                        setOf(MONDAY, WEDNESDAY, FRIDAY),
                        SCHEDULED_DATE
                    )
                ),
                due,
                due
            )
        )
    }

    @Test
    fun overdueSelectedWeekdays_skipsMissedDates() {
        val task = task(
            dueAt = instant(2026, 8, 3, 9),
            rule = RecurrenceRule.SelectedWeekdays(setOf(MONDAY, FRIDAY), SCHEDULED_DATE)
        )

        assertEquals(
            next(2026, 8, 14, 9),
            calculator(task, completedAt = instant(2026, 8, 12, 18), referenceAt = instant(2026, 8, 12, 18))
        )
    }

    @Test
    fun monthlyAnchor28_usesFebruary28() {
        assertEquals(next(2025, 2, 28, 9), nextAfter(instant(2025, 1, 28, 9), monthlyDay(28)))
    }

    @Test
    fun monthlyAnchor29_clampsThenRecoversInMarch() {
        assertEquals(next(2025, 3, 29, 9), nextAfter(instant(2025, 2, 28, 9), monthlyDay(29)))
    }

    @Test
    fun monthlyAnchor30_clampsThenRecoversInMarch() {
        assertEquals(next(2025, 3, 30, 9), nextAfter(instant(2025, 2, 28, 9), monthlyDay(30)))
    }

    @Test
    fun monthlyAnchor31_clampsThenRecoversInMarch() {
        assertEquals(next(2025, 3, 31, 9), nextAfter(instant(2025, 2, 28, 9), monthlyDay(31)))
    }

    @Test
    fun monthlyAnchor31_usesFebruary29InLeapYear() {
        assertEquals(next(2024, 2, 29, 9), nextAfter(instant(2024, 1, 31, 9), monthlyDay(31)))
    }

    @Test
    fun monthlyFirstWeekday_selectsFirstTuesday() {
        assertEquals(next(2025, 2, 4, 9), nextAfter(instant(2025, 1, 1, 9), ordinal(MonthlyOrdinalValue.FIRST, TUESDAY)))
    }

    @Test
    fun monthlySecondWeekday_selectsSecondTuesday() {
        assertEquals(next(2025, 2, 11, 9), nextAfter(instant(2025, 1, 1, 9), ordinal(MonthlyOrdinalValue.SECOND, TUESDAY)))
    }

    @Test
    fun monthlyThirdWeekday_selectsThirdTuesday() {
        assertEquals(next(2025, 2, 18, 9), nextAfter(instant(2025, 1, 1, 9), ordinal(MonthlyOrdinalValue.THIRD, TUESDAY)))
    }

    @Test
    fun monthlyFourthWeekday_selectsFourthTuesday() {
        assertEquals(next(2025, 2, 25, 9), nextAfter(instant(2025, 1, 1, 9), ordinal(MonthlyOrdinalValue.FOURTH, TUESDAY)))
    }

    @Test
    fun monthlyLastWeekday_selectsFinalMonday() {
        assertEquals(next(2025, 2, 24, 9), nextAfter(instant(2025, 1, 1, 9), ordinal(MonthlyOrdinalValue.LAST, MONDAY)))
    }

    @Test
    fun dailyInterval_resolvesEuropeRomeDstGap() {
        assertEquals(
            next(2025, 3, 30, 3, 30),
            calculator(
                task(dueAt = instant(2025, 3, 29, 2, 30), rule = dailyRule(SCHEDULED_DATE)),
                instant(2025, 3, 29, 3),
                instant(2025, 3, 29, 3)
            )
        )
    }

    @Test
    fun dailyInterval_preservesEuropeRomeOverlapLocalTime() {
        assertEquals(
            next(2025, 10, 26, 2, 30),
            calculator(
                task(dueAt = instant(2025, 10, 25, 2, 30), rule = dailyRule(SCHEDULED_DATE)),
                instant(2025, 10, 25, 3),
                instant(2025, 10, 25, 3)
            )
        )
    }

    @Test
    fun overdueDailyInterval_skipsAncientMissedDates() {
        assertEquals(
            next(2026, 1, 2, 9),
            calculator(
                task(dueAt = instant(1970, 1, 1, 9), rule = dailyRule(SCHEDULED_DATE)),
                instant(2026, 1, 1, 12),
                instant(2026, 1, 1, 12)
            )
        )
    }

    @Test
    fun recurrenceEnd_equalToNextOccurrence_isIncluded() {
        val due = instant(2025, 1, 1, 9)
        assertEquals(
            next(2025, 1, 2, 9),
            calculator(
                task(dueAt = due, rule = dailyRule(SCHEDULED_DATE), recurrenceEndAt = instant(2025, 1, 2, 9)),
                due,
                due
            )
        )
    }

    @Test
    fun recurrenceEnd_beforeNextOccurrence_endsSeries() {
        val due = instant(2025, 1, 1, 9)
        assertEquals(
            NextOccurrenceResult.Ended,
            calculator(
                task(dueAt = due, rule = dailyRule(SCHEDULED_DATE), recurrenceEndAt = instant(2025, 1, 2, 8)),
                due,
                due
            )
        )
    }

    @Test
    fun candidateEqualToReference_skipsToOneStrictlyFutureOccurrence() {
        assertEquals(
            next(2025, 1, 3, 9),
            calculator(
                task(rule = dailyRule(SCHEDULED_DATE)),
                instant(2025, 1, 2, 9),
                instant(2025, 1, 2, 9)
            )
        )
    }

    @Test
    fun arithmeticOverflow_isInvalid() {
        assertEquals(
            NextOccurrenceResult.Invalid(NextOccurrenceResult.Reason.OVERFLOW),
            calculator(task(dueAt = Long.MAX_VALUE, rule = dailyRule(SCHEDULED_DATE)), Long.MIN_VALUE, Long.MIN_VALUE)
        )
    }

    private fun nextAfter(dueAt: Long, rule: RecurrenceRule): NextOccurrenceResult =
        calculator(task(dueAt = dueAt, rule = rule), dueAt, dueAt)

    private fun task(
        dueAt: Long? = instant(2025, 1, 1, 9),
        rule: RecurrenceRule = RecurrenceRule.None,
        recurrenceEndAt: Long? = null
    ) = Task(
        title = "Task",
        description = "Description",
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        recurrenceRule = rule,
        recurrenceEndAt = recurrenceEndAt,
        createdAt = 0,
        updatedAt = 0
    )

    private fun dailyRule(basis: RecurrenceBasis, every: Int = 1) =
        RecurrenceRule.Interval(IntervalUnit.DAYS, every, basis)

    private fun weeklyRule(basis: RecurrenceBasis, every: Int = 1) =
        RecurrenceRule.Interval(IntervalUnit.WEEKS, every, basis)

    private fun monthlyDay(anchorDay: Int) = RecurrenceRule.MonthlyDay(anchorDay, 1, SCHEDULED_DATE)

    private fun ordinal(value: MonthlyOrdinalValue, weekday: DayOfWeek) =
        RecurrenceRule.MonthlyOrdinal(value, weekday, 1, SCHEDULED_DATE)

    private fun next(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        NextOccurrenceResult.Next(instant(year, month, day, hour, minute))

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
