package com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionDecision
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
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
        assertEquals(2, updater.updateCount)
    }

    @Test
    fun recurringCompletion_isAcceptedAndLeavesRecurrenceToCompleteTask() = runTest {
        val repository = FakeTaskRepository(
            task(id = 8, dueAt = 86_400_000, recurrenceRule = dailyRule)
        )
        val updater = RecordingUpdater()

        val result = useCase(repository, updater)(8)

        assertEquals(CompleteQuickCaptureResult.Completed, result)
        assertEquals(1, repository.createdOccurrences)
        assertEquals(listOf(8), repository.completedIds)
        assertEquals(2, updater.updateCount)
    }

    @Test
    fun invalidRecurringCompletion_failsWithoutMutatingThroughDelegatedFlow() = runTest {
        val current = task(id = 19, dueAt = null, recurrenceRule = dailyRule)
        val repository = FakeTaskRepository(current)
        val updater = RecordingUpdater()

        val result = useCase(repository, updater)(19)

        assertEquals(CompleteQuickCaptureResult.Failed, result)
        assertEquals(current, repository.tasks.getValue(19))
        assertTrue(repository.completedIds.isEmpty())
        assertEquals(2, updater.updateCount)
    }

    @Test
    fun missingAndAlreadyCompletedTasks_areIgnoredAndEachRefreshesWidgets() = runTest {
        val repository = FakeTaskRepository(task(9).copy(isCompleted = true, completedAt = 500))
        val updater = RecordingUpdater()
        val complete = useCase(repository, updater)

        assertEquals(CompleteQuickCaptureResult.Ignored, complete(404))
        assertEquals(CompleteQuickCaptureResult.Ignored, complete(9))
        assertTrue(repository.completedIds.isEmpty())
        assertEquals(4, updater.updateCount)
    }

    @Test
    fun completionException_mapsToFailedAndRefreshesWidgets() = runTest {
        val repository = FakeTaskRepository(task(10)).apply {
            completionFailure = IllegalStateException("database unavailable")
        }
        val updater = RecordingUpdater()

        val result = useCase(repository, updater)(10)

        assertEquals(CompleteQuickCaptureResult.Failed, result)
        assertEquals(2, updater.updateCount)
    }

    @Test
    fun claimedTask_refreshesDisabledStateBeforeCompleteTaskAndClearsItBeforeTerminalRefresh() =
        runTest {
            val repository = FakeTaskRepository(task(13))
            val firstUpdateGate = CompletableDeferred<Unit>()
            val renderedInFlightStates = mutableListOf<Set<Int>>()
            lateinit var complete: CompleteQuickCaptureTask
            val updater = QuickCaptureWidgetUpdater {
                renderedInFlightStates += complete.inFlightTaskIds.value
                if (renderedInFlightStates.size == 1) firstUpdateGate.await()
            }
            complete = useCase(repository, updater)

            val action = async { complete(13) }
            runCurrent()

            val statesBeforeCompletion = renderedInFlightStates.toList()
            val readsBeforeCompletion = repository.getTaskCalls
            firstUpdateGate.complete(Unit)
            val result = action.await()

            assertEquals(listOf(setOf(13)), statesBeforeCompletion)
            assertEquals(0, readsBeforeCompletion)
            assertEquals(CompleteQuickCaptureResult.Completed, result)
            assertEquals(listOf(setOf(13), emptySet()), renderedInFlightStates)
        }

    @Test
    fun initialRefreshFailure_doesNotPreventCompletionAndReturnsFailedAfterTerminalRefresh() =
        runTest {
            val repository = FakeTaskRepository(task(14))
            var updates = 0
            val updater = QuickCaptureWidgetUpdater {
                updates++
                if (updates == 1) throw IllegalStateException("host unavailable")
            }

            val result = useCase(repository, updater)(14)

            assertEquals(CompleteQuickCaptureResult.Failed, result)
            assertEquals(listOf(14), repository.completedIds)
            assertEquals(2, updates)
        }

    @Test
    fun initialRefreshCancellation_propagatesWithoutCompletingAndStillClearsAndRefreshes() =
        runTest {
            val repository = FakeTaskRepository(task(15))
            var updates = 0
            lateinit var complete: CompleteQuickCaptureTask
            val updater = QuickCaptureWidgetUpdater {
                updates++
                if (updates == 1) throw CancellationException("cancel initial render")
            }
            complete = useCase(repository, updater)

            val action = async { complete(15) }
            runCurrent()

            assertTrue(action.isCancelled)
            assertEquals(0, repository.getTaskCalls)
            assertFalse(15 in complete.inFlightTaskIds.value)
            assertEquals(2, updates)
        }

    @Test
    fun terminalRefreshFailure_returnsFailedAfterCompletionAndClearsInFlightState() = runTest {
        val repository = FakeTaskRepository(task(16))
        var updates = 0
        lateinit var complete: CompleteQuickCaptureTask
        val updater = QuickCaptureWidgetUpdater {
            updates++
            if (updates == 2) throw IllegalStateException("terminal host failure")
        }
        complete = useCase(repository, updater)

        val result = complete(16)

        assertEquals(CompleteQuickCaptureResult.Failed, result)
        assertEquals(listOf(16), repository.completedIds)
        assertFalse(16 in complete.inFlightTaskIds.value)
        assertEquals(2, updates)
    }

    @Test
    fun terminalRefreshCancellation_propagatesAfterCompletionAndClearsInFlightState() = runTest {
        val repository = FakeTaskRepository(task(17))
        var updates = 0
        lateinit var complete: CompleteQuickCaptureTask
        val updater = QuickCaptureWidgetUpdater {
            updates++
            if (updates == 2) throw CancellationException("cancel terminal render")
        }
        complete = useCase(repository, updater)

        val action = async { complete(17) }
        runCurrent()

        assertTrue(action.isCancelled)
        assertEquals(listOf(17), repository.completedIds)
        assertFalse(17 in complete.inFlightTaskIds.value)
        assertEquals(2, updates)
    }

    @Test
    fun cancellationWhileTerminalRefreshSuspends_completesPromptlyWithoutLeakingInFlightState() =
        runTest {
            val repository = FakeTaskRepository(task(18))
            val terminalRefreshStarted = CompletableDeferred<Unit>()
            val releaseTerminalRefresh = CompletableDeferred<Unit>()
            var updates = 0
            lateinit var complete: CompleteQuickCaptureTask
            val updater = QuickCaptureWidgetUpdater {
                updates++
                if (updates == 2) {
                    terminalRefreshStarted.complete(Unit)
                    releaseTerminalRefresh.await()
                }
            }
            complete = useCase(repository, updater)
            val action = async { complete(18) }
            terminalRefreshStarted.await()

            try {
                action.cancel()
                runCurrent()

                assertTrue(action.isCompleted)
                assertTrue(action.isCancelled)
                assertFalse(18 in complete.inFlightTaskIds.value)
            } finally {
                releaseTerminalRefresh.complete(Unit)
                runCurrent()
            }
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
        assertEquals(1, repository.completeAtomicallyCalls)
        repository.completionGate?.complete(Unit)
        assertEquals(CompleteQuickCaptureResult.Completed, first.await())
        assertEquals(1, repository.completeAtomicallyCalls)
        assertEquals(3, updater.updateCount)
    }

    @Test
    fun cancellation_propagatesRemovesInFlightStateAndRefreshesWidgets() = runTest {
        val repository = FakeTaskRepository(task(12)).apply { suspendCompletion = true }
        val updater = RecordingUpdater()
        val complete = useCase(repository, updater)

        val action = async { complete(12) }
        runCurrent()
        assertEquals(setOf(12), complete.inFlightTaskIds.value)

        action.cancel()
        runCurrent()

        assertTrue(action.isCancelled)
        assertFalse(12 in complete.inFlightTaskIds.value)
        assertEquals(2, updater.updateCount)
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
        recurrenceRule: RecurrenceRule = RecurrenceRule.None
    ) = Task(
        id = id,
        title = "Task $id",
        description = "Description",
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        recurrenceRule = recurrenceRule,
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

    private val dailyRule = RecurrenceRule.Interval(
        IntervalUnit.DAYS,
        1,
        RecurrenceBasis.SCHEDULED_DATE
    )

    private class FakeTaskRepository(initialTask: Task? = null) : TaskRepository {
        val tasks = mutableMapOf<Int, Task>()
        val completedIds = mutableListOf<Int>()
        var completionFailure: RuntimeException? = null
        var completionGate: CompletableDeferred<Unit>? = null
        var suspendCompletion = false
        var getTaskCalls = 0
        var completeAtomicallyCalls = 0
        var createdOccurrences = 0

        init {
            initialTask?.let { tasks[it.id] = it }
        }

        override fun observeTask(taskId: Int): Flow<Task?> = flowOf(tasks[taskId])

        override fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections> =
            flowOf(TaskSections())

        override suspend fun getTask(taskId: Int): Task? {
            getTaskCalls++
            return tasks[taskId]
        }

        override suspend fun upsert(task: Task) = error("Not used")

        override suspend fun updateIfUnchanged(
            task: Task,
            expectedVersion: TaskSnapshotVersion
        ): Boolean = false

        override suspend fun completeAtomically(
            taskId: Int,
            completedAt: Long,
            completionDecision: (Task, Long) -> AtomicCompletionDecision
        ): AtomicCompletionResult {
            completeAtomicallyCalls++
            if (suspendCompletion) awaitCancellation()
            completionFailure?.let { throw it }
            completionGate?.await()
            val current = tasks[taskId] ?: return AtomicCompletionResult.NotFound
            if (current.isCompleted) return AtomicCompletionResult.AlreadyCompleted
            val next = when (val decision = completionDecision(current, completedAt)) {
                is AtomicCompletionDecision.Create -> decision.task
                AtomicCompletionDecision.CompleteOnly -> null
                is AtomicCompletionDecision.Invalid -> return AtomicCompletionResult.Invalid(decision.reason)
            }
            completedIds += taskId
            val completed = current.copy(isCompleted = true, completedAt = completedAt)
            tasks[taskId] = completed
            val occurrence = next?.copy(id = 100 + createdOccurrences++)
            occurrence?.let { tasks[it.id] = it }
            return AtomicCompletionResult.Completed(completed, occurrence)
        }

        override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot = error("Not used")
        override suspend fun deleteAll(): List<Int> = error("Not used")
        override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = error("Not used")
        override suspend fun deleteCompleted(taskId: Int) = error("Not used")
        override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) = Unit
        override suspend fun updateReminderStatusIfCurrent(
            expectedVersion: TaskSnapshotVersion,
            status: ReminderStatus
        ): Boolean {
            val current = tasks[expectedVersion.id] ?: return false
            if (current.snapshotVersion() != expectedVersion) return false
            tasks[current.id] = current.copy(reminderStatus = status)
            return true
        }
        override suspend fun futureReminders(after: Long): List<Task> = error("Not used")
    }
}
