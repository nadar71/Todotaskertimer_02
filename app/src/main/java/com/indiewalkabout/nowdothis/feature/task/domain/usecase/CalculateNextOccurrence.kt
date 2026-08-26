package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import java.time.Instant

class CalculateNextOccurrence(
    private val zoneIdProvider: ZoneIdProvider
) {
    operator fun invoke(task: Task): Long? {
        val dueAt = task.dueAt ?: return null
        val rule = task.recurrenceRule
        if (rule is RecurrenceRule.None) return null

        val dueDateTime = Instant.ofEpochMilli(dueAt).atZone(zoneIdProvider.zoneId())
        val next = when (rule) {
            is RecurrenceRule.Interval -> when (rule.unit) {
                IntervalUnit.DAYS -> dueDateTime.plusDays(rule.every.toLong())
                IntervalUnit.WEEKS -> dueDateTime.plusWeeks(rule.every.toLong())
            }
            is RecurrenceRule.MonthlyDay -> dueDateTime.plusMonths(rule.everyMonths.toLong())
            is RecurrenceRule.SelectedWeekdays,
            is RecurrenceRule.MonthlyOrdinal -> {
                error("Advanced recurrence calculation is not available yet")
            }
            RecurrenceRule.None -> return null
        }.toInstant().toEpochMilli()

        return next.takeIf { endAt -> task.recurrenceEndAt == null || endAt <= task.recurrenceEndAt }
    }
}
