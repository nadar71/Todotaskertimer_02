package com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompleteQuickCaptureTaskTest {
    @Test
    fun positiveTask_completesThroughCompleteTaskAndRefreshesWidgets() = runTest {
        val repository = FakeTaskRepository(task(7))
        val updater = RecordingUpdater()

        val result = useCase(repository, updater)(7)

        assertEquals(CompleteQuickCaptureResult.Completed, result)
        assertTrue(repository.tasks.getValue(7).isCompleted)
        assertEquals(listOf(7), repository.completedIds)
        assertEquals(1, updater.updateCount)
    }

    @Test
    fun recurringCompletion_isAcceptedAndLeavesRecurrenceToCompleteTask() = runTest {
        val repository = FakeTaskRepository(
            task(id = 8, dueAt = 86_400_000, recurrence = RecurrenceType.DAILY)
        )
        val updater = RecordingUpdater()

        val result = useCase(repository, updater)(8)

        assertEquals(CompleteQuickCaptureResult.Completed, result)
        assertEquals(1, repository.createdOccurrences)
        assertEquals(listOf(8), repository.completedIds)
        assertEquals(1, updater.updateCount)
    }

    @Test
    fun missingAndAlreadyCompletedTasks_areIgnoredAndEachRefreshesWidgets() = runTest {
        val repository = FakeTaskRepository(task(9).copy(isCompleted = true, completedAt = 500))
        val updater = RecordingUpdater()
        val complete = useCase(repository, updater)

        assertEquals(CompleteQuickCaptureResult.Ignored, complete(404))
        assertEquals(CompleteQuickCaptureResult.Ignored, complete(9))
        assertTrue(repository.completedIds.isEmpty())
        assertEquals(2, updater.updateCount)
    }

    @Test
    fun completionException_mapsToFailedAndRefreshesWidgets() = runTest {
        val repository = FakeTaskRepository(task(10)).apply {
            completionFailure = IllegalStateException("database unavailable")
        }
        val updater = RecordingUpdater()

        val result = useCase(repository, updater)(10)

        assertEquals(CompleteQuickCaptureResult.Failed, result)
        assertEquals(1, updater.updateCount)
    }

    @Test
    fun duplicateConcurrentTap_invokesCompleteTaskOnceAndReportsInFlightState() = runTest {
        val repository = FakeTaskRepository(task(11)).apply {
            completionGate = CompletableDeferred()
        }
        val updater = RecordingUpdater()
        val complete = useCase(repository, updater)

        val first = async { complete(11) }
        runCurrent()
        assertEquals(setOf(11), complete.inFlightTaskIds.value)

        val duplicate = async { complete(11) }
        runCurrent()

        assertEquals(CompleteQuickCaptureResult.Ignored, duplicate.await())
        assertEquals(1, repository.getTaskCalls)
        repository.completionGate?.complete(Unit)
        assertEquals(CompleteQuickCaptureResult.Completed, first.await())
        assertEquals(1, repository.getTaskCalls)
        assertEquals(2, updater.updateCount)
    }

    @Test
    fun cancellation_propagatesRemovesInFlightStateAndRefreshesWidgets() = runTest {
        val repository = FakeTaskRepository(task(12)).apply { suspendGetTask = true }
        val updater = RecordingUpdater()
        val complete = useCase(repository, updater)

        val action = async { complete(12) }
        runCurrent()
        assertEquals(setOf(12), complete.inFlightTaskIds.value)

        action.cancel()
        runCurrent()

        assertTrue(action.isCancelled)
        assertFalse(12 in complete.inFlightTaskIds.value)
        assertEquals(1, updater.updateCount)
    }

    private fun useCase(
        repository: TaskRepository,
        updater: QuickCaptureWidgetUpdater
    ) = CompleteQuickCaptureTask(
        completeTask = CompleteTask(
            repository = repository,
            scheduler = NoOpReminderScheduler,
            calculateNextOccurrence = CalculateNextOccurrence(ZoneIdProvider { ZoneId.of("UTC") }),
            clock = AppClock { 1_000 }
        ),
        updater = updater
    )

    private fun task(
        id: Int,
        dueAt: Long? = null,
        recurrence: RecurrenceType = RecurrenceType.NONE
    ) = Task(
        id = id,
        title = "Task $id",
        description = "Description",
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        recurrence = recurrence,
        createdAt = 0,
        updatedAt = 0
    )

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

    private class FakeTaskRepository(initialTask: Task? = null) : TaskRepository {
        val tasks = mutableMapOf<Int, Task>()
        val completedIds = mutableListOf<Int>()
        var completionFailure: RuntimeException? = null
        var completionGate: CompletableDeferred<Unit>? = null
        var suspendGetTask = false
        var getTaskCalls = 0
        var createdOccurrences = 0

        init {
            initialTask?.let { tasks[it.id] = it }
        }

        override fun observeTask(taskId: Int): Flow<Task?> = flowOf(tasks[taskId])

        override fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections> =
            flowOf(TaskSections())

        override suspend fun getTask(taskId: Int): Task? {
            getTaskCalls++
            if (suspendGetTask) awaitCancellation()
            return tasks[taskId]
        }

        override suspend fun upsert(task: Task) = error("Not used")

        override suspend fun completeAtomically(
            taskId: Int,
            completedAt: Long,
            next: Task?
        ): AtomicCompletionResult? {
            completionFailure?.let { throw it }
            completionGate?.await()
            val current = tasks[taskId] ?: return null
            completedIds += taskId
            val completed = current.copy(isCompleted = true, completedAt = completedAt)
            tasks[taskId] = completed
            val occurrence = next?.copy(id = 100 + createdOccurrences++)
            occurrence?.let { tasks[it.id] = it }
            return AtomicCompletionResult(completed, occurrence)
        }

        override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot = error("Not used")
        override suspend fun deleteAll(): List<Int> = error("Not used")
        override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = error("Not used")
        override suspend fun deleteCompleted(taskId: Int) = error("Not used")
        override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) = Unit
        override suspend fun futureReminders(after: Long): List<Task> = error("Not used")
    }
}
