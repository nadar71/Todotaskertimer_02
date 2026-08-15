package com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository

import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import kotlinx.coroutines.flow.Flow

fun interface QuickCaptureTaskSource {
    fun observe(): Flow<TaskSections>
}
