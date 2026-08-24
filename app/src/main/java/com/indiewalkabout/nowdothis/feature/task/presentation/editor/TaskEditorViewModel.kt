package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderPermissionChecker
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.TaskValidationError
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel(assistedFactory = TaskEditorViewModel.Factory::class)
class TaskEditorViewModel @AssistedInject constructor(
    @Assisted private val key: TaskEditorKey,
    private val savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val saveTask: SaveTask,
    private val permissionChecker: ReminderPermissionChecker,
    private val clock: AppClock
) : ViewModel() {
    private val restoredDraft = savedStateHandle.hasDraft()
    private val _uiState = MutableStateFlow(savedStateHandle.restoreState(key, restoredDraft))
    val uiState = _uiState.asStateFlow()

    private val effectChannel = Channel<TaskEditorEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var loadedTask: Task? = null
    private var draftVersion: TaskSnapshotVersion? = restoreDraftVersion()

    init {
        observeCategories()
        if (key.taskId == null) {
            _uiState.update { it.copy(isLoading = false) }
            persistDraft(_uiState.value)
            refreshReminderAccessState()
        } else {
            loadExistingTask(key.taskId)
        }
    }

    fun onEvent(event: TaskEditorEvent) {
        when (event) {
            is TaskEditorEvent.UpdateTitle -> updateDraft {
                it.copy(title = event.value, errors = it.errors.copy(title = null))
            }
            is TaskEditorEvent.UpdateDescription -> updateDraft {
                it.copy(description = event.value, errors = it.errors.copy(description = null))
            }
            is TaskEditorEvent.SelectPriority -> updateDraft { it.copy(priority = event.value) }
            is TaskEditorEvent.SelectCategory -> updateDraft {
                it.copy(categoryId = event.categoryId)
            }
            is TaskEditorEvent.UpdateDueAt -> updateDraft {
                it.copy(
                    dueAt = event.value,
                    errors = it.errors.copy(reminder = null, recurrence = null, recurrenceEnd = null)
                )
            }
            is TaskEditorEvent.UpdateReminderAt -> updateReminder(event.value)
            is TaskEditorEvent.NotificationPermissionResult -> {
                _uiState.update {
                    it.copy(notificationPermissionDenied = !event.granted)
                }
            }
            TaskEditorEvent.RefreshExactAlarmAccess -> {
                _uiState.update {
                    it.copy(exactTimingUnavailable = permissionChecker.needsExactAlarmAccess())
                }
            }
            is TaskEditorEvent.SelectRecurrence -> updateDraft {
                it.copy(
                    recurrence = event.value,
                    recurrenceEndAt = it.recurrenceEndAt.takeUnless {
                        event.value == RecurrenceType.NONE
                    },
                    errors = it.errors.copy(recurrence = null, recurrenceEnd = null)
                )
            }
            is TaskEditorEvent.UpdateRecurrenceEndAt -> updateDraft {
                it.copy(
                    recurrenceEndAt = event.value,
                    errors = it.errors.copy(recurrenceEnd = null)
                )
            }
            TaskEditorEvent.AddSubtask -> addSubtask()
            is TaskEditorEvent.RenameSubtask -> updateSubtask(event.draftId) {
                it.copy(title = event.value)
            }
            is TaskEditorEvent.ToggleSubtask -> updateSubtask(event.draftId) { subtask ->
                val completed = !subtask.isCompleted
                subtask.copy(
                    isCompleted = completed,
                    completedAt = clock.nowMillis().takeIf { completed }
                )
            }
            is TaskEditorEvent.MoveSubtask -> moveSubtask(event.draftId, event.offset)
            is TaskEditorEvent.DeleteSubtask -> updateDraft { state ->
                state.copy(subtasks = state.subtasks.filterNot { it.draftId == event.draftId })
            }
            TaskEditorEvent.Save -> save()
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            categoryRepository.observeAll()
                .catch { error ->
                    if (error is CancellationException) throw error
                    effectChannel.send(TaskEditorEffect.ShowMessage(R.string.task_editor_load_failed))
                }
                .collect { categories ->
                    _uiState.update { it.copy(categories = categories) }
                }
        }
    }

    private fun loadExistingTask(taskId: Int) {
        viewModelScope.launch {
            try {
                val task = taskRepository.observeTask(taskId).first()
                if (task == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    effectChannel.send(TaskEditorEffect.ShowMessage(R.string.task_editor_missing))
                    effectChannel.send(TaskEditorEffect.NavigateBack)
                    return@launch
                }
                loadedTask = task
                if (!restoredDraft) {
                    draftVersion = task.snapshotVersion()
                    _uiState.value = task.toEditorState(_uiState.value.categories)
                    persistDraft(_uiState.value)
                } else {
                    _uiState.update { it.copy(isLoading = false, taskId = task.id) }
                }
                refreshReminderAccessState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                effectChannel.send(TaskEditorEffect.ShowMessage(R.string.task_editor_load_failed))
            }
        }
    }

    private fun updateReminder(value: Long?) {
        val wasDisabled = _uiState.value.reminderAt == null
        updateDraft {
            it.copy(
                reminderAt = value,
                reminderStatus = if (value == null) {
                    ReminderStatus.NONE
                } else {
                    ReminderStatus.REQUESTED
                },
                notificationPermissionDenied = it.notificationPermissionDenied && value != null,
                exactTimingUnavailable = it.exactTimingUnavailable && value != null,
                errors = it.errors.copy(reminder = null)
            )
        }
        if (wasDisabled && value != null) {
            viewModelScope.launch {
                if (permissionChecker.needsNotificationPermission()) {
                    effectChannel.send(TaskEditorEffect.RequestNotificationPermission)
                }
                val exactAlarmRequired = permissionChecker.needsExactAlarmAccess()
                _uiState.update { it.copy(exactTimingUnavailable = exactAlarmRequired) }
                if (exactAlarmRequired) {
                    effectChannel.send(TaskEditorEffect.RequestExactAlarmAccess)
                }
            }
        }
    }

    private fun refreshReminderAccessState() {
        if (_uiState.value.reminderAt == null) return
        _uiState.update {
            it.copy(
                notificationPermissionDenied = permissionChecker.needsNotificationPermission(),
                exactTimingUnavailable = permissionChecker.needsExactAlarmAccess()
            )
        }
    }

    private fun addSubtask() {
        updateDraft { state ->
            val draftId = (state.subtasks.minOfOrNull { it.draftId } ?: 0L).coerceAtMost(0L) - 1L
            state.copy(subtasks = state.subtasks + TaskEditorSubtask(draftId = draftId))
        }
    }

    private fun updateSubtask(
        draftId: Long,
        transform: (TaskEditorSubtask) -> TaskEditorSubtask
    ) {
        updateDraft { state ->
            state.copy(
                subtasks = state.subtasks.map { subtask ->
                    if (subtask.draftId == draftId) transform(subtask) else subtask
                }
            )
        }
    }

    private fun moveSubtask(draftId: Long, offset: Int) {
        updateDraft { state ->
            val current = state.subtasks.indexOfFirst { it.draftId == draftId }
            if (current == -1) return@updateDraft state
            val target = (current + offset).coerceIn(state.subtasks.indices)
            if (target == current) return@updateDraft state
            val reordered = state.subtasks.toMutableList()
            val moved = reordered.removeAt(current)
            reordered.add(target, moved)
            state.copy(subtasks = reordered)
        }
    }

    private fun save() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errors = TaskEditorErrors()) }
        viewModelScope.launch {
            val draft = _uiState.value.toTask(loadedTask)
            try {
                when (val result = saveTask(draft, draftVersion)) {
                    is SaveTaskResult.Invalid -> {
                        _uiState.update {
                            it.copy(isSaving = false, errors = result.errors.toEditorErrors())
                        }
                    }
                    SaveTaskResult.Conflict -> {
                        _uiState.update { it.copy(isSaving = false) }
                        effectChannel.send(
                            TaskEditorEffect.ShowMessage(R.string.task_editor_save_failed)
                        )
                    }
                    is SaveTaskResult.Saved -> handleSaved(draft, result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { it.copy(isSaving = false) }
                effectChannel.send(TaskEditorEffect.ShowMessage(R.string.task_editor_save_failed))
            }
        }
    }

    private suspend fun handleSaved(task: Task, result: SaveTaskResult.Saved) {
        loadedTask = task.copy(
            id = result.taskId,
            isCompleted = result.version.isCompleted,
            completedAt = result.version.completedAt,
            reminderStatus = result.reminderStatus,
            seriesId = result.version.seriesId,
            createdAt = result.version.createdAt,
            updatedAt = result.version.updatedAt
        )
        draftVersion = result.version
        _uiState.update {
            it.copy(
                isSaving = false,
                taskId = result.taskId,
                reminderStatus = result.reminderStatus
            )
        }
        persistDraft(_uiState.value)
        if (result.reminderStatus == ReminderStatus.UNAVAILABLE) {
            effectChannel.send(
                TaskEditorEffect.ShowMessage(R.string.task_editor_reminder_unavailable)
            )
        } else {
            effectChannel.send(TaskEditorEffect.NavigateBack)
        }
    }

    private fun updateDraft(transform: (TaskEditorUiState) -> TaskEditorUiState) {
        _uiState.update(transform)
        persistDraft(_uiState.value)
    }

    private fun persistDraft(state: TaskEditorUiState) {
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_TITLE] = state.title
        savedStateHandle[KEY_DESCRIPTION] = state.description
        savedStateHandle[KEY_PRIORITY] = state.priority.name
        savedStateHandle.set<Int?>(KEY_CATEGORY_ID, state.categoryId)
        savedStateHandle[KEY_DUE_SET] = true
        savedStateHandle.set<Long?>(KEY_DUE_AT, state.dueAt)
        savedStateHandle[KEY_REMINDER_SET] = true
        savedStateHandle.set<Long?>(KEY_REMINDER_AT, state.reminderAt)
        savedStateHandle[KEY_REMINDER_STATUS] = state.reminderStatus.name
        savedStateHandle[KEY_RECURRENCE] = state.recurrence.name
        savedStateHandle[KEY_RECURRENCE_END_SET] = true
        savedStateHandle.set<Long?>(KEY_RECURRENCE_END_AT, state.recurrenceEndAt)
        savedStateHandle[KEY_SUBTASKS] = Json.encodeToString(state.subtasks)
        persistDraftVersion()
    }

    private fun restoreDraftVersion(): TaskSnapshotVersion? {
        if (savedStateHandle.get<Boolean>(KEY_VERSION_SET) != true) return null
        return TaskSnapshotVersion(
            id = savedStateHandle.get<Int>(KEY_VERSION_ID) ?: return null,
            seriesId = savedStateHandle[KEY_VERSION_SERIES_ID],
            createdAt = savedStateHandle.get<Long>(KEY_VERSION_CREATED_AT) ?: return null,
            updatedAt = savedStateHandle.get<Long>(KEY_VERSION_UPDATED_AT) ?: return null,
            isCompleted = savedStateHandle.get<Boolean>(KEY_VERSION_COMPLETED) ?: return null,
            completedAt = savedStateHandle[KEY_VERSION_COMPLETED_AT]
        )
    }

    private fun persistDraftVersion() {
        val version = draftVersion
        savedStateHandle[KEY_VERSION_SET] = version != null
        if (version == null) return
        savedStateHandle[KEY_VERSION_ID] = version.id
        savedStateHandle.set<String?>(KEY_VERSION_SERIES_ID, version.seriesId)
        savedStateHandle[KEY_VERSION_CREATED_AT] = version.createdAt
        savedStateHandle[KEY_VERSION_UPDATED_AT] = version.updatedAt
        savedStateHandle[KEY_VERSION_COMPLETED] = version.isCompleted
        savedStateHandle.set<Long?>(KEY_VERSION_COMPLETED_AT, version.completedAt)
    }

    @AssistedFactory
    interface Factory {
        fun create(key: TaskEditorKey): TaskEditorViewModel
    }

    private companion object {
        const val KEY_INITIALIZED = "draft_initialized"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_PRIORITY = "priority"
        const val KEY_CATEGORY_ID = "category_id"
        const val KEY_DUE_SET = "due_at_set"
        const val KEY_DUE_AT = "due_at"
        const val KEY_REMINDER_SET = "reminder_at_set"
        const val KEY_REMINDER_AT = "reminder_at"
        const val KEY_REMINDER_STATUS = "reminder_status"
        const val KEY_RECURRENCE = "recurrence"
        const val KEY_RECURRENCE_END_SET = "recurrence_end_at_set"
        const val KEY_RECURRENCE_END_AT = "recurrence_end_at"
        const val KEY_SUBTASKS = "subtasks"
        const val KEY_VERSION_SET = "draft_version_set"
        const val KEY_VERSION_ID = "draft_version_id"
        const val KEY_VERSION_SERIES_ID = "draft_version_series_id"
        const val KEY_VERSION_CREATED_AT = "draft_version_created_at"
        const val KEY_VERSION_UPDATED_AT = "draft_version_updated_at"
        const val KEY_VERSION_COMPLETED = "draft_version_completed"
        const val KEY_VERSION_COMPLETED_AT = "draft_version_completed_at"
    }
}

private fun SavedStateHandle.hasDraft(): Boolean =
    get<Boolean>("draft_initialized") == true || contains("title")

private fun SavedStateHandle.restoreState(
    key: TaskEditorKey,
    restored: Boolean
): TaskEditorUiState {
    if (!restored) {
        return TaskEditorUiState(taskId = key.taskId, dueAt = key.initialDueAt)
    }
    return TaskEditorUiState(
        taskId = key.taskId,
        title = get<String>("title").orEmpty(),
        description = get<String>("description").orEmpty(),
        priority = enumValueOrDefault(get("priority"), TaskPriority.LOW),
        categoryId = get("category_id"),
        dueAt = if (get<Boolean>("due_at_set") == true) get("due_at") else key.initialDueAt,
        reminderAt = if (get<Boolean>("reminder_at_set") == true) get("reminder_at") else null,
        reminderStatus = enumValueOrDefault(get("reminder_status"), ReminderStatus.NONE),
        recurrence = enumValueOrDefault(get("recurrence"), RecurrenceType.NONE),
        recurrenceEndAt = if (get<Boolean>("recurrence_end_at_set") == true) {
            get("recurrence_end_at")
        } else {
            null
        },
        subtasks = get<String>("subtasks")?.let { encoded ->
            runCatching { Json.decodeFromString<List<TaskEditorSubtask>>(encoded) }.getOrDefault(emptyList())
        }.orEmpty()
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

private fun Task.toEditorState(categories: List<com.indiewalkabout.nowdothis.feature.category.domain.model.Category>) =
    TaskEditorUiState(
        isLoading = false,
        taskId = id,
        title = title,
        description = description,
        priority = priority,
        categoryId = categoryId,
        dueAt = dueAt,
        reminderAt = reminderAt,
        reminderStatus = reminderStatus,
        recurrence = recurrence,
        recurrenceEndAt = recurrenceEndAt,
        subtasks = subtasks.sortedBy(Subtask::position).map { subtask ->
            TaskEditorSubtask(
                draftId = subtask.id.toLong(),
                persistedId = subtask.id,
                title = subtask.title,
                isCompleted = subtask.isCompleted,
                completedAt = subtask.completedAt
            )
        },
        categories = categories
    )

private fun TaskEditorUiState.toTask(existing: Task?): Task = Task(
    id = taskId ?: existing?.id ?: 0,
    title = title.trim(),
    description = description.trim(),
    priority = priority,
    categoryId = categoryId,
    isCompleted = existing?.isCompleted ?: false,
    completedAt = existing?.completedAt,
    dueAt = dueAt,
    reminderAt = reminderAt,
    reminderStatus = reminderStatus,
    recurrence = recurrence,
    recurrenceEndAt = recurrenceEndAt,
    seriesId = existing?.seriesId,
    createdAt = existing?.createdAt ?: 0,
    updatedAt = existing?.updatedAt ?: 0,
    subtasks = subtasks.filter { it.title.isNotBlank() }.mapIndexed { index, subtask ->
        Subtask(
            id = subtask.persistedId,
            taskId = taskId ?: existing?.id ?: 0,
            title = subtask.title.trim(),
            isCompleted = subtask.isCompleted,
            completedAt = subtask.completedAt,
            position = index
        )
    }
)

private fun List<TaskValidationError>.toEditorErrors(): TaskEditorErrors {
    var mapped = TaskEditorErrors()
    forEach { error ->
        mapped = when (error) {
            TaskValidationError.BLANK_TITLE -> mapped.copy(title = TaskEditorFieldError.REQUIRED)
            TaskValidationError.BLANK_DESCRIPTION -> {
                mapped.copy(description = TaskEditorFieldError.REQUIRED)
            }
            TaskValidationError.REMINDER_AFTER_DUE -> {
                mapped.copy(reminder = TaskEditorFieldError.REMINDER_AFTER_DUE)
            }
            TaskValidationError.REMINDER_IN_PAST -> {
                mapped.copy(reminder = TaskEditorFieldError.REMINDER_IN_PAST)
            }
            TaskValidationError.RECURRENCE_WITHOUT_DUE_TIME -> {
                mapped.copy(recurrence = TaskEditorFieldError.DUE_REQUIRED)
            }
            TaskValidationError.RECURRENCE_END_BEFORE_DUE -> {
                mapped.copy(recurrenceEnd = TaskEditorFieldError.END_BEFORE_DUE)
            }
        }
    }
    return mapped
}
