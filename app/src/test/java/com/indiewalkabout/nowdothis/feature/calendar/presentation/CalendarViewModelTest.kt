package com.indiewalkabout.nowdothis.feature.calendar.presentation

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.calendar.domain.usecase.ObserveCalendar
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskScheduleReader
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val rome = ZoneId.of("Europe/Rome")
    private val now = LocalDate.of(2025, 3, 30).atTime(12, 0).atZone(rome).toInstant().toEpochMilli()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialObservation_usesLocalMonthAndDstDayBounds() = runTest(dispatcher) {
        val reader = FakeTaskScheduleReader()
        val viewModel = createViewModel(reader)
        advanceUntilIdle()

        assertEquals(YearMonth.of(2025, 3), viewModel.uiState.value.visibleMonth)
        assertEquals(LocalDate.of(2025, 3, 30), viewModel.uiState.value.selectedDate)
        assertEquals(bounds(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 4, 1)), reader.monthBounds.last())
        assertEquals(bounds(LocalDate.of(2025, 3, 30), LocalDate.of(2025, 3, 31)), reader.dayBounds.last())
        val dayDuration = reader.dayBounds.last().second - reader.dayBounds.last().first
        assertEquals(23 * 60 * 60 * 1_000L, dayDuration)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun monthTasks_areCountedByLocalDueDateAndSelectedTasksRemainOrdered() = runTest(dispatcher) {
        val firstDue = LocalDate.of(2025, 3, 12).atTime(9, 0).atZone(rome).toInstant().toEpochMilli()
        val secondDue = LocalDate.of(2025, 3, 12).atTime(14, 0).atZone(rome).toInstant().toEpochMilli()
        val reader = FakeTaskScheduleReader(
            monthTasks = listOf(task(2, "Second", secondDue), task(1, "First", firstDue)),
            dayTasks = listOf(task(1, "First", firstDue), task(2, "Second", secondDue))
        )
        val viewModel = createViewModel(reader)
        viewModel.onEvent(CalendarEvent.SelectDate(LocalDate.of(2025, 3, 12)))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.taskCounts[LocalDate.of(2025, 3, 12)])
        assertEquals(listOf("First", "Second"), viewModel.uiState.value.selectedTasks.map(Task::title))
    }

    @Test
    fun selectDate_observesDayAndPrefillsEditorAtLocalStart() = runTest(dispatcher) {
        val reader = FakeTaskScheduleReader()
        val viewModel = createViewModel(reader)
        advanceUntilIdle()

        val selected = LocalDate.of(2025, 12, 16)
        viewModel.onEvent(CalendarEvent.SelectDate(selected))
        advanceUntilIdle()
        val effect = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }
        viewModel.onEvent(CalendarEvent.AddTask)

        assertEquals(selected, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2025, 12), viewModel.uiState.value.visibleMonth)
        assertEquals(
            CalendarEffect.OpenEditor(null, selected.atStartOfDay(rome).toInstant().toEpochMilli()),
            effect.await()
        )
    }

    @Test
    fun nextMonth_clampsMonthEndAndPreviousReturnsRelativeToClampedDate() = runTest(dispatcher) {
        val reader = FakeTaskScheduleReader()
        val jan31 = LocalDate.of(2025, 1, 31).atTime(12, 0).atZone(rome).toInstant().toEpochMilli()
        val viewModel = createViewModel(reader, jan31)
        advanceUntilIdle()

        viewModel.onEvent(CalendarEvent.NextMonth)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2025, 2, 28), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2025, 2), viewModel.uiState.value.visibleMonth)

        viewModel.onEvent(CalendarEvent.PreviousMonth)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2025, 1, 28), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun today_resetsMonthAndDateAfterNavigation() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeTaskScheduleReader())
        advanceUntilIdle()
        viewModel.onEvent(CalendarEvent.SelectDate(LocalDate.of(2027, 8, 3)))
        viewModel.onEvent(CalendarEvent.Today)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2025, 3, 30), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2025, 3), viewModel.uiState.value.visibleMonth)
    }

    @Test
    fun openTask_emitsEditorEffectWithoutInitialDate() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeTaskScheduleReader())
        advanceUntilIdle()
        val effect = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }

        viewModel.onEvent(CalendarEvent.OpenTask(42))

        assertEquals(CalendarEffect.OpenEditor(taskId = 42, initialDueAt = null), effect.await())
    }

    @Test
    fun failedObservation_canRetry() = runTest(dispatcher) {
        val reader = FakeTaskScheduleReader().apply { failObservation = true }
        val viewModel = createViewModel(reader)
        advanceUntilIdle()
        assertEquals(CalendarError.LOAD_FAILED, viewModel.uiState.value.error)

        reader.failObservation = false
        viewModel.onEvent(CalendarEvent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(reader.monthObservationCount >= 2)
    }

    private fun createViewModel(
        reader: FakeTaskScheduleReader,
        clockMillis: Long = now
    ) = CalendarViewModel(
        observeCalendar = ObserveCalendar(reader, ZoneIdProvider { rome }),
        clock = AppClock { clockMillis },
        zoneIdProvider = ZoneIdProvider { rome }
    )

    private fun bounds(start: LocalDate, end: LocalDate) =
        start.atStartOfDay(rome).toInstant().toEpochMilli() to
            end.atStartOfDay(rome).toInstant().toEpochMilli()
}

private class FakeTaskScheduleReader(
    monthTasks: List<Task> = emptyList(),
    dayTasks: List<Task> = emptyList()
) : TaskScheduleReader {
    private val month = MutableStateFlow(monthTasks)
    private val day = MutableStateFlow(dayTasks)
    val monthBounds = mutableListOf<Pair<Long, Long>>()
    val dayBounds = mutableListOf<Pair<Long, Long>>()
    var monthObservationCount = 0
    var failObservation = false

    override fun observeMonth(startInclusive: Long, endExclusive: Long): Flow<List<Task>> {
        monthObservationCount += 1
        monthBounds += startInclusive to endExclusive
        if (failObservation) error("month failed")
        return month
    }

    override fun observeDay(startInclusive: Long, endExclusive: Long): Flow<List<Task>> {
        dayBounds += startInclusive to endExclusive
        if (failObservation) error("day failed")
        return day
    }
}

private fun task(id: Int, title: String, dueAt: Long) = Task(
    id = id,
    title = title,
    description = "Description",
    priority = TaskPriority.MEDIUM,
    dueAt = dueAt,
    reminderStatus = ReminderStatus.NONE,
    createdAt = 1,
    updatedAt = 1
)
