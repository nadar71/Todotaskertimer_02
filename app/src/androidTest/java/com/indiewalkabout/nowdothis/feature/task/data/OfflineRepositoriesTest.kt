package com.indiewalkabout.nowdothis.feature.task.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.util.Constants.PREFERENCE_KEY
import com.indiewalkabout.nowdothis.feature.category.data.repository.OfflineCategoryRepository
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryError
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.task.data.repository.DataStoreTaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.data.repository.OfflineTaskRepository
import com.indiewalkabout.nowdothis.feature.task.data.repository.dataStore
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineRepositoriesTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var categories: OfflineCategoryRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tasks = OfflineTaskRepository(database, database.taskDao())
        categories = OfflineCategoryRepository(
            database = database,
            categoryDao = database.categoryDao(),
            clock = AppClock { 123L }
        )
        context.dataStore.edit { it.clear() }
    }

    @After
    fun tearDown() = runTest {
        context.dataStore.edit { it.clear() }
        database.close()
    }

    @Test
    fun observeSections_appliesBoundsFiltersAndPrioritySort() = runTest {
        tasks.upsert(task(id = 1, title = "Old report", dueAt = 99, priority = TaskPriority.HIGH))
        tasks.upsert(task(id = 2, title = "Today low", dueAt = 100, priority = TaskPriority.LOW))
        tasks.upsert(task(id = 3, title = "Today high", dueAt = 199, priority = TaskPriority.HIGH))
        tasks.upsert(task(id = 4, title = "Later", dueAt = 200))
        tasks.upsert(task(id = 5, title = "No date", dueAt = null))
        tasks.upsert(
            task(id = 6, title = "Completed report", dueAt = 50)
                .copy(isCompleted = true, completedAt = 150)
        )

        val sections = tasks.observeSections(
            filter = TaskFilter(query = "", sort = TaskSort.HIGH_FIRST),
            bounds = DayBounds(100, 200)
        ).first()

        assertEquals(listOf(1), sections.overdue.map(Task::id))
        assertEquals(listOf(3, 2), sections.today.map(Task::id))
        assertEquals(listOf(4), sections.upcoming.map(Task::id))
        assertEquals(listOf(5), sections.unscheduled.map(Task::id))
        assertEquals(listOf(6), sections.completedToday.map(Task::id))
    }

    @Test
    fun observeTask_emitsAgainAfterTransactionalUpsert() = runTest {
        val emissions = Channel<Task?>(capacity = 2)
        val collector = launch {
            tasks.observeTask(40).take(2).collect(emissions::send)
        }
        assertNull(emissions.receive())

        tasks.upsert(task(id = 40, title = "Created"))

        assertEquals("Created", emissions.receive()?.title)
        collector.join()
    }

    @Test
    fun completeAtomically_completesPendingChildrenAndInsertsNextOccurrence() = runTest {
        tasks.upsert(
            task(
                id = 10,
                title = "Recurring",
                dueAt = 1_000,
                subtasks = listOf(
                    Subtask(id = 20, taskId = 10, title = "Pending", position = 1),
                    Subtask(
                        id = 21,
                        taskId = 10,
                        title = "Already done",
                        isCompleted = true,
                        completedAt = 700,
                        position = 0
                    )
                )
            )
        )
        val next = task(id = 0, title = "Recurring", dueAt = 2_000)

        val completed = tasks.completeAtomically(10, completedAt = 1_500, next = next)

        assertEquals(1_500L, completed?.completedAt)
        assertTrue(completed!!.subtasks.all(Subtask::isCompleted))
        assertEquals(700L, completed.subtasks.first().completedAt)
        assertEquals(1_500L, completed.subtasks.last().completedAt)
        assertEquals("Recurring", tasks.observeDay(2_000, 2_001).first().single().title)
    }

    @Test
    fun completeAtomically_rollsBackWhenNextOccurrenceCannotBeInserted() = runTest {
        tasks.upsert(
            task(
                id = 11,
                title = "Keep pending",
                subtasks = listOf(Subtask(id = 22, taskId = 11, title = "Child", position = 0))
            )
        )

        var failed = false
        try {
            tasks.completeAtomically(
                taskId = 11,
                completedAt = 1_500,
                next = task(id = 0, title = "Invalid next").copy(categoryId = 999)
            )
        } catch (_: Exception) {
            failed = true
        }

        assertTrue(failed)
        val unchanged = tasks.getTask(11)!!
        assertFalse(unchanged.isCompleted)
        assertNull(unchanged.completedAt)
        assertFalse(unchanged.subtasks.single().isCompleted)
    }

    @Test
    fun deleteWithSnapshotAndRestore_preserveTaskIdentityAndOrderedSubtasks() = runTest {
        val original = task(
            id = 42,
            title = "Restore all",
            dueAt = 800,
            subtasks = listOf(
                Subtask(id = 91, taskId = 42, title = "Second", position = 1),
                Subtask(id = 90, taskId = 42, title = "First", position = 0)
            )
        ).copy(createdAt = 0, updatedAt = 0)
        tasks.upsert(original)

        val snapshot = tasks.deleteWithSnapshot(42)
        assertNull(tasks.getTask(42))
        val restoredId = tasks.restore(snapshot)

        assertEquals(42, restoredId)
        assertEquals(
            original.copy(subtasks = original.subtasks.sortedBy(Subtask::position)),
            tasks.getTask(42)
        )
    }

    @Test
    fun scheduleHistoryAndFutureReminderReads_useExactFieldsAndBounds() = runTest {
        tasks.upsert(task(id = 1, title = "Before", dueAt = 99))
        tasks.upsert(task(id = 2, title = "At start", dueAt = 100))
        tasks.upsert(task(id = 3, title = "At end", dueAt = 200))
        tasks.upsert(
            task(id = 4, title = "History", dueAt = 80)
                .copy(isCompleted = true, completedAt = 90)
        )
        tasks.upsert(
            task(id = 5, title = "Remind", dueAt = 500)
                .copy(reminderAt = 400, reminderStatus = ReminderStatus.REQUESTED)
        )

        assertEquals(listOf(2), tasks.observeDay(100, 200).first().map(Task::id))
        assertEquals(listOf(2), tasks.observeMonth(100, 200).first().map(Task::id))
        assertEquals(listOf(4), tasks.observeHistory(100, TaskFilter()).first().map(Task::id))
        assertEquals(listOf(5), tasks.futureReminders(after = 399).map(Task::id))
    }

    @Test
    fun categoryMutations_trimNamesRejectCaseInsensitiveDuplicatesAndReorder() = runTest {
        assertEquals(CategoryMutationResult.Success, categories.create("  Clients  ", CategoryColor.BLUE))
        assertEquals(
            CategoryMutationResult.Failure(CategoryError.DuplicateName),
            categories.create("clients", CategoryColor.GREEN)
        )
        assertEquals(
            CategoryMutationResult.Failure(CategoryError.BlankName),
            categories.create("   ", CategoryColor.PINK)
        )
        val created = categories.observeAll().first().single()
        assertEquals("Clients", created.customName)
        assertEquals(123L, created.createdAt)

        assertEquals(CategoryMutationResult.Success, categories.create("Home", CategoryColor.GREEN))
        val before = categories.observeAll().first()
        assertEquals(
            CategoryMutationResult.Success,
            categories.reorder(before.map { it.id }.reversed())
        )
        assertEquals(before.map { it.id }.reversed(), categories.observeAll().first().map { it.id })
    }

    @Test
    fun taskPreferences_mapLegacyValuesAndPersistUsingLegacyCompatibleTokens() = runTest {
        val repository = DataStoreTaskPreferencesRepository(context)
        val key = stringPreferencesKey(PREFERENCE_KEY)

        context.dataStore.edit { it[key] = "LOW" }
        assertEquals(TaskSort.LOW_FIRST, repository.taskSort.first())
        context.dataStore.edit { it[key] = "HIGH" }
        assertEquals(TaskSort.HIGH_FIRST, repository.taskSort.first())
        context.dataStore.edit { it[key] = "unexpected" }
        assertEquals(TaskSort.DEFAULT, repository.taskSort.first())

        repository.setTaskSort(TaskSort.LOW_FIRST)
        assertEquals("LOW", context.dataStore.data.first()[key])
        repository.setTaskSort(TaskSort.DEFAULT)
        assertEquals("NONE", context.dataStore.data.first()[key])
    }

    private fun task(
        id: Int,
        title: String,
        dueAt: Long? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        subtasks: List<Subtask> = emptyList()
    ) = Task(
        id = id,
        title = title,
        description = "Description $id",
        priority = priority,
        dueAt = dueAt,
        reminderStatus = ReminderStatus.NONE,
        recurrence = RecurrenceType.NONE,
        createdAt = id.toLong(),
        updatedAt = id.toLong(),
        subtasks = subtasks
    )
}
