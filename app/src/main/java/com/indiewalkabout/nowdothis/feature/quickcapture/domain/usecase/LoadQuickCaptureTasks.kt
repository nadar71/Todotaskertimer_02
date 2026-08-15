package com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase

import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureDueState
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureSnapshot
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class LoadQuickCaptureTasks(
    private val source: QuickCaptureTaskSource
) {
    fun observe(capacity: Int): Flow<QuickCaptureSnapshot> {
        require(capacity in MIN_CAPACITY..MAX_CAPACITY)

        return source.observe().map { sections ->
            QuickCaptureSnapshot(
                tasks = (
                    sections.overdue.toQuickCaptureTasks(QuickCaptureDueState.OVERDUE) +
                        sections.today.toQuickCaptureTasks(QuickCaptureDueState.TODAY) +
                        sections.upcoming.toQuickCaptureTasks(QuickCaptureDueState.UPCOMING)
                    ).take(capacity)
            )
        }
    }

    suspend operator fun invoke(capacity: Int): QuickCaptureSnapshot = observe(capacity).first()

    private fun List<Task>.toQuickCaptureTasks(dueState: QuickCaptureDueState): List<QuickCaptureTask> =
        asSequence()
            .filterNot(Task::isCompleted)
            .mapNotNull { task ->
                task.dueAt?.let { dueAt ->
                    QuickCaptureTask(
                        id = task.id,
                        title = task.title,
                        dueAt = dueAt,
                        dueState = dueState
                    )
                }
            }
            .toList()

    private companion object {
        const val MIN_CAPACITY = 1
        const val MAX_CAPACITY = 8
    }
}
