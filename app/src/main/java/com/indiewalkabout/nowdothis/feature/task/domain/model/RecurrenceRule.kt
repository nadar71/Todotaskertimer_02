package com.indiewalkabout.nowdothis.feature.task.domain.model

import java.time.DayOfWeek
import java.util.Collections
import java.util.EnumSet

enum class RecurrenceBasis { SCHEDULED_DATE, COMPLETION_DATE }

enum class IntervalUnit { DAYS, WEEKS }

enum class MonthlyOrdinalValue { FIRST, SECOND, THIRD, FOURTH, LAST }

sealed interface RecurrenceRule {
    data object None : RecurrenceRule

    data class Interval(
        val unit: IntervalUnit,
        val every: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule {
        init {
            require(every in 1..MAX_INTERVAL) { "Interval must be between 1 and $MAX_INTERVAL" }
        }
    }

    data class SelectedWeekdays(
        private var weekdaySnapshot: Set<DayOfWeek>,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule {
        init {
            require(weekdaySnapshot.isNotEmpty()) { "At least one weekday is required" }
            weekdaySnapshot = Collections.unmodifiableSet(EnumSet.copyOf(weekdaySnapshot))
        }

        val weekdays: Set<DayOfWeek>
            get() = weekdaySnapshot
    }

    data class MonthlyDay(
        val anchorDay: Int,
        val everyMonths: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule {
        init {
            require(anchorDay in 1..DAYS_IN_LONGEST_MONTH) {
                "Monthly anchor day must be between 1 and $DAYS_IN_LONGEST_MONTH"
            }
            require(everyMonths in 1..MAX_INTERVAL) {
                "Month interval must be between 1 and $MAX_INTERVAL"
            }
        }
    }

    data class MonthlyOrdinal(
        val ordinal: MonthlyOrdinalValue,
        val weekday: DayOfWeek,
        val everyMonths: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule {
        init {
            require(everyMonths in 1..MAX_INTERVAL) {
                "Month interval must be between 1 and $MAX_INTERVAL"
            }
        }
    }

    private companion object {
        const val MAX_INTERVAL = 999
        const val DAYS_IN_LONGEST_MONTH = 31
    }
}
