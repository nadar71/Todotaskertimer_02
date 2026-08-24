package com.indiewalkabout.nowdothis.feature.task.domain

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteAllTasks
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ObserveTaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.RestoreDeletedTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.RestoreDeletedTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.TaskValidationError
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ValidateTask
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLifecycleUseCasesTest {
    @Test
    fun validate_returnsAllApplicableErrorsInFieldOrder() {
        val errors = ValidateTask().invoke(
            task(
                title = " ",
                description = "\t",
                dueAt = 1_000,
                reminderAt = 1_001,
                recurrence = RecurrenceType.DAILY,
                recurrenceEndAt = 999
            ),
            now = 2_000
        )

        assertEquals(
            listOf(
                TaskValidationError.BLANK_TITLE,
                TaskValidationError.BLANK_DESCRIPTION,
                TaskValidationError.REMINDER_AFTER_DUE,
                TaskValidationError.RECURRENCE_END_BEFORE_DUE,
                TaskValidationError.REMINDER_IN_PAST
            ),
            errors
        )
    }

    @Test
    fun validate_rejectsRecurrenceWithoutDueTime() {
        assertEquals(
            listOf(TaskValidationError.RECURRENCE_WITHOUT_DUE_TIME),
            ValidateTask().invoke(task(recurrence = RecurrenceType.WEEKLY), now = 500)
        )
    }

    @Test
    fun validate_acceptsEqualReminderDueEndAndNowBoundaries() {
        val boundary = 1_000L

        val errors = ValidateTask().invoke(
            task(
                dueAt = boundary,
                reminderAt = boundary,
                recurrence = RecurrenceType.MONTHLY,
                recurrenceEndAt = boundary
            ),
            now = boundary
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun observeTaskSections_usesSavedSortAndCurrentLocalDayBounds() = runTest {
        val repository = FakeTaskRepository()
        val expectedSections = TaskSections(today = listOf(task(id = 8, dueAt = 10)))
        repository.sections = expectedSections
        val preferences = FakeTaskPreferencesRepository(TaskSort.HIGH_FIRST)
        val useCase = ObserveTaskSections(
            repository = repository,
            preferencesRepository = preferences,
            clock = AppClock { 1_000 },
            zoneIdProvider = ZoneIdProvider { ZoneId.of("UTC") }
        )

        val result = useCase(TaskFilter(query = "report", categoryId = 3)).first()

        assertEquals(expectedSections, result)
        assertEquals(
            TaskFilter(query = "report", categoryId = 3, sort = TaskSort.HIGH_FIRST),
            repository.observedFilter
        )
        assertEquals(DayBounds(0, 86_400_000), repository.observedBounds)
    }

    @Test
    fun save_invalidTaskReturnsTypedErrorsWithoutPersistence() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = saveUseCase(repository, scheduler)(task(title = ""))

        assertEquals(
            SaveTaskResult.Invalid(listOf(TaskValidationError.BLANK_TITLE)),
            result
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun save_newRecurringTaskStampsAndPersistsBeforeExactScheduling() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(nextId = 41, events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = saveUseCase(repository, scheduler)(
            task(
                dueAt = 3_000,
                reminderAt = 2_000,
                recurrence = RecurrenceType.DAILY
            )
        )

        assertEquals(41, (result as SaveTaskResult.Saved).taskId)
        assertEquals(ReminderStatus.SCHEDULED, result.reminderStatus)
        val saved = repository.tasks.getValue(41)
        assertEquals(1_000L, saved.createdAt)
        assertEquals(1_000L, saved.updatedAt)
        assertNotNull(UUID.fromString(saved.seriesId))
        assertEquals(ReminderStatus.SCHEDULED, saved.reminderStatus)
        assertEquals(listOf("upsert:41", "schedule:41:2000", "status:41:SCHEDULED"), events)
    }

    @Test
    fun save_updatePreservesStoredCreationAndSeriesAndMapsInexactToScheduled() = runTest {
        val events = mutableListOf<String>()
        val existing = task(
            id = 7,
            dueAt = 3_000,
            recurrence = RecurrenceType.WEEKLY,
            seriesId = "existing-series",
            createdAt = 25,
            updatedAt = 30
        )
        val repository = FakeTaskRepository(existing, events = events)
        val scheduler = FakeReminderScheduler(
            scheduleResult = ReminderScheduleResult.INEXACT,
            events = events
        )

        val result = saveUseCase(repository, scheduler)(
            existing.copy(
                title = "Updated",
                reminderAt = 2_000,
                updatedAt = 30
            )
        )

        assertEquals(7, (result as SaveTaskResult.Saved).taskId)
        assertEquals(ReminderStatus.SCHEDULED, result.reminderStatus)
        val saved = repository.tasks.getValue(7)
        assertEquals(25L, saved.createdAt)
        assertEquals(1_000L, saved.updatedAt)
        assertEquals("existing-series", saved.seriesId)
        assertEquals(listOf("upsert:7", "schedule:7:2000", "status:7:SCHEDULED"), events)
    }

    @Test
    fun save_updateAdvancesVersionWhenClockMatchesStoredTimestamp() = runTest {
        val existing = task(id = 7, createdAt = 25, updatedAt = 1_000)
        val repository = FakeTaskRepository(existing)

        val result = saveUseCase(repository, FakeReminderScheduler())(
            existing.copy(title = "Updated in the same millisecond")
        )

        assertEquals(7, (result as SaveTaskResult.Saved).taskId)
        assertEquals(ReminderStatus.NONE, result.reminderStatus)
        assertEquals(1_001L, repository.tasks.getValue(7).updatedAt)
    }

    @Test
    fun save_failedSchedulingKeepsPersistedTaskAndRecordsUnavailable() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(nextId = 42, events = events)
        val scheduler = FakeReminderScheduler(
            scheduleResult = ReminderScheduleResult.FAILED,
            events = events
        )

        val result = saveUseCase(repository, scheduler)(
            task(dueAt = 3_000, reminderAt = 2_000)
        )

        assertEquals(42, (result as SaveTaskResult.Saved).taskId)
        assertEquals(ReminderStatus.UNAVAILABLE, result.reminderStatus)
        assertEquals(ReminderStatus.UNAVAILABLE, repository.tasks.getValue(42).reminderStatus)
        assertEquals(listOf("upsert:42", "schedule:42:2000", "status:42:UNAVAILABLE"), events)
    }

    @Test
    fun save_withoutFutureReminderCancelsStableAlarm() = runTest {
        val events = mutableListOf<String>()
        val existing = task(id = 9, reminderAt = null, reminderStatus = ReminderStatus.SCHEDULED)
        val repository = FakeTaskRepository(existing, events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = saveUseCase(repository, scheduler)(existing)

        assertEquals(9, (result as SaveTaskResult.Saved).taskId)
        assertEquals(ReminderStatus.NONE, result.reminderStatus)
        assertEquals(ReminderStatus.NONE, repository.tasks.getValue(9).reminderStatus)
        assertEquals(listOf("upsert:9", "cancel:9"), events)
    }

    @Test
    fun save_editWithoutExpectedVersionDoesNotRecreateMissingTask() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(events = events)

        val result = saveUseCase(repository, FakeReminderScheduler(events = events))(
            task(id = 7),
            expectedVersion = null
        )

        assertEquals(SaveTaskResult.Conflict, result)
        assertTrue(repository.tasks.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun save_repositoryFailurePropagatesWithoutScheduling() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(events = events).apply {
            upsertFailure = IllegalStateException("database unavailable")
        }
        val scheduler = FakeReminderScheduler(events = events)

        var failure: IllegalStateException? = null
        try {
            saveUseCase(repository, scheduler)(task(dueAt = 3_000, reminderAt = 2_000))
        } catch (exception: IllegalStateException) {
            failure = exception
        }

        assertEquals("database unavailable", failure?.message)
        assertTrue(events.isEmpty())
    }

    @Test
    fun complete_missingTaskReturnsNotFoundWithoutSchedulerCalls() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = completeUseCase(repository, scheduler)(404)

        assertEquals(CompleteTaskResult.NotFound, result)
        assertTrue(events.isEmpty())
    }

    @Test
    fun complete_alreadyCompletedTaskIsNoOp() = runTest {
        val events = mutableListOf<String>()
        val completed = task(id = 5).copy(isCompleted = true, completedAt = 500)
        val repository = FakeTaskRepository(completed, events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = completeUseCase(repository, scheduler)(5)

        assertEquals(CompleteTaskResult.AlreadyCompleted, result)
        assertEquals(completed, repository.tasks.getValue(5))
        assertTrue(events.isEmpty())
    }

    @Test
    fun complete_nonRecurringTaskCompletesChildrenAtomicallyThenCancelsAlarm() = runTest {
        val events = mutableListOf<String>()
        val current = task(id = 4).copy(
            subtasks = listOf(
                Subtask(id = 10, taskId = 4, title = "Pending", position = 1),
                Subtask(
                    id = 11,
                    taskId = 4,
                    title = "Done",
                    isCompleted = true,
                    completedAt = 700,
                    position = 0
                )
            )
        )
        val repository = FakeTaskRepository(current, events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = completeUseCase(repository, scheduler)(4) as CompleteTaskResult.Completed

        assertEquals(1_000L, result.completed.completedAt)
        assertTrue(result.completed.subtasks.all(Subtask::isCompleted))
        assertEquals(700L, result.completed.subtasks.first().completedAt)
        assertEquals(1_000L, result.completed.subtasks.last().completedAt)
        assertEquals(null, result.nextOccurrence)
        assertEquals(listOf("complete:4", "cancel:4"), events)
    }

    @Test
    fun complete_dailyTaskCopiesOrderedFreshSubtasksAndSchedulesPersistedNextIdentity() = runTest {
        val events = mutableListOf<String>()
        val due = epoch("2025-01-01T09:00:00Z")
        val nextDue = epoch("2025-01-02T09:00:00Z")
        val current = task(
            id = 4,
            dueAt = due,
            reminderAt = due - 3_600_000,
            reminderStatus = ReminderStatus.SCHEDULED,
            recurrence = RecurrenceType.DAILY,
            recurrenceEndAt = nextDue,
            seriesId = "series-4",
            createdAt = 50,
            updatedAt = 60
        ).copy(
            categoryId = 9,
            priority = TaskPriority.HIGH,
            subtasks = listOf(
                Subtask(id = 20, taskId = 4, title = "Second", position = 1),
                Subtask(
                    id = 21,
                    taskId = 4,
                    title = "First",
                    isCompleted = true,
                    completedAt = 40,
                    position = 0
                )
            )
        )
        val repository = FakeTaskRepository(current, nextId = 77, events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = completeUseCase(repository, scheduler)(4) as CompleteTaskResult.Completed

        val next = result.nextOccurrence!!
        assertEquals(77, next.id)
        assertEquals(nextDue, next.dueAt)
        assertEquals(nextDue - 3_600_000, next.reminderAt)
        assertEquals(ReminderStatus.SCHEDULED, next.reminderStatus)
        assertEquals("series-4", next.seriesId)
        assertEquals(9, next.categoryId)
        assertEquals(TaskPriority.HIGH, next.priority)
        assertEquals(listOf("First", "Second"), next.subtasks.map(Subtask::title))
        assertTrue(next.subtasks.all { it.id > 0 && it.taskId == 77 && !it.isCompleted })
        assertEquals(current.title, result.completed.title)
        assertEquals(current.dueAt, result.completed.dueAt)
        assertEquals(current.reminderAt, result.completed.reminderAt)
        assertEquals(current.recurrence, result.completed.recurrence)
        assertEquals(current.recurrenceEndAt, result.completed.recurrenceEndAt)
        assertEquals(current.seriesId, result.completed.seriesId)
        assertEquals(current.createdAt, result.completed.createdAt)
        assertTrue(repository.suppliedNext!!.subtasks.all { it.id == 0 && it.taskId == 0 })
        assertEquals(
            listOf(
                "complete:4",
                "cancel:4",
                "schedule:77:${nextDue - 3_600_000}",
                "status:77:SCHEDULED"
            ),
            events
        )
    }

    @Test
    fun complete_weeklyTaskAdvancesOneWeek() = runTest {
        val due = epoch("2025-01-01T09:00:00Z")
        val repository = FakeTaskRepository(
            task(id = 6, dueAt = due, recurrence = RecurrenceType.WEEKLY)
        )

        val result = completeUseCase(repository, FakeReminderScheduler())(6)
            as CompleteTaskResult.Completed

        assertEquals(epoch("2025-01-08T09:00:00Z"), result.nextOccurrence?.dueAt)
    }

    @Test
    fun complete_monthlyTaskUsesCalendarMonthProgression() = runTest {
        val repository = FakeTaskRepository(
            task(
                id = 7,
                dueAt = epoch("2025-01-31T09:00:00Z"),
                recurrence = RecurrenceType.MONTHLY
            )
        )

        val result = completeUseCase(repository, FakeReminderScheduler())(7)
            as CompleteTaskResult.Completed

        assertEquals(epoch("2025-02-28T09:00:00Z"), result.nextOccurrence?.dueAt)
    }

    @Test
    fun complete_afterRecurrenceEndDoesNotCreateAnotherOccurrence() = runTest {
        val due = epoch("2025-01-01T09:00:00Z")
        val repository = FakeTaskRepository(
            task(
                id = 8,
                dueAt = due,
                recurrence = RecurrenceType.DAILY,
                recurrenceEndAt = epoch("2025-01-02T08:59:59Z")
            )
        )
        val scheduler = FakeReminderScheduler()

        val result = completeUseCase(repository, scheduler)(8) as CompleteTaskResult.Completed

        assertEquals(null, result.nextOccurrence)
        assertEquals(null, repository.suppliedNext)
        assertEquals(listOf(8), scheduler.cancelledIds)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun complete_failedNextSchedulingRecordsUnavailableOnPersistedIdentity() = runTest {
        val due = epoch("2025-01-01T09:00:00Z")
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(
            initialTask = task(
                id = 10,
                dueAt = due,
                reminderAt = due - 1_000,
                recurrence = RecurrenceType.DAILY
            ),
            nextId = 88,
            events = events
        )
        val scheduler = FakeReminderScheduler(ReminderScheduleResult.FAILED, events)

        val result = completeUseCase(repository, scheduler)(10) as CompleteTaskResult.Completed

        assertEquals(ReminderStatus.UNAVAILABLE, result.nextOccurrence?.reminderStatus)
        assertEquals(ReminderStatus.UNAVAILABLE, repository.tasks.getValue(88).reminderStatus)
        assertEquals("status:88:UNAVAILABLE", events.last())
    }

    @Test
    fun delete_returnsCompleteSnapshotBeforeCancellingStableAlarm() = runTest {
        val events = mutableListOf<String>()
        val current = task(id = 4, reminderAt = 2_000).copy(
            subtasks = listOf(Subtask(id = 20, taskId = 4, title = "Child", position = 0))
        )
        val repository = FakeTaskRepository(current, events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val snapshot = DeleteTask(repository, scheduler)(4)

        assertEquals(DeletedTaskSnapshot(current), snapshot)
        assertTrue(4 !in repository.tasks)
        assertEquals(listOf("delete:4", "cancel:4"), events)
    }

    @Test
    fun restore_schedulesRequestedFutureReminderUsingCollisionRemappedIdentity() = runTest {
        val events = mutableListOf<String>()
        val snapshot = DeletedTaskSnapshot(
            task(
                id = 4,
                reminderAt = 2_000,
                reminderStatus = ReminderStatus.SCHEDULED
            )
        )
        val repository = FakeTaskRepository(events = events).apply { restoreId = 54 }
        val scheduler = FakeReminderScheduler(ReminderScheduleResult.INEXACT, events)

        val result = RestoreDeletedTask(repository, scheduler, AppClock { 1_000 })(snapshot)

        assertEquals(RestoreDeletedTaskResult(54, ReminderStatus.SCHEDULED), result)
        assertEquals(ReminderStatus.SCHEDULED, repository.tasks.getValue(54).reminderStatus)
        assertEquals(listOf("restore:54", "schedule:54:2000", "status:54:SCHEDULED"), events)
    }

    @Test
    fun restore_doesNotScheduleReminderWithoutARequest() = runTest {
        val events = mutableListOf<String>()
        val snapshot = DeletedTaskSnapshot(task(id = 4, reminderAt = 2_000))
        val repository = FakeTaskRepository(events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = RestoreDeletedTask(repository, scheduler, AppClock { 1_000 })(snapshot)

        assertEquals(RestoreDeletedTaskResult(4, ReminderStatus.NONE), result)
        assertEquals(listOf("restore:4"), events)
    }

    @Test
    fun restore_completedTaskKeepsHistoryButNormalizesReminderWithoutScheduling() = runTest {
        val events = mutableListOf<String>()
        val original = task(
            id = 11,
            reminderAt = 2_000,
            reminderStatus = ReminderStatus.SCHEDULED
        )
        val repository = FakeTaskRepository(original, events = events)
        val scheduler = FakeReminderScheduler(events = events)

        completeUseCase(repository, scheduler)(11)
        val snapshot = DeleteTask(repository, scheduler)(11)
        val result = RestoreDeletedTask(repository, scheduler, AppClock { 1_000 })(snapshot)

        val restored = repository.tasks.getValue(11)
        assertEquals(RestoreDeletedTaskResult(11, ReminderStatus.NONE), result)
        assertTrue(restored.isCompleted)
        assertEquals(1_000L, restored.completedAt)
        assertEquals(2_000L, restored.reminderAt)
        assertEquals(ReminderStatus.NONE, restored.reminderStatus)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(
            listOf(
                "complete:11",
                "cancel:11",
                "delete:11",
                "cancel:11",
                "restore:11",
                "status:11:NONE"
            ),
            events
        )
    }

    @Test
    fun restore_expiredScheduledReminderPersistsAndReturnsNoneWithoutScheduling() = runTest {
        val events = mutableListOf<String>()
        val snapshot = DeletedTaskSnapshot(
            task(
                id = 4,
                reminderAt = 999,
                reminderStatus = ReminderStatus.SCHEDULED
            )
        )
        val repository = FakeTaskRepository(events = events)
        val scheduler = FakeReminderScheduler(events = events)

        val result = RestoreDeletedTask(repository, scheduler, AppClock { 1_000 })(snapshot)

        val restored = repository.tasks.getValue(4)
        assertEquals(RestoreDeletedTaskResult(4, ReminderStatus.NONE), result)
        assertEquals(999L, restored.reminderAt)
        assertEquals(ReminderStatus.NONE, restored.reminderStatus)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf("restore:4", "status:4:NONE"), events)
    }

    @Test
    fun deleteAll_cancelsReturnedActiveReminderIdsAfterDeletion() = runTest {
        val events = mutableListOf<String>()
        val repository = FakeTaskRepository(events = events).apply {
            deleteAllReminderIds = listOf(9, 3, 12)
        }
        val scheduler = FakeReminderScheduler(events = events)

        DeleteAllTasks(repository, scheduler)()

        assertEquals(
            listOf("deleteAll", "cancel:9", "cancel:3", "cancel:12"),
            events
        )
    }

    private fun task(
        id: Int = 0,
        title: String = "Task",
        description: String = "Description",
        dueAt: Long? = null,
        reminderAt: Long? = null,
        reminderStatus: ReminderStatus = ReminderStatus.NONE,
        recurrence: RecurrenceType = RecurrenceType.NONE,
        recurrenceEndAt: Long? = null,
        seriesId: String? = null,
        createdAt: Long = 0,
        updatedAt: Long = 0
    ) = Task(
        id = id,
        title = title,
        description = description,
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        reminderAt = reminderAt,
        reminderStatus = reminderStatus,
        recurrence = recurrence,
        recurrenceEndAt = recurrenceEndAt,
        seriesId = seriesId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun epoch(value: String): Long = Instant.parse(value).toEpochMilli()

    private fun saveUseCase(
        repository: TaskRepository,
        scheduler: ReminderScheduler
    ) = SaveTask(repository, scheduler, ValidateTask(), AppClock { 1_000 })

    private fun completeUseCase(
        repository: TaskRepository,
        scheduler: ReminderScheduler
    ) = CompleteTask(
        repository,
        scheduler,
        CalculateNextOccurrence { ZoneId.of("UTC") },
        AppClock { 1_000 }
    )

    private class FakeTaskPreferencesRepository(initialSort: TaskSort) :
        TaskPreferencesRepository {
        override val taskSort = MutableStateFlow(initialSort)

        override suspend fun setTaskSort(sort: TaskSort) {
            taskSort.value = sort
        }
    }

    private class FakeReminderScheduler(
        var scheduleResult: ReminderScheduleResult = ReminderScheduleResult.EXACT,
        private val events: MutableList<String> = mutableListOf()
    ) : ReminderScheduler {
        val scheduled = mutableListOf<Pair<Int, Long>>()
        val cancelledIds = mutableListOf<Int>()

        override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
            events += "schedule:$taskId:$triggerAt"
            scheduled += taskId to triggerAt
            return scheduleResult
        }

        override suspend fun cancel(taskId: Int) {
            events += "cancel:$taskId"
            cancelledIds += taskId
        }

        override suspend fun reconcile() = Unit
    }

    private class FakeTaskRepository(
        initialTask: Task? = null,
        private var nextId: Int = 100,
        private val events: MutableList<String> = mutableListOf()
    ) : TaskRepository {
        val tasks = mutableMapOf<Int, Task>()
        var sections = TaskSections()
        var observedFilter: TaskFilter? = null
        var observedBounds: DayBounds? = null
        var upsertFailure: RuntimeException? = null
        var suppliedNext: Task? = null
        var restoreId: Int? = null
        var deleteAllReminderIds: List<Int> = emptyList()

        init {
            initialTask?.let { tasks[it.id] = it }
        }

        override fun observeTask(taskId: Int): Flow<Task?> = flowOf(tasks[taskId])

        override fun observeSections(
            filter: TaskFilter,
            bounds: DayBounds
        ): Flow<TaskSections> {
            observedFilter = filter
            observedBounds = bounds
            return flowOf(sections)
        }

        override suspend fun getTask(taskId: Int): Task? = tasks[taskId]

        override suspend fun upsert(task: Task): Int {
            upsertFailure?.let { throw it }
            val id = task.id.takeIf { it != 0 } ?: nextId++
            tasks[id] = task.copy(id = id)
            events += "upsert:$id"
            return id
        }

        override suspend fun updateIfUnchanged(
            task: Task,
            expectedVersion: TaskSnapshotVersion
        ): Boolean {
            upsertFailure?.let { throw it }
            val current = tasks[task.id] ?: return false
            if (current.snapshotVersion() != expectedVersion) return false
            tasks[task.id] = task
            events += "upsert:${task.id}"
            return true
        }

        override suspend fun completeAtomically(
            taskId: Int,
            completedAt: Long,
            nextOccurrence: (Task) -> Task?
        ): AtomicCompletionResult {
            val current = tasks[taskId] ?: return AtomicCompletionResult.NotFound
            if (current.isCompleted) return AtomicCompletionResult.AlreadyCompleted
            val next = nextOccurrence(current)
            suppliedNext = next
            events += "complete:$taskId"
            val completed = current.copy(
                isCompleted = true,
                completedAt = completedAt,
                updatedAt = completedAt,
                subtasks = current.subtasks.sortedBy { it.position }.map { subtask ->
                    if (subtask.isCompleted) {
                        subtask
                    } else {
                        subtask.copy(isCompleted = true, completedAt = completedAt)
                    }
                }
            )
            tasks[taskId] = completed
            val persistedNext = next?.let { occurrence ->
                val id = nextId++
                val persisted = occurrence.copy(
                    id = id,
                    subtasks = occurrence.subtasks.mapIndexed { index, subtask ->
                        subtask.copy(id = 1_000 + index, taskId = id)
                    }
                )
                tasks[id] = persisted
                persisted
            }
            return AtomicCompletionResult.Completed(completed, persistedNext)
        }

        override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot {
            val deleted = requireNotNull(tasks.remove(taskId))
            events += "delete:$taskId"
            return DeletedTaskSnapshot(deleted)
        }

        override suspend fun deleteAll(): List<Int> {
            tasks.clear()
            events += "deleteAll"
            return deleteAllReminderIds
        }

        override suspend fun restore(snapshot: DeletedTaskSnapshot): Int {
            val id = restoreId ?: snapshot.task.id.takeIf { it != 0 } ?: nextId++
            tasks[id] = snapshot.task.copy(
                id = id,
                subtasks = snapshot.task.subtasks.map { it.copy(taskId = id) }
            )
            events += "restore:$id"
            return id
        }

        override suspend fun deleteCompleted(taskId: Int) = error("Not used")

        override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(reminderStatus = status) }
            events += "status:$taskId:$status"
        }

        override suspend fun updateReminderStatusIfCurrent(
            expectedVersion: TaskSnapshotVersion,
            status: ReminderStatus
        ): Boolean {
            val task = tasks[expectedVersion.id] ?: return false
            if (task.snapshotVersion() != expectedVersion) return false
            tasks[task.id] = task.copy(reminderStatus = status)
            events += "status:${task.id}:$status"
            return true
        }

        override suspend fun futureReminders(after: Long): List<Task> = error("Not used")
    }
}
