package com.indiewalkabout.nowdothis.feature.task.domain.repository

import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import kotlinx.coroutines.flow.Flow

interface CompletionHistoryReader {
    fun observeHistory(before: Long, filter: TaskFilter): Flow<List<Task>>
}
