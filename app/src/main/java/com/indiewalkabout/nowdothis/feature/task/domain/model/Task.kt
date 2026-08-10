package com.indiewalkabout.nowdothis.feature.task.domain.model

data class Task(
    val id: Int = 0,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val categoryId: Int? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val reminderStatus: ReminderStatus = ReminderStatus.NONE,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val recurrenceEndAt: Long? = null,
    val seriesId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val subtasks: List<Subtask> = emptyList()
)
