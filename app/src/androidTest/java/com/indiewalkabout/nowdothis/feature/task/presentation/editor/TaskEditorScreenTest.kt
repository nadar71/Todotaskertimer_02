package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
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
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class TaskEditorScreenTest {
    private val composeRule = createAndroidComposeRule<TaskEditorTestActivity>()
    private val applicationLocaleRule = ApplicationLocaleRule(::quickEntryLocaleFor)

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(applicationLocaleRule).around(composeRule)

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
                categoryReadiness = CategoryReadiness.READY,
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
    fun quickEntry_categoryReadinessDisablesParseAndExposesRetrySemantics() {
        val events = mutableListOf<TaskEditorEvent>()
        val input = "Task #Work"
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                categoryReadiness = CategoryReadiness.LOADING,
                quickEntryInput = input
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("quick-entry-parse").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick-entry-categories-loading")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(context.getString(R.string.quick_entry_categories_loading))
                )
            )

        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                categoryReadiness = CategoryReadiness.ERROR,
                quickEntryInput = input
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("quick-entry-parse").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick-entry-categories-error")
            .assertTextEquals(context.getString(R.string.category_load_failed))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
        composeRule.onNodeWithTag("quick-entry-categories-retry")
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf(TaskEditorEvent.RetryCategoryLoad), events)

        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                categoryReadiness = CategoryReadiness.READY,
                quickEntryInput = input
            )
        )
        composeRule.onNodeWithTag("quick-entry-parse").assertIsEnabled()
        composeRule.onAllNodesWithTag("quick-entry-categories-error").assertCountEquals(0)
        composeRule.onAllNodesWithTag("quick-entry-categories-retry").assertCountEquals(0)
    }

    @Test
    fun quickEntry_isDisabledWhileSaveOwnsTheDraft() {
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                isSaving = true,
                categoryReadiness = CategoryReadiness.READY,
                quickEntryInput = "Task tomorrow"
            )
        )

        composeRule.onNodeWithTag("quick-entry-input").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick-entry-parse").assertIsNotEnabled()
    }

    @Test
    fun quickEntry_rendersItalianInProductionCompose() {
        assertLocaleExpectationsDiffer()
        assertLocalizedQuickEntry(ITALIAN_QUICK_ENTRY)
    }

    @Test
    fun quickEntry_rendersEnglishInProductionCompose() {
        assertLocalizedQuickEntry(ENGLISH_QUICK_ENTRY)
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
        setScreen(
            state = TaskEditorUiState(
                isLoading = false,
                categoryReadiness = CategoryReadiness.READY,
                quickEntryInput = "Compra latte"
            )
        )

        composeRule.onNodeWithTag("quick-entry-parse").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun quickEntry_largeTextFitsWithinOneSurfaceWithoutOverlapOrClipping() {
        assertActiveApplicationLocale(ITALIAN_QUICK_ENTRY)
        setQuickEntrySectionSurface(fontScale = 2f)

        val root = nodeBounds("quick-entry-layout-root")
        val section = nodeBounds("quick-entry-section")
        val input = nodeBounds("quick-entry-input")
        val parse = nodeBounds("quick-entry-parse")
        val summary = nodeBounds("quick-entry-summary")
        val issues = nodeBounds("quick-entry-issues")

        listOf(root, section, input, parse, summary, issues).forEach(::assertNotClipped)
        assertContains(root.unclipped, section.unclipped)
        listOf(input, parse, summary, issues).forEach { child ->
            assertContains(section.unclipped, child.unclipped)
        }
        assertOrdered(input.unclipped, parse.unclipped)
        assertOrdered(parse.unclipped, summary.unclipped)
        assertOrdered(summary.unclipped, issues.unclipped)
    }

    @Test
    fun quickEntry_clippingDetectorDetectsConstrainedSurface() {
        assertActiveApplicationLocale(ITALIAN_QUICK_ENTRY)
        setQuickEntrySectionSurface(fontScale = 2f, height = 300.dp)

        val issues = nodeBounds("quick-entry-issues")

        assertNotEquals(issues.unclipped, issues.clipped)
        assertTrue(isClipped(issues))
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
                recurrence = RecurrenceEditorState.forKind(RecurrenceEditorKind.INTERVAL)
            )
        )

        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_title_required))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_description_required))
            .assertIsDisplayed()
        scrollEditorTo("task-reminder-section")
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_reminder_after_due))
            .assertIsDisplayed()
        scrollEditorTo("task-recurrence-kind")
        composeRule.onNodeWithText(context.getString(R.string.task_editor_error_recurrence_due_required))
            .performScrollTo()
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
        scrollEditorTo("task-recurrence-kind")
        composeRule.onNodeWithTag("task-recurrence-kind").performClick()
        composeRule.onNodeWithTag("task-recurrence-kind-option-interval").performClick()

        assertEquals(
            listOf(
                TaskEditorEvent.UpdateDueAt(null),
                TaskEditorEvent.UpdateReminderAt(null),
                TaskEditorEvent.SelectRecurrenceKind(RecurrenceEditorKind.INTERVAL)
            ),
            events
        )
    }

    @Test
    fun recurrenceKindMenu_dispatchesEveryRuleSelector() {
        RecurrenceEditorKind.entries.forEach { kind ->
            val events = mutableListOf<TaskEditorEvent>()
            setScreen(onEvent = events::add)

            scrollEditorTo("task-recurrence-kind")
            composeRule.onNodeWithTag("task-recurrence-kind").performClick()
            composeRule.onNodeWithTag(
                "task-recurrence-kind-option-${kind.name.lowercase()}"
            ).performClick()

            assertEquals(listOf(TaskEditorEvent.SelectRecurrenceKind(kind)), events)
        }
    }

    @Test
    fun intervalEditor_usesNumericStepperAndUnitMenu() {
        val events = mutableListOf<TaskEditorEvent>()
        setRecurrenceEditorSurface(
            state = RecurrenceEditorState(
                kind = RecurrenceEditorKind.INTERVAL,
                basis = RecurrenceEditorBasis.COMPLETION_DATE,
                intervalUnit = RecurrenceEditorIntervalUnit.DAYS,
                intervalEvery = 1
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("task-recurrence-interval-decrement")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("task-recurrence-interval-value")
            .performTextReplacement("999")
        composeRule.onNodeWithTag("task-recurrence-interval-increment")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("task-recurrence-interval-unit").performClick()
        composeRule.onNodeWithTag("task-recurrence-interval-unit-weeks").performClick()

        assertTrue(
            TaskEditorEvent.UpdateRecurrenceIntervalEvery(null) in events
        )
        assertTrue(
            TaskEditorEvent.UpdateRecurrenceIntervalEvery(999) in events
        )
        assertTrue(
            TaskEditorEvent.UpdateRecurrenceIntervalEvery(2) in events
        )
        assertEquals(
            listOf(
                TaskEditorEvent.SelectRecurrenceIntervalUnit(
                    RecurrenceEditorIntervalUnit.WEEKS
                )
            ),
            events.filterNot { it is TaskEditorEvent.UpdateRecurrenceIntervalEvery }
        )
    }

    @Test
    fun recurrenceBasis_usesSegmentedRadioSemanticsAndDispatchesOverride() {
        val events = mutableListOf<TaskEditorEvent>()
        setRecurrenceEditorSurface(
            state = RecurrenceEditorState(
                kind = RecurrenceEditorKind.INTERVAL,
                basis = RecurrenceEditorBasis.COMPLETION_DATE,
                intervalUnit = RecurrenceEditorIntervalUnit.DAYS,
                intervalEvery = 1
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("task-recurrence-basis-scheduled_date")
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()
        composeRule.onNodeWithTag("task-recurrence-basis-completion_date")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))

        assertEquals(
            listOf(
                TaskEditorEvent.SelectRecurrenceBasis(RecurrenceEditorBasis.SCHEDULED_DATE)
            ),
            events
        )
    }

    @Test
    fun weekdayEditor_exposesToggleSemanticsTargetsAndInlineError() {
        val events = mutableListOf<TaskEditorEvent>()
        setRecurrenceEditorSurface(
            state = RecurrenceEditorState(
                kind = RecurrenceEditorKind.SELECTED_WEEKDAYS,
                basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                selectedWeekdays = setOf(RecurrenceEditorWeekday.MONDAY)
            ),
            recurrenceError = TaskEditorFieldError.RECURRENCE_WEEKDAY_REQUIRED,
            onEvent = events::add
        )

        composeRule.onNodeWithTag("task-recurrence-weekday-monday")
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithTag("task-recurrence-weekday-friday")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.task_editor_error_recurrence_weekday_required)
        ).assertIsDisplayed()

        assertEquals(
            listOf(
                TaskEditorEvent.ToggleRecurrenceWeekday(RecurrenceEditorWeekday.FRIDAY)
            ),
            events
        )
    }

    @Test
    fun monthlyDayEditor_dispatchesAnchorAndIntervalControls() {
        val events = mutableListOf<TaskEditorEvent>()
        setRecurrenceEditorSurface(
            state = RecurrenceEditorState(
                kind = RecurrenceEditorKind.MONTHLY_DAY,
                basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                monthlyEvery = 1,
                monthlyAnchorDay = 31
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("task-recurrence-monthly-anchor-value")
            .performTextReplacement("1")
        composeRule.onNodeWithTag("task-recurrence-monthly-every-value")
            .performTextReplacement("999")

        assertTrue(
            TaskEditorEvent.UpdateRecurrenceMonthlyAnchorDay(1) in events
        )
        assertTrue(
            TaskEditorEvent.UpdateRecurrenceMonthlyEvery(999) in events
        )
    }

    @Test
    fun monthlyOrdinalEditor_dispatchesOrdinalAndWeekdayMenus() {
        val events = mutableListOf<TaskEditorEvent>()
        setRecurrenceEditorSurface(
            state = RecurrenceEditorState(
                kind = RecurrenceEditorKind.MONTHLY_ORDINAL,
                basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                monthlyEvery = 2,
                ordinal = RecurrenceEditorOrdinal.FIRST,
                ordinalWeekday = RecurrenceEditorWeekday.MONDAY
            ),
            onEvent = events::add
        )
        composeRule.onNodeWithTag("task-recurrence-ordinal").performClick()
        composeRule.onNodeWithTag("task-recurrence-ordinal-last").performClick()
        composeRule.onNodeWithTag("task-recurrence-ordinal-weekday").performClick()
        composeRule.onNodeWithTag("task-recurrence-ordinal-weekday-sunday").performClick()

        assertEquals(
            listOf(
                TaskEditorEvent.SelectRecurrenceOrdinal(RecurrenceEditorOrdinal.LAST),
                TaskEditorEvent.SelectRecurrenceOrdinalWeekday(RecurrenceEditorWeekday.SUNDAY)
            ),
            events
        )
    }

    @Test
    fun recurrenceEditor_rendersItalianResources() {
        assertActiveApplicationLocale(ITALIAN_QUICK_ENTRY)
        setLocalizedRecurrenceEditorSurface()

        composeRule.onNodeWithText("Giorni selezionati").assertIsDisplayed()
        composeRule.onNodeWithText("Data programmata").assertIsDisplayed()
        composeRule.onNodeWithText("Lun").assertIsDisplayed()
        composeRule.onNodeWithText("Termina ripetizione").assertIsDisplayed()
    }

    @Test
    fun recurrenceEditor_rendersEnglishResources() {
        assertActiveApplicationLocale(ENGLISH_QUICK_ENTRY)
        setLocalizedRecurrenceEditorSurface()

        composeRule.onNodeWithText("Selected weekdays").assertIsDisplayed()
        composeRule.onNodeWithText("Scheduled date").assertIsDisplayed()
        composeRule.onNodeWithText("Mon").assertIsDisplayed()
        composeRule.onNodeWithText("End repeat").assertIsDisplayed()
    }

    @Test
    fun recurrenceEditor_largeItalianTextHasNoOverlapOrClipping() {
        assertActiveApplicationLocale(ITALIAN_QUICK_ENTRY)
        setLocalizedRecurrenceEditorSurface(fontScale = 2f)

        val root = nodeBounds("recurrence-editor-layout-root")
        val section = nodeBounds("task-recurrence-editor")
        val kind = nodeBounds("task-recurrence-kind")
        val weekdays = nodeBounds("task-recurrence-weekdays")
        val basis = nodeBounds("task-recurrence-basis")
        val end = nodeBounds("task-recurrence-end")

        listOf(root, section, kind, weekdays, basis, end).forEach(::assertNotClipped)
        assertContains(root.unclipped, section.unclipped)
        listOf(kind, weekdays, basis, end).forEach { child ->
            assertContains(section.unclipped, child.unclipped)
        }
        assertOrdered(kind.unclipped, weekdays.unclipped)
        assertOrdered(weekdays.unclipped, basis.unclipped)
        assertOrdered(basis.unclipped, end.unclipped)
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
            .performScrollTo()
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

    private fun assertLocalizedQuickEntry(expected: QuickEntryLocaleExpectation) {
        assertActiveApplicationLocale(expected)
        setQuickEntrySectionSurface(fontScale = 1f)
        composeRule.onNodeWithTag("quick-entry-input").performClick()

        val root = nodeBounds("quick-entry-layout-root").unclipped
        val labels = composeRule.onAllNodesWithText(expected.label).fetchSemanticsNodes()
        assertEquals(2, labels.size)
        labels.forEach { labelNode ->
            assertContains(root, labelNode.unclippedBoundsInRoot())
        }
        composeRule.onNodeWithText(expected.hint).assertIsDisplayed()
        composeRule.onNodeWithText(expected.parse).assertIsDisplayed()
        composeRule.onNodeWithTag("quick-entry-summary")
            .assertTextEquals(expected.summary)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("quick-entry-issues")
            .assertTextEquals(expected.issue)
            .assertIsDisplayed()
    }

    private fun setQuickEntrySectionSurface(fontScale: Float, height: androidx.compose.ui.unit.Dp? = null) {
        composeRule.runOnUiThread {
            TaskEditorTestContent.content = {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    MaterialTheme {
                        val rootModifier = if (height == null) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.fillMaxWidth().height(height)
                        }
                        Box(
                            modifier = rootModifier
                                .clipToBounds()
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

    private fun setRecurrenceEditorSurface(
        state: RecurrenceEditorState,
        recurrenceError: TaskEditorFieldError? = null,
        recurrenceEndError: TaskEditorFieldError? = null,
        fontScale: Float = 1f,
        onEvent: (TaskEditorEvent) -> Unit = {}
    ) {
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
                                .clipToBounds()
                                .padding(16.dp)
                                .testTag("recurrence-editor-layout-root")
                        ) {
                            RecurrenceEditor(
                                state = state,
                                recurrenceError = recurrenceError,
                                recurrenceEndError = recurrenceEndError,
                                onEvent = onEvent,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setLocalizedRecurrenceEditorSurface(fontScale: Float = 1f) {
        setRecurrenceEditorSurface(
            state = RecurrenceEditorState(
                kind = RecurrenceEditorKind.SELECTED_WEEKDAYS,
                basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                selectedWeekdays = setOf(
                    RecurrenceEditorWeekday.MONDAY,
                    RecurrenceEditorWeekday.WEDNESDAY,
                    RecurrenceEditorWeekday.FRIDAY
                ),
                endAt = 290_000L
            ),
            fontScale = fontScale
        )
    }

    private fun assertActiveApplicationLocale(expected: QuickEntryLocaleExpectation) {
        val localeManager = context.applicationContext.getSystemService(LocaleManager::class.java)
        assertEquals(expected.primaryLanguage, localeManager.applicationLocales[0].language)
        assertEquals(
            expected.primaryLanguage,
            composeRule.activity.resources.configuration.locales[0].language
        )
    }

    private fun nodeBounds(tag: String): NodeBounds {
        val node = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single()
        return NodeBounds(
            clipped = node.boundsInRoot,
            unclipped = node.unclippedBoundsInRoot()
        )
    }

    private fun assertContains(
        parent: androidx.compose.ui.geometry.Rect,
        child: androidx.compose.ui.geometry.Rect
    ) {
        assertTrue(contains(parent, child))
    }

    private fun assertNotClipped(bounds: NodeBounds) {
        assertTrue(!isClipped(bounds))
    }

    private fun isClipped(bounds: NodeBounds): Boolean = !contains(bounds.clipped, bounds.unclipped)

    private fun contains(
        parent: androidx.compose.ui.geometry.Rect,
        child: androidx.compose.ui.geometry.Rect
    ): Boolean =
        child.left >= parent.left &&
            child.top >= parent.top &&
            child.right <= parent.right &&
            child.bottom <= parent.bottom

    private fun assertOrdered(
        upper: androidx.compose.ui.geometry.Rect,
        lower: androidx.compose.ui.geometry.Rect
    ) {
        assertTrue(upper.bottom <= lower.top)
    }
}

private data class NodeBounds(val clipped: Rect, val unclipped: Rect)

private fun androidx.compose.ui.semantics.SemanticsNode.unclippedBoundsInRoot(): Rect {
    val coordinates = layoutInfo.coordinates
    val position = coordinates.positionInRoot()
    return Rect(
        offset = position,
        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
    )
}

private data class QuickEntryLocaleExpectation(
    val languageTag: String,
    val primaryLanguage: String,
    val label: String,
    val hint: String,
    val parse: String,
    val summary: String,
    val issue: String
)

private val ITALIAN_QUICK_ENTRY = QuickEntryLocaleExpectation(
    languageTag = "it",
    primaryLanguage = "it",
    label = "Inserimento rapido",
    hint = "Compra latte domani alle 18",
    parse = "Analizza",
    summary = "Riconosciuti: Titolo, Scadenza, Promemoria, Priorità, Categoria, Ripetizione",
    issue = "Valori ripetuti: è stato usato l’ultimo valido."
)

private val ENGLISH_QUICK_ENTRY = QuickEntryLocaleExpectation(
    languageTag = "en",
    primaryLanguage = "en",
    label = "Quick entry",
    hint = "Buy milk tomorrow at 6 pm",
    parse = "Parse",
    summary = "Recognized: Title, Due date, Reminder, Priority, Category, Repeat",
    issue = "Repeated values: the last valid value was used."
)

private fun quickEntryLocaleFor(testMethod: String): QuickEntryLocaleExpectation? = when (testMethod) {
    "quickEntry_rendersItalianInProductionCompose",
    "quickEntry_largeTextFitsWithinOneSurfaceWithoutOverlapOrClipping",
    "quickEntry_clippingDetectorDetectsConstrainedSurface" -> ITALIAN_QUICK_ENTRY
    "recurrenceEditor_rendersItalianResources",
    "recurrenceEditor_largeItalianTextHasNoOverlapOrClipping" -> ITALIAN_QUICK_ENTRY
    "recurrenceEditor_rendersEnglishResources" -> ENGLISH_QUICK_ENTRY
    "quickEntry_rendersEnglishInProductionCompose" -> ENGLISH_QUICK_ENTRY
    else -> null
}

private class ApplicationLocaleRule(
    private val localeForTest: (String) -> QuickEntryLocaleExpectation?
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val expected = localeForTest(description.methodName) ?: return base.evaluate()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val localeManager = instrumentation.targetContext.applicationContext
                .getSystemService(LocaleManager::class.java)
            val previousLocales = localeManager.applicationLocales
            try {
                localeManager.applicationLocales = LocaleList.forLanguageTags(expected.languageTag)
                instrumentation.waitForIdleSync()
                base.evaluate()
            } finally {
                localeManager.applicationLocales = previousLocales
                instrumentation.waitForIdleSync()
            }
        }
    }
}

private fun TaskEditorScreenTest.assertLocaleExpectationsDiffer() {
    assertNotEquals(ITALIAN_QUICK_ENTRY.label, ENGLISH_QUICK_ENTRY.label)
    assertNotEquals(ITALIAN_QUICK_ENTRY.hint, ENGLISH_QUICK_ENTRY.hint)
    assertNotEquals(ITALIAN_QUICK_ENTRY.parse, ENGLISH_QUICK_ENTRY.parse)
    assertNotEquals(ITALIAN_QUICK_ENTRY.summary, ENGLISH_QUICK_ENTRY.summary)
    assertNotEquals(ITALIAN_QUICK_ENTRY.issue, ENGLISH_QUICK_ENTRY.issue)
}

private fun category(id: Int, name: String) = Category(
    id = id,
    customName = name,
    color = CategoryColor.BLUE,
    position = 0,
    createdAt = 1
)
