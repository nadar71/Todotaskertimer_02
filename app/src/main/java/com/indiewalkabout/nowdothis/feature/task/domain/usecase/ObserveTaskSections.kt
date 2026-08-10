package com.indiewalkabout.nowdothis.feature.task.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class ObserveTaskSections(
    private val repository: TaskRepository,
    private val preferencesRepository: TaskPreferencesRepository,
    private val clock: AppClock,
    private val zoneIdProvider: ZoneIdProvider
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(filter: TaskFilter): Flow<TaskSections> {
        val bounds = DayBounds.forEpochMillis(clock.nowMillis(), zoneIdProvider.zoneId())
        return preferencesRepository.taskSort.flatMapLatest { sort ->
            repository.observeSections(filter.copy(sort = sort), bounds)
        }
    }
}
