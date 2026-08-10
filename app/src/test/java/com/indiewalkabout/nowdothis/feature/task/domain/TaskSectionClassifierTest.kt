package com.indiewalkabout.nowdothis.feature.task.domain

import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.TaskSectionClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSectionClassifierTest {
    private val bounds = DayBounds(startInclusive = 1_000, endExclusive = 2_000)

    @Test
    fun classify_placesUndatedPendingTaskInUnscheduled() {
        val result = TaskSectionClassifier.classify(
            tasks = listOf(task(dueAt = null)),
            bounds = DayBounds(1_000, 2_000)
        )
        assertEquals(1, result.unscheduled.size)
        assertTrue(result.overdue.isEmpty())
    }

    @Test
    fun classify_placesPendingTasksInDueDateSections() {
        val result = TaskSectionClassifier.classify(
            tasks = listOf(
                task(id = 1, dueAt = 999),
                task(id = 2, dueAt = 1_000),
                task(id = 3, dueAt = 1_999),
                task(id = 4, dueAt = 2_000),
                task(id = 5, dueAt = null)
            ),
            bounds = bounds
        )

        assertEquals(listOf(1), result.overdue.map(Task::id))
        assertEquals(listOf(2, 3), result.today.map(Task::id))
        assertEquals(listOf(4), result.upcoming.map(Task::id))
        assertEquals(listOf(5), result.unscheduled.map(Task::id))
    }

    @Test
    fun classify_placesTasksCompletedTodayInCompletedToday() {
        val result = TaskSectionClassifier.classify(
            tasks = listOf(
                task(id = 1, dueAt = 900, isCompleted = true, completedAt = 1_500),
                task(id = 2, dueAt = 1_500, isCompleted = true, completedAt = 999)
            ),
            bounds = bounds
        )

        assertEquals(listOf(1), result.completedToday.map(Task::id))
        assertTrue(result.overdue.isEmpty())
        assertTrue(result.today.isEmpty())
    }

    @Test
    fun classify_defaultSort_preservesOrderWithinSections() {
        val result = TaskSectionClassifier.classify(
            tasks = listOf(
                task(id = 1, dueAt = 900, priority = TaskPriority.HIGH),
                task(id = 2, dueAt = 901, priority = TaskPriority.LOW),
                task(id = 3, dueAt = 902, priority = TaskPriority.MEDIUM)
            ),
            bounds = bounds,
            sort = TaskSort.DEFAULT
        )

        assertEquals(listOf(1, 2, 3), result.overdue.map(Task::id))
    }

    @Test
    fun classify_lowFirstSort_ordersPrioritiesAscending() {
        val result = TaskSectionClassifier.classify(
            tasks = listOf(
                task(id = 1, dueAt = 900, priority = TaskPriority.HIGH),
                task(id = 2, dueAt = 901, priority = TaskPriority.LOW),
                task(id = 3, dueAt = 902, priority = TaskPriority.MEDIUM)
            ),
            bounds = bounds,
            sort = TaskSort.LOW_FIRST
        )

        assertEquals(listOf(2, 3, 1), result.overdue.map(Task::id))
    }

    @Test
    fun classify_highFirstSort_ordersPrioritiesDescending() {
        val result = TaskSectionClassifier.classify(
            tasks = listOf(
                task(id = 1, dueAt = 900, priority = TaskPriority.LOW),
                task(id = 2, dueAt = 901, priority = TaskPriority.MEDIUM),
                task(id = 3, dueAt = 902, priority = TaskPriority.HIGH)
            ),
            bounds = bounds,
            sort = TaskSort.HIGH_FIRST
        )

        assertEquals(listOf(3, 2, 1), result.overdue.map(Task::id))
    }

    private fun task(
        id: Int = 0,
        dueAt: Long?,
        priority: TaskPriority = TaskPriority.MEDIUM,
        isCompleted: Boolean = false,
        completedAt: Long? = null
    ) = Task(
        id = id,
        title = "Task $id",
        description = "Description",
        priority = priority,
        isCompleted = isCompleted,
        completedAt = completedAt,
        dueAt = dueAt,
        createdAt = 0,
        updatedAt = 0
    )
}
