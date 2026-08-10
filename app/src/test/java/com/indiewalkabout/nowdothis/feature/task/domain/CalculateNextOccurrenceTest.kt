package com.indiewalkabout.nowdothis.feature.task.domain

import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateNextOccurrenceTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val calculator = CalculateNextOccurrence { zone }

    @Test
    fun noneRecurrence_returnsNull() {
        assertNull(calculator(task(recurrence = RecurrenceType.NONE)))
    }

    @Test
    fun recurringTaskWithoutDueTime_returnsNull() {
        assertNull(calculator(task(dueAt = null, recurrence = RecurrenceType.DAILY)))
    }

    @Test
    fun dailyRecurrence_advancesOneLocalDay() {
        val due = instant(2025, 3, 29, 9)

        assertEquals(instant(2025, 3, 30, 9), calculator(task(due, RecurrenceType.DAILY)))
    }

    @Test
    fun weeklyRecurrence_advancesOneLocalWeek() {
        val due = instant(2025, 1, 6, 9)

        assertEquals(instant(2025, 1, 13, 9), calculator(task(due, RecurrenceType.WEEKLY)))
    }

    @Test
    fun monthlyRecurrence_clampsJanuary31ToFebruaryEnd() {
        val zone = ZoneId.of("Europe/Rome")
        val due = ZonedDateTime.of(2025, 1, 31, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val task = task(dueAt = due, recurrence = RecurrenceType.MONTHLY)

        val next = CalculateNextOccurrence { zone }.invoke(task)

        assertEquals(
            ZonedDateTime.of(2025, 2, 28, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
            next
        )
    }

    @Test
    fun recurrenceEndAt_equalToNextOccurrence_keepsOccurrence() {
        val next = instant(2025, 1, 2, 9)

        assertEquals(
            next,
            calculator(
                task(
                    dueAt = instant(2025, 1, 1, 9),
                    recurrence = RecurrenceType.DAILY,
                    recurrenceEndAt = next
                )
            )
        )
    }

    @Test
    fun recurrenceEndAt_beforeNextOccurrence_returnsNull() {
        assertNull(
            calculator(
                task(
                    dueAt = instant(2025, 1, 1, 9),
                    recurrence = RecurrenceType.DAILY,
                    recurrenceEndAt = instant(2025, 1, 2, 8)
                )
            )
        )
    }

    private fun task(
        dueAt: Long? = instant(2025, 1, 1, 9),
        recurrence: RecurrenceType = RecurrenceType.NONE,
        recurrenceEndAt: Long? = null
    ) = Task(
        title = "Task",
        description = "Description",
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        recurrence = recurrence,
        recurrenceEndAt = recurrenceEndAt,
        createdAt = 0,
        updatedAt = 0
    )

    private fun instant(year: Int, month: Int, day: Int, hour: Int): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
}
