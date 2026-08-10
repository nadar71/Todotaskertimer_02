package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import java.time.Instant

class CalculateNextOccurrence(
    private val zoneIdProvider: ZoneIdProvider
) {
    operator fun invoke(task: Task): Long? {
        val dueAt = task.dueAt ?: return null
        if (task.recurrence == RecurrenceType.NONE) return null

        val dueDateTime = Instant.ofEpochMilli(dueAt).atZone(zoneIdProvider.zoneId())
        val next = when (task.recurrence) {
            RecurrenceType.DAILY -> dueDateTime.plusDays(1)
            RecurrenceType.WEEKLY -> dueDateTime.plusWeeks(1)
            RecurrenceType.MONTHLY -> dueDateTime.plusMonths(1)
            RecurrenceType.NONE -> return null
        }.toInstant().toEpochMilli()

        return next.takeIf { endAt -> task.recurrenceEndAt == null || endAt <= task.recurrenceEndAt }
    }
}
