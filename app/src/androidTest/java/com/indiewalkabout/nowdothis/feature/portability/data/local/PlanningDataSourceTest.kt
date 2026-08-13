package com.indiewalkabout.nowdothis.feature.portability.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningDataSourceTest {
    private lateinit var database: AppDatabase
    private lateinit var dataSource: PlanningDataSource

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataSource = PlanningDataSource(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun snapshot_preservesEveryPersistedFieldAndUsesStableOrdering() = runTest {
        seedOriginalGraph()

        val snapshot = dataSource.snapshot(createdAtEpochMillis = 9_999L)

        assertEquals(
            PlanningBackup(
                format = "now-do-this-backup",
                version = 1,
                createdAtEpochMillis = 9_999L,
                categories = listOf(
                    PlanningCategory(10, "Home", null, "GREEN", 0, 100L),
                    PlanningCategory(20, null, "WORK", "BLUE", 1, 200L)
                ),
                tasks = listOf(
                    task(
                        id = 100,
                        title = "Completed",
                        description = "All fields stay intact",
                        priority = "HIGH",
                        categoryId = 20,
                        isCompleted = true,
                        completedAt = 1_001L,
                        dueAt = 900L,
                        reminderAt = 800L,
                        reminderStatus = "SCHEDULED",
                        recurrence = "WEEKLY",
                        recurrenceEndAt = 2_000L,
                        seriesId = "series-1",
                        createdAt = 111L,
                        updatedAt = 222L,
                        subtasks = listOf(
                            subtask(1_002, 100, "First", false, null, 0),
                            subtask(1_001, 100, "Second", true, 1_003L, 1)
                        )
                    ),
                    task(
                        id = 200,
                        title = "Uncategorized",
                        description = "",
                        priority = "LOW",
                        categoryId = null,
                        isCompleted = false,
                        completedAt = null,
                        dueAt = null,
                        reminderAt = null,
                        reminderStatus = "NONE",
                        recurrence = "NONE",
                        recurrenceEndAt = null,
                        seriesId = null,
                        createdAt = 333L,
                        updatedAt = 444L,
                        subtasks = emptyList()
                    )
                )
            ),
            snapshot
        )
    }

    @Test
    fun replaceAll_removesCurrentGraphAndPreservesBackupIdsAndRelations() = runTest {
        seedOriginalGraph()
        val replacement = replacementBackup()

        val replacedTaskIds = dataSource.replaceAll(replacement)
        val snapshot = dataSource.snapshot(createdAtEpochMillis = 7L)

        assertEquals(setOf(100, 200), replacedTaskIds)
        assertEquals(replacement.copy(createdAtEpochMillis = 7L), snapshot)
    }

    @Test
    fun replaceAll_canRestoreTheSameBackupRepeatedly() = runTest {
        val backup = replacementBackup()

        dataSource.replaceAll(backup)
        database.taskDao().insertTask(TaskEntity(id = 777, title = "Temporary", description = "", priority = "LOW"))
        dataSource.replaceAll(backup)

        assertEquals(backup.copy(createdAtEpochMillis = 8L), dataSource.snapshot(8L))
    }

    @Test
    fun replaceAll_rollsBackOriginalGraphWhenABulkInsertFails() = runTest {
        seedOriginalGraph()
        val invalidBackup = replacementBackup().copy(
            tasks = listOf(
                replacementBackup().tasks.single().copy(
                    subtasks = listOf(
                        subtask(900, 300, "First", false, null, 0),
                        subtask(900, 300, "Duplicate", false, null, 1)
                    )
                )
            )
        )

        val failure = runCatching { dataSource.replaceAll(invalidBackup) }
        org.junit.Assert.assertTrue(failure.isFailure)
        org.junit.Assert.assertTrue(failure.exceptionOrNull() is SQLiteConstraintException)

        assertEquals(
            dataSource.snapshot(createdAtEpochMillis = 1L),
            originalBackup(createdAtEpochMillis = 1L)
        )
    }

    private suspend fun seedOriginalGraph() {
        database.categoryDao().insert(CategoryEntity(20, null, "WORK", "BLUE", 1, 200L))
        database.categoryDao().insert(CategoryEntity(10, "Home", null, "GREEN", 0, 100L))
        database.taskDao().insertTask(
            TaskEntity(
                id = 200,
                title = "Uncategorized",
                description = "",
                priority = "LOW",
                createdAt = 333L,
                updatedAt = 444L
            )
        )
        database.taskDao().insertTask(
            TaskEntity(
                id = 100,
                title = "Completed",
                description = "All fields stay intact",
                priority = "HIGH",
                categoryId = 20,
                isCompleted = true,
                completedAt = 1_001L,
                dueAt = 900L,
                reminderAt = 800L,
                reminderStatus = "SCHEDULED",
                recurrence = "WEEKLY",
                recurrenceEndAt = 2_000L,
                seriesId = "series-1",
                createdAt = 111L,
                updatedAt = 222L
            )
        )
        database.taskDao().insertRestoredSubtasks(
            listOf(
                SubtaskEntity(1_001, 100, "Second", true, 1_003L, 1),
                SubtaskEntity(1_002, 100, "First", false, null, 0)
            )
        )
    }

    private fun originalBackup(createdAtEpochMillis: Long) = PlanningBackup(
        format = "now-do-this-backup",
        version = 1,
        createdAtEpochMillis = createdAtEpochMillis,
        categories = listOf(
            PlanningCategory(10, "Home", null, "GREEN", 0, 100L),
            PlanningCategory(20, null, "WORK", "BLUE", 1, 200L)
        ),
        tasks = listOf(
            task(
                id = 100,
                title = "Completed",
                description = "All fields stay intact",
                priority = "HIGH",
                categoryId = 20,
                isCompleted = true,
                completedAt = 1_001L,
                dueAt = 900L,
                reminderAt = 800L,
                reminderStatus = "SCHEDULED",
                recurrence = "WEEKLY",
                recurrenceEndAt = 2_000L,
                seriesId = "series-1",
                createdAt = 111L,
                updatedAt = 222L,
                subtasks = listOf(
                    subtask(1_002, 100, "First", false, null, 0),
                    subtask(1_001, 100, "Second", true, 1_003L, 1)
                )
            ),
            task(
                id = 200,
                title = "Uncategorized",
                description = "",
                priority = "LOW",
                categoryId = null,
                isCompleted = false,
                completedAt = null,
                dueAt = null,
                reminderAt = null,
                reminderStatus = "NONE",
                recurrence = "NONE",
                recurrenceEndAt = null,
                seriesId = null,
                createdAt = 333L,
                updatedAt = 444L,
                subtasks = emptyList()
            )
        )
    )

    private fun replacementBackup() = PlanningBackup(
        format = "now-do-this-backup",
        version = 1,
        createdAtEpochMillis = 5L,
        categories = listOf(PlanningCategory(30, "Restored", null, "RED", 0, 500L)),
        tasks = listOf(
            task(
                id = 300,
                title = "Restored task",
                description = "Restored description",
                priority = "MEDIUM",
                categoryId = 30,
                isCompleted = false,
                completedAt = null,
                dueAt = 3_000L,
                reminderAt = 2_500L,
                reminderStatus = "SCHEDULED",
                recurrence = "MONTHLY",
                recurrenceEndAt = 5_000L,
                seriesId = "restored-series",
                createdAt = 600L,
                updatedAt = 700L,
                subtasks = listOf(subtask(900, 300, "Restored subtask", false, null, 0))
            )
        )
    )

    private fun task(
        id: Int,
        title: String,
        description: String,
        priority: String,
        categoryId: Int?,
        isCompleted: Boolean,
        completedAt: Long?,
        dueAt: Long?,
        reminderAt: Long?,
        reminderStatus: String,
        recurrence: String,
        recurrenceEndAt: Long?,
        seriesId: String?,
        createdAt: Long,
        updatedAt: Long,
        subtasks: List<PlanningSubtask>
    ) = PlanningTask(
        id,
        title,
        description,
        priority,
        categoryId,
        isCompleted,
        completedAt,
        dueAt,
        reminderAt,
        reminderStatus,
        recurrence,
        recurrenceEndAt,
        seriesId,
        createdAt,
        updatedAt,
        subtasks
    )

    private fun subtask(
        id: Int,
        taskId: Int,
        title: String,
        isCompleted: Boolean,
        completedAt: Long?,
        position: Int
    ) = PlanningSubtask(id, taskId, title, isCompleted, completedAt, position)
}
