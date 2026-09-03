package com.indiewalkabout.nowdothis.feature.task.presentation.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TaskListTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sections_renderInApprovedOrderAndOmitEmptySections() {
        setScreen(
            TaskListUiState(
                isLoading = false,
                sections = TaskSections(
                    overdue = listOf(task(1, "Late")),
                    today = listOf(task(2, "Today")),
                    upcoming = listOf(task(3, "Later")),
                    completedToday = listOf(task(4, "Done", completed = true))
                )
            )
        )

        val headings = composeRule.onAllNodesWithTag("task-section-heading")
        headings[0].assertTextEquals(context.getString(R.string.task_section_overdue))
        headings[1].assertTextEquals(context.getString(R.string.task_section_today))
        headings[2].assertTextEquals(context.getString(R.string.task_section_upcoming))
        headings[3].assertTextEquals(context.getString(R.string.task_section_completed_today))
        composeRule.onNodeWithText(context.getString(R.string.task_section_unscheduled))
            .assertDoesNotExist()
    }

    @Test
    fun emptySections_showGlobalEmptyState() {
        setScreen(TaskListUiState(isLoading = false))

        composeRule.onNodeWithText(context.getString(R.string.task_list_empty))
            .assertIsDisplayed()
    }

    @Test
    fun categoryChips_beginWithAllAndDispatchSelection() {
        val events = mutableListOf<TaskListEvent>()
        setScreen(
            state = TaskListUiState(
                isLoading = false,
                categories = listOf(
                    Category(
                        id = 7,
                        customName = "Client",
                        color = CategoryColor.BLUE,
                        position = 0,
                        createdAt = 1
                    )
                )
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("category-chip-all")
            .assertTextEquals(context.getString(R.string.task_category_all))
        composeRule.onNodeWithTag("category-chip-7").performClick()

        assertEquals(TaskListEvent.SelectCategory(7), events.single())
    }

    @Test
    fun taskRow_dispatchesEditCompleteAndSwipeDelete() {
        val events = mutableListOf<TaskListEvent>()
        setScreen(
            state = TaskListUiState(
                isLoading = false,
                sections = TaskSections(today = listOf(task(11, "Write report")))
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("task-row-11").performClick()
        composeRule.onNode(
            hasContentDescription(
                context.getString(R.string.task_complete_description, "Write report")
            )
        ).assertExists()
        composeRule.onNodeWithTag("task-complete-11").performClick()
        composeRule.onNodeWithTag("task-swipe-11").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(TaskListEvent.OpenTaskEditor(11), events[0])
        assertEquals(TaskListEvent.CompleteTask(11), events[1])
        assertEquals(TaskListEvent.DeleteTask(11), events[2])

        composeRule.onNodeWithTag("task-complete-11")
            .assertIsToggleable()
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        composeRule.onNodeWithTag("task-row-11")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun completedHeaderAndDeleteConfirmation_dispatchExpectedEvents() {
        val events = mutableListOf<TaskListEvent>()
        setScreen(
            state = TaskListUiState(
                isLoading = false,
                sections = TaskSections(completedToday = listOf(task(5, "Done", completed = true))),
                showDeleteAllConfirmation = true
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithTag("completed-see-all").performClick()
        composeRule.onNodeWithText(context.getString(R.string.task_delete_all_body))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.task_delete_all_confirm)).performClick()

        assertEquals(TaskListEvent.OpenHistory, events[0])
        assertEquals(TaskListEvent.ConfirmDeleteAll, events[1])
        assertFalse(events.contains(TaskListEvent.UndoDelete))
    }

    @Test
    fun overflow_backupAndRestore_dispatchesDataPortabilityEventAfterHistory() {
        val events = mutableListOf<TaskListEvent>()
        setScreen(
            state = TaskListUiState(isLoading = false),
            onEvent = events::add
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.task_more_actions)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.task_open_history)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.portability_menu_action)).performClick()

        assertEquals(listOf(TaskListEvent.OpenDataPortability), events)
    }

    @Test
    fun overflow_privacyChoicesAppearsOnlyWhenRequiredAndInvokesCallback() {
        var privacyOptionsOpened = false
        setScreen(
            state = TaskListUiState(isLoading = false),
            showPrivacyOptions = true,
            onPrivacyOptions = { privacyOptionsOpened = true }
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.task_more_actions)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.ads_privacy_options)).performClick()

        assertTrue(privacyOptionsOpened)
    }

    @Test
    fun longTitleAndCategory_keepSingleNodesAndCompletionSemantics() {
        val longTitle = "Prepare the detailed quarterly planning report for every stakeholder"
        val longCategory = "International client delivery and account coordination"
        setScreen(
            state = TaskListUiState(
                isLoading = false,
                categories = listOf(
                    Category(
                        id = 9,
                        customName = longCategory,
                        color = CategoryColor.GREEN,
                        position = 0,
                        createdAt = 1
                    )
                ),
                sections = TaskSections(today = listOf(task(12, longTitle).copy(categoryId = 9)))
            )
        )

        composeRule.onAllNodesWithText(longTitle).assertCountEquals(1)
        composeRule.onAllNodesWithText(longCategory).assertCountEquals(2)
        composeRule.onNode(
            hasContentDescription(
                context.getString(R.string.task_complete_description, longTitle)
            )
        ).assertExists()
    }

    private fun setScreen(
        state: TaskListUiState,
        onEvent: (TaskListEvent) -> Unit = {},
        showPrivacyOptions: Boolean = false,
        onPrivacyOptions: () -> Unit = {}
    ) {
        composeRule.runOnUiThread {
            TaskListTestContent.content = {
                MaterialTheme {
                    TaskListScreen(
                        state = state,
                        snackbarHostState = SnackbarHostState(),
                        onEvent = onEvent,
                        showPrivacyOptions = showPrivacyOptions,
                        onPrivacyOptions = onPrivacyOptions
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}

private fun task(id: Int, title: String, completed: Boolean = false) = Task(
    id = id,
    title = title,
    description = "Description",
    priority = TaskPriority.MEDIUM,
    isCompleted = completed,
    completedAt = 1L.takeIf { completed },
    dueAt = 1_000,
    createdAt = 1,
    updatedAt = 1
)
