package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionDecision
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderReconcileResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmManagerReminderSchedulerTest {
    @Test
    fun schedule_usesExactAlarmWhenAccessIsAvailable() = runTest {
        val gateway = FakeAlarmGateway(canScheduleExact = true)
        val scheduler = scheduler(gateway)

        val result = scheduler.schedule(taskId = 7, triggerAt = 5_000)

        assertEquals(ReminderScheduleResult.EXACT, result)
        assertEquals(listOf(AlarmCall.Exact(7, 5_000)), gateway.calls)
    }

    @Test
    fun schedule_usesInexactAlarmWhenExactAccessIsUnavailable() = runTest {
        val gateway = FakeAlarmGateway(canScheduleExact = false)
        val scheduler = scheduler(gateway)

        val result = scheduler.schedule(taskId = 9, triggerAt = 5_000)

        assertEquals(ReminderScheduleResult.INEXACT, result)
        assertEquals(listOf(AlarmCall.Inexact(9, 5_000)), gateway.calls)
    }

    @Test
    fun schedule_fallsBackToInexactWhenExactInstallationLosesAccess() = runTest {
        val gateway = FakeAlarmGateway(
            canScheduleExact = true,
            exactResults = mutableMapOf(12 to false)
        )
        val scheduler = scheduler(gateway)

        val result = scheduler.schedule(taskId = 12, triggerAt = 8_000)

        assertEquals(ReminderScheduleResult.INEXACT, result)
        assertEquals(
            listOf(AlarmCall.Exact(12, 8_000), AlarmCall.Inexact(12, 8_000)),
            gateway.calls
        )
    }

    @Test
    fun schedule_returnsFailedWhenNoAlarmCanBeInstalled() = runTest {
        val gateway = FakeAlarmGateway(
            canScheduleExact = true,
            exactResults = mutableMapOf(13 to false),
            inexactResults = mutableMapOf(13 to false)
        )
        val scheduler = scheduler(gateway)

        val result = scheduler.schedule(taskId = 13, triggerAt = 9_000)

        assertEquals(ReminderScheduleResult.FAILED, result)
        assertEquals(
            listOf(AlarmCall.Exact(13, 9_000), AlarmCall.Inexact(13, 9_000)),
            gateway.calls
        )
    }

    @Test
    fun schedule_reusesTaskIdAsStableReplacementIdentity() = runTest {
        val gateway = FakeAlarmGateway(canScheduleExact = true)
        val scheduler = scheduler(gateway)

        scheduler.schedule(taskId = 21, triggerAt = 5_000)
        scheduler.schedule(taskId = 21, triggerAt = 7_000)

        assertEquals(
            listOf(AlarmCall.Exact(21, 5_000), AlarmCall.Exact(21, 7_000)),
            gateway.calls
        )
    }

    @Test
    fun cancel_delegatesStableTaskIdentity() = runTest {
        val gateway = FakeAlarmGateway(canScheduleExact = true)
        val scheduler = scheduler(gateway)

        scheduler.cancel(taskId = 34)

        assertEquals(listOf(AlarmCall.Cancel(34)), gateway.calls)
    }

    @Test
    fun reconcile_requestsCurrentFutureRemindersAndRecordsEveryOutcome() = runTest {
        val repository = FakeTaskRepository(
            reminders = listOf(
                task(id = 1, reminderAt = 2_000, status = ReminderStatus.REQUESTED),
                task(id = 2, reminderAt = 3_000, status = ReminderStatus.UNAVAILABLE),
                task(id = 3, reminderAt = 4_000, status = ReminderStatus.SCHEDULED)
            )
        )
        val gateway = FakeAlarmGateway(
            canScheduleExact = true,
            exactResults = mutableMapOf(2 to false, 3 to false),
            inexactResults = mutableMapOf(3 to false)
        )
        val scheduler = scheduler(gateway, repository)

        val result = scheduler.reconcileWithResult()

        assertEquals(1_000L, repository.requestedAfter)
        assertEquals(
            listOf(
                1 to ReminderStatus.SCHEDULED,
                2 to ReminderStatus.SCHEDULED,
                3 to ReminderStatus.UNAVAILABLE
            ),
            repository.statusUpdates
        )
        assertEquals(ReminderReconcileResult.SOME_UNAVAILABLE, result)
    }

    @Test
    fun reconcile_defensivelyIgnoresStaleCompletedAndNonReminderRows() = runTest {
        val repository = FakeTaskRepository(
            reminders = listOf(
                task(id = 1, reminderAt = 1_000, status = ReminderStatus.REQUESTED),
                task(id = 2, reminderAt = 2_000, status = ReminderStatus.REQUESTED, completed = true),
                task(id = 3, reminderAt = 3_000, status = ReminderStatus.NONE),
                task(id = 4, reminderAt = null, status = ReminderStatus.UNAVAILABLE),
                task(id = 5, reminderAt = 5_000, status = ReminderStatus.UNAVAILABLE)
            )
        )
        val gateway = FakeAlarmGateway(canScheduleExact = true)
        val scheduler = scheduler(gateway, repository)

        val result = scheduler.reconcileWithResult()

        assertEquals(listOf(AlarmCall.Exact(5, 5_000)), gateway.calls)
        assertEquals(listOf(5 to ReminderStatus.SCHEDULED), repository.statusUpdates)
        assertEquals(ReminderReconcileResult.SUCCESS, result)
    }

    @Test
    fun reconcile_continuesAfterOneReminderThrows() = runTest {
        val repository = FakeTaskRepository(
            reminders = listOf(
                task(id = 1, reminderAt = 2_000, status = ReminderStatus.REQUESTED),
                task(id = 2, reminderAt = 3_000, status = ReminderStatus.REQUESTED)
            )
        )
        val gateway = FakeAlarmGateway(
            canScheduleExact = true,
            throwingTaskIds = setOf(1)
        )
        val scheduler = scheduler(gateway, repository)

        val result = scheduler.reconcileWithResult()

        assertEquals(
            listOf(
                1 to ReminderStatus.UNAVAILABLE,
                2 to ReminderStatus.SCHEDULED
            ),
            repository.statusUpdates
        )
        assertTrue(gateway.calls.contains(AlarmCall.Exact(2, 3_000)))
        assertEquals(ReminderReconcileResult.SOME_UNAVAILABLE, result)
    }

    @Test
    fun reconcile_reportsUnavailableAndContinuesWhenOneStatusUpdateFails() = runTest {
        val repository = FakeTaskRepository(
            reminders = listOf(
                task(id = 1, reminderAt = 2_000, status = ReminderStatus.REQUESTED),
                task(id = 2, reminderAt = 3_000, status = ReminderStatus.REQUESTED)
            ),
            failingStatusTaskIds = setOf(1)
        )
        val scheduler = scheduler(FakeAlarmGateway(canScheduleExact = true), repository)

        val result = scheduler.reconcileWithResult()

        assertEquals(listOf(2 to ReminderStatus.SCHEDULED), repository.statusUpdates)
        assertEquals(ReminderReconcileResult.SOME_UNAVAILABLE, result)
    }

    @Test
    fun reconcile_reusedIdWithoutReminderCancelsStaleAlarmWithoutClobberingReplacement() = runTest {
        val original = task(id = 41, reminderAt = 2_000, status = ReminderStatus.REQUESTED)
        val replacement = original.copy(
            reminderAt = null,
            reminderStatus = ReminderStatus.NONE
        )
        val repository = FakeTaskRepository(reminders = listOf(original))
        var replaced = false
        val gateway = FakeAlarmGateway(
            canScheduleExact = true,
            beforeExact = { taskId, triggerAt ->
                if (!replaced && taskId == original.id && triggerAt == original.reminderAt) {
                    replaced = true
                    repository.replace(replacement)
                }
            }
        )
        val scheduler = scheduler(gateway, repository)

        val result = scheduler.reconcileWithResult()

        assertEquals(ReminderReconcileResult.SUCCESS, result)
        assertEquals(replacement, repository.current(original.id))
        assertTrue(repository.statusUpdates.isEmpty())
        assertTrue(original.id !in gateway.activeAlarms)
        assertTrue(gateway.calls.contains(AlarmCall.Cancel(original.id)))
    }

    @Test
    fun reconcile_reusedIdWithDifferentTriggerCancelsStaleAlarmAndSchedulesReplacement() = runTest {
        val original = task(id = 42, reminderAt = 2_000, status = ReminderStatus.REQUESTED)
        val replacement = original.copy(
            reminderAt = 4_000
        )
        val repository = FakeTaskRepository(reminders = listOf(original))
        var replaced = false
        val gateway = FakeAlarmGateway(
            canScheduleExact = true,
            beforeExact = { taskId, triggerAt ->
                if (!replaced && taskId == original.id && triggerAt == original.reminderAt) {
                    replaced = true
                    repository.replace(replacement)
                }
            }
        )
        val scheduler = scheduler(gateway, repository)

        val result = scheduler.reconcileWithResult()

        assertEquals(ReminderReconcileResult.SUCCESS, result)
        assertEquals(4_000L, gateway.activeAlarms[original.id])
        assertEquals(
            replacement.copy(reminderStatus = ReminderStatus.SCHEDULED),
            repository.current(original.id)
        )
        assertEquals(
            listOf(original.id to ReminderStatus.SCHEDULED),
            repository.statusUpdates
        )
        assertTrue(gateway.calls.contains(AlarmCall.Cancel(original.id)))
        assertEquals(AlarmCall.Exact(original.id, 4_000), gateway.calls.last())
    }

    private fun scheduler(
        gateway: AlarmGateway,
        repository: TaskRepository = FakeTaskRepository()
    ) = AlarmManagerReminderScheduler(
        gateway = gateway,
        taskRepository = repository,
        clock = AppClock { 1_000 }
    )

    private fun task(
        id: Int,
        reminderAt: Long?,
        status: ReminderStatus,
        completed: Boolean = false
    ) = Task(
        id = id,
        title = "Task $id",
        description = "Description",
        priority = TaskPriority.MEDIUM,
        isCompleted = completed,
        reminderAt = reminderAt,
        reminderStatus = status,
        createdAt = 100,
        updatedAt = 100
    )
}

internal sealed interface AlarmCall {
    data class Exact(val taskId: Int, val triggerAt: Long) : AlarmCall
    data class Inexact(val taskId: Int, val triggerAt: Long) : AlarmCall
    data class Cancel(val taskId: Int) : AlarmCall
}

internal class FakeAlarmGateway(
    override val canScheduleExact: Boolean,
    private val exactResults: MutableMap<Int, Boolean> = mutableMapOf(),
    private val inexactResults: MutableMap<Int, Boolean> = mutableMapOf(),
    private val throwingTaskIds: Set<Int> = emptySet(),
    private val beforeExact: (Int, Long) -> Unit = { _, _ -> }
) : AlarmGateway {
    val calls = mutableListOf<AlarmCall>()
    val activeAlarms = mutableMapOf<Int, Long>()

    override fun setExact(taskId: Int, triggerAt: Long): Boolean {
        beforeExact(taskId, triggerAt)
        calls += AlarmCall.Exact(taskId, triggerAt)
        if (taskId in throwingTaskIds) error("alarm service unavailable")
        return (exactResults[taskId] ?: true).also { installed ->
            if (installed) activeAlarms[taskId] = triggerAt
        }
    }

    override fun setInexact(taskId: Int, triggerAt: Long): Boolean {
        calls += AlarmCall.Inexact(taskId, triggerAt)
        if (taskId in throwingTaskIds) error("alarm service unavailable")
        return (inexactResults[taskId] ?: true).also { installed ->
            if (installed) activeAlarms[taskId] = triggerAt
        }
    }

    override fun cancel(taskId: Int) {
        calls += AlarmCall.Cancel(taskId)
        activeAlarms.remove(taskId)
    }
}

internal class FakeTaskRepository(
    private val reminders: List<Task> = emptyList(),
    private val failingStatusTaskIds: Set<Int> = emptySet()
) : TaskRepository {
    private val tasks = reminders.associateBy(Task::id).toMutableMap()
    var requestedAfter: Long? = null
    val statusUpdates = mutableListOf<Pair<Int, ReminderStatus>>()

    fun replace(task: Task) {
        tasks[task.id] = task
    }

    fun current(taskId: Int): Task? = tasks[taskId]

    override fun observeTask(taskId: Int): Flow<Task?> = flowOf(null)
    override fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections> =
        flowOf(TaskSections())

    override suspend fun getTask(taskId: Int): Task? = tasks[taskId]
    override suspend fun upsert(task: Task): Int = task.id
    override suspend fun updateIfUnchanged(
        task: Task,
        expectedVersion: TaskSnapshotVersion
    ): Boolean = false
    override suspend fun completeAtomically(
        taskId: Int,
        completedAt: Long,
        completionDecision: (Task, Long) -> AtomicCompletionDecision
    ): AtomicCompletionResult = AtomicCompletionResult.NotFound

    override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot =
        error("Not used")

    override suspend fun deleteAll(): List<Int> = emptyList()
    override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = snapshot.task.id
    override suspend fun deleteCompleted(taskId: Int) = Unit

    override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) {
        if (taskId in failingStatusTaskIds) error("database unavailable")
        tasks[taskId]?.let { task -> tasks[taskId] = task.copy(reminderStatus = status) }
        statusUpdates += taskId to status
    }

    override suspend fun updateReminderStatusIfCurrent(
        expectedVersion: TaskSnapshotVersion,
        status: ReminderStatus
    ): Boolean {
        if (expectedVersion.id in failingStatusTaskIds) error("database unavailable")
        val current = tasks[expectedVersion.id] ?: return false
        if (current.snapshotVersion() != expectedVersion) return false
        tasks[expectedVersion.id] = current.copy(reminderStatus = status)
        statusUpdates += expectedVersion.id to status
        return true
    }

    override suspend fun futureReminders(after: Long): List<Task> {
        requestedAfter = after
        return reminders
    }
}
