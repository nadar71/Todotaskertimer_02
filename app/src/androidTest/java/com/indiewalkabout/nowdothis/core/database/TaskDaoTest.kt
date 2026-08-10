package com.indiewalkabout.nowdothis.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.task.data.repository.ToDoRepository
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskDao
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.domain.model.Priority
import com.indiewalkabout.nowdothis.feature.task.domain.model.ToDoTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.taskDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun pendingTimeSections_applyExactBoundsWithoutOverlap() = runTest {
        insertTask(id = 1, dueAt = 99)
        insertTask(id = 2, dueAt = 100)
        insertTask(id = 3, dueAt = 199)
        insertTask(id = 4, dueAt = 200)
        insertTask(id = 5, dueAt = null)
        insertTask(id = 6, dueAt = 50, isCompleted = true, completedAt = 60)

        assertEquals(listOf(1), dao.observeOverdue(100, "", null).first().ids())
        assertEquals(listOf(2, 3), dao.observeDueBetween(100, 200, "", null).first().ids())
        assertEquals(listOf(4), dao.observeUpcoming(200, "", null).first().ids())
        assertEquals(listOf(5), dao.observeUnscheduled("", null).first().ids())
    }

    @Test
    fun completedAndCalendarQueries_applyExactBounds() = runTest {
        insertTask(id = 1, dueAt = 100, isCompleted = true, completedAt = 299)
        insertTask(id = 2, dueAt = 199, isCompleted = true, completedAt = 300)
        insertTask(id = 3, dueAt = 200, isCompleted = true, completedAt = 399)
        insertTask(id = 4, dueAt = 250, isCompleted = true, completedAt = 400)
        insertTask(id = 5, dueAt = 150, isCompleted = false)

        assertEquals(
            listOf(2, 3),
            dao.observeCompletedBetween(300, 400, "", null).first().ids()
        )
        assertEquals(listOf(1, 2, 5), dao.observeMonth(100, 200).first().ids())
        assertEquals(listOf(1), dao.observeHistory(300, "", null).first().ids())
    }

    @Test
    fun sectionQueries_filterBySearchAndCategory() = runTest {
        database.categoryDao().insert(
            CategoryEntity(id = 10, customName = "Clients", colorToken = "BLUE")
        )
        database.categoryDao().insert(
            CategoryEntity(id = 11, customName = "Home", colorToken = "GREEN")
        )
        insertTask(id = 1, title = "Write report", description = "Quarterly", categoryId = 10)
        insertTask(id = 2, title = "Buy paint", description = "Write room", categoryId = 11)
        insertTask(id = 3, title = "WRITE tests", description = "Room", categoryId = null)

        assertEquals(
            listOf(1),
            dao.observeUnscheduled(query = "write", categoryId = 10).first().ids()
        )
        assertEquals(
            listOf(1, 2, 3),
            dao.observeUnscheduled(query = "write", categoryId = null).first().ids()
        )
        assertTrue(dao.observeUnscheduled(query = "missing", categoryId = null).first().isEmpty())
    }

    @Test
    fun observeTask_loadsTheCompleteSubtaskRelation() = runTest {
        insertTask(id = 1)
        dao.replaceSubtasks(
            taskId = 1,
            subtasks = listOf(
                subtask(id = 1, taskId = 1, title = "Second", position = 1),
                subtask(id = 2, taskId = 1, title = "First", position = 0)
            )
        )

        val relation = dao.observeTask(1).first()

        assertEquals(
            listOf("First", "Second"),
            relation?.subtasks?.map { it.title }?.sorted()
        )
    }

    @Test
    fun replaceSubtasks_replacesTheWholeChildSet() = runTest {
        insertTask(id = 1)
        dao.replaceSubtasks(1, listOf(subtask(id = 1, taskId = 1, title = "Old")))

        dao.replaceSubtasks(
            1,
            listOf(
                subtask(id = 2, taskId = 999, title = "New", position = 0),
                subtask(id = 3, taskId = 999, title = "Next", position = 1)
            )
        )

        val relation = dao.observeTask(1).first()
        assertEquals(listOf("New", "Next"), relation?.subtasks?.map { it.title })
        assertEquals(listOf(1, 1), relation?.subtasks?.map { it.taskId })
    }

    @Test
    fun deletingTask_cascadesToSubtasks() = runTest {
        insertTask(id = 1)
        dao.replaceSubtasks(1, listOf(subtask(id = 1, taskId = 1)))

        dao.deleteTaskById(1)

        assertEquals(0, scalarInt("SELECT COUNT(*) FROM subtasks"))
    }

    @Test
    fun deletingCategory_setsTaskCategoryToNull() = runTest {
        database.categoryDao().insert(
            CategoryEntity(id = 10, customName = "Clients", colorToken = "BLUE")
        )
        insertTask(id = 1, categoryId = 10)

        database.categoryDao().deleteById(10)

        assertNull(dao.observeTask(1).first()?.task?.categoryId)
    }

    @Test
    fun deletingAllTasks_cascadesToEverySubtask() = runTest {
        insertTask(id = 1)
        insertTask(id = 2)
        dao.replaceSubtasks(1, listOf(subtask(id = 1, taskId = 1)))
        dao.replaceSubtasks(2, listOf(subtask(id = 2, taskId = 2)))

        dao.deleteAllTasks()

        assertEquals(0, scalarInt("SELECT COUNT(*) FROM tasks"))
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM subtasks"))
    }

    @Test
    fun freshDatabase_seedsStableDefaultCategories() = runTest {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DEFAULT_CATEGORIES_CALLBACK)
            .allowMainThreadQueries()
            .build()
        dao = database.taskDao()

        val categories = database.categoryDao().observeAll().first()

        assertEquals(listOf(1, 2, 3), categories.map { it.id })
        assertEquals(listOf("WORK", "PERSONAL", "WISHLIST"), categories.map { it.defaultKey })
    }

    @Test
    fun compatibilityUpdate_changesLegacyFieldsWithoutClearingV2Fields() = runTest {
        database.categoryDao().insert(
            CategoryEntity(id = 10, customName = "Clients", colorToken = "BLUE")
        )
        dao.insertTask(
            TaskEntity(
                id = 1,
                title = "Before",
                description = "Old",
                priority = "LOW",
                categoryId = 10,
                dueAt = 500,
                reminderAt = 400,
                recurrence = "WEEKLY",
                createdAt = 100,
                updatedAt = 200
            )
        )
        val repository = ToDoRepository(dao)

        repository.updateTask(ToDoTask(1, "After", "New", Priority.HIGH))

        val updated = dao.observeTask(1).first()!!.task
        assertEquals("After", updated.title)
        assertEquals("New", updated.description)
        assertEquals("HIGH", updated.priority)
        assertEquals(10, updated.categoryId)
        assertEquals(500L, updated.dueAt)
        assertEquals(400L, updated.reminderAt)
        assertEquals("WEEKLY", updated.recurrence)
        assertEquals(100L, updated.createdAt)
        assertEquals(200L, updated.updatedAt)
    }

    @Test
    fun compatibilityDeleteThenRestore_preservesCompleteTaskAndOrderedSubtasks() = runTest {
        database.categoryDao().insert(
            CategoryEntity(id = 10, customName = "Clients", colorToken = "BLUE")
        )
        val expectedTask = TaskEntity(
            id = 42,
            title = "Complete",
            description = "Preserve every field",
            priority = "HIGH",
            categoryId = 10,
            isCompleted = true,
            completedAt = 900,
            dueAt = 800,
            reminderAt = 700,
            reminderStatus = "SCHEDULED",
            recurrence = "MONTHLY",
            recurrenceEndAt = 1_200,
            seriesId = "series-42",
            createdAt = 100,
            updatedAt = 950
        )
        val expectedSubtasks = listOf(
            SubtaskEntity(
                id = 91,
                taskId = 42,
                title = "First",
                isCompleted = true,
                completedAt = 901,
                position = 0
            ),
            SubtaskEntity(
                id = 90,
                taskId = 42,
                title = "Second",
                isCompleted = false,
                completedAt = null,
                position = 1
            )
        )
        dao.insertTask(expectedTask)
        dao.replaceSubtasks(42, expectedSubtasks.reversed())
        val repository = ToDoRepository(dao)

        repository.deleteTask(ToDoTask(42, "Complete", "Preserve every field", Priority.HIGH))
        assertNull(dao.observeTask(42).first())
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM subtasks WHERE task_id = 42"))

        repository.restoreDeletedTask()

        val restored = dao.observeTask(42).first()!!
        assertEquals(expectedTask, restored.task)
        assertEquals(expectedSubtasks, restored.subtasks.sortedBy { it.position })
    }

    private suspend fun insertTask(
        id: Int,
        title: String = "Task $id",
        description: String = "Description $id",
        categoryId: Int? = null,
        dueAt: Long? = null,
        isCompleted: Boolean = false,
        completedAt: Long? = null
    ) {
        dao.insertTask(
            TaskEntity(
                id = id,
                title = title,
                description = description,
                priority = "MEDIUM",
                categoryId = categoryId,
                isCompleted = isCompleted,
                completedAt = completedAt,
                dueAt = dueAt,
                createdAt = id.toLong(),
                updatedAt = id.toLong()
            )
        )
    }

    private fun subtask(
        id: Int,
        taskId: Int,
        title: String = "Subtask $id",
        position: Int = 0
    ) = SubtaskEntity(
        id = id,
        taskId = taskId,
        title = title,
        position = position
    )

    private fun List<com.indiewalkabout.nowdothis.feature.task.data.local.TaskWithSubtasks>.ids() =
        map { it.task.id }.sorted()

    private fun scalarInt(query: String): Int = database.openHelper.writableDatabase
        .query(query)
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
