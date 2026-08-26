package com.indiewalkabout.nowdothis.core.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Api33ReminderPermissionConnectedTest {
    @Test
    fun notificationPermission_freshDenialThenPlatformGrant_updatesChecker() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val packageManager = context.packageManager
        val checker = AndroidReminderPermissionChecker(
            context = context,
            alarmGateway = ConnectedAlarmGateway()
        )

        assertEquals(
            PackageManager.PERMISSION_DENIED,
            packageManager.checkPermission(
                Manifest.permission.POST_NOTIFICATIONS,
                context.packageName
            )
        )
        assertTrue(checker.needsNotificationPermission())

        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
        instrumentation.waitForIdleSync()

        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            packageManager.checkPermission(
                Manifest.permission.POST_NOTIFICATIONS,
                context.packageName
            )
        )
        assertFalse(checker.needsNotificationPermission())
    }
}

private class ConnectedAlarmGateway : AlarmGateway {
    override val canScheduleExact: Boolean = false
    override fun setExact(taskId: Int, triggerAt: Long): Boolean = false
    override fun setInexact(taskId: Int, triggerAt: Long): Boolean = false
    override fun cancel(taskId: Int) = Unit
}
