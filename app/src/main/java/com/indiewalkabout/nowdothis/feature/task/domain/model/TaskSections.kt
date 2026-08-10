package com.indiewalkabout.nowdothis.feature.task.domain.model

data class TaskSections(
    val overdue: List<Task> = emptyList(),
    val today: List<Task> = emptyList(),
    val upcoming: List<Task> = emptyList(),
    val unscheduled: List<Task> = emptyList(),
    val completedToday: List<Task> = emptyList()
)
