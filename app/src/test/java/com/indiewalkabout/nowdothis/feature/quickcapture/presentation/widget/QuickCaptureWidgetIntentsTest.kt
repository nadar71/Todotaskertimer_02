package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.content.Intent
import android.net.Uri
import com.indiewalkabout.nowdothis.app.MainActivity
import com.indiewalkabout.nowdothis.app.navigation.TaskEditorRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class QuickCaptureWidgetIntentsTest {
    @Test
    fun add_createsAnExplicitUniqueAddIntentThatParsesAsAdd() {
        val intent = QuickCaptureWidgetIntents.add(RuntimeEnvironment.getApplication())

        assertEquals(TaskEditorRequest.Add, QuickCaptureWidgetIntents.parse(intent))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Uri.parse("nowdothis://quick-capture/add"), intent.data)
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            intent.flags and (Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    @Test
    fun open_createsAnExplicitUniqueOpenIntentThatParsesPositiveTaskId() {
        val intent = QuickCaptureWidgetIntents.open(
            context = RuntimeEnvironment.getApplication(),
            taskId = 42
        )

        assertEquals(TaskEditorRequest.Open(42), QuickCaptureWidgetIntents.parse(intent))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Uri.parse("nowdothis://quick-capture/task/42"), intent.data)
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            intent.flags and (Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    @Test
    fun parse_rejectsOpenIntentsWithoutPositiveTaskIds() {
        val missingId = Intent(QuickCaptureWidgetIntents.ACTION_OPEN)
        val zeroId = Intent(QuickCaptureWidgetIntents.ACTION_OPEN)
            .putExtra(QuickCaptureWidgetIntents.EXTRA_TASK_ID, 0)
        val negativeId = Intent(QuickCaptureWidgetIntents.ACTION_OPEN)
            .putExtra(QuickCaptureWidgetIntents.EXTRA_TASK_ID, -1)

        assertNull(QuickCaptureWidgetIntents.parse(missingId))
        assertNull(QuickCaptureWidgetIntents.parse(zeroId))
        assertNull(QuickCaptureWidgetIntents.parse(negativeId))
    }

    @Test
    fun parse_rejectsUnrelatedActions() {
        val intent = Intent("another-action")
            .putExtra(QuickCaptureWidgetIntents.EXTRA_TASK_ID, 42)

        assertNull(QuickCaptureWidgetIntents.parse(intent))
    }
}
