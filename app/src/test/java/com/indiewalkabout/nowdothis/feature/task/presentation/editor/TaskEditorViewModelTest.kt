package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderPermissionChecker
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ValidateTask
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val clock = AppClock { 50_000L }
    private lateinit var repository: EditorTaskRepository
    private lateinit var categories: EditorCategoryRepository
    private lateinit var scheduler: EditorReminderScheduler
    private lateinit var permissions: EditorPermissionChecker

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = EditorTaskRepository()
        categories = EditorCategoryRepository()
        scheduler = EditorReminderScheduler()
        permissions = EditorPermissionChecker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newTask_usesInitialDateAndRestoredTitle() = runTest(dispatcher) {
        val viewModel = createViewModel(
            key = TaskEditorKey(taskId = null, initialDueAt = 90_000L),
            handle = SavedStateHandle(mapOf("title" to "Bozza"))
        )
        advanceUntilIdle()

        assertEquals("Bozza", viewModel.uiState.value.title)
        assertEquals(90_000L, viewModel.uiState.value.dueAt)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun existingTask_loadsOnceWithoutOverwritingEditedDraft() = runTest(dispatcher) {
        repository.emit(existingTask(title = "Original"))
        val viewModel = createViewModel(TaskEditorKey(taskId = 7, initialDueAt = null))
        advanceUntilIdle()
        assertEquals("Original", viewModel.uiState.value.title)

        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Edited"))
        repository.emit(existingTask(title = "Remote replacement"))
        advanceUntilIdle()

        assertEquals("Edited", viewModel.uiState.value.title)
    }

    @Test
    fun missingExistingTask_emitsMessageThenNavigateBack() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(taskId = 99, initialDueAt = null))
        val effects = async { List(2) { viewModel.effects.first() } }
        advanceUntilIdle()

        assertEquals(
            listOf(
                TaskEditorEffect.ShowMessage(R.string.task_editor_missing),
                TaskEditorEffect.NavigateBack
            ),
            effects.await()
        )
    }

    @Test
    fun draftAndSubtasks_restoreInEditedOrder() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = createViewModel(TaskEditorKey(null, 80_000L), handle)
        advanceUntilIdle()
        first.onEvent(TaskEditorEvent.UpdateTitle("Draft"))
        first.onEvent(TaskEditorEvent.UpdateDescription("Details"))
        first.onEvent(TaskEditorEvent.SelectPriority(TaskPriority.HIGH))
        first.onEvent(TaskEditorEvent.AddSubtask)
        first.onEvent(TaskEditorEvent.AddSubtask)
        val initial = first.uiState.value.subtasks
        first.onEvent(TaskEditorEvent.RenameSubtask(initial[0].draftId, "First"))
        first.onEvent(TaskEditorEvent.RenameSubtask(initial[1].draftId, "Second"))
        first.onEvent(TaskEditorEvent.ToggleSubtask(initial[1].draftId))
        first.onEvent(TaskEditorEvent.MoveSubtask(initial[1].draftId, -1))

        val restored = createViewModel(TaskEditorKey(null, null), handle)
        advanceUntilIdle()

        assertEquals("Draft", restored.uiState.value.title)
        assertEquals("Details", restored.uiState.value.description)
        assertEquals(TaskPriority.HIGH, restored.uiState.value.priority)
        assertEquals(listOf("Second", "First"), restored.uiState.value.subtasks.map { it.title })
        assertTrue(restored.uiState.value.subtasks.first().isCompleted)
    }

    @Test
    fun enablingReminder_emitsOnlyRequiredPermissionEffectsOnce() = runTest(dispatcher) {
        permissions.notificationRequired = true
        permissions.exactAlarmRequired = true
        val viewModel = createViewModel(TaskEditorKey(null, null))
        val effects = async { List(2) { viewModel.effects.first() } }
        advanceUntilIdle()

        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(70_000L))
        advanceUntilIdle()
        assertEquals(
            listOf(
                TaskEditorEffect.RequestNotificationPermission,
                TaskEditorEffect.RequestExactAlarmAccess
            ),
            effects.await()
        )

        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(75_000L))
        advanceUntilIdle()
        assertEquals(1, permissions.notificationChecks)
        assertEquals(1, permissions.exactAlarmChecks)
    }

    @Test
    fun permissionDenial_keepsReminderAndExposesRecoverableAccessState() = runTest(dispatcher) {
        permissions.notificationRequired = true
        permissions.exactAlarmRequired = true
        val handle = SavedStateHandle()
        val viewModel = createViewModel(TaskEditorKey(null, null), handle)
        val effects = async { List(2) { viewModel.effects.first() } }
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(70_000L))
        advanceUntilIdle()
        effects.await()

        viewModel.onEvent(TaskEditorEvent.NotificationPermissionResult(granted = false))
        assertEquals(70_000L, viewModel.uiState.value.reminderAt)
        assertTrue(viewModel.uiState.value.notificationPermissionDenied)
        assertTrue(viewModel.uiState.value.exactTimingUnavailable)

        permissions.exactAlarmRequired = false
        viewModel.onEvent(TaskEditorEvent.RefreshExactAlarmAccess)
        assertFalse(viewModel.uiState.value.exactTimingUnavailable)

        val restored = createViewModel(TaskEditorKey(null, null), handle)
        advanceUntilIdle()
        assertTrue(restored.uiState.value.notificationPermissionDenied)
        assertFalse(restored.uiState.value.exactTimingUnavailable)
    }

    @Test
    fun successfulSave_mapsEntireDraftAndNavigatesBack() = runTest(dispatcher) {
        categories.categories.value = listOf(category(3))
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Plan"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Quarter"))
        viewModel.onEvent(TaskEditorEvent.SelectPriority(TaskPriority.HIGH))
        viewModel.onEvent(TaskEditorEvent.SelectCategory(3))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(90_000L))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(80_000L))
        viewModel.onEvent(TaskEditorEvent.SelectRecurrence(RecurrenceType.WEEKLY))
        viewModel.onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(190_000L))
        viewModel.onEvent(TaskEditorEvent.AddSubtask)
        val subtaskId = viewModel.uiState.value.subtasks.single().draftId
        viewModel.onEvent(TaskEditorEvent.RenameSubtask(subtaskId, "Outline"))
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        val saved = requireNotNull(repository.lastUpsert)
        assertEquals("Plan", saved.title)
        assertEquals("Quarter", saved.description)
        assertEquals(TaskPriority.HIGH, saved.priority)
        assertEquals(3, saved.categoryId)
        assertEquals(90_000L, saved.dueAt)
        assertEquals(80_000L, saved.reminderAt)
        assertEquals(RecurrenceType.WEEKLY, saved.recurrence)
        assertEquals(190_000L, saved.recurrenceEndAt)
        assertEquals(listOf("Outline"), saved.subtasks.map { it.title })
        assertEquals(listOf(0), saved.subtasks.map { it.position })
        assertEquals(TaskEditorEffect.NavigateBack, effect.await())
    }

    @Test
    fun invalidSave_mapsEveryValidationErrorInline() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(70_000L))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(80_000L))
        viewModel.onEvent(TaskEditorEvent.SelectRecurrence(RecurrenceType.DAILY))
        viewModel.onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(60_000L))

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        val errors = viewModel.uiState.value.errors
        assertEquals(TaskEditorFieldError.REQUIRED, errors.title)
        assertEquals(TaskEditorFieldError.REQUIRED, errors.description)
        assertEquals(TaskEditorFieldError.REMINDER_AFTER_DUE, errors.reminder)
        assertEquals(TaskEditorFieldError.END_BEFORE_DUE, errors.recurrenceEnd)

        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(null))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(40_000L))
        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()
        assertEquals(TaskEditorFieldError.DUE_REQUIRED, viewModel.uiState.value.errors.recurrence)
        assertEquals(TaskEditorFieldError.REMINDER_IN_PAST, viewModel.uiState.value.errors.reminder)
    }

    @Test
    fun subtaskDeleteAndMove_keepStableDraftIds() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        repeat(3) { viewModel.onEvent(TaskEditorEvent.AddSubtask) }
        val ids = viewModel.uiState.value.subtasks.map { it.draftId }

        viewModel.onEvent(TaskEditorEvent.MoveSubtask(ids[2], -1))
        viewModel.onEvent(TaskEditorEvent.DeleteSubtask(ids[0]))

        assertEquals(listOf(ids[2], ids[1]), viewModel.uiState.value.subtasks.map { it.draftId })
    }

    @Test
    fun clearingReminder_clearsStaleDeliveryStatus() = runTest(dispatcher) {
        repository.emit(
            existingTask(title = "Reminder").copy(reminderStatus = ReminderStatus.UNAVAILABLE)
        )
        val viewModel = createViewModel(TaskEditorKey(7, null))
        advanceUntilIdle()

        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(null))

        assertNull(viewModel.uiState.value.reminderAt)
        assertEquals(ReminderStatus.NONE, viewModel.uiState.value.reminderStatus)

        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(85_000L))

        assertEquals(ReminderStatus.REQUESTED, viewModel.uiState.value.reminderStatus)
    }

    @Test
    fun selectingNoRecurrence_clearsHiddenEndDateAndRestoresClearedState() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            repository.emit(existingTask(title = "Recurring"))
            val viewModel = createViewModel(TaskEditorKey(7, null), handle)
            advanceUntilIdle()

            viewModel.onEvent(TaskEditorEvent.SelectRecurrence(RecurrenceType.NONE))
            val restored = createViewModel(TaskEditorKey(7, null), handle)
            advanceUntilIdle()

            assertEquals(RecurrenceType.NONE, restored.uiState.value.recurrence)
            assertNull(restored.uiState.value.recurrenceEndAt)
        }

    @Test
    fun persistenceFailure_preservesDraftAndEmitsRetryableMessage() = runTest(dispatcher) {
        repository.upsertFailure = IllegalStateException("database unavailable")
        val viewModel = validViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        assertEquals("Valid", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(TaskEditorEffect.ShowMessage(R.string.task_editor_save_failed), effect.await())
    }

    @Test
    fun optimisticConflict_preservesDraftAndEmitsRetryableMessageWithoutNavigation() =
        runTest(dispatcher) {
            repository.emit(existingTask(title = "Canonical"))
            repository.rejectConditionalUpdate = true
            val viewModel = createViewModel(TaskEditorKey(7, null))
            advanceUntilIdle()
            viewModel.onEvent(TaskEditorEvent.UpdateTitle("Local draft"))
            val effect = async { viewModel.effects.first() }

            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals("Local draft", viewModel.uiState.value.title)
            assertFalse(viewModel.uiState.value.isSaving)
            assertNull(repository.lastUpsert)
            assertEquals(
                TaskEditorEffect.ShowMessage(R.string.task_editor_save_failed),
                effect.await()
            )
        }

    @Test
    fun unavailableReminder_keepsEditorOpenForRetry() = runTest(dispatcher) {
        scheduler.result = ReminderScheduleResult.FAILED
        val viewModel = validViewModel(reminderAt = 70_000L, dueAt = 80_000L)
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        assertEquals(ReminderStatus.UNAVAILABLE, viewModel.uiState.value.reminderStatus)
        assertEquals(
            TaskEditorEffect.ShowMessage(R.string.task_editor_reminder_unavailable),
            effect.await()
        )
        assertEquals("Valid", viewModel.uiState.value.title)
    }

    @Test
    fun unavailableReminder_retryUsesSavedVersionAndCanNavigateAfterSuccess() =
        runTest(dispatcher) {
            repository.emit(existingTask(title = "Reminder retry"))
            scheduler.result = ReminderScheduleResult.FAILED
            val viewModel = createViewModel(TaskEditorKey(7, null))
            advanceUntilIdle()
            val unavailable = async { viewModel.effects.first() }

            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()
            assertEquals(
                TaskEditorEffect.ShowMessage(R.string.task_editor_reminder_unavailable),
                unavailable.await()
            )

            scheduler.result = ReminderScheduleResult.EXACT
            val success = async { viewModel.effects.first() }
            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals(TaskEditorEffect.NavigateBack, success.await())
        }

    private suspend fun validViewModel(
        reminderAt: Long? = null,
        dueAt: Long? = null
    ): TaskEditorViewModel {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Valid"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Description"))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(dueAt))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(reminderAt))
        return viewModel
    }

    private fun createViewModel(
        key: TaskEditorKey,
        handle: SavedStateHandle = SavedStateHandle()
    ) = TaskEditorViewModel(
        key = key,
        savedStateHandle = handle,
        taskRepository = repository,
        categoryRepository = categories,
        saveTask = SaveTask(repository, scheduler, ValidateTask(), clock) { "series" },
        permissionChecker = permissions,
        clock = clock
    )

    private fun existingTask(title: String) = Task(
        id = 7,
        title = title,
        description = "Existing description",
        priority = TaskPriority.MEDIUM,
        categoryId = 3,
        dueAt = 90_000L,
        reminderAt = 80_000L,
        recurrence = RecurrenceType.MONTHLY,
        recurrenceEndAt = 190_000L,
        createdAt = 10L,
        updatedAt = 20L,
        subtasks = listOf(Subtask(4, 7, "Existing subtask", false, null, 0))
    )
}

private class EditorPermissionChecker : ReminderPermissionChecker {
    var notificationRequired = false
    var exactAlarmRequired = false
    var notificationChecks = 0
    var exactAlarmChecks = 0

    override fun needsNotificationPermission(): Boolean {
        notificationChecks += 1
        return notificationRequired
    }

    override fun needsExactAlarmAccess(): Boolean {
        exactAlarmChecks += 1
        return exactAlarmRequired
    }
}

private class EditorReminderScheduler : ReminderScheduler {
    var result = ReminderScheduleResult.EXACT
    override suspend fun schedule(taskId: Int, triggerAt: Long) = result
    override suspend fun cancel(taskId: Int) = Unit
    override suspend fun reconcile() = Unit
}

private class EditorCategoryRepository : CategoryRepository {
    val categories = MutableStateFlow(emptyList<Category>())
    override fun observeAll(): Flow<List<Category>> = categories
    override suspend fun create(name: String, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun rename(id: Int, name: String) = CategoryMutationResult.Success
    override suspend fun recolor(id: Int, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun reorder(orderedIds: List<Int>) = CategoryMutationResult.Success
    override suspend fun delete(id: Int) = CategoryMutationResult.Success
}

private class EditorTaskRepository : TaskRepository {
    private val observed = mutableMapOf<Int, MutableStateFlow<Task?>>()
    var lastUpsert: Task? = null
    var upsertFailure: Throwable? = null
    var rejectConditionalUpdate = false
    private var nextId = 40

    fun emit(task: Task) {
        observed.getOrPut(task.id) { MutableStateFlow(null) }.value = task
    }

    override fun observeTask(taskId: Int): Flow<Task?> =
        observed.getOrPut(taskId) { MutableStateFlow(null) }

    override fun observeSections(filter: TaskFilter, bounds: com.indiewalkabout.nowdothis.core.time.DayBounds): Flow<TaskSections> =
        MutableStateFlow(TaskSections())

    override suspend fun getTask(taskId: Int): Task? = observed[taskId]?.value

    override suspend fun upsert(task: Task): Int {
        upsertFailure?.let { throw it }
        val id = task.id.takeIf { it != 0 } ?: nextId++
        val persisted = task.copy(id = id)
        lastUpsert = persisted
        emit(persisted)
        return id
    }

    override suspend fun updateIfUnchanged(
        task: Task,
        expectedVersion: TaskSnapshotVersion
    ): Boolean {
        if (rejectConditionalUpdate) return false
        val current = observed[task.id]?.value ?: return false
        if (current.snapshotVersion() != expectedVersion) return false
        lastUpsert = task
        emit(task)
        return true
    }

    override suspend fun completeAtomically(
        taskId: Int,
        completedAt: Long,
        nextOccurrence: (Task) -> Task?
    ): AtomicCompletionResult = AtomicCompletionResult.NotFound

    override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot = error("unused")
    override suspend fun deleteAll(): List<Int> = emptyList()
    override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = snapshot.task.id
    override suspend fun deleteCompleted(taskId: Int) = Unit
    override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) {
        observed[taskId]?.value?.let { emit(it.copy(reminderStatus = status)) }
    }
    override suspend fun updateReminderStatusIfCurrent(
        expectedVersion: TaskSnapshotVersion,
        status: ReminderStatus
    ): Boolean = false
    override suspend fun futureReminders(after: Long): List<Task> = emptyList()
}

private fun category(id: Int) = Category(
    id = id,
    customName = "Category $id",
    color = CategoryColor.BLUE,
    position = 0,
    createdAt = 1
)
