package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun quickEntry_isShownForNewTasksAndAbsentForExistingTasks() {
        setScreen()

        composeRule.onNodeWithTag("quick-entry-input").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-entry-parse").assertIsDisplayed()

        setScreen(state = TaskEditorUiState(isLoading = false, taskId = 7))

        composeRule.onAllNodesWithTag("quick-entry-input").assertCountEquals(0)
        composeRule.onAllNodesWithTag("quick-entry-parse").assertCountEquals(0)
    }

    @Test
    fun quickEntry_dispatchesInputAndExplicitParseWhileBlankInputDisablesParse() {
        val events = mutableListOf<TaskEditorEvent>()
        setScreen(onEvent = events::add)

        composeRule.onNodeWithTag("quick-entry-parse").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick-entry-input")
            .performTextReplacement("Compra latte domani")
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                quickEntryInput = "Compra latte domani"
            ),
            onEvent = events::add
        )
        composeRule.onNodeWithTag("quick-entry-parse").performClick()

        assertEquals(
            listOf(
                TaskEditorEvent.UpdateQuickEntry("Compra latte domani"),
                TaskEditorEvent.ParseQuickEntry
            ),
            events
        )
    }

    @Test
    fun quickEntry_rendersItalianAndEnglishInProductionCompose() {
        withApplicationLocale("it") {
            assertLocalizedQuickEntry()
        }
        withApplicationLocale("en") {
            assertLocalizedQuickEntry()
        }
    }

    @Test
    fun quickEntry_rendersConciseSummaryAndIssuesWithLiveSemantics() {
        val summary = context.getString(
            R.string.quick_entry_summary,
            listOf(
                context.getString(R.string.quick_entry_field_title),
                context.getString(R.string.quick_entry_field_due_date)
            ).joinToString()
        )
        val issue = context.getString(R.string.quick_entry_issue_unknown_category)
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                quickEntrySummary = listOf(
                    QuickEntrySummaryField.TITLE,
                    QuickEntrySummaryField.DUE_DATE
                ),
                quickEntryIssues = listOf(QuickEntryIssue.UNKNOWN_CATEGORY)
            )
        )

        composeRule.onNodeWithTag("quick-entry-summary")
            .assertTextEquals(summary)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
        composeRule.onNodeWithTag("quick-entry-issues")
            .assertTextEquals(issue)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
    }

    @Test
    fun quickEntry_parseActionHasAtLeast48DpTarget() {
        setScreen(state = TaskEditorUiState(isLoading = false, quickEntryInput = "Compra latte"))

        composeRule.onNodeWithTag("quick-entry-parse").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun quickEntry_largeTextFitsWithinOneSurfaceWithoutOverlapOrClipping() {
        withApplicationLocale("it") {
            setQuickEntrySectionSurface(fontScale = 2f)

            val root = bounds("quick-entry-layout-root")
            val section = bounds("quick-entry-section")
            val input = bounds("quick-entry-input")
            val parse = bounds("quick-entry-parse")
            val summary = bounds("quick-entry-summary")
            val issues = bounds("quick-entry-issues")

            assertContains(root, section)
            listOf(input, parse, summary, issues).forEach { child ->
                assertContains(section, child)
            }
            assertOrdered(input, parse)
            assertOrdered(parse, summary)
            assertOrdered(summary, issues)
        }
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
        scrollEditorTo("task-reminder-section")
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_reminder_after_due))
            .assertIsDisplayed()
        scrollEditorTo("task-recurrence-field")
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
        composeRule.onNodeWithTag("task-priority-high")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
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
        scrollEditorTo("task-recurrence-field")
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

        scrollEditorTo("subtask-title--1")
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

        scrollEditorTo("task-reminder-section")
        composeRule.onNodeWithText(context.getString(R.string.task_editor_reminder_not_active))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("task-reminder-status")
            .assertTextEquals(context.getString(R.string.task_editor_reminder_not_active))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.task_editor_reminder_not_active)
                )
            )
        composeRule.onNodeWithText(context.getString(R.string.task_editor_notification_denied))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_editor_inexact_notice))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("task-editor-save")
            .assertTextEquals(context.getString(R.string.task_editor_save))
    }

    private fun setScreen(
        state: TaskEditorUiState = TaskEditorUiState(isLoading = false),
        onEvent: (TaskEditorEvent) -> Unit = {},
        onBack: () -> Unit = {},
        fontScale: Float = 1f
    ) {
        composeRule.runOnUiThread {
            TaskEditorTestContent.content = {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
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
        }
        composeRule.waitForIdle()
    }

    private fun scrollEditorTo(tag: String) {
        composeRule.onNodeWithTag("task-editor-form")
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitForIdle()
    }

    private fun assertLocalizedQuickEntry() {
        val activity = composeRule.activity
        val label = activity.getString(R.string.quick_entry_label)
        val hint = activity.getString(R.string.quick_entry_hint)
        val parse = activity.getString(R.string.quick_entry_parse)
        val summary = activity.getString(
            R.string.quick_entry_summary,
            QuickEntrySummaryField.entries.joinToString { field ->
                activity.getString(field.stringRes())
            }
        )
        val issue = activity.getString(R.string.quick_entry_issue_duplicate_field)
        setQuickEntrySectionSurface(fontScale = 1f)
        composeRule.onNodeWithTag("quick-entry-input").performClick()

        val root = bounds("quick-entry-layout-root")
        val labels = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
        assertEquals(2, labels.size)
        labels.forEach { labelNode -> assertContains(root, labelNode.boundsInRoot) }
        composeRule.onNodeWithText(hint).assertIsDisplayed()
        composeRule.onNodeWithText(parse).assertIsDisplayed()
        composeRule.onNodeWithTag("quick-entry-summary")
            .assertTextEquals(summary)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("quick-entry-issues")
            .assertTextEquals(issue)
            .assertIsDisplayed()
    }

    private fun setQuickEntrySectionSurface(fontScale: Float) {
        composeRule.runOnUiThread {
            TaskEditorTestContent.content = {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    MaterialTheme {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .testTag("quick-entry-layout-root")
                        ) {
                            QuickEntrySection(
                                input = "",
                                summary = QuickEntrySummaryField.entries,
                                issues = listOf(QuickEntryIssue.DUPLICATE_FIELD),
                                onEvent = {},
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun withApplicationLocale(languageTags: String, block: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val localeManager = instrumentation.targetContext.applicationContext
            .getSystemService(LocaleManager::class.java)
        val previousLocales = localeManager.applicationLocales
        try {
            localeManager.applicationLocales = LocaleList.forLanguageTags(languageTags)
            instrumentation.waitForIdleSync()
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            block()
        } finally {
            localeManager.applicationLocales = previousLocales
            instrumentation.waitForIdleSync()
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
        }
    }

    private fun bounds(tag: String) = composeRule.onAllNodesWithTag(tag)
        .fetchSemanticsNodes()
        .single()
        .boundsInRoot

    private fun assertContains(
        parent: androidx.compose.ui.geometry.Rect,
        child: androidx.compose.ui.geometry.Rect
    ) {
        assertTrue(child.left >= parent.left)
        assertTrue(child.top >= parent.top)
        assertTrue(child.right <= parent.right)
        assertTrue(child.bottom <= parent.bottom)
    }

    private fun assertOrdered(
        upper: androidx.compose.ui.geometry.Rect,
        lower: androidx.compose.ui.geometry.Rect
    ) {
        assertTrue(upper.bottom <= lower.top)
    }
}

private fun QuickEntrySummaryField.stringRes(): Int = when (this) {
    QuickEntrySummaryField.TITLE -> R.string.quick_entry_field_title
    QuickEntrySummaryField.DUE_DATE -> R.string.quick_entry_field_due_date
    QuickEntrySummaryField.REMINDER -> R.string.quick_entry_field_reminder
    QuickEntrySummaryField.PRIORITY -> R.string.quick_entry_field_priority
    QuickEntrySummaryField.CATEGORY -> R.string.quick_entry_field_category
    QuickEntrySummaryField.RECURRENCE -> R.string.quick_entry_field_recurrence
}

private fun category(id: Int, name: String) = Category(
    id = id,
    customName = name,
    color = CategoryColor.BLUE,
    position = 0,
    createdAt = 1
)
