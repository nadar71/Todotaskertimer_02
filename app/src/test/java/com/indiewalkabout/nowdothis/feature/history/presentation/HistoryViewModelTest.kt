package com.indiewalkabout.nowdothis.feature.history.presentation

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.history.domain.usecase.ObserveCompletionHistory
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.CompletionHistoryReader
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val rome = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2025, 3, 30)
    private val now = today.atTime(12, 0).atZone(rome).toInstant().toEpochMilli()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun history_usesStartOfTodayAsExclusiveUpperBound() = runTest(dispatcher) {
        val history = FakeCompletionHistoryReader()
        createViewModel(history = history)
        advanceUntilIdle()

        assertEquals(today.atStartOfDay(rome).toInstant().toEpochMilli(), history.before.last())
    }

    @Test
    fun completedTasks_areGroupedByLocalDateInDescendingOrder() = runTest(dispatcher) {
        val recent = completedTask(2, "Recente", today.minusDays(1), 18)
        val morning = completedTask(1, "Mattina", today.minusDays(1), 9)
        val older = completedTask(3, "Vecchia", today.minusDays(3), 12)
        val viewModel = createViewModel(
            history = FakeCompletionHistoryReader(listOf(older, morning, recent))
        )
        advanceUntilIdle()

        assertEquals(listOf(today.minusDays(1), today.minusDays(3)), viewModel.uiState.value.sections.map { it.date })
        assertEquals(listOf("Recente", "Mattina"), viewModel.uiState.value.sections.first().tasks.map(Task::title))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun queryAndCategory_restartHistoryWithExactFilter() = runTest(dispatcher) {
        val history = FakeCompletionHistoryReader()
        val viewModel = createViewModel(history = history)
        advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.UpdateQuery("report"))
        viewModel.onEvent(HistoryEvent.SelectCategory(4))
        advanceUntilIdle()

        assertEquals(TaskFilter(query = "report", categoryId = 4), history.filters.last())
        assertEquals("report", viewModel.uiState.value.query)
        assertEquals(4, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun categories_areObservedAlongsideHistory() = runTest(dispatcher) {
        val category = Category(4, customName = "Clienti", color = CategoryColor.BLUE, position = 0, createdAt = 1)
        val viewModel = createViewModel(categories = FakeCategoryRepository(listOf(category)))
        advanceUntilIdle()

        assertEquals(listOf(category), viewModel.uiState.value.categories)
    }

    @Test
    fun inspectAndDismiss_exposesReadOnlyTaskState() = runTest(dispatcher) {
        val task = completedTask(8, "Archiviata", today.minusDays(2), 10)
        val viewModel = createViewModel(history = FakeCompletionHistoryReader(listOf(task)))
        advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.Inspect(8))
        advanceUntilIdle()
        assertEquals(task, viewModel.uiState.value.inspectedTask)
        viewModel.onEvent(HistoryEvent.DismissInspection)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.inspectedTask)
    }

    @Test
    fun permanentDelete_requiresConfirmationAndClearsInspection() = runTest(dispatcher) {
        val task = completedTask(8, "Archiviata", today.minusDays(2), 10)
        val repository = FakeTaskRepository()
        val viewModel = createViewModel(
            history = FakeCompletionHistoryReader(listOf(task)),
            tasks = repository
        )
        advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.Inspect(8))
        viewModel.onEvent(HistoryEvent.RequestDelete(8))
        advanceUntilIdle()
        assertTrue(repository.deletedCompleted.isEmpty())
        assertEquals(task, viewModel.uiState.value.pendingDelete)
        viewModel.onEvent(HistoryEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(listOf(8), repository.deletedCompleted)
        assertNull(viewModel.uiState.value.pendingDelete)
        assertNull(viewModel.uiState.value.inspectedTask)
    }

    @Test
    fun deleteFailure_keepsConfirmationForRetry() = runTest(dispatcher) {
        val task = completedTask(8, "Archiviata", today.minusDays(2), 10)
        val repository = FakeTaskRepository().apply { failDelete = true }
        val viewModel = createViewModel(
            history = FakeCompletionHistoryReader(listOf(task)),
            tasks = repository
        )
        advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.RequestDelete(8))
        viewModel.onEvent(HistoryEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(8, viewModel.uiState.value.pendingDelete?.id)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun failedHistoryObservation_canRetry() = runTest(dispatcher) {
        val history = FakeCompletionHistoryReader().apply { failObservation = true }
        val viewModel = createViewModel(history = history)
        advanceUntilIdle()
        assertEquals(HistoryError.LOAD_FAILED, viewModel.uiState.value.error)

        history.failObservation = false
        viewModel.onEvent(HistoryEvent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(history.observationCount >= 2)
    }

    @Test
    fun failedCategoryObservation_canRetry() = runTest(dispatcher) {
        val categories = FakeCategoryRepository().apply { failObservation = true }
        val viewModel = createViewModel(categories = categories)
        advanceUntilIdle()
        assertEquals(HistoryError.LOAD_FAILED, viewModel.uiState.value.error)

        categories.failObservation = false
        viewModel.onEvent(HistoryEvent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(categories.observationCount >= 2)
    }

    @Test
    fun repeatedDeleteConfirmation_launchesOneDeletion() = runTest(dispatcher) {
        val task = completedTask(8, "Archiviata", today.minusDays(2), 10)
        val repository = FakeTaskRepository()
        val viewModel = createViewModel(
            history = FakeCompletionHistoryReader(listOf(task)),
            tasks = repository
        )
        advanceUntilIdle()

        viewModel.onEvent(HistoryEvent.RequestDelete(8))
        viewModel.onEvent(HistoryEvent.ConfirmDelete)
        viewModel.onEvent(HistoryEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(listOf(8), repository.deletedCompleted)
    }

    private fun createViewModel(
        history: FakeCompletionHistoryReader = FakeCompletionHistoryReader(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        tasks: FakeTaskRepository = FakeTaskRepository()
    ) = HistoryViewModel(
        observeHistory = ObserveCompletionHistory(
            reader = history,
            clock = AppClock { now },
            zoneIdProvider = ZoneIdProvider { rome }
        ),
        categoryRepository = categories,
        taskRepository = tasks
    )
}

private class FakeCompletionHistoryReader(initial: List<Task> = emptyList()) : CompletionHistoryReader {
    private val tasks = MutableStateFlow(initial)
    val before = mutableListOf<Long>()
    val filters = mutableListOf<TaskFilter>()
    var failObservation = false
    var observationCount = 0

    override fun observeHistory(before: Long, filter: TaskFilter): Flow<List<Task>> {
        observationCount += 1
        this.before += before
        filters += filter
        if (failObservation) error("history failed")
        return tasks
    }
}

private class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val categories = MutableStateFlow(initial)
    var failObservation = false
    var observationCount = 0

    override fun observeAll(): Flow<List<Category>> {
        observationCount += 1
        if (failObservation) error("categories failed")
        return categories
    }
    override suspend fun create(name: String, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun rename(id: Int, name: String) = CategoryMutationResult.Success
    override suspend fun recolor(id: Int, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun reorder(orderedIds: List<Int>) = CategoryMutationResult.Success
    override suspend fun delete(id: Int) = CategoryMutationResult.Success
}

private class FakeTaskRepository : TaskRepository {
    val deletedCompleted = mutableListOf<Int>()
    var failDelete = false

    override suspend fun deleteCompleted(taskId: Int) {
        if (failDelete) error("delete failed")
        deletedCompleted += taskId
    }

    override fun observeTask(taskId: Int): Flow<Task?> = flowOf(null)
    override fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections> = flowOf(TaskSections())
    override suspend fun getTask(taskId: Int): Task? = null
    override suspend fun upsert(task: Task): Int = task.id
    override suspend fun updateIfUnchanged(
        task: Task,
        expectedVersion: TaskSnapshotVersion
    ): Boolean = false
    override suspend fun completeAtomically(
        taskId: Int,
        completedAt: Long,
        nextOccurrence: (Task) -> Task?
    ): AtomicCompletionResult = AtomicCompletionResult.NotFound
    override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot = error("unused")
    override suspend fun deleteAll(): List<Int> = emptyList()
    override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = snapshot.task.id
    override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) = Unit
    override suspend fun updateReminderStatusIfCurrent(
        expectedVersion: TaskSnapshotVersion,
        status: ReminderStatus
    ): Boolean = false
    override suspend fun futureReminders(after: Long): List<Task> = emptyList()
}

private fun completedTask(id: Int, title: String, date: LocalDate, hour: Int): Task {
    val completedAt = date.atTime(hour, 0).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
    return Task(
        id = id,
        title = title,
        description = "Description",
        priority = TaskPriority.MEDIUM,
        isCompleted = true,
        completedAt = completedAt,
        reminderStatus = ReminderStatus.NONE,
        createdAt = 1,
        updatedAt = completedAt
    )
}
