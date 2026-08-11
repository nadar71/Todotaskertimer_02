package com.indiewalkabout.nowdothis.feature.calendar.presentation

import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val isLoading: Boolean = true,
    val error: CalendarError? = null,
    val visibleMonth: YearMonth = YearMonth.of(1970, 1),
    val selectedDate: LocalDate = LocalDate.of(1970, 1, 1),
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val taskCounts: Map<LocalDate, Int> = emptyMap(),
    val selectedTasks: List<Task> = emptyList()
)

enum class CalendarError {
    LOAD_FAILED
}

sealed interface CalendarEvent {
    data object PreviousMonth : CalendarEvent
    data object NextMonth : CalendarEvent
    data object Today : CalendarEvent
    data class SelectDate(val date: LocalDate) : CalendarEvent
    data object AddTask : CalendarEvent
    data class OpenTask(val taskId: Int) : CalendarEvent
    data object Retry : CalendarEvent
}

sealed interface CalendarEffect {
    data class OpenEditor(
        val taskId: Int?,
        val initialDueAt: Long?
    ) : CalendarEffect
}
