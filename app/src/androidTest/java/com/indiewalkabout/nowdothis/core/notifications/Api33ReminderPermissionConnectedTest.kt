package com.indiewalkabout.nowdothis.core.notifications

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Api33ReminderPermissionConnectedTest {
    @Test
    fun notificationPermission_forcedDenialThenGrant_updatesCheckerAndRestoresIncomingState() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val state = NotificationPermissionTestState.create()
        val incomingState = state.isGranted
        val checker = checker()

        state.withForced(granted = false) {
            assertFalse(state.isGranted)
            assertTrue(checker.needsNotificationPermission())

            state.force(granted = true)

            assertTrue(state.isGranted)
            assertFalse(checker.needsNotificationPermission())
        }

        assertEquals(incomingState, state.isGranted)
    }

    @Test
    fun notificationPermission_forcedGrantThenDenial_updatesCheckerAndRestoresIncomingState() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val state = NotificationPermissionTestState.create()
        val incomingState = state.isGranted
        val checker = checker()

        state.withForced(granted = true) {
            assertTrue(state.isGranted)
            assertFalse(checker.needsNotificationPermission())

            state.force(granted = false)

            assertFalse(state.isGranted)
            assertTrue(checker.needsNotificationPermission())
        }

        assertEquals(incomingState, state.isGranted)
    }

    @Test
    fun notificationPermission_failedBody_restoresIncomingState() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val state = NotificationPermissionTestState.create()
        val incomingState = state.isGranted

        assertThrows(IntentionalPermissionTestFailure::class.java) {
            state.withForced(granted = !incomingState) {
                assertEquals(!incomingState, state.isGranted)
                throw IntentionalPermissionTestFailure()
            }
        }

        assertEquals(incomingState, state.isGranted)
    }

    private fun checker(): AndroidReminderPermissionChecker {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        return AndroidReminderPermissionChecker(
            context = context,
            alarmGateway = ConnectedAlarmGateway()
        )
    }
}

private class IntentionalPermissionTestFailure : RuntimeException()

private class ConnectedAlarmGateway : AlarmGateway {
    override val canScheduleExact: Boolean = false
    override fun setExact(taskId: Int, triggerAt: Long): Boolean = false
    override fun setInexact(taskId: Int, triggerAt: Long): Boolean = false
    override fun cancel(taskId: Int) = Unit
}
