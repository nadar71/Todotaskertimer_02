package com.indiewalkabout.nowdothis.feature.quickcapture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataSource
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureResult
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.task.data.repository.OfflineTaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionDecision
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ValidateTask
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickCaptureCompletionConcurrencyTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineTaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OfflineTaskRepository(database, database.taskDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun concurrentWidgetAndNormalCompletion_createAndScheduleOneRecurringSuccessor() = runTest {
        val currentDueAt = 2_000L
        val nextDueAt = currentDueAt + ONE_DAY_MILLIS
        val currentId = repository.upsert(
            Task(
                title = "Recurring",
                description = "Description",
                priority = TaskPriority.MEDIUM,
                dueAt = currentDueAt,
                reminderAt = 1_500,
                recurrenceRule = DAILY_RULE,
                createdAt = 0,
                updatedAt = 0
            )
        )
        val barrierRepository = CoordinatedCompletionRepository(repository, expectedArrivals = 2)
        val scheduler = RecordingReminderScheduler()
        val normalCompleteTask = completeTask(barrierRepository, scheduler)
        val widgetCompleteTask = CompleteQuickCaptureTask(
            completeTask = completeTask(barrierRepository, scheduler),
            updater = QuickCaptureWidgetUpdater { }
        )

        val widgetResult = async { widgetCompleteTask(currentId) }
        val normalResult = async { normalCompleteTask(currentId) }

        val terminalResults = listOf(widgetResult.await(), normalResult.await())
        assertEquals(
            1,
            terminalResults.count {
                it == CompleteQuickCaptureResult.Completed || it is CompleteTaskResult.Completed
            }
        )
        assertEquals(1, repository.observeDay(nextDueAt, nextDueAt + 1).first().size)
        assertEquals(1, scheduler.cancelledTaskIds.size)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(nextDueAt - 500 to ReminderScheduleResult.EXACT, scheduler.scheduled.single().second)
    }

    @Test
    fun completionInterleavedWithSave_usesSavedTaskForCompletionAndSuccessor() = runTest {
        val originalDueAt = 2_000L
        val savedDueAt = 20_000L
        val currentId = repository.upsert(
            recurringTask(
                title = "Original daily task",
                dueAt = originalDueAt,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            )
        )
        val coordinatedRepository = CoordinatedCompletionRepository(repository)
        val scheduler = RecordingReminderScheduler()
        val completion = async { completeTask(coordinatedRepository, scheduler)(currentId) }

        coordinatedRepository.awaitArrivals()
        repository.upsert(
            requireNotNull(repository.getTask(currentId)).copy(
                title = "Saved weekly task",
                dueAt = savedDueAt,
                reminderAt = 19_000L,
                recurrenceRule = WEEKLY_RULE,
                updatedAt = 500L
            )
        )
        coordinatedRepository.release()

        val result = completion.await() as CompleteTaskResult.Completed
        val expectedNextDueAt = savedDueAt + ONE_WEEK_MILLIS
        assertEquals("Saved weekly task", result.completed.title)
        assertEquals("Saved weekly task", requireNotNull(result.nextOccurrence).title)
        assertEquals(expectedNextDueAt, result.nextOccurrence.dueAt)
        assertEquals(expectedNextDueAt - 1_000L, result.nextOccurrence.reminderAt)
        assertEquals(WEEKLY_RULE, result.nextOccurrence.recurrenceRule)
    }

    @Test
    fun completionInterleavedWithReplaceAll_usesRestoredNonRecurringTaskSnapshot() = runTest {
        val currentId = repository.upsert(
            recurringTask(
                title = "Original recurring task",
                dueAt = 2_000L,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            )
        )
        val coordinatedRepository = CoordinatedCompletionRepository(repository)
        val completion = async {
            completeTask(coordinatedRepository, RecordingReminderScheduler())(currentId)
        }

        coordinatedRepository.awaitArrivals()
        PlanningDataSource(database).replaceAll(
            PlanningBackup(
                format = "now-do-this-backup",
                version = 1,
                createdAtEpochMillis = 500L,
                categories = emptyList(),
                tasks = listOf(
                    PlanningTask(
                        id = currentId,
                        title = "Restored one-off task",
                        description = "Restored description",
                        priority = TaskPriority.HIGH.name,
                        categoryId = null,
                        isCompleted = false,
                        completedAt = null,
                        dueAt = 30_000L,
                        reminderAt = null,
                        reminderStatus = "NONE",
                        recurrenceRule = RecurrenceRule.None,
                        recurrenceEndAt = null,
                        seriesId = null,
                        createdAt = 100L,
                        updatedAt = 500L,
                        subtasks = emptyList()
                    )
                )
            )
        )
        coordinatedRepository.release()

        val result = completion.await() as CompleteTaskResult.Completed
        assertEquals("Restored one-off task", result.completed.title)
        assertEquals(null, result.nextOccurrence)
        assertEquals(listOf(currentId), database.taskDao().getAllTaskIds())
    }

    @Test
    fun staleSaveResumingAfterCompletion_doesNotResurrectOriginalOrOverwriteSuccessor() = runTest {
        val currentId = repository.upsert(
            recurringTask(
                title = "Canonical recurring task",
                dueAt = 2_000L,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            ).copy(reminderAt = null)
        )
        val staleDraft = requireNotNull(repository.getTask(currentId)).copy(
            title = "Stale editor title"
        )
        val coordinatedRepository = CoordinatedSaveReadRepository(repository)
        val save = async {
            SaveTask(
                repository = coordinatedRepository,
                scheduler = RecordingReminderScheduler(),
                validateTask = ValidateTask(),
                clock = AppClock { 1_000L },
                seriesIdFactory = { "unused-series" }
            )(staleDraft)
        }

        coordinatedRepository.awaitRead()
        completeTask(repository, RecordingReminderScheduler())(currentId)
        coordinatedRepository.release()
        assertEquals(SaveTaskResult.Conflict, save.await())

        val tasks = database.taskDao().getAllTaskEntities()
        val original = tasks.single { it.id == currentId }
        assertEquals("Canonical recurring task", original.title)
        assertEquals(true, original.isCompleted)
        assertEquals(2, tasks.size)
        assertEquals(1, tasks.count { !it.isCompleted })
        assertEquals(ReminderStatus.NONE.name, tasks.single { !it.isCompleted }.reminderStatus)
    }

    @Test
    fun saveScheduleResumingAfterWidgetCompletion_keepsOnlySuccessorAlarm() = runTest {
        val currentDueAt = 10_000L
        val currentReminderAt = 9_000L
        val currentId = repository.upsert(
            recurringTask(
                title = "Recurring before save",
                dueAt = currentDueAt,
                reminderAt = currentReminderAt,
                recurrenceRule = DAILY_RULE
            )
        )
        val staleDraft = requireNotNull(repository.getTask(currentId)).copy(
            title = "Committed before completion"
        )
        val scheduler = SchedulePausingReminderScheduler()
        val save = async {
            SaveTask(
                repository = repository,
                scheduler = scheduler,
                validateTask = ValidateTask(),
                clock = AppClock { 1_000L },
                seriesIdFactory = { "unused-series" }
            )(staleDraft)
        }

        assertEquals(currentId, scheduler.awaitPausedSchedule())
        val completion = CompleteQuickCaptureTask(
            completeTask = completeTask(repository, scheduler),
            updater = QuickCaptureWidgetUpdater { }
        )(currentId)
        assertEquals(CompleteQuickCaptureResult.Completed, completion)

        val successorBeforeResume = requireNotNull(
            repository.observeDay(
                currentDueAt + ONE_DAY_MILLIS,
                currentDueAt + ONE_DAY_MILLIS + 1
            ).first().singleOrNull()
        )
        assertEquals(
            mapOf(successorBeforeResume.id to (currentReminderAt + ONE_DAY_MILLIS)),
            scheduler.activeAlarms
        )

        scheduler.releasePausedSchedule()
        val staleSaveResult = save.await()

        val completedSource = requireNotNull(repository.getTask(currentId))
        val successor = requireNotNull(repository.getTask(successorBeforeResume.id))
        assertTrue(completedSource.isCompleted)
        assertEquals(ReminderStatus.REQUESTED, completedSource.reminderStatus)
        assertEquals(ReminderStatus.SCHEDULED, successor.reminderStatus)
        assertEquals(
            mapOf(successor.id to requireNotNull(successor.reminderAt)),
            scheduler.activeAlarms
        )
        assertEquals(SaveTaskResult.Conflict, staleSaveResult)
    }

    @Test
    fun staleNoReminderCancel_reconcilesLaterSaveAlarmForSameTaskId() = runTest {
        val originalReminderAt = 9_000L
        val laterReminderAt = 19_000L
        val currentId = repository.upsert(
            recurringTask(
                title = "Owner before cancellation",
                dueAt = 10_000L,
                reminderAt = originalReminderAt,
                recurrenceRule = RecurrenceRule.None
            ).copy(reminderStatus = ReminderStatus.SCHEDULED)
        )
        val scheduler = CancelPausingReminderScheduler(
            initialAlarms = mapOf(currentId to originalReminderAt)
        )
        val original = requireNotNull(repository.getTask(currentId))
        val removingDraft = original.copy(reminderAt = null)
        val staleSave = async {
            SaveTask(
                repository = repository,
                scheduler = scheduler,
                validateTask = ValidateTask(),
                clock = AppClock { 1_000L }
            ).invoke(
                task = removingDraft,
                expectedVersion = original.snapshotVersion()
            )
        }

        assertEquals(currentId, scheduler.awaitPausedCancel())
        val ownerAfterRemoval = requireNotNull(repository.getTask(currentId))
        val laterSaveTask = SaveTask(
            repository = repository,
            scheduler = scheduler,
            validateTask = ValidateTask(),
            clock = AppClock { 1_000L }
        )
        val laterSave = laterSaveTask.invoke(
            task = ownerAfterRemoval.copy(
                title = "Later reminder owner",
                dueAt = 20_000L,
                reminderAt = laterReminderAt
            ),
            expectedVersion = ownerAfterRemoval.snapshotVersion()
        )
        assertTrue(laterSave is SaveTaskResult.Saved)
        assertEquals(mapOf(currentId to laterReminderAt), scheduler.activeAlarms)

        scheduler.releasePausedCancel()
        val staleSaveResult = staleSave.await()

        val current = requireNotNull(repository.getTask(currentId))
        assertEquals("Later reminder owner", current.title)
        assertEquals(laterReminderAt, current.reminderAt)
        assertEquals(ReminderStatus.SCHEDULED, current.reminderStatus)
        assertEquals(mapOf(currentId to laterReminderAt), scheduler.activeAlarms)
        assertEquals(SaveTaskResult.Conflict, staleSaveResult)
    }

    @Test
    fun replaceAllAfterCompletionCommit_doesNotScheduleDeletedOrReusedSuccessor() = runTest {
        val currentId = repository.upsert(
            recurringTask(
                title = "Recurring with reminder",
                dueAt = 2_000L,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            )
        )
        val coordinatedRepository = CoordinatedPostCompletionRepository(repository)
        val scheduler = RecordingReminderScheduler()
        val completion = async { completeTask(coordinatedRepository, scheduler)(currentId) }

        val committed = coordinatedRepository.awaitCommit()
        val successorId = requireNotNull(committed.nextOccurrence).id
        replaceAllWithReusedTaskId(successorId)
        coordinatedRepository.release()
        completion.await()

        val restored = requireNotNull(repository.getTask(successorId))
        assertEquals("Restored replacement", restored.title)
        assertEquals(ReminderStatus.NONE, restored.reminderStatus)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun sameTokenReplaceAllAfterCommit_doesNotScheduleRemovedReminder() = runTest {
        val currentId = repository.upsert(
            recurringTask(
                title = "Recurring with reminder",
                dueAt = 2_000L,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            ).copy(seriesId = "same-token-series")
        )
        val coordinatedRepository = CoordinatedPostCompletionRepository(repository)
        val scheduler = RecordingReminderScheduler()
        val completion = async { completeTask(coordinatedRepository, scheduler)(currentId) }

        val committed = coordinatedRepository.awaitCommit()
        val successor = requireNotNull(committed.nextOccurrence)
        replaceAllWithReusedTaskId(
            taskId = successor.id,
            title = "Same-token restored replacement",
            reminderAt = null,
            reminderStatus = ReminderStatus.NONE,
            seriesId = requireNotNull(successor.seriesId),
            createdAt = successor.createdAt,
            updatedAt = successor.updatedAt,
            isCompleted = successor.isCompleted,
            completedAt = successor.completedAt,
            recurrenceRule = successor.recurrenceRule,
            recurrenceEndAt = successor.recurrenceEndAt
        )
        coordinatedRepository.release()
        completion.await()

        val restored = requireNotNull(repository.getTask(successor.id))
        assertEquals("Same-token restored replacement", restored.title)
        assertEquals(null, restored.reminderAt)
        assertEquals(ReminderStatus.NONE, restored.reminderStatus)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun replaceAllAfterSuccessorScheduling_cancelsStaleAlarmWithoutUpdatingReusedRow() = runTest {
        val currentId = repository.upsert(
            recurringTask(
                title = "Recurring with reminder",
                dueAt = 2_000L,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            )
        )
        val scheduler = CoordinatedScheduleReminderScheduler()
        val completion = async { completeTask(repository, scheduler)(currentId) }

        val successorId = scheduler.awaitScheduleStart()
        replaceAllWithReusedTaskId(successorId)
        scheduler.releaseSchedule()
        completion.await()

        val restored = requireNotNull(repository.getTask(successorId))
        assertEquals("Restored replacement", restored.title)
        assertEquals(ReminderStatus.NONE, restored.reminderStatus)
        assertTrue(successorId in scheduler.cancelledTaskIds)
    }

    @Test
    fun replaceAllAfterSuccessorScheduling_preservesReconciledRestoredReminder() = runTest {
        val currentId = repository.upsert(
            recurringTask(
                title = "Recurring with reminder",
                dueAt = 2_000L,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            )
        )
        val restoredReminderAt = 25_000L
        val scheduler = ReconciledActiveReminderScheduler(repository, now = 1_000L)
        val completion = async { completeTask(repository, scheduler)(currentId) }

        val successorId = scheduler.awaitFirstSchedule()
        replaceAllWithReusedTaskId(
            taskId = successorId,
            reminderAt = restoredReminderAt,
            reminderStatus = ReminderStatus.REQUESTED
        )
        scheduler.reconcile()
        scheduler.releaseFirstSchedule()
        completion.await()

        val restored = requireNotNull(repository.getTask(successorId))
        assertEquals("Restored replacement", restored.title)
        assertEquals(ReminderStatus.SCHEDULED, restored.reminderStatus)
        assertEquals(restoredReminderAt, scheduler.activeAlarms[successorId])
    }

    @Test
    fun secondOwnerChangeDuringFallback_cancelsAlarmForNowIneligibleOwner() = runTest {
        val currentId = repository.upsert(
            recurringTask(
                title = "Recurring with reminder",
                dueAt = 2_000L,
                reminderAt = 1_500L,
                recurrenceRule = DAILY_RULE
            )
        )
        val ownerAReminderAt = 25_000L
        val coordinatedRepository = CoordinatedFallbackStatusRepository(repository)
        val scheduler = ReconciledActiveReminderScheduler(repository, now = 1_000L)
        val completion = async {
            completeTask(coordinatedRepository, scheduler)(currentId)
        }

        val successorId = scheduler.awaitFirstSchedule()
        replaceAllWithReusedTaskId(
            taskId = successorId,
            title = "Restored owner A",
            reminderAt = ownerAReminderAt,
            reminderStatus = ReminderStatus.REQUESTED,
            seriesId = "restored-series-a",
            updatedAt = 500L
        )
        scheduler.reconcile()
        scheduler.releaseFirstSchedule()
        coordinatedRepository.awaitFallbackStatusUpdate()
        assertEquals(ownerAReminderAt, scheduler.activeAlarms[successorId])

        replaceAllWithReusedTaskId(
            taskId = successorId,
            title = "Restored owner B",
            reminderAt = null,
            reminderStatus = ReminderStatus.NONE,
            seriesId = "restored-series-b",
            updatedAt = 600L
        )
        coordinatedRepository.releaseFallbackStatusUpdate()
        completion.await()

        val current = requireNotNull(repository.getTask(successorId))
        assertEquals("Restored owner B", current.title)
        assertEquals(ReminderStatus.NONE, current.reminderStatus)
        assertTrue(successorId !in scheduler.activeAlarms)
    }

    private fun completeTask(
        repository: TaskRepository,
        scheduler: ReminderScheduler
    ) = CompleteTask(
        repository = repository,
        scheduler = scheduler,
        calculateNextOccurrence = CalculateNextOccurrence(ZoneIdProvider { ZoneId.of("UTC") }),
        clock = AppClock { 1_000 }
    )

    private suspend fun replaceAllWithReusedTaskId(
        taskId: Int,
        title: String = "Restored replacement",
        reminderAt: Long? = null,
        reminderStatus: ReminderStatus = ReminderStatus.NONE,
        seriesId: String = "restored-series",
        createdAt: Long = 400L,
        updatedAt: Long = 500L,
        isCompleted: Boolean = false,
        completedAt: Long? = null,
        recurrenceRule: RecurrenceRule = RecurrenceRule.None,
        recurrenceEndAt: Long? = null
    ) {
        PlanningDataSource(database).replaceAll(
            PlanningBackup(
                format = "now-do-this-backup",
                version = 1,
                createdAtEpochMillis = 500L,
                categories = emptyList(),
                tasks = listOf(
                    PlanningTask(
                        id = taskId,
                        title = title,
                        description = "Restored data must remain untouched",
                        priority = TaskPriority.HIGH.name,
                        categoryId = null,
                        isCompleted = isCompleted,
                        completedAt = completedAt,
                        dueAt = 30_000L,
                        reminderAt = reminderAt,
                        reminderStatus = reminderStatus.name,
                        recurrenceRule = recurrenceRule,
                        recurrenceEndAt = recurrenceEndAt,
                        seriesId = seriesId,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        subtasks = emptyList()
                    )
                )
            )
        )
    }

    private fun recurringTask(
        title: String,
        dueAt: Long,
        reminderAt: Long,
        recurrenceRule: RecurrenceRule
    ) = Task(
        title = title,
        description = "Description",
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        reminderAt = reminderAt,
        recurrenceRule = recurrenceRule,
        createdAt = 0,
        updatedAt = 0
    )

    private class CoordinatedCompletionRepository(
        private val delegate: TaskRepository,
        private val expectedArrivals: Int = 1
    ) : TaskRepository by delegate {
        private val arrivals = AtomicInteger()
        private val allArrived = CompletableDeferred<Unit>()
        private val proceed = CompletableDeferred<Unit>()

        override suspend fun completeAtomically(
            taskId: Int,
            completedAt: Long,
            completionDecision: (Task, Long) -> AtomicCompletionDecision
        ): AtomicCompletionResult {
            if (arrivals.incrementAndGet() == expectedArrivals) allArrived.complete(Unit)
            allArrived.await()
            if (expectedArrivals == 1) proceed.await()
            return delegate.completeAtomically(taskId, completedAt, completionDecision)
        }

        suspend fun awaitArrivals() = allArrived.await()

        fun release() = proceed.complete(Unit)
    }

    private class CoordinatedSaveReadRepository(
        private val delegate: TaskRepository
    ) : TaskRepository by delegate {
        private val read = CompletableDeferred<Unit>()
        private val proceed = CompletableDeferred<Unit>()

        override suspend fun getTask(taskId: Int): Task? {
            val task = delegate.getTask(taskId)
            read.complete(Unit)
            proceed.await()
            return task
        }

        suspend fun awaitRead() = read.await()

        fun release() = proceed.complete(Unit)
    }

    private class CoordinatedPostCompletionRepository(
        private val delegate: TaskRepository
    ) : TaskRepository by delegate {
        private val committed = CompletableDeferred<AtomicCompletionResult.Completed>()
        private val proceed = CompletableDeferred<Unit>()

        override suspend fun completeAtomically(
            taskId: Int,
            completedAt: Long,
            completionDecision: (Task, Long) -> AtomicCompletionDecision
        ): AtomicCompletionResult {
            val result = delegate.completeAtomically(taskId, completedAt, completionDecision)
            if (result is AtomicCompletionResult.Completed) committed.complete(result)
            proceed.await()
            return result
        }

        suspend fun awaitCommit(): AtomicCompletionResult.Completed = committed.await()

        fun release() = proceed.complete(Unit)
    }

    private class CoordinatedFallbackStatusRepository(
        private val delegate: TaskRepository
    ) : TaskRepository by delegate {
        private val statusAttempts = AtomicInteger()
        private val fallbackStatusUpdate = CompletableDeferred<Unit>()
        private val proceed = CompletableDeferred<Unit>()

        override suspend fun updateReminderStatusIfCurrent(
            expectedVersion: TaskSnapshotVersion,
            status: ReminderStatus
        ): Boolean {
            if (statusAttempts.incrementAndGet() == 2) {
                fallbackStatusUpdate.complete(Unit)
                proceed.await()
            }
            return delegate.updateReminderStatusIfCurrent(expectedVersion, status)
        }

        suspend fun awaitFallbackStatusUpdate() = fallbackStatusUpdate.await()

        fun releaseFallbackStatusUpdate() = proceed.complete(Unit)
    }

    private class RecordingReminderScheduler : ReminderScheduler {
        val cancelledTaskIds = mutableListOf<Int>()
        val scheduled = mutableListOf<Pair<Int, Pair<Long, ReminderScheduleResult>>>()

        override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
            val result = ReminderScheduleResult.EXACT
            synchronized(scheduled) {
                scheduled += taskId to (triggerAt to result)
            }
            return result
        }

        override suspend fun cancel(taskId: Int) {
            synchronized(cancelledTaskIds) {
                cancelledTaskIds += taskId
            }
        }

        override suspend fun reconcile() = Unit
    }

    private class CoordinatedScheduleReminderScheduler : ReminderScheduler {
        val cancelledTaskIds = mutableListOf<Int>()
        private val scheduleStarted = CompletableDeferred<Int>()
        private val proceed = CompletableDeferred<Unit>()

        override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
            scheduleStarted.complete(taskId)
            proceed.await()
            return ReminderScheduleResult.EXACT
        }

        override suspend fun cancel(taskId: Int) {
            cancelledTaskIds += taskId
        }

        override suspend fun reconcile() = Unit

        suspend fun awaitScheduleStart(): Int = scheduleStarted.await()

        fun releaseSchedule() = proceed.complete(Unit)
    }

    private class SchedulePausingReminderScheduler : ReminderScheduler {
        val activeAlarms = mutableMapOf<Int, Long>()
        private val scheduleStarted = CompletableDeferred<Int>()
        private val proceed = CompletableDeferred<Unit>()
        private var pauseNextSchedule = true

        override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
            if (pauseNextSchedule) {
                pauseNextSchedule = false
                scheduleStarted.complete(taskId)
                proceed.await()
            }
            activeAlarms[taskId] = triggerAt
            return ReminderScheduleResult.EXACT
        }

        override suspend fun cancel(taskId: Int) {
            activeAlarms.remove(taskId)
        }

        override suspend fun reconcile() = Unit

        suspend fun awaitPausedSchedule(): Int = scheduleStarted.await()

        fun releasePausedSchedule() = proceed.complete(Unit)
    }

    private class CancelPausingReminderScheduler(
        initialAlarms: Map<Int, Long>
    ) : ReminderScheduler {
        val activeAlarms = initialAlarms.toMutableMap()
        private val cancelStarted = CompletableDeferred<Int>()
        private val proceed = CompletableDeferred<Unit>()
        private var pauseNextCancel = true

        override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
            activeAlarms[taskId] = triggerAt
            return ReminderScheduleResult.EXACT
        }

        override suspend fun cancel(taskId: Int) {
            if (pauseNextCancel) {
                pauseNextCancel = false
                cancelStarted.complete(taskId)
                proceed.await()
            }
            activeAlarms.remove(taskId)
        }

        override suspend fun reconcile() = Unit

        suspend fun awaitPausedCancel(): Int = cancelStarted.await()

        fun releasePausedCancel() = proceed.complete(Unit)
    }

    private class ReconciledActiveReminderScheduler(
        private val repository: TaskRepository,
        private val now: Long
    ) : ReminderScheduler {
        val activeAlarms = mutableMapOf<Int, Long>()
        private val firstScheduleStarted = CompletableDeferred<Int>()
        private val firstScheduleProceed = CompletableDeferred<Unit>()
        private var coordinateNextSchedule = true

        override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
            activeAlarms[taskId] = triggerAt
            if (coordinateNextSchedule) {
                coordinateNextSchedule = false
                firstScheduleStarted.complete(taskId)
                firstScheduleProceed.await()
            }
            return ReminderScheduleResult.EXACT
        }

        override suspend fun cancel(taskId: Int) {
            activeAlarms.remove(taskId)
        }

        override suspend fun reconcile() {
            repository.futureReminders(now).forEach { task ->
                schedule(task.id, requireNotNull(task.reminderAt))
                repository.updateReminderStatus(task.id, ReminderStatus.SCHEDULED)
            }
        }

        suspend fun awaitFirstSchedule(): Int = firstScheduleStarted.await()

        fun releaseFirstSchedule() = firstScheduleProceed.complete(Unit)
    }

    private companion object {
        const val ONE_DAY_MILLIS = 86_400_000L
        const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS
    }
}

private val DAILY_RULE = RecurrenceRule.Interval(
    IntervalUnit.DAYS,
    1,
    RecurrenceBasis.SCHEDULED_DATE
)

private val WEEKLY_RULE = RecurrenceRule.Interval(
    IntervalUnit.WEEKS,
    1,
    RecurrenceBasis.SCHEDULED_DATE
)
