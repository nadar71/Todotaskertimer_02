package com.indiewalkabout.nowdothis.feature.calendar.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.ConfigurationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CalendarTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun monthGrid_rendersStableDatesSelectionTodayAndTaskCount() {
        setScreen(calendarState())

        composeRule.onNodeWithTag("calendar-grid").assertIsDisplayed()
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
            ?: java.util.Locale.getDefault()
        val firstWeekday = WeekFields.of(locale).firstDayOfWeek
            .getDisplayName(TextStyle.NARROW, locale)
        composeRule.onNodeWithTag("calendar-weekday-0", useUnmergedTree = true)
            .assertTextEquals(firstWeekday)
        composeRule.onNodeWithTag("calendar-day-2025-03-12").assertIsSelected()
        composeRule.onNodeWithTag("calendar-day-2025-03-30")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertContentDescriptionContains(
                context.getString(R.string.calendar_today_description),
                substring = true
            )
        composeRule.onNodeWithTag("calendar-count-2025-03-12", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun controlsAndDateSelection_dispatchCalendarEvents() {
        val events = mutableListOf<CalendarEvent>()
        setScreen(calendarState(), events::add)

        composeRule.onNodeWithTag("calendar-prev").performClick()
        composeRule.onNodeWithTag("calendar-next").performClick()
        composeRule.onNodeWithTag("calendar-today").performClick()
        composeRule.onNodeWithTag("calendar-add").performClick()
        composeRule.onNodeWithTag("calendar-day-2025-03-13").performClick()

        composeRule.onNodeWithTag("calendar-prev")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("calendar-next")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        assertEquals(
            listOf(
                CalendarEvent.PreviousMonth,
                CalendarEvent.NextMonth,
                CalendarEvent.Today,
                CalendarEvent.AddTask,
                CalendarEvent.SelectDate(LocalDate.of(2025, 3, 13))
            ),
            events
        )
    }

    @Test
    fun selectedDay_rendersTasksAndOpensTask() {
        val events = mutableListOf<CalendarEvent>()
        setScreen(
            calendarState().copy(
                selectedTasks = listOf(
                    task(1, "Invia rapporto", 9),
                    task(2, "Revisione", 14, completed = true)
                )
            ),
            events::add
        )

        composeRule.onNodeWithText("Invia rapporto").assertIsDisplayed()
        composeRule.onNodeWithText("Revisione").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-task-1").performClick()

        assertEquals(listOf(CalendarEvent.OpenTask(1)), events)
    }

    @Test
    fun emptyAndFailureStates_areActionable() {
        val events = mutableListOf<CalendarEvent>()
        setScreen(calendarState().copy(selectedTasks = emptyList()), events::add)
        composeRule.onNodeWithText(context.getString(R.string.calendar_no_tasks)).assertIsDisplayed()

        setScreen(
            calendarState().copy(error = CalendarError.LOAD_FAILED),
            events::add
        )
        composeRule.onNodeWithText(context.getString(R.string.calendar_load_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.calendar_retry)).performClick()
        assertEquals(CalendarEvent.Retry, events.last())
    }

    private fun setScreen(
        state: CalendarUiState,
        onEvent: (CalendarEvent) -> Unit = {}
    ) {
        composeRule.runOnUiThread {
            CalendarTestContent.content = {
                MaterialTheme {
                    CalendarScreen(state = state, onEvent = onEvent)
                }
            }
        }
        composeRule.waitForIdle()
    }
}

private fun calendarState() = CalendarUiState(
    isLoading = false,
    visibleMonth = YearMonth.of(2025, 3),
    selectedDate = LocalDate.of(2025, 3, 12),
    today = LocalDate.of(2025, 3, 30),
    taskCounts = mapOf(LocalDate.of(2025, 3, 12) to 2)
)

private fun task(id: Int, title: String, hour: Int, completed: Boolean = false): Task {
    val dueAt = LocalDate.of(2025, 3, 12).atTime(hour, 0)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    return Task(
        id = id,
        title = title,
        description = "Description",
        priority = TaskPriority.MEDIUM,
        isCompleted = completed,
        dueAt = dueAt,
        reminderStatus = ReminderStatus.NONE,
        createdAt = 1,
        updatedAt = 1
    )
}
