package com.indiewalkabout.nowdothis.feature.task.presentation.list

import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.DayBounds
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteAllTasks
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ObserveTaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.RestoreDeletedTask
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class TaskListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialObservation_exposesSectionsCategoriesAndSavedSort() = runTest(dispatcher) {
        val task = task(id = 4, title = "Report")
        val repository = FakeTaskRepository(TaskSections(today = listOf(task)))
        val categories = FakeCategoryRepository(
            listOf(Category(2, customName = "Client", color = CategoryColor.BLUE, position = 0, createdAt = 1))
        )
        val preferences = FakeTaskPreferencesRepository(TaskSort.HIGH_FIRST)

        val viewModel = createViewModel(repository, categories, preferences)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(task), viewModel.uiState.value.sections.today)
        assertEquals("Client", viewModel.uiState.value.categories.single().customName)
        assertEquals(TaskSort.HIGH_FIRST, viewModel.uiState.value.sort)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun categoryAndQuerySelection_restartSectionsWithExactFilter() = runTest(dispatcher) {
        val repository = FakeTaskRepository()
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(TaskListEvent.SelectCategory(3))
        viewModel.onEvent(TaskListEvent.UpdateQuery("report"))
        advanceUntilIdle()

        assertEquals(TaskFilter(query = "report", categoryId = 3), repository.observedFilters.last())
        assertEquals(3, viewModel.uiState.value.selectedCategoryId)
        assertEquals("report", viewModel.uiState.value.query)
    }

    @Test
    fun sortSelection_persistsThroughPreferences() = runTest(dispatcher) {
        val preferences = FakeTaskPreferencesRepository()
        val viewModel = createViewModel(preferences = preferences)
        advanceUntilIdle()

        viewModel.onEvent(TaskListEvent.SelectSort(TaskSort.LOW_FIRST))
        advanceUntilIdle()

        assertEquals(listOf(TaskSort.LOW_FIRST), preferences.savedSorts)
        assertEquals(TaskSort.LOW_FIRST, viewModel.uiState.value.sort)
    }

    @Test
    fun completeMissingTask_emitsFailureWithoutClearingSections() = runTest(dispatcher) {
        val visible = task(id = 8)
        val repository = FakeTaskRepository(TaskSections(unscheduled = listOf(visible)))
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()
        val effect = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }

        viewModel.onEvent(TaskListEvent.CompleteTask(99))
        advanceUntilIdle()

        assertEquals(TaskListEffect.ShowMessage(R.string.task_list_complete_failed), effect.await())
        assertEquals(listOf(visible), viewModel.uiState.value.sections.unscheduled)
    }

    @Test
    fun deleteThenUndo_restoresLatestSnapshotOnce() = runTest(dispatcher) {
        val deleted = task(id = 8, title = "Draft")
        val repository = FakeTaskRepository().apply { tasks[8] = deleted }
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()
        val effect = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }

        viewModel.onEvent(TaskListEvent.DeleteTask(8))
        advanceUntilIdle()

        assertEquals(
            TaskListEffect.ShowUndoDelete(R.string.task_list_deleted, "Draft"),
            effect.await()
        )
        viewModel.onEvent(TaskListEvent.UndoDelete)
        viewModel.onEvent(TaskListEvent.UndoDelete)
        advanceUntilIdle()

        assertEquals(listOf(8), repository.restoredTaskIds)
    }

    @Test
    fun deleteAll_requiresConfirmationAndOffersNoUndo() = runTest(dispatcher) {
        val repository = FakeTaskRepository()
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(TaskListEvent.RequestDeleteAll)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDeleteAllConfirmation)
        assertEquals(0, repository.deleteAllCalls)

        viewModel.onEvent(TaskListEvent.ConfirmDeleteAll)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDeleteAllConfirmation)
        assertEquals(1, repository.deleteAllCalls)
        viewModel.onEvent(TaskListEvent.UndoDelete)
        advanceUntilIdle()
        assertTrue(repository.restoredTaskIds.isEmpty())
    }

    @Test
    fun retry_restartsFailedSectionObservation() = runTest(dispatcher) {
        val repository = FakeTaskRepository().apply { failObservation = true }
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()
        assertEquals(TaskListError.LOAD_FAILED, viewModel.uiState.value.error)

        repository.failObservation = false
        viewModel.onEvent(TaskListEvent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(repository.observedFilters.size >= 2)
    }

    @Test
    fun navigationEvents_emitOneTimeDestinationEffects() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val effects = listOf(
            TaskListEvent.OpenTaskEditor(7) to TaskListEffect.OpenTaskEditor(7),
            TaskListEvent.OpenCategoryManagement to TaskListEffect.OpenCategoryManagement,
            TaskListEvent.OpenCalendar to TaskListEffect.OpenCalendar,
            TaskListEvent.OpenHistory to TaskListEffect.OpenHistory
        )

        effects.forEach { (event, expected) ->
            val effect = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }
            viewModel.onEvent(event)
            assertEquals(expected, effect.await())
        }
    }

    private fun createViewModel(
        repository: FakeTaskRepository = FakeTaskRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        preferences: FakeTaskPreferencesRepository = FakeTaskPreferencesRepository()
    ): TaskListViewModel {
        val scheduler = FakeReminderScheduler()
        val clock = AppClock { 1_000 }
        val zone = ZoneIdProvider { ZoneId.of("UTC") }
        return TaskListViewModel(
            observeTaskSections = ObserveTaskSections(repository, preferences, clock, zone),
            completeTask = CompleteTask(repository, scheduler, CalculateNextOccurrence(zone), clock),
            deleteTask = DeleteTask(repository, scheduler),
            deleteAllTasks = DeleteAllTasks(repository, scheduler),
            restoreDeletedTask = RestoreDeletedTask(repository, scheduler, clock),
            categoryRepository = categories,
            preferencesRepository = preferences
        )
    }
}

private class FakeTaskPreferencesRepository(
    initial: TaskSort = TaskSort.DEFAULT
) : TaskPreferencesRepository {
    private val sort = MutableStateFlow(initial)
    override val taskSort: Flow<TaskSort> = sort
    val savedSorts = mutableListOf<TaskSort>()

    override suspend fun setTaskSort(sort: TaskSort) {
        savedSorts += sort
        this.sort.value = sort
    }
}

private class FakeCategoryRepository(
    initial: List<Category> = emptyList()
) : CategoryRepository {
    private val categories = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<Category>> = categories
    override suspend fun create(name: String, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun rename(id: Int, name: String) = CategoryMutationResult.Success
    override suspend fun recolor(id: Int, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun reorder(orderedIds: List<Int>) = CategoryMutationResult.Success
    override suspend fun delete(id: Int) = CategoryMutationResult.Success
}

private class FakeReminderScheduler : ReminderScheduler {
    override suspend fun schedule(taskId: Int, triggerAt: Long) = ReminderScheduleResult.EXACT
    override suspend fun cancel(taskId: Int) = Unit
    override suspend fun reconcile() = Unit
}

private class FakeTaskRepository(
    initialSections: TaskSections = TaskSections()
) : TaskRepository {
    private val sections = MutableStateFlow(initialSections)
    val tasks = mutableMapOf<Int, Task>()
    val observedFilters = mutableListOf<TaskFilter>()
    val restoredTaskIds = mutableListOf<Int>()
    var failObservation = false
    var deleteAllCalls = 0

    override fun observeTask(taskId: Int): Flow<Task?> = MutableStateFlow(tasks[taskId])

    override fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections> {
        observedFilters += filter
        if (failObservation) error("section failure")
        return sections
    }

    override suspend fun getTask(taskId: Int): Task? = tasks[taskId]
    override suspend fun upsert(task: Task): Int = task.id

    override suspend fun completeAtomically(
        taskId: Int,
        completedAt: Long,
        next: Task?
    ): AtomicCompletionResult? {
        val task = tasks[taskId] ?: return null
        return AtomicCompletionResult(task.copy(isCompleted = true, completedAt = completedAt), next)
    }

    override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot =
        DeletedTaskSnapshot(requireNotNull(tasks.remove(taskId)))

    override suspend fun deleteAll(): List<Int> {
        deleteAllCalls += 1
        tasks.clear()
        return emptyList()
    }

    override suspend fun restore(snapshot: DeletedTaskSnapshot): Int {
        tasks[snapshot.task.id] = snapshot.task
        restoredTaskIds += snapshot.task.id
        return snapshot.task.id
    }

    override suspend fun deleteCompleted(taskId: Int) {
        tasks.remove(taskId)
    }

    override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) = Unit
    override suspend fun futureReminders(after: Long): List<Task> = emptyList()
}

private fun task(
    id: Int,
    title: String = "Task"
) = Task(
    id = id,
    title = title,
    description = "Description",
    priority = TaskPriority.LOW,
    createdAt = 1,
    updatedAt = 1
)
