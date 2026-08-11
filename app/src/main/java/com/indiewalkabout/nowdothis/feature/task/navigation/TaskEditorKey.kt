package com.indiewalkabout.nowdothis.feature.task.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class TaskEditorKey(
    val taskId: Int?,
    val initialDueAt: Long?
) : NavKey
