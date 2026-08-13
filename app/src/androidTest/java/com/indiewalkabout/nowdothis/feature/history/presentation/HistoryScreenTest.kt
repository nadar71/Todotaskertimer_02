package com.indiewalkabout.nowdothis.feature.history.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.history.domain.usecase.CompletionHistorySection
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<HistoryTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun groupedHistory_rendersDateHeaderAndDispatchesInspection() {
        val events = mutableListOf<HistoryEvent>()
        val date = LocalDate.of(2025, 3, 28)
        setScreen(historyState(task = completedTask(1, "Archiviata"), date = date), events::add)

        composeRule.onNodeWithTag("history-date-$date").assertIsDisplayed()
        composeRule.onNodeWithText("Archiviata").assertIsDisplayed()
        composeRule.onNodeWithTag("history-task-1").performClick()
        composeRule.onNodeWithTag("history-task-1")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        assertEquals(listOf(HistoryEvent.Inspect(1)), events)
    }

    @Test
    fun searchCategoryAndBack_dispatchCommands() {
        val events = mutableListOf<HistoryEvent>()
        var backCalls = 0
        setScreen(historyState(), events::add) { backCalls += 1 }

        composeRule.onNodeWithTag("history-search").performTextReplacement("report")
        composeRule.onNodeWithTag("history-category-4").performClick()
        composeRule.onNodeWithTag("history-back").performClick()

        assertEquals(
            listOf(HistoryEvent.UpdateQuery("report"), HistoryEvent.SelectCategory(4)),
            events
        )
        assertEquals(1, backCalls)
    }

    @Test
    fun inspectionSheet_isReadOnlyAndOffersOnlyCloseAndDelete() {
        val events = mutableListOf<HistoryEvent>()
        val task = completedTask(8, "Rapporto", description = "Consegnato")
            .copy(
                subtasks = listOf(
                    Subtask(1, 8, "Controlla", isCompleted = true, completedAt = 1, position = 0)
                )
            )
        setScreen(historyState().copy(inspectedTask = task), events::add)

        composeRule.onNodeWithText("Rapporto").assertIsDisplayed()
        composeRule.onNodeWithText("Consegnato").assertIsDisplayed()
        composeRule.onAllNodesWithTag("history-inspection-reopen").assertCountEquals(0)
        composeRule.onNodeWithTag("history-inspection-delete").performClick()
        composeRule.onNodeWithTag("history-inspection-close").performClick()

        assertEquals(
            listOf(HistoryEvent.RequestDelete(8), HistoryEvent.DismissInspection),
            events
        )
    }

    @Test
    fun permanentDeleteDialog_warnsAndRequiresConfirmation() {
        val events = mutableListOf<HistoryEvent>()
        val task = completedTask(8, "Rapporto")
        setScreen(historyState().copy(pendingDelete = task), events::add)

        composeRule.onNodeWithText(context.getString(R.string.history_delete_title, "Rapporto"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_delete_body)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_delete_confirm)).performClick()

        assertEquals(listOf(HistoryEvent.ConfirmDelete), events)
    }

    @Test
    fun emptyAndErrorStates_areVisibleAndRetryable() {
        val events = mutableListOf<HistoryEvent>()
        setScreen(historyState().copy(sections = emptyList()), events::add)
        composeRule.onNodeWithText(context.getString(R.string.history_empty)).assertIsDisplayed()

        setScreen(historyState().copy(error = HistoryError.LOAD_FAILED), events::add)
        composeRule.onNodeWithText(context.getString(R.string.history_load_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_retry)).performClick()

        assertEquals(HistoryEvent.Retry, events.last())
    }

    private fun setScreen(
        state: HistoryUiState,
        onEvent: (HistoryEvent) -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.runOnUiThread {
            HistoryTestContent.content = {
                MaterialTheme {
                    HistoryScreen(
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

private fun historyState(
    task: Task = completedTask(1, "Archiviata"),
    date: LocalDate = LocalDate.of(2025, 3, 28)
) = HistoryUiState(
    isLoading = false,
    sections = listOf(CompletionHistorySection(date, listOf(task))),
    categories = listOf(
        Category(4, customName = "Clienti", color = CategoryColor.BLUE, position = 0, createdAt = 1)
    )
)

private fun completedTask(
    id: Int,
    title: String,
    description: String = "Description"
): Task {
    val completedAt = LocalDate.of(2025, 3, 28).atTime(11, 30)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return Task(
        id = id,
        title = title,
        description = description,
        priority = TaskPriority.HIGH,
        categoryId = 4,
        isCompleted = true,
        completedAt = completedAt,
        reminderStatus = ReminderStatus.NONE,
        createdAt = 1,
        updatedAt = completedAt
    )
}
