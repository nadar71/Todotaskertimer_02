package com.indiewalkabout.nowdothis.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.core.notifications.NotificationPublisher
import com.indiewalkabout.nowdothis.core.notifications.ReminderReceiver
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetIntents
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityQuickCaptureNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val database: AppDatabase by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugDatabaseEntryPoint::class.java
        ).database()
    }

    @Before
    fun resetLocalState() = runBlocking {
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    @After
    fun closeActivityAndResetLocalState() {
        if (::scenario.isInitialized) scenario.close()
        runBlocking {
            withContext(Dispatchers.IO) { database.clearAllTables() }
        }
    }

    @Test
    fun widgetAndReminderIntents_routeThroughMainActivityWithoutReplay() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitForTag("task-search")

        deliverNewIntent(QuickCaptureWidgetIntents.add(context))
        assertNewTaskEditor()
        assertIntentConsumed()
        scenario.recreate()
        assertNewTaskEditor()
        navigateBackToTaskList()

        val widgetTaskTitle = "Widget task"
        deliverNewIntent(QuickCaptureWidgetIntents.open(context, seedTask(widgetTaskTitle)))
        assertExistingTaskEditor(widgetTaskTitle)
        assertIntentConsumed()
        scenario.recreate()
        assertExistingTaskEditor(widgetTaskTitle)
        navigateBackToTaskList()

        val reminderTaskTitle = "Reminder task"
        deliverNewIntent(
            Intent(context, MainActivity::class.java)
                .setAction(NotificationPublisher.OPEN_TASK_ACTION)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, seedTask(reminderTaskTitle))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        assertExistingTaskEditor(reminderTaskTitle)
        assertIntentConsumed()
        scenario.recreate()
        assertExistingTaskEditor(reminderTaskTitle)
        navigateBackToTaskList()
    }

    private fun deliverNewIntent(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
    }

    private fun assertIntentConsumed() = scenario.onActivity { activity ->
        assertNull(activity.intent.action)
        assertNull(activity.intent.data)
    }

    private fun seedTask(title: String): Int = runBlocking {
        withContext(Dispatchers.IO) {
            database.taskDao().insertTask(
                TaskEntity(
                    title = title,
                    description = "Task opened from an activity intent",
                    priority = "MEDIUM"
                )
            ).toInt()
        }
    }

    private fun assertNewTaskEditor() {
        waitForTag("task-title")
        waitForText(context.getString(R.string.task_editor_create_title))
    }

    private fun assertExistingTaskEditor(title: String) {
        waitForTag("task-title")
        waitForText(context.getString(R.string.task_editor_edit_title))
        waitForText(title)
    }

    private fun navigateBackToTaskList() {
        waitForTag("task-editor-back").click()
        waitForTag("task-search")
    }

    private fun waitForTag(tag: String): UiObject2 = requireNotNull(
        device.wait(Until.findObject(By.res(tag)), WAIT_TIMEOUT_MILLIS)
    ) { "Timed out waiting for test tag '$tag'" }

    private fun waitForText(text: String): UiObject2 = requireNotNull(
        device.wait(Until.findObject(By.text(text)), WAIT_TIMEOUT_MILLIS)
    ) { "Timed out waiting for text '$text'" }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 10_000L
    }
}
