package com.indiewalkabout.nowdothis.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.calendar.domain.usecase.CalendarSnapshot
import com.indiewalkabout.nowdothis.feature.calendar.domain.usecase.ObserveCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observeCalendar: ObserveCalendar,
    private val clock: AppClock,
    private val zoneIdProvider: ZoneIdProvider
) : ViewModel() {
    private val initialToday = currentDate()
    private val selection = MutableStateFlow(
        CalendarSelection(
            visibleMonth = YearMonth.from(initialToday),
            selectedDate = initialToday,
            today = initialToday
        )
    )
    private val retryToken = MutableStateFlow(0)
    private val effectChannel = Channel<CalendarEffect>(Channel.BUFFERED)

    val effects: Flow<CalendarEffect> = effectChannel.receiveAsFlow()

    private val observedCalendar = combine(selection, retryToken) { current, _ -> current }
        .flatMapLatest { current ->
            flow { emitAll(observeCalendar(current.visibleMonth, current.selectedDate)) }
                .map<CalendarSnapshot, CalendarLoadState> { CalendarLoadState.Data(it) }
                .onStart { emit(CalendarLoadState.Loading) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(CalendarLoadState.Failed)
                }
        }

    val uiState = combine(selection, observedCalendar) { current, observed ->
        CalendarUiState(
            isLoading = observed is CalendarLoadState.Loading,
            error = CalendarError.LOAD_FAILED.takeIf { observed is CalendarLoadState.Failed },
            visibleMonth = current.visibleMonth,
            selectedDate = current.selectedDate,
            today = current.today,
            taskCounts = (observed as? CalendarLoadState.Data)?.snapshot?.taskCounts.orEmpty(),
            selectedTasks = (observed as? CalendarLoadState.Data)?.snapshot?.selectedTasks.orEmpty()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        CalendarUiState(
            visibleMonth = selection.value.visibleMonth,
            selectedDate = selection.value.selectedDate,
            today = selection.value.today
        )
    )

    fun onEvent(event: CalendarEvent) {
        when (event) {
            CalendarEvent.PreviousMonth -> shiftMonth(-1)
            CalendarEvent.NextMonth -> shiftMonth(1)
            CalendarEvent.Today -> selectToday()
            is CalendarEvent.SelectDate -> selection.value = selection.value.copy(
                visibleMonth = YearMonth.from(event.date),
                selectedDate = event.date
            )
            CalendarEvent.AddTask -> openEditor(
                taskId = null,
                initialDueAt = selection.value.selectedDate
                    .atStartOfDay(zoneIdProvider.zoneId())
                    .toInstant()
                    .toEpochMilli()
            )
            is CalendarEvent.OpenTask -> openEditor(event.taskId, initialDueAt = null)
            CalendarEvent.Retry -> retryToken.value += 1
        }
    }

    private fun shiftMonth(months: Long) {
        val current = selection.value
        val selectedDate = current.selectedDate.plusMonths(months)
        selection.value = current.copy(
            visibleMonth = current.visibleMonth.plusMonths(months),
            selectedDate = selectedDate
        )
    }

    private fun selectToday() {
        val today = currentDate()
        selection.value = CalendarSelection(
            visibleMonth = YearMonth.from(today),
            selectedDate = today,
            today = today
        )
    }

    private fun openEditor(taskId: Int?, initialDueAt: Long?) {
        viewModelScope.launch {
            effectChannel.send(CalendarEffect.OpenEditor(taskId, initialDueAt))
        }
    }

    private fun currentDate(): LocalDate = Instant.ofEpochMilli(clock.nowMillis())
        .atZone(zoneIdProvider.zoneId())
        .toLocalDate()
}

private data class CalendarSelection(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val today: LocalDate
)

private sealed interface CalendarLoadState {
    data object Loading : CalendarLoadState
    data class Data(val snapshot: CalendarSnapshot) : CalendarLoadState
    data object Failed : CalendarLoadState
}
