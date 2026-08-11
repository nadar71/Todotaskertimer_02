package com.indiewalkabout.nowdothis.feature.history.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.repository.CompletionHistoryReader
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CompletionHistorySection(
    val date: LocalDate,
    val tasks: List<Task>
)

class ObserveCompletionHistory @Inject constructor(
    private val reader: CompletionHistoryReader,
    private val clock: AppClock,
    private val zoneIdProvider: ZoneIdProvider
) {
    operator fun invoke(filter: TaskFilter): Flow<List<CompletionHistorySection>> {
        val zone = zoneIdProvider.zoneId()
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        val before = today.atStartOfDay(zone).toInstant().toEpochMilli()
        return reader.observeHistory(before, filter).map { tasks ->
            tasks.mapNotNull { task ->
                task.completedAt?.let { completedAt ->
                    Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate() to task
                }
            }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .toSortedMap(reverseOrder())
                .map { (date, datedTasks) ->
                    CompletionHistorySection(
                        date = date,
                        tasks = datedTasks.sortedWith(
                            compareByDescending<Task> { it.completedAt }.thenByDescending(Task::id)
                        )
                    )
                }
        }
    }
}
