package com.indiewalkabout.nowdothis.feature.task.domain.model

data class TaskFilter(
    val query: String = "",
    val categoryId: Int? = null,
    val sort: TaskSort = TaskSort.DEFAULT
)
