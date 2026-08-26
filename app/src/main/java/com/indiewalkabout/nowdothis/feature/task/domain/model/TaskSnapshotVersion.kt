package com.indiewalkabout.nowdothis.feature.task.domain.model

data class TaskSnapshotVersion(
    val id: Int,
    val seriesId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val recurrenceRule: RecurrenceRule,
    val recurrenceEndAt: Long?
)

fun Task.snapshotVersion(): TaskSnapshotVersion = TaskSnapshotVersion(
    id = id,
    seriesId = seriesId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isCompleted = isCompleted,
    completedAt = completedAt,
    recurrenceRule = recurrenceRule,
    recurrenceEndAt = recurrenceEndAt
)
