package com.indiewalkabout.nowdothis.feature.task.domain.repository

interface ReminderPermissionChecker {
    fun needsNotificationPermission(): Boolean
    fun needsExactAlarmAccess(): Boolean
}
