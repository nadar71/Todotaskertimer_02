package com.indiewalkabout.nowdothis.feature.task.domain.repository

import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskScheduleReader {
    fun observeMonth(startInclusive: Long, endExclusive: Long): Flow<List<Task>>
    fun observeDay(startInclusive: Long, endExclusive: Long): Flow<List<Task>>
}
