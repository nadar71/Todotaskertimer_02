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
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.RestoreFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.model.UnsupportedFutureVersion
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.CreateBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.InspectBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.RestoreBackup
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import java.time.DayOfWeek
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
        reminders = RecordingReminderScheduler(database)
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
        assertTrue(exportedBytes.decodeToString().contains("\"version\":2"))
        assertTrue(exportedBytes.decodeToString().contains("\"kind\":\"MONTHLY_ORDINAL\""))

        replaceWithMutation()
        reminders.liveAlarms[MUTATED_TASK_ID] = 9_500L
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
        assertEquals(mapOf(101 to 1_786_896_400_000L), reminders.liveAlarms)

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
                .replace("\"version\":2", "\"version\":3")
                .encodeToByteArray()
        }

        assertEquals(
            PortabilityResult.Failed(UnsupportedFutureVersion(3)),
            inspectBackup(backupReference)
        )
        assertEquals(before, dataSource.snapshot(BACKUP_CREATED_AT))
        assertTrue(reminders.cancelledTaskIds.isEmpty())
        assertEquals(0, reminders.reconcileCalls)
    }

    @Test
    fun replaceAllFailure_rollsBackRowsSequencesAndLeavesAlarmsUntouched() = runTest {
        seedPlanningGraph()
        val beforeRows = dataSource.snapshot(BACKUP_CREATED_AT)
        val beforeSequences = readSequences()
        reminders.liveAlarms[101] = 1_786_896_400_000L
        val beforeAlarms = reminders.liveAlarms.toMap()
        val invalidTask = expectedBackup().tasks.first().copy(
            id = 9_101,
            categoryId = 941,
            subtasks = listOf(
                PlanningSubtask(9_501, 9_101, "First duplicate", false, null, 0),
                PlanningSubtask(9_501, 9_101, "Second duplicate", false, null, 1)
            )
        )
        val invalidBackup = expectedBackup().copy(
            categories = listOf(PlanningCategory(941, "Invalid", null, "BLUE", 0, 1L)),
            tasks = listOf(invalidTask)
        )
        val candidate = BackupCandidate(
            backup = invalidBackup,
            summary = BackupSummary(BACKUP_CREATED_AT, 1, 1, 0, 2)
        )

        assertEquals(PortabilityResult.Failed(RestoreFailed), restoreBackup(candidate))

        assertEquals(beforeRows, dataSource.snapshot(BACKUP_CREATED_AT))
        assertEquals(beforeSequences, readSequences())
        assertEquals(beforeAlarms, reminders.liveAlarms)
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
                    recurrence = "MONTHLY_ORDINAL",
                    recurrenceKind = "MONTHLY_ORDINAL",
                    recurrenceIntervalCount = 3,
                    recurrenceBasis = "COMPLETION_DATE",
                    recurrenceOrdinal = "LAST",
                    recurrenceOrdinalWeekday = "FRIDAY",
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
        version = 2,
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
                recurrenceRule = RecurrenceRule.MonthlyOrdinal(
                    ordinal = MonthlyOrdinalValue.LAST,
                    weekday = DayOfWeek.FRIDAY,
                    everyMonths = 3,
                    basis = RecurrenceBasis.COMPLETION_DATE
                ),
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
                recurrenceRule = RecurrenceRule.None,
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

    private fun readSequences(): Map<String, Long?> {
        val sqlite = database.openHelper.writableDatabase
        return listOf("categories", "tasks", "subtasks").associateWith { table ->
            sqlite.query(
                "SELECT seq FROM sqlite_sequence WHERE name = ?",
                arrayOf(table)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }
    }

    private class RecordingReminderScheduler(
        private val database: AppDatabase
    ) : ReminderScheduler {
        val cancelledTaskIds = mutableListOf<Int>()
        val liveAlarms = mutableMapOf<Int, Long>()
        var reconcileCalls: Int = 0

        override suspend fun schedule(taskId: Int, triggerAt: Long) = ReminderScheduleResult.EXACT

        override suspend fun cancel(taskId: Int) {
            cancelledTaskIds += taskId
            liveAlarms.remove(taskId)
        }

        override suspend fun reconcile() {
            reconcileCalls += 1
            database.taskDao().getAllTaskEntities().forEach { task ->
                if (task.reminderStatus == "SCHEDULED" && task.reminderAt != null) {
                    liveAlarms[task.id] = task.reminderAt
                }
            }
        }
    }

    private companion object {
        const val BACKUP_CREATED_AT = 1_786_640_000_000L
        const val MUTATED_CATEGORY_ID = 90
        const val MUTATED_TASK_ID = 900
    }
}
