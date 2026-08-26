package com.indiewalkabout.nowdothis.feature.portability.domain.model

import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule

data class PlanningBackup(
    val format: String,
    val version: Int,
    val createdAtEpochMillis: Long,
    val categories: List<PlanningCategory>,
    val tasks: List<PlanningTask>
)

data class PlanningCategory(
    val id: Int,
    val customName: String?,
    val defaultKey: String?,
    val colorToken: String,
    val position: Int,
    val createdAt: Long
)

data class PlanningTask(
    val id: Int,
    val title: String,
    val description: String,
    val priority: String,
    val categoryId: Int?,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val dueAt: Long?,
    val reminderAt: Long?,
    val reminderStatus: String,
    val recurrenceRule: RecurrenceRule,
    val recurrenceEndAt: Long?,
    val seriesId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val subtasks: List<PlanningSubtask>
)

data class PlanningSubtask(
    val id: Int,
    val taskId: Int,
    val title: String,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val position: Int
)

data class BackupSummary(
    val createdAtEpochMillis: Long,
    val categoryCount: Int,
    val taskCount: Int,
    val completedTaskCount: Int,
    val subtaskCount: Int
)
