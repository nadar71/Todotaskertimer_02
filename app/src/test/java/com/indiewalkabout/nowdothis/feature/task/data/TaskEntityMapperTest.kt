package com.indiewalkabout.nowdothis.feature.task.data

import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks
import com.indiewalkabout.nowdothis.feature.task.data.mapper.TaskEntityMapper
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskEntityMapperTest {
    @Test
    fun taskRoundTrip_preservesEveryFieldIncludingEpochZeroAndSubtasks() {
        val original = task(
            id = 7,
            categoryId = 2,
            dueAt = 1_000,
            reminderAt = 900,
            recurrenceRule = weeklyRule,
            subtasks = listOf(
                Subtask(
                    id = 11,
                    taskId = 7,
                    title = "One",
                    isCompleted = true,
                    completedAt = 850,
                    position = 0
                )
            )
        )

        val (entity, subtasks) = TaskEntityMapper.toEntities(original)
        val mapped = TaskEntityMapper.toDomain(TaskWithSubtasks(entity, subtasks))

        assertEquals("HIGH", entity.priority)
        assertEquals("SCHEDULED", entity.reminderStatus)
        assertEquals("WEEKLY", entity.recurrence)
        assertEquals(0L, entity.createdAt)
        assertEquals(0L, entity.updatedAt)
        assertEquals(original, mapped)
    }

    @Test
    fun toDomain_sortsRelationSubtasksByPositionDeterministically() {
        val (entity, _) = TaskEntityMapper.toEntities(task(id = 9))
        val relation = TaskWithSubtasks(
            task = entity,
            subtasks = listOf(
                subtask(id = 3, taskId = 9, title = "Last", position = 2),
                subtask(id = 1, taskId = 9, title = "First", position = 0),
                subtask(id = 2, taskId = 9, title = "Middle", position = 1)
            )
        )

        val mapped = TaskEntityMapper.toDomain(relation)

        assertEquals(listOf("First", "Middle", "Last"), mapped.subtasks.map { it.title })
    }

    @Test
    fun toDomain_rejectsLegacyDailyAndWeeklyRulesWithoutDueTime() {
        listOf("DAILY", "WEEKLY").forEach { recurrence ->
            assertThrows(IllegalArgumentException::class.java) {
                TaskEntityMapper.toDomain(
                    TaskWithSubtasks(taskEntity(dueAt = null, recurrence = recurrence), emptyList())
                )
            }
        }
    }

    @Test
    fun toEntities_rejectsEveryActiveRuleWithoutDueTime() {
        listOf(
            weeklyRule,
            RecurrenceRule.SelectedWeekdays(
                setOf(java.time.DayOfWeek.MONDAY),
                RecurrenceBasis.SCHEDULED_DATE
            )
        ).forEach { rule ->
            assertThrows(IllegalArgumentException::class.java) {
                TaskEntityMapper.toEntities(task(dueAt = null, recurrenceRule = rule))
            }
        }
    }

    @Test
    fun toEntities_rejectsNoneRuleWithRecurrenceEnd() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskEntityMapper.toEntities(task().copy(recurrenceEndAt = 2_000))
        }
    }

    @Test
    fun toDomain_rejectsLegacyNoneWithRecurrenceEnd() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskEntityMapper.toDomain(
                TaskWithSubtasks(
                    taskEntity(dueAt = null, recurrence = "NONE", recurrenceEndAt = 2_000),
                    emptyList()
                )
            )
        }
    }

    @Test
    fun toEntities_rejectsMonthlyAnchorThatDiffersFromDueDate() {
        val dueAt = 90_000L
        val dueDay = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).dayOfMonth
        val task = task(
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule.MonthlyDay(
                anchorDay = if (dueDay == 31) 30 else 31,
                everyMonths = 1,
                basis = RecurrenceBasis.SCHEDULED_DATE
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            TaskEntityMapper.toEntities(task)
        }
    }

    @Test
    fun monthlyRuleWithDueDateAnchor_roundTripsThroughLegacyMapper() {
        val dueAt = 90_000L
        val original = task(
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule.MonthlyDay(
                anchorDay = Instant.ofEpochMilli(dueAt)
                    .atZone(ZoneId.systemDefault())
                    .dayOfMonth,
                everyMonths = 1,
                basis = RecurrenceBasis.SCHEDULED_DATE
            )
        )

        val (entity, subtasks) = TaskEntityMapper.toEntities(original)

        assertEquals(original, TaskEntityMapper.toDomain(TaskWithSubtasks(entity, subtasks)))
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
        recurrenceEndAt = if (recurrenceRule is RecurrenceRule.None) null else 2_000,
        seriesId = "series-1",
        createdAt = 0,
        updatedAt = 0,
        subtasks = subtasks
    )

    private fun subtask(
        id: Int,
        taskId: Int,
        title: String,
        position: Int
    ) = SubtaskEntity(
        id = id,
        taskId = taskId,
        title = title,
        position = position
    )

    private fun taskEntity(
        dueAt: Long?,
        recurrence: String,
        recurrenceEndAt: Long? = null
    ) = TaskEntity(
        id = 9,
        title = "Task",
        description = "Description",
        priority = TaskPriority.HIGH.name,
        dueAt = dueAt,
        recurrence = recurrence,
        recurrenceEndAt = recurrenceEndAt,
        createdAt = 1,
        updatedAt = 1
    )

    private val weeklyRule = RecurrenceRule.Interval(
        IntervalUnit.WEEKS,
        1,
        RecurrenceBasis.SCHEDULED_DATE
    )
}
