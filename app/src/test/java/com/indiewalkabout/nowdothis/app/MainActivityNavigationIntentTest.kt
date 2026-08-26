package com.indiewalkabout.nowdothis.app

import android.content.Intent
import com.indiewalkabout.nowdothis.app.navigation.TaskEditorRequest
import com.indiewalkabout.nowdothis.core.notifications.NotificationPublisher
import com.indiewalkabout.nowdothis.core.notifications.ReminderReceiver
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityNavigationIntentTest {
    @Test
    fun consumeNavigationIntent_routesWidgetAddOnColdLaunchOnce() {
        val intent = QuickCaptureWidgetIntents.add(org.robolectric.RuntimeEnvironment.getApplication())

        assertEquals(TaskEditorRequest.Add, consumeNavigationIntent(intent))
        assertNull(consumeNavigationIntent(intent))
        assertNull(intent.action)
        assertNull(intent.data)
    }

    @Test
    fun consumeNavigationIntent_routesWidgetOpenOnNewIntentOnce() {
        val intent = QuickCaptureWidgetIntents.open(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            taskId = 42
        )

        assertEquals(TaskEditorRequest.Open(42), consumeNavigationIntent(intent))
        assertNull(consumeNavigationIntent(intent))
        assertFalse(intent.hasExtra(QuickCaptureWidgetIntents.EXTRA_TASK_ID))
    }

    @Test
    fun consumeNavigationIntent_preservesReminderTaskRouting() {
        val intent = Intent(NotificationPublisher.OPEN_TASK_ACTION)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, 7)

        assertEquals(TaskEditorRequest.Open(7), consumeNavigationIntent(intent))
        assertNull(consumeNavigationIntent(intent))
        assertFalse(intent.hasExtra(ReminderReceiver.EXTRA_TASK_ID))
    }

    @Test
    fun clearConsumedNavigationIntent_preservesUnrelatedLauncherIntent() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        clearConsumedNavigationIntent(intent)

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(setOf(Intent.CATEGORY_LAUNCHER), intent.categories)
    }
}
