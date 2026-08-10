package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.feature.task.domain.model.Task

sealed interface ReminderNotificationContent {
    data class TaskTitle(val value: String) : ReminderNotificationContent
    data object Fallback : ReminderNotificationContent

    companion object {
        fun from(task: Task?): ReminderNotificationContent? {
            if (task == null || task.isCompleted) return null
            val title = task.title.trim().takeIf(String::isNotEmpty)
            return title?.let(::TaskTitle) ?: Fallback
        }
    }
}
