package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TaskEditorTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createEditor_showsTitleAndDispatchesBackAndSave() {
        val events = mutableListOf<TaskEditorEvent>()
        var backCalls = 0
        setScreen(onEvent = events::add, onBack = { backCalls += 1 })

        composeRule.onNodeWithText(context.getString(R.string.task_editor_create_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("task-editor-back").performClick()
        composeRule.onNodeWithTag("task-editor-save").performClick()

        assertEquals(1, backCalls)
        assertEquals(listOf(TaskEditorEvent.Save), events)

        setScreen(state = TaskEditorUiState(isLoading = false, taskId = 7))
        composeRule.onNodeWithText(context.getString(R.string.task_editor_edit_title))
            .assertIsDisplayed()
    }

    @Test
    fun validationErrors_areRenderedInline() {
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                errors = TaskEditorErrors(
                    title = TaskEditorFieldError.REQUIRED,
                    description = TaskEditorFieldError.REQUIRED,
                    reminder = TaskEditorFieldError.REMINDER_AFTER_DUE,
                    recurrence = TaskEditorFieldError.DUE_REQUIRED,
                    recurrenceEnd = TaskEditorFieldError.END_BEFORE_DUE
                ),
                recurrence = RecurrenceType.DAILY
            )
        )

        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_title_required))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_description_required))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_reminder_after_due))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_recurrence_due_required))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_end_before_due))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun priorityAndCategoryControls_dispatchSelections() {
        val events = mutableListOf<TaskEditorEvent>()
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                categories = listOf(category(3, "Client"))
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("task-priority-high").performClick()
        composeRule.onNodeWithTag("task-category-field").performClick()
        composeRule.onNodeWithText("Client").performClick()

        assertEquals(
            listOf(
                TaskEditorEvent.SelectPriority(TaskPriority.HIGH),
                TaskEditorEvent.SelectCategory(3)
            ),
            events
        )
    }

    @Test
    fun scheduleControls_clearValuesAndSelectRecurrence() {
        val events = mutableListOf<TaskEditorEvent>()
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                dueAt = 90_000L,
                reminderAt = 80_000L
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("task-due-clear").performClick()
        composeRule.onNodeWithTag("task-reminder-clear").performClick()
        composeRule.onNodeWithTag("task-recurrence-field").performClick()
        composeRule.onNodeWithText(context.getString(R.string.task_recurrence_weekly)).performClick()

        assertEquals(
            listOf(
                TaskEditorEvent.UpdateDueAt(null),
                TaskEditorEvent.UpdateReminderAt(null),
                TaskEditorEvent.SelectRecurrence(RecurrenceType.WEEKLY)
            ),
            events
        )
    }

    @Test
    fun subtaskControls_dispatchStableIdEvents() {
        val events = mutableListOf<TaskEditorEvent>()
        val state = TaskEditorUiState(
            isLoading = false,
            subtasks = listOf(
                TaskEditorSubtask(draftId = -1, title = "First"),
                TaskEditorSubtask(draftId = -2, title = "Second")
            )
        )
        setScreen(
            state = state,
            onEvent = events::add
        )

        composeRule.onNodeWithTag("subtask-title--1").performTextReplacement("Renamed")
        assertEquals(listOf(TaskEditorEvent.RenameSubtask(-1, "Renamed")), events)

        events.clear()
        setScreen(
            state = state.copy(
                subtasks = state.subtasks.map {
                    if (it.draftId == -1L) it.copy(title = "Renamed") else it
                }
            ),
            onEvent = events::add
        )
        composeRule.onNodeWithTag("subtask-add").performScrollTo().performClick()
        composeRule.onNodeWithTag("subtask-toggle--1").performClick()
        composeRule.onNodeWithTag("subtask-up--2").performClick()
        composeRule.onNodeWithTag("subtask-delete--1").performClick()

        assertEquals(
            listOf(
                TaskEditorEvent.AddSubtask,
                TaskEditorEvent.ToggleSubtask(-1),
                TaskEditorEvent.MoveSubtask(-2, -1),
                TaskEditorEvent.DeleteSubtask(-1)
            ),
            events
        )
    }

    @Test
    fun unavailableReminder_isVisibleWithoutDisablingSave() {
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                reminderAt = 80_000L,
                reminderStatus = ReminderStatus.UNAVAILABLE,
                notificationPermissionDenied = true,
                exactTimingUnavailable = true
            )
        )

        composeRule.onNodeWithText(context.getString(R.string.task_editor_reminder_not_active))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("task-reminder-status")
            .assertTextEquals(context.getString(R.string.task_editor_reminder_not_active))
        composeRule.onNodeWithText(context.getString(R.string.task_editor_notification_denied))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_editor_inexact_notice))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("task-editor-save")
            .assertTextEquals(context.getString(R.string.task_editor_save))
    }

    private fun setScreen(
        state: TaskEditorUiState = TaskEditorUiState(isLoading = false),
        onEvent: (TaskEditorEvent) -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.runOnUiThread {
            TaskEditorTestContent.content = {
                MaterialTheme {
                    TaskEditorScreen(
                        state = state,
                        snackbarHostState = remember { SnackbarHostState() },
                        onEvent = onEvent,
                        onBack = onBack
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}

private fun category(id: Int, name: String) = Category(
    id = id,
    customName = name,
    color = CategoryColor.BLUE,
    position = 0,
    createdAt = 1
)
