package com.indiewalkabout.nowdothis.feature.portability

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataSource
import com.indiewalkabout.nowdothis.feature.portability.data.repository.DocumentGateway
import com.indiewalkabout.nowdothis.feature.portability.data.repository.OfflinePortabilityRepository
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupCodec
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupValidator
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.UnsupportedFutureVersion
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.CreateBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.InspectBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.RestoreBackup
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataPortabilityJourneyTest {
    private lateinit var database: AppDatabase
    private lateinit var dataSource: PlanningDataSource
    private lateinit var documents: MemoryDocumentGateway
    private lateinit var reminders: RecordingReminderScheduler
    private lateinit var createBackup: CreateBackup
    private lateinit var inspectBackup: InspectBackup
    private lateinit var restoreBackup: RestoreBackup

    private val backupReference = DocumentReference("memory://planning-backup")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataSource = PlanningDataSource(database)
        documents = MemoryDocumentGateway()
        reminders = RecordingReminderScheduler()
        val repository = OfflinePortabilityRepository(
            planningDataStore = dataSource,
            documentGateway = documents,
            backupCodec = BackupCodec(),
            backupValidator = BackupValidator(),
            clock = AppClock { BACKUP_CREATED_AT },
            dispatcher = Dispatchers.IO
        )
        createBackup = CreateBackup(repository)
        inspectBackup = InspectBackup(repository)
        restoreBackup = RestoreBackup(repository, reminders)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportMutateRestore_preservesFullPlanningGraphAndReconcilesReminders() = runTest {
        seedPlanningGraph()
        val original = expectedBackup()

        assertEquals(
            PortabilityResult.Exported(expectedSummary()),
            createBackup(backupReference)
        )
        val exportedBytes = documents.requireBytes(backupReference)
        assertTrue(exportedBytes.isNotEmpty())

        replaceWithMutation()
        assertEquals(setOf(MUTATED_TASK_ID), database.taskDao().getAllTaskIds().toSet())

        val inspection = inspectBackup(backupReference)
        assertTrue(inspection is PortabilityResult.Inspected)
        val candidate = (inspection as PortabilityResult.Inspected).candidate
        assertEquals(expectedSummary(), candidate.summary)

        assertEquals(
            PortabilityResult.Restored(expectedSummary()),
            restoreBackup(candidate)
        )
        assertEquals(original, dataSource.snapshot(BACKUP_CREATED_AT))
        assertEquals(listOf(MUTATED_TASK_ID), reminders.cancelledTaskIds)
        assertEquals(1, reminders.reconcileCalls)

        val reExportReference = DocumentReference("memory://restored-backup")
        createBackup(reExportReference)
        assertArrayEquals(exportedBytes, documents.requireBytes(reExportReference))
    }

    @Test
    fun invalidBackup_isRejectedWithoutMutatingPlanningData() = runTest {
        seedPlanningGraph()
        val before = dataSource.snapshot(BACKUP_CREATED_AT)
        createBackup(backupReference)
        documents.transform(backupReference) { bytes ->
            bytes.decodeToString()
                .replace("\"categoryId\":41", "\"categoryId\":999")
                .encodeToByteArray()
        }

        assertEquals(
            PortabilityResult.Failed(InvalidBackup),
            inspectBackup(backupReference)
        )
        assertEquals(before, dataSource.snapshot(BACKUP_CREATED_AT))
        assertTrue(reminders.cancelledTaskIds.isEmpty())
        assertEquals(0, reminders.reconcileCalls)
    }

    @Test
    fun futureBackupVersion_isRejectedWithoutMutatingPlanningData() = runTest {
        seedPlanningGraph()
        val before = dataSource.snapshot(BACKUP_CREATED_AT)
        createBackup(backupReference)
        documents.transform(backupReference) { bytes ->
            bytes.decodeToString()
                .replace("\"version\":1", "\"version\":2")
                .encodeToByteArray()
        }

        assertEquals(
            PortabilityResult.Failed(UnsupportedFutureVersion(2)),
            inspectBackup(backupReference)
        )
        assertEquals(before, dataSource.snapshot(BACKUP_CREATED_AT))
        assertTrue(reminders.cancelledTaskIds.isEmpty())
        assertEquals(0, reminders.reconcileCalls)
    }

    private suspend fun seedPlanningGraph() {
        database.categoryDao().insertAll(
            listOf(
                CategoryEntity(
                    id = 41,
                    customName = "Clients",
                    defaultKey = null,
                    colorToken = "PINK",
                    position = 0,
                    createdAt = 1_000L
                ),
                CategoryEntity(
                    id = 42,
                    customName = null,
                    defaultKey = "PERSONAL",
                    colorToken = "GREEN",
                    position = 1,
                    createdAt = 1_100L
                )
            )
        )
        database.taskDao().insertTasks(
            listOf(
                TaskEntity(
                    id = 101,
                    title = "Prepare launch",
                    description = "Coordinate the release checklist",
                    priority = "HIGH",
                    categoryId = 41,
                    isCompleted = false,
                    completedAt = null,
                    dueAt = 1_786_900_000_000L,
                    reminderAt = 1_786_896_400_000L,
                    reminderStatus = "SCHEDULED",
                    recurrence = "WEEKLY",
                    recurrenceEndAt = 1_789_000_000_000L,
                    seriesId = "series-launch",
                    createdAt = 1_786_000_000_000L,
                    updatedAt = 1_786_100_000_000L
                ),
                TaskEntity(
                    id = 202,
                    title = "Archive invoices",
                    description = "Completed record",
                    priority = "LOW",
                    categoryId = 42,
                    isCompleted = true,
                    completedAt = 1_786_200_000_000L,
                    dueAt = 1_786_190_000_000L,
                    reminderAt = null,
                    reminderStatus = "NONE",
                    recurrence = "NONE",
                    recurrenceEndAt = null,
                    seriesId = null,
                    createdAt = 1_786_000_000_001L,
                    updatedAt = 1_786_200_000_000L
                )
            )
        )
        database.taskDao().insertRestoredSubtasks(
            listOf(
                SubtaskEntity(501, 101, "Confirm copy", true, 1_786_150_000_000L, 0),
                SubtaskEntity(502, 101, "Publish build", false, null, 1)
            )
        )
    }

    private suspend fun replaceWithMutation() {
        database.clearAllTables()
        database.categoryDao().insert(
            CategoryEntity(MUTATED_CATEGORY_ID, "Temporary", null, "BLUE", 0, 9_000L)
        )
        database.taskDao().insertTask(
            TaskEntity(
                id = MUTATED_TASK_ID,
                title = "Created after backup",
                description = "Must disappear",
                priority = "MEDIUM",
                categoryId = MUTATED_CATEGORY_ID,
                createdAt = 9_100L,
                updatedAt = 9_100L
            )
        )
    }

    private fun expectedBackup() = PlanningBackup(
        format = "now-do-this-backup",
        version = 1,
        createdAtEpochMillis = BACKUP_CREATED_AT,
        categories = listOf(
            PlanningCategory(41, "Clients", null, "PINK", 0, 1_000L),
            PlanningCategory(42, null, "PERSONAL", "GREEN", 1, 1_100L)
        ),
        tasks = listOf(
            PlanningTask(
                id = 101,
                title = "Prepare launch",
                description = "Coordinate the release checklist",
                priority = "HIGH",
                categoryId = 41,
                isCompleted = false,
                completedAt = null,
                dueAt = 1_786_900_000_000L,
                reminderAt = 1_786_896_400_000L,
                reminderStatus = "SCHEDULED",
                recurrence = "WEEKLY",
                recurrenceEndAt = 1_789_000_000_000L,
                seriesId = "series-launch",
                createdAt = 1_786_000_000_000L,
                updatedAt = 1_786_100_000_000L,
                subtasks = listOf(
                    PlanningSubtask(501, 101, "Confirm copy", true, 1_786_150_000_000L, 0),
                    PlanningSubtask(502, 101, "Publish build", false, null, 1)
                )
            ),
            PlanningTask(
                id = 202,
                title = "Archive invoices",
                description = "Completed record",
                priority = "LOW",
                categoryId = 42,
                isCompleted = true,
                completedAt = 1_786_200_000_000L,
                dueAt = 1_786_190_000_000L,
                reminderAt = null,
                reminderStatus = "NONE",
                recurrence = "NONE",
                recurrenceEndAt = null,
                seriesId = null,
                createdAt = 1_786_000_000_001L,
                updatedAt = 1_786_200_000_000L,
                subtasks = emptyList()
            )
        )
    )

    private fun expectedSummary() = BackupSummary(
        createdAtEpochMillis = BACKUP_CREATED_AT,
        categoryCount = 2,
        taskCount = 2,
        completedTaskCount = 1,
        subtaskCount = 2
    )

    private class MemoryDocumentGateway : DocumentGateway {
        private val documents = mutableMapOf<DocumentReference, ByteArray>()

        override suspend fun write(reference: DocumentReference, bytes: ByteArray) {
            documents[reference] = bytes.copyOf()
        }

        override suspend fun read(reference: DocumentReference, maxBytes: Long): ByteArray =
            requireBytes(reference).also { require(it.size <= maxBytes) }

        fun requireBytes(reference: DocumentReference): ByteArray =
            requireNotNull(documents[reference]).copyOf()

        fun transform(reference: DocumentReference, transform: (ByteArray) -> ByteArray) {
            documents[reference] = transform(requireBytes(reference))
        }
    }

    private class RecordingReminderScheduler : ReminderScheduler {
        val cancelledTaskIds = mutableListOf<Int>()
        var reconcileCalls: Int = 0

        override suspend fun schedule(taskId: Int, triggerAt: Long) = ReminderScheduleResult.EXACT

        override suspend fun cancel(taskId: Int) {
            cancelledTaskIds += taskId
        }

        override suspend fun reconcile() {
            reconcileCalls += 1
        }
    }

    private companion object {
        const val BACKUP_CREATED_AT = 1_786_640_000_000L
        const val MUTATED_CATEGORY_ID = 90
        const val MUTATED_TASK_ID = 900
    }
}
