package com.indiewalkabout.nowdothis.feature.quickcapture.data

import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ObserveTaskSections
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class TaskSectionsQuickCaptureSource @Inject constructor(
    private val observeTaskSections: ObserveTaskSections
) : QuickCaptureTaskSource {
    override fun observe(): Flow<TaskSections> = observeTaskSections(TaskFilter())
}
