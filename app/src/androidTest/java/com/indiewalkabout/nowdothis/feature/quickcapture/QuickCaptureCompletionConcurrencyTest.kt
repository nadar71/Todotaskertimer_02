package com.indiewalkabout.nowdothis.feature.quickcapture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureResult
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.task.data.repository.OfflineTaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTaskResult
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
                recurrence = RecurrenceType.DAILY,
                createdAt = 0,
                updatedAt = 0
            )
        )
        val barrierRepository = CoordinatedTaskRepository(repository)
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

    private fun completeTask(
        repository: TaskRepository,
        scheduler: ReminderScheduler
    ) = CompleteTask(
        repository = repository,
        scheduler = scheduler,
        calculateNextOccurrence = CalculateNextOccurrence(ZoneIdProvider { ZoneId.of("UTC") }),
        clock = AppClock { 1_000 }
    )

    private class CoordinatedTaskRepository(
        private val delegate: TaskRepository
    ) : TaskRepository by delegate {
        private val arrivals = AtomicInteger()
        private val bothCallersRead = CompletableDeferred<Unit>()

        override suspend fun getTask(taskId: Int): Task? {
            val task = delegate.getTask(taskId)
            if (arrivals.incrementAndGet() == 2) bothCallersRead.complete(Unit)
            bothCallersRead.await()
            return task
        }
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

    private companion object {
        const val ONE_DAY_MILLIS = 86_400_000L
    }
}
