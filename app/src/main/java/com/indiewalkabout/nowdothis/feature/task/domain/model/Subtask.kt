package com.indiewalkabout.nowdothis.feature.task.domain.model

data class Subtask(
    val id: Int = 0,
    val taskId: Int = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val position: Int
)
