package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import androidx.glance.action.actionParametersOf
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.quickcapture.di.QuickCaptureWidgetEntryPoint
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaptureWidgetActionTest {
    @Test
    fun completeAction_delegatesPositiveTaskIdToCompletionWrapper() = runTest {
        val repository = RecordingRepository(task(21))
        val updater = RecordingUpdater()
        val entryPoint = TestEntryPoint(completeTask(repository, updater), updater)

        runCompleteQuickCaptureAction(
            entryPoint,
            actionParametersOf(QuickCaptureWidgetActionParameters.taskId to 21)
        )

        assertEquals(listOf(21), repository.requestedTaskIds)
        assertTrue(repository.task?.isCompleted == true)
        assertEquals(2, updater.updateCount)
    }

    @Test
    fun completeAction_ignoresMissingAndNonPositiveTaskIds() = runTest {
        val repository = RecordingRepository(task(22))
        val updater = RecordingUpdater()
        val entryPoint = TestEntryPoint(completeTask(repository, updater), updater)

        runCompleteQuickCaptureAction(entryPoint, actionParametersOf())
        runCompleteQuickCaptureAction(
            entryPoint,
            actionParametersOf(QuickCaptureWidgetActionParameters.taskId to 0)
        )

        assertTrue(repository.requestedTaskIds.isEmpty())
        assertEquals(0, updater.updateCount)
    }

    @Test
    fun retryAction_requestsAnExplicitAllWidgetUpdate() = runTest {
        val repository = RecordingRepository()
        val updater = RecordingUpdater()
        val entryPoint = TestEntryPoint(completeTask(repository, updater), updater)

        runRetryQuickCaptureAction(entryPoint)

        assertEquals(1, updater.updateCount)
        assertTrue(repository.requestedTaskIds.isEmpty())
    }

    private fun completeTask(
        repository: TaskRepository,
        updater: QuickCaptureWidgetUpdater
    ) = CompleteQuickCaptureTask(
        CompleteTask(
            repository,
            NoOpReminderScheduler,
            CalculateNextOccurrence(ZoneIdProvider { ZoneId.of("UTC") }),
            AppClock { 1_000 }
        ),
        updater
    )

    private fun task(id: Int) = Task(
        id = id,
        title = "Task $id",
        description = "Description",
        priority = TaskPriority.MEDIUM,
        createdAt = 0,
        updatedAt = 0
    )

    private class TestEntryPoint(
        private val complete: CompleteQuickCaptureTask,
        private val updater: QuickCaptureWidgetUpdater
    ) : QuickCaptureWidgetEntryPoint {
        private val refreshSignal = QuickCaptureWidgetRefreshSignal()

        override fun loadQuickCaptureTasks(): LoadQuickCaptureTasks = error("Not used")
        override fun completeQuickCaptureTask() = complete
        override fun quickCaptureWidgetUpdater() = updater
        override fun quickCaptureWidgetRefreshSignal() = refreshSignal
    }

    private class RecordingUpdater : QuickCaptureWidgetUpdater {
        var updateCount = 0

        override suspend fun updateAll() {
            updateCount++
        }
    }

    private object NoOpReminderScheduler : ReminderScheduler {
        override suspend fun schedule(taskId: Int, triggerAt: Long) = ReminderScheduleResult.EXACT
        override suspend fun cancel(taskId: Int) = Unit
        override suspend fun reconcile() = Unit
    }

    private class RecordingRepository(var task: Task? = null) : TaskRepository {
        val requestedTaskIds = mutableListOf<Int>()

        override fun observeTask(taskId: Int): Flow<Task?> = flowOf(task)
        override fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections> =
            flowOf(TaskSections())

        override suspend fun getTask(taskId: Int): Task? {
            requestedTaskIds += taskId
            return task?.takeIf { it.id == taskId }
        }

        override suspend fun completeAtomically(
            taskId: Int,
            completedAt: Long,
            nextOccurrence: (Task) -> Task?
        ): AtomicCompletionResult {
            requestedTaskIds += taskId
            val current = task?.takeIf { it.id == taskId } ?: return AtomicCompletionResult.NotFound
            if (current.isCompleted) return AtomicCompletionResult.AlreadyCompleted
            val completed = current.copy(isCompleted = true, completedAt = completedAt)
            task = completed
            return AtomicCompletionResult.Completed(completed, nextOccurrence(current))
        }

        override suspend fun upsert(task: Task): Int = error("Not used")
        override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot = error("Not used")
        override suspend fun deleteAll(): List<Int> = error("Not used")
        override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = error("Not used")
        override suspend fun deleteCompleted(taskId: Int) = error("Not used")
        override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) = Unit
        override suspend fun futureReminders(after: Long): List<Task> = error("Not used")
    }
}
