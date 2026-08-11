package com.indiewalkabout.nowdothis.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidReminderPermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmGateway: AlarmGateway
) : ReminderPermissionChecker {
    override fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

    override fun needsExactAlarmAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmGateway.canScheduleExact
}
