package com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase

import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureDueState
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureSnapshot
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LoadQuickCaptureTasksTest {
    @Test
    fun observe_concatenatesDueSectionsInOrderAndExcludesCompletedAndUnscheduledTasks() = runTest {
        val source = FakeQuickCaptureTaskSource(
            TaskSections(
                overdue = listOf(
                    task(id = 9, dueAt = 90),
                    task(id = 2, dueAt = 20),
                    task(id = 8, dueAt = 80, isCompleted = true)
                ),
                today = listOf(
                    task(id = 7, dueAt = 70),
                    task(id = 3, dueAt = 30),
                    task(id = 6, dueAt = null)
                ),
                upcoming = listOf(
                    task(id = 5, dueAt = 50),
                    task(id = 4, dueAt = 40)
                ),
                unscheduled = listOf(task(id = 1, dueAt = null))
            )
        )

        val snapshot = LoadQuickCaptureTasks(source).observe(8).first()

        assertEquals(
            QuickCaptureSnapshot(
                listOf(
                    quickCaptureTask(9, 90, QuickCaptureDueState.OVERDUE),
                    quickCaptureTask(2, 20, QuickCaptureDueState.OVERDUE),
                    quickCaptureTask(7, 70, QuickCaptureDueState.TODAY),
                    quickCaptureTask(3, 30, QuickCaptureDueState.TODAY),
                    quickCaptureTask(5, 50, QuickCaptureDueState.UPCOMING),
                    quickCaptureTask(4, 40, QuickCaptureDueState.UPCOMING)
                )
            ),
            snapshot
        )
    }

    @Test
    fun observe_limitsSnapshotToThreeTasks() = runTest {
        val snapshot = LoadQuickCaptureTasks(FakeQuickCaptureTaskSource(taskSectionsWithEightTasks()))
            .observe(3)
            .first()

        assertEquals(listOf(1, 2, 3), snapshot.tasks.map(QuickCaptureTask::id))
    }

    @Test
    fun observe_limitsSnapshotToFiveTasks() = runTest {
        val snapshot = LoadQuickCaptureTasks(FakeQuickCaptureTaskSource(taskSectionsWithEightTasks()))
            .observe(5)
            .first()

        assertEquals(listOf(1, 2, 3, 4, 5), snapshot.tasks.map(QuickCaptureTask::id))
    }

    @Test
    fun observe_limitsSnapshotToEightTasks() = runTest {
        val snapshot = LoadQuickCaptureTasks(FakeQuickCaptureTaskSource(taskSectionsWithEightTasks()))
            .observe(8)
            .first()

        assertEquals((1..8).toList(), snapshot.tasks.map(QuickCaptureTask::id))
    }

    @Test
    fun observe_returnsEmptySnapshotWhenThereAreNoEligibleTasks() = runTest {
        val source = FakeQuickCaptureTaskSource(
            TaskSections(
                overdue = listOf(task(id = 1, dueAt = 10, isCompleted = true)),
                unscheduled = listOf(task(id = 2, dueAt = null))
            )
        )

        val snapshot = LoadQuickCaptureTasks(source).observe(3).first()

        assertEquals(QuickCaptureSnapshot(emptyList()), snapshot)
    }

    @Test
    fun invoke_readsTheSourceAgainForEverySnapshot() = runTest {
        val source = FakeQuickCaptureTaskSource(TaskSections(overdue = listOf(task(id = 1, dueAt = 10))))
        val loadTasks = LoadQuickCaptureTasks(source)

        val firstSnapshot = loadTasks(3)
        source.sections = TaskSections(today = listOf(task(id = 2, dueAt = 20)))
        val refreshedSnapshot = loadTasks(3)

        assertEquals(listOf(1), firstSnapshot.tasks.map(QuickCaptureTask::id))
        assertEquals(listOf(2), refreshedSnapshot.tasks.map(QuickCaptureTask::id))
        assertEquals(2, source.observeCalls)
    }

    @Test
    fun observe_rejectsCapacitiesOutsideTheSupportedRange() = runTest {
        val loadTasks = LoadQuickCaptureTasks(FakeQuickCaptureTaskSource())

        listOf(0, 9).forEach { capacity ->
            try {
                loadTasks.observe(capacity)
                fail("Expected capacity $capacity to be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    private fun taskSectionsWithEightTasks() = TaskSections(
        overdue = listOf(task(id = 1, dueAt = 10), task(id = 2, dueAt = 20)),
        today = listOf(task(id = 3, dueAt = 30), task(id = 4, dueAt = 40), task(id = 5, dueAt = 50)),
        upcoming = listOf(task(id = 6, dueAt = 60), task(id = 7, dueAt = 70), task(id = 8, dueAt = 80))
    )
}

private class FakeQuickCaptureTaskSource(
    var sections: TaskSections = TaskSections()
) : QuickCaptureTaskSource {
    var observeCalls = 0

    override fun observe(): Flow<TaskSections> = flow {
        observeCalls += 1
        emit(sections)
    }
}

private fun task(id: Int, dueAt: Long?, isCompleted: Boolean = false) = Task(
    id = id,
    title = "Task $id",
    description = "Description",
    priority = TaskPriority.MEDIUM,
    isCompleted = isCompleted,
    dueAt = dueAt,
    reminderStatus = ReminderStatus.NONE,
    createdAt = 1,
    updatedAt = 1
)

private fun quickCaptureTask(id: Int, dueAt: Long, dueState: QuickCaptureDueState) = QuickCaptureTask(
    id = id,
    title = "Task $id",
    dueAt = dueAt,
    dueState = dueState
)
