package com.indiewalkabout.nowdothis.feature.calendar.domain.usecase

import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskScheduleReader
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class CalendarSnapshot(
    val taskCounts: Map<LocalDate, Int>,
    val selectedTasks: List<Task>
)

class ObserveCalendar @Inject constructor(
    private val scheduleReader: TaskScheduleReader,
    private val zoneIdProvider: ZoneIdProvider
) {
    operator fun invoke(
        visibleMonth: YearMonth,
        selectedDate: LocalDate
    ): Flow<CalendarSnapshot> {
        val zone = zoneIdProvider.zoneId()
        val monthStart = visibleMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = visibleMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayStart = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return combine(
            scheduleReader.observeMonth(monthStart, monthEnd),
            scheduleReader.observeDay(dayStart, dayEnd)
        ) { monthTasks, dayTasks ->
            CalendarSnapshot(
                taskCounts = monthTasks
                    .mapNotNull(Task::dueAt)
                    .map { dueAt -> Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate() }
                    .groupingBy { it }
                    .eachCount(),
                selectedTasks = dayTasks.sortedWith(compareBy<Task> { it.dueAt }.thenBy(Task::id))
            )
        }
    }
}
