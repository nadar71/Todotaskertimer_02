package com.indiewalkabout.nowdothis.feature.task.domain.repository

import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import kotlinx.coroutines.flow.Flow

interface TaskPreferencesRepository {
    val taskSort: Flow<TaskSort>
    suspend fun setTaskSort(sort: TaskSort)
}
