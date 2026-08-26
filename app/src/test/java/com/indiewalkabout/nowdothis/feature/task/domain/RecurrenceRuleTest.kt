package com.indiewalkabout.nowdothis.feature.task.domain

import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecurrenceRuleTest {
    @Test
    fun selectedWeekdays_snapshotsInput() {
        val source = mutableSetOf(DayOfWeek.MONDAY)

        val rule = RecurrenceRule.SelectedWeekdays(source, RecurrenceBasis.SCHEDULED_DATE)
        source += DayOfWeek.FRIDAY

        assertEquals(setOf(DayOfWeek.MONDAY), rule.weekdays)
    }

    @Test
    fun interval_rejectsCountsOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule.Interval(IntervalUnit.DAYS, 0, RecurrenceBasis.SCHEDULED_DATE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule.Interval(IntervalUnit.WEEKS, 1_000, RecurrenceBasis.COMPLETION_DATE)
        }
    }

    @Test
    fun selectedWeekdays_rejectsAnEmptySchedule() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule.SelectedWeekdays(emptySet(), RecurrenceBasis.SCHEDULED_DATE)
        }
    }

    @Test
    fun monthlyDay_rejectsInvalidAnchorDaysAndMonthCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule.MonthlyDay(0, 1, RecurrenceBasis.SCHEDULED_DATE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule.MonthlyDay(32, 1, RecurrenceBasis.SCHEDULED_DATE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule.MonthlyDay(31, 1_000, RecurrenceBasis.COMPLETION_DATE)
        }
    }

    @Test
    fun monthlyOrdinal_acceptsEachSupportedOrdinal() {
        val rules = MonthlyOrdinalValue.entries.map { ordinal ->
            RecurrenceRule.MonthlyOrdinal(
                ordinal = ordinal,
                weekday = DayOfWeek.WEDNESDAY,
                everyMonths = 999,
                basis = RecurrenceBasis.COMPLETION_DATE
            )
        }

        assertEquals(MonthlyOrdinalValue.entries.toList(), rules.map { it.ordinal })
    }
}
