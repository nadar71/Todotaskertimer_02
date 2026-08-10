package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort

object TaskSectionClassifier {
    fun classify(
        tasks: List<Task>,
        bounds: DayBounds,
        sort: TaskSort = TaskSort.DEFAULT
    ): TaskSections {
        val overdue = mutableListOf<Task>()
        val today = mutableListOf<Task>()
        val upcoming = mutableListOf<Task>()
        val unscheduled = mutableListOf<Task>()
        val completedToday = mutableListOf<Task>()

        tasks.forEach { task ->
            if (task.isCompleted) {
                if (task.completedAt.isWithin(bounds)) completedToday += task
                return@forEach
            }

            val dueAt = task.dueAt
            when {
                dueAt == null -> unscheduled += task
                dueAt < bounds.startInclusive -> overdue += task
                dueAt < bounds.endExclusive -> today += task
                else -> upcoming += task
            }
        }

        return TaskSections(
            overdue = overdue.sorted(sort),
            today = today.sorted(sort),
            upcoming = upcoming.sorted(sort),
            unscheduled = unscheduled.sorted(sort),
            completedToday = completedToday.sorted(sort)
        )
    }

    private fun List<Task>.sorted(sort: TaskSort): List<Task> = when (sort) {
        TaskSort.DEFAULT -> this
        TaskSort.LOW_FIRST -> sortedByDescending { it.priority.sortOrder }
        TaskSort.HIGH_FIRST -> sortedBy { it.priority.sortOrder }
    }

    private val TaskPriority.sortOrder: Int
        get() = when (this) {
            TaskPriority.HIGH -> 0
            TaskPriority.MEDIUM -> 1
            TaskPriority.LOW -> 2
        }

    private fun Long?.isWithin(bounds: DayBounds): Boolean =
        this != null && this >= bounds.startInclusive && this < bounds.endExclusive
}
