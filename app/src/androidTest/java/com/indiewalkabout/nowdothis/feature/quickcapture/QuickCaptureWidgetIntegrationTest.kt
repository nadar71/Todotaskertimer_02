package com.indiewalkabout.nowdothis.feature.quickcapture

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataSource
import com.indiewalkabout.nowdothis.feature.portability.data.repository.DocumentGateway
import com.indiewalkabout.nowdothis.feature.portability.data.repository.OfflinePortabilityRepository
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupCodec
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupValidator
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.RestoreBackup
import com.indiewalkabout.nowdothis.feature.quickcapture.data.TaskSectionsQuickCaptureSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureSnapshot
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetCoordinator
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetReceiver
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetState
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.observeQuickCaptureWidgetState
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.repository.OfflineTaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ObserveTaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ValidateTask
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickCaptureWidgetIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val now = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val dueAt = now + 60_000
    private lateinit var applicationDatabase: AppDatabase

    @Before
    fun clearApplicationDatabase() {
        applicationDatabase = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugDatabaseEntryPoint::class.java
        ).database()
        runBlocking { withContext(Dispatchers.IO) { applicationDatabase.clearAllTables() } }
    }

    @After
    fun resetApplicationDatabase() {
        runBlocking { withContext(Dispatchers.IO) { applicationDatabase.clearAllTables() } }
    }

    @Test
    fun coordinatorReadsFreshRoomStateAfterSaveDeleteCompletionRecurrenceAndReplaceAll() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = OfflineTaskRepository(database, database.taskDao())
        val clock = AppClock { now }
        val scheduler = NoOpReminderScheduler()
        val source = TaskSectionsQuickCaptureSource(
            ObserveTaskSections(repository, FixedTaskPreferences(), clock, ZoneIdProvider { zone })
        )
        val coordinatorLoad = LoadQuickCaptureTasks(source)
        val providerReads = AtomicInteger()
        val providerLoad = LoadQuickCaptureTasks(
            QuickCaptureTaskSource {
                providerReads.incrementAndGet()
                source.observe()
            }
        )
        val renderedSnapshots = Channel<QuickCaptureSnapshot>(Channel.UNLIMITED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = QuickCaptureWidgetCoordinator(
            coordinatorLoad,
            QuickCaptureWidgetUpdater { renderedSnapshots.send(providerLoad(8)) },
            scope
        )
        val saveTask = SaveTask(repository, scheduler, ValidateTask(), clock)
        val deleteTask = DeleteTask(repository, scheduler)
        val completeTask = CompleteTask(
            repository,
            scheduler,
            CalculateNextOccurrence(ZoneIdProvider { zone }),
            clock
        )

        try {
            coordinator.onApplicationStart()
            awaitSnapshot(renderedSnapshots) { it.tasks.isEmpty() }

            val savedId = saveTask(task(title = "Saved through use case"))
                .let { result -> (result as com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTaskResult.Saved).taskId }
            awaitSnapshot(renderedSnapshots) { it.tasks.map { task -> task.title } == listOf("Saved through use case") }

            deleteTask(savedId)
            awaitSnapshot(renderedSnapshots) { it.tasks.isEmpty() }

            val recurringId = saveTask(
                task(title = "Recurring through use case", recurrence = RecurrenceType.DAILY)
            ).let { result ->
                (result as com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTaskResult.Saved).taskId
            }
            awaitSnapshot(renderedSnapshots) { it.tasks.singleOrNull()?.id == recurringId }

            completeTask(recurringId)
            val recurrenceSnapshot = awaitSnapshot(renderedSnapshots) {
                it.tasks.singleOrNull()?.let { task ->
                    task.title == "Recurring through use case" && task.id != recurringId
                } == true
            }
            assertEquals(dueAt + ONE_DAY_MILLIS, recurrenceSnapshot.tasks.single().dueAt)

            val restoredTitle = "Restored atomically"
            val restoreResult = RestoreBackup(
                repository = OfflinePortabilityRepository(
                    planningDataStore = PlanningDataSource(database),
                    documentGateway = UnusedDocumentGateway,
                    backupCodec = BackupCodec(),
                    backupValidator = BackupValidator(),
                    clock = clock,
                    dispatcher = Dispatchers.IO
                ),
                reminderScheduler = scheduler
            )(backupCandidate(restoredTitle))
            assertFalse(restoreResult is com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult.Failed)
            awaitSnapshot(renderedSnapshots) { it.tasks.singleOrNull()?.title == restoredTitle }

            assertTrue(providerReads.get() >= 6)
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun productionWidgetStateFlowReadsRoomAgainAfterMutationWithoutAnUpdater() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = OfflineTaskRepository(database, database.taskDao())
        val clock = AppClock { now }
        val scheduler = NoOpReminderScheduler()
        val productionSource = TaskSectionsQuickCaptureSource(
            ObserveTaskSections(repository, FixedTaskPreferences(), clock, ZoneIdProvider { zone })
        )
        val productionReads = AtomicInteger()
        val loadTasks = LoadQuickCaptureTasks(
            QuickCaptureTaskSource {
                productionSource.observe().onEach { productionReads.incrementAndGet() }
            }
        )
        val states = Channel<QuickCaptureWidgetState>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            observeQuickCaptureWidgetState(loadTasks, capacity = 8).collect(states::send)
        }
        val saveTask = SaveTask(repository, scheduler, ValidateTask(), clock)

        try {
            awaitWidgetState(states) { it == QuickCaptureWidgetState.Empty }
            saveTask(task(title = "Fresh production read"))
            val content = awaitWidgetState(states) {
                it is QuickCaptureWidgetState.Content &&
                    it.snapshot.tasks.singleOrNull()?.title == "Fresh production read"
            }

            assertEquals("Fresh production read", (content as QuickCaptureWidgetState.Content).snapshot.tasks.single().title)
            assertTrue(productionReads.get() >= 2)
        } finally {
            collection.cancelAndJoin()
            database.close()
        }
    }

    @Test
    fun actualGlanceProvideContentRendersFreshRoomStateAfterMutation() {
        withWidgetHost { hostView ->
            hostView.awaitText(context.getString(R.string.quick_capture_widget_empty))

            runBlocking(Dispatchers.IO) {
                applicationDatabase.taskDao().insertTask(taskEntity("Startup-observed task"))
            }
            hostView.awaitText("Startup-observed task")
        }
    }

    @Test
    fun receiverUpdateAndCompletionActionWorkWithoutAnAppActivity() {
        val taskId = runBlocking(Dispatchers.IO) {
            applicationDatabase.taskDao().insertTask(taskEntity("Receiver task")).toInt()
        }

        withWidgetHost { hostView ->
            hostView.awaitText("Receiver task")
            assertTrue(resumedActivities().isEmpty())

            val updateCount = hostView.updateCount
            context.sendBroadcast(
                Intent(context, QuickCaptureWidgetReceiver::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(hostView.appWidgetId))
            )
            hostView.awaitUpdateAfter(updateCount)
            hostView.awaitText("Receiver task")

            val completionDescription = context.getString(
                R.string.quick_capture_widget_complete_description,
                "Receiver task"
            )
            assertTrue(hostView.clickWithContentDescription(completionDescription))
            waitUntil {
                runBlocking(Dispatchers.IO) {
                    applicationDatabase.taskDao().getTask(taskId)?.task?.isCompleted == true
                }
            }
            hostView.awaitText(context.getString(R.string.quick_capture_widget_empty))
            assertTrue(resumedActivities().isEmpty())
        }
    }

    private suspend fun awaitSnapshot(
        snapshots: Channel<QuickCaptureSnapshot>,
        predicate: (QuickCaptureSnapshot) -> Boolean
    ): QuickCaptureSnapshot = withTimeout(WAIT_TIMEOUT_MILLIS) {
        while (true) {
            val snapshot = snapshots.receive()
            if (predicate(snapshot)) return@withTimeout snapshot
        }
        error("unreachable")
    }

    private suspend fun awaitWidgetState(
        states: Channel<QuickCaptureWidgetState>,
        predicate: (QuickCaptureWidgetState) -> Boolean
    ): QuickCaptureWidgetState = withTimeout(WAIT_TIMEOUT_MILLIS) {
        while (true) {
            val state = states.receive()
            if (predicate(state)) return@withTimeout state
        }
        error("unreachable")
    }

    private fun task(title: String, recurrence: RecurrenceType = RecurrenceType.NONE) = Task(
        title = title,
        description = "Description",
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        recurrence = recurrence,
        createdAt = 0,
        updatedAt = 0
    )

    private fun taskEntity(title: String) = TaskEntity(
        title = title,
        description = "Description",
        priority = TaskPriority.MEDIUM.name,
        dueAt = dueAt,
        createdAt = now,
        updatedAt = now
    )

    private fun backupCandidate(title: String): BackupCandidate {
        val backup = PlanningBackup(
            format = "now-do-this-backup",
            version = 1,
            createdAtEpochMillis = now,
            categories = emptyList(),
            tasks = listOf(
                PlanningTask(
                    id = 99,
                    title = title,
                    description = "Description",
                    priority = TaskPriority.MEDIUM.name,
                    categoryId = null,
                    isCompleted = false,
                    completedAt = null,
                    dueAt = dueAt,
                    reminderAt = null,
                    reminderStatus = ReminderStatus.NONE.name,
                    recurrence = RecurrenceType.NONE.name,
                    recurrenceEndAt = null,
                    seriesId = null,
                    createdAt = now,
                    updatedAt = now,
                    subtasks = emptyList()
                )
            )
        )
        return BackupCandidate(
            backup,
            BackupSummary(now, categoryCount = 0, taskCount = 1, completedTaskCount = 0, subtaskCount = 0)
        )
    }

    private fun withWidgetHost(block: (RecordingAppWidgetHostView) -> Unit) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, QuickCaptureWidgetReceiver::class.java)
        val host = RecordingAppWidgetHost(context)
        var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            instrumentation.runOnMainSync {
                host.startListening()
                appWidgetId = host.allocateAppWidgetId()
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(appWidgetId, provider))
            val providerInfo = requireNotNull(manager.getAppWidgetInfo(appWidgetId))
            val hostView = instrumentation.runOnMainSyncWithResult {
                host.createView(context, appWidgetId, providerInfo) as RecordingAppWidgetHostView
            }
            block(hostView)
        } finally {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(appWidgetId)
            host.stopListening()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun resumedActivities(): Collection<android.app.Activity> {
        var activities: Collection<android.app.Activity> = emptyList()
        instrumentation.runOnMainSync {
            activities = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
        }
        return activities
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Condition was not met within $WAIT_TIMEOUT_MILLIS ms")
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncWithResult(block: () -> T): T {
        var result: Any? = null
        runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private inner class RecordingAppWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo
        ): AppWidgetHostView = RecordingAppWidgetHostView(context)
    }

    private inner class RecordingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
        @Volatile
        var updateCount: Int = 0
            private set

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) updateCount++
        }

        fun awaitText(expected: String) {
            val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
            var texts = emptyList<String>()
            while (SystemClock.uptimeMillis() < deadline) {
                texts = instrumentation.runOnMainSyncWithResult {
                    descendants().filterIsInstance<TextView>().map { it.text.toString() }
                }
                if (expected in texts) return
                SystemClock.sleep(POLL_INTERVAL_MILLIS)
            }
            throw AssertionError(
                "Text '$expected' was not rendered after $updateCount updates; texts=$texts"
            )
        }

        fun awaitUpdateAfter(previousCount: Int) = waitUntil { updateCount > previousCount }

        fun clickWithContentDescription(description: String): Boolean =
            instrumentation.runOnMainSyncWithResult {
                val semanticView = descendants()
                    .firstOrNull { it.contentDescription?.toString() == description }
                    ?: return@runOnMainSyncWithResult false
                generateSequence(semanticView) { it.parent as? View }
                    .firstOrNull(View::isClickable)
                    ?.performClick() == true
            }

        private fun descendants(): List<View> = buildList {
            fun addRecursively(view: View) {
                add(view)
                if (view is ViewGroup) {
                    repeat(view.childCount) { index -> addRecursively(view.getChildAt(index)) }
                }
            }
            addRecursively(this@RecordingAppWidgetHostView)
        }
    }

    private class FixedTaskPreferences : TaskPreferencesRepository {
        override val taskSort: Flow<TaskSort> = flowOf(TaskSort.DEFAULT)
        override suspend fun setTaskSort(sort: TaskSort) = Unit
    }

    private class NoOpReminderScheduler : ReminderScheduler {
        override suspend fun schedule(taskId: Int, triggerAt: Long) = ReminderScheduleResult.EXACT
        override suspend fun cancel(taskId: Int) = Unit
        override suspend fun reconcile() = Unit
    }

    private data object UnusedDocumentGateway : DocumentGateway {
        override suspend fun write(reference: DocumentReference, bytes: ByteArray) = error("unused")
        override suspend fun read(reference: DocumentReference, maxBytes: Long): ByteArray = error("unused")
    }

    private companion object {
        const val HOST_ID = 0x5144
        const val WAIT_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 100L
        const val ONE_DAY_MILLIS = 86_400_000L
    }
}
