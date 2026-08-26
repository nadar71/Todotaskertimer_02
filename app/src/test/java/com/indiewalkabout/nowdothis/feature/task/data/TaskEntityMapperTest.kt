package com.indiewalkabout.nowdothis.feature.task.data

import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks
import com.indiewalkabout.nowdothis.feature.task.data.mapper.InvalidRecurrenceRecord
import com.indiewalkabout.nowdothis.feature.task.data.mapper.TaskEntityMapper
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskEntityMapperTest {
    @Test
    fun everyRecurrenceRule_roundTripsWithoutLosingParameters() {
        val rules = listOf(
            RecurrenceRule.None,
            RecurrenceRule.Interval(
                unit = IntervalUnit.DAYS,
                every = 3,
                basis = RecurrenceBasis.COMPLETION_DATE
            ),
            RecurrenceRule.SelectedWeekdays(
                weekdaySnapshot = setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.SUNDAY
                ),
                basis = RecurrenceBasis.SCHEDULED_DATE
            ),
            RecurrenceRule.MonthlyDay(
                anchorDay = 31,
                everyMonths = 2,
                basis = RecurrenceBasis.COMPLETION_DATE
            ),
            RecurrenceRule.MonthlyOrdinal(
                ordinal = MonthlyOrdinalValue.LAST,
                weekday = DayOfWeek.FRIDAY,
                everyMonths = 4,
                basis = RecurrenceBasis.SCHEDULED_DATE
            )
        )

        rules.forEach { rule ->
            val original = task(
                id = 7,
                dueAt = if (rule is RecurrenceRule.None) null else 10_000,
                recurrenceRule = rule
            )
            val (entity, subtasks) = TaskEntityMapper.toEntities(original)

            assertEquals(
                rule,
                TaskEntityMapper.toDomain(TaskWithSubtasks(entity, subtasks)).recurrenceRule
            )
        }
    }

    @Test
    fun toEntities_persistsStableIsoWeekdayMask() {
        val rule = RecurrenceRule.SelectedWeekdays(
            weekdaySnapshot = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.SUNDAY
            ),
            basis = RecurrenceBasis.SCHEDULED_DATE
        )

        val entity = TaskEntityMapper.toEntities(
            task(dueAt = 10_000, recurrenceRule = rule)
        ).first

        assertEquals("SELECTED_WEEKDAYS", entity.recurrenceKind)
        assertEquals(69, entity.recurrenceWeekdayMask)
        assertEquals("SCHEDULED_DATE", entity.recurrenceBasis)
        assertNull(entity.recurrenceIntervalUnit)
        assertNull(entity.recurrenceIntervalCount)
        assertNull(entity.recurrenceAnchorDay)
        assertNull(entity.recurrenceOrdinal)
        assertNull(entity.recurrenceOrdinalWeekday)
    }

    @Test
    fun toEntities_noneLeavesEveryRuleParameterNull() {
        val entity = TaskEntityMapper.toEntities(task()).first

        assertEquals("NONE", entity.recurrenceKind)
        assertNull(entity.recurrenceIntervalUnit)
        assertNull(entity.recurrenceIntervalCount)
        assertNull(entity.recurrenceBasis)
        assertNull(entity.recurrenceWeekdayMask)
        assertNull(entity.recurrenceAnchorDay)
        assertNull(entity.recurrenceOrdinal)
        assertNull(entity.recurrenceOrdinalWeekday)
    }

    @Test
    fun taskRoundTrip_preservesEveryNonRecurrenceFieldAndSortedSubtasks() {
        val original = task(
            id = 7,
            categoryId = 2,
            dueAt = 1_000,
            reminderAt = 900,
            recurrenceRule = weeklyRule,
            subtasks = listOf(
                Subtask(
                    id = 10,
                    taskId = 7,
                    title = "Zero",
                    position = 0
                ),
                Subtask(
                    id = 11,
                    taskId = 7,
                    title = "One",
                    isCompleted = true,
                    completedAt = 850,
                    position = 1
                )
            )
        )

        val (entity, subtasks) = TaskEntityMapper.toEntities(original)
        val mapped = TaskEntityMapper.toDomain(
            TaskWithSubtasks(entity, subtasks.reversed())
        )

        assertEquals("HIGH", entity.priority)
        assertEquals("SCHEDULED", entity.reminderStatus)
        assertEquals("INTERVAL", entity.recurrenceKind)
        assertEquals(0L, entity.createdAt)
        assertEquals(0L, entity.updatedAt)
        assertEquals(original, mapped)
    }

    @Test
    fun toDomain_rejectsIllegalOrUnusedRecurrenceColumnsWithControlledError() {
        val malformed = listOf(
            taskEntity().copy(recurrenceKind = "FUTURE_KIND"),
            taskEntity().copy(recurrenceIntervalCount = 1),
            taskEntity().copy(
                recurrenceKind = "INTERVAL",
                recurrenceIntervalUnit = "DAYS",
                recurrenceIntervalCount = 1,
                recurrenceBasis = "SCHEDULED_DATE",
                recurrenceWeekdayMask = 1
            ),
            taskEntity().copy(
                recurrenceKind = "SELECTED_WEEKDAYS",
                recurrenceBasis = "SCHEDULED_DATE",
                recurrenceWeekdayMask = 0
            ),
            taskEntity().copy(
                recurrenceKind = "MONTHLY_DAY",
                recurrenceIntervalCount = 1,
                recurrenceBasis = "SCHEDULED_DATE",
                recurrenceAnchorDay = 32
            ),
            taskEntity().copy(
                recurrenceKind = "MONTHLY_ORDINAL",
                recurrenceIntervalCount = 1,
                recurrenceBasis = "SCHEDULED_DATE",
                recurrenceOrdinal = "FIRST"
            )
        )

        malformed.forEach { entity ->
            assertThrows(InvalidRecurrenceRecord::class.java) {
                TaskEntityMapper.toDomain(TaskWithSubtasks(entity, emptyList()))
            }
        }
    }

    @Test
    fun toDomain_rejectsActiveRuleWithoutDueTimeWithControlledError() {
        val entity = taskEntity(dueAt = null).copy(
            recurrenceKind = "INTERVAL",
            recurrenceIntervalUnit = "WEEKS",
            recurrenceIntervalCount = 1,
            recurrenceBasis = "SCHEDULED_DATE"
        )

        assertThrows(InvalidRecurrenceRecord::class.java) {
            TaskEntityMapper.toDomain(TaskWithSubtasks(entity, emptyList()))
        }
    }

    @Test
    fun toDomain_rejectsInvalidRecurrenceEndWithControlledError() {
        listOf(
            taskEntity(dueAt = null).copy(recurrenceEndAt = 2_000),
            taskEntity(dueAt = 2_000).copy(
                recurrenceKind = "INTERVAL",
                recurrenceIntervalUnit = "DAYS",
                recurrenceIntervalCount = 1,
                recurrenceBasis = "SCHEDULED_DATE",
                recurrenceEndAt = 1_999
            )
        ).forEach { entity ->
            assertThrows(InvalidRecurrenceRecord::class.java) {
                TaskEntityMapper.toDomain(TaskWithSubtasks(entity, emptyList()))
            }
        }
    }

    @Test
    fun toEntities_rejectsInvalidTaskRecurrenceBoundaries() {
        listOf(
            task(dueAt = null, recurrenceRule = weeklyRule),
            task().copy(recurrenceEndAt = 2_000),
            task(dueAt = 2_000, recurrenceRule = weeklyRule).copy(recurrenceEndAt = 1_999)
        ).forEach { invalidTask ->
            assertThrows(IllegalArgumentException::class.java) {
                TaskEntityMapper.toEntities(invalidTask)
            }
        }
    }

    private fun task(
        id: Int = 0,
        categoryId: Int? = null,
        dueAt: Long? = null,
        reminderAt: Long? = null,
        recurrenceRule: RecurrenceRule = RecurrenceRule.None,
        subtasks: List<Subtask> = emptyList()
    ) = Task(
        id = id,
        title = "Task",
        description = "All fields",
        priority = TaskPriority.HIGH,
        categoryId = categoryId,
        isCompleted = false,
        completedAt = null,
        dueAt = dueAt,
        reminderAt = reminderAt,
        reminderStatus = ReminderStatus.SCHEDULED,
        recurrenceRule = recurrenceRule,
        recurrenceEndAt = if (recurrenceRule is RecurrenceRule.None) null else 20_000,
        seriesId = "series-1",
        createdAt = 0,
        updatedAt = 0,
        subtasks = subtasks
    )

    private fun taskEntity(dueAt: Long? = 10_000) = TaskEntity(
        id = 9,
        title = "Task",
        description = "Description",
        priority = TaskPriority.HIGH.name,
        dueAt = dueAt,
        recurrenceKind = "NONE",
        createdAt = 1,
        updatedAt = 1
    )

    private val weeklyRule = RecurrenceRule.Interval(
        IntervalUnit.WEEKS,
        1,
        RecurrenceBasis.SCHEDULED_DATE
    )
}
