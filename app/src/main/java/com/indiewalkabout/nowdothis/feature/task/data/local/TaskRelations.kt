package com.indiewalkabout.nowdothis.feature.task.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithSubtasks(
    @Embedded
    val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "task_id"
    )
    val subtasks: List<SubtaskEntity>
)
