package com.indiewalkabout.nowdothis.feature.task.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteAllTasks
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ObserveTaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.RestoreDeletedTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val observeTaskSections: ObserveTaskSections,
    private val completeTask: CompleteTask,
    private val deleteTask: DeleteTask,
    private val deleteAllTasks: DeleteAllTasks,
    private val restoreDeletedTask: RestoreDeletedTask,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: TaskPreferencesRepository
) : ViewModel() {
    private val filter = MutableStateFlow(TaskFilter())
    private val retryToken = MutableStateFlow(0)
    private val deleteAllConfirmation = MutableStateFlow(false)
    private val effectChannel = Channel<TaskListEffect>(Channel.BUFFERED)
    private var pendingDeletedSnapshot: DeletedTaskSnapshot? = null

    val effects: Flow<TaskListEffect> = effectChannel.receiveAsFlow()

    private val sections = combine(filter, retryToken) { current, _ -> current }
        .restartable { current -> observeTaskSections(current) }

    private val categories = retryToken.restartable { categoryRepository.observeAll() }

    private val observedState = combine(
        sections,
        categories,
        preferencesRepository.taskSort.asLoadState(),
        filter
    ) { sectionState, categoryState, sortState, currentFilter ->
        val failed = sectionState is LoadState.Failed ||
            categoryState is LoadState.Failed ||
            sortState is LoadState.Failed
        ObservedState(
            isLoading = sectionState is LoadState.Loading ||
                categoryState is LoadState.Loading ||
                sortState is LoadState.Loading,
            error = TaskListError.LOAD_FAILED.takeIf { failed },
            sections = sectionState.valueOr(TaskSections()),
            categories = categoryState.valueOr(emptyList()),
            sort = sortState.valueOr(TaskSort.DEFAULT),
            filter = currentFilter
        )
    }

    val uiState = combine(observedState, deleteAllConfirmation) { observed, confirmation ->
        TaskListUiState(
            isLoading = observed.isLoading,
            error = observed.error,
            sections = observed.sections,
            categories = observed.categories,
            query = observed.filter.query,
            selectedCategoryId = observed.filter.categoryId,
            sort = observed.sort,
            showDeleteAllConfirmation = confirmation
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TaskListUiState())

    fun onEvent(event: TaskListEvent) {
        when (event) {
            is TaskListEvent.UpdateQuery -> filter.value = filter.value.copy(query = event.query)
            is TaskListEvent.SelectCategory -> {
                filter.value = filter.value.copy(categoryId = event.categoryId)
            }
            is TaskListEvent.SelectSort -> launchMutation {
                preferencesRepository.setTaskSort(event.sort)
            }
            is TaskListEvent.CompleteTask -> launchMutation {
                when (completeTask(event.taskId)) {
                    CompleteTaskResult.NotFound,
                    CompleteTaskResult.AlreadyCompleted,
                    is CompleteTaskResult.Invalid -> showMessage(R.string.task_list_complete_failed)
                    is CompleteTaskResult.Completed -> Unit
                }
            }
            is TaskListEvent.DeleteTask -> launchMutation {
                val snapshot = deleteTask(event.taskId)
                pendingDeletedSnapshot = snapshot
                effectChannel.send(
                    TaskListEffect.ShowUndoDelete(
                        messageRes = R.string.task_list_deleted,
                        taskTitle = snapshot.task.title
                    )
                )
            }
            TaskListEvent.UndoDelete -> undoDelete()
            TaskListEvent.RequestDeleteAll -> deleteAllConfirmation.value = true
            TaskListEvent.DismissDeleteAll -> deleteAllConfirmation.value = false
            TaskListEvent.ConfirmDeleteAll -> {
                deleteAllConfirmation.value = false
                pendingDeletedSnapshot = null
                launchMutation { deleteAllTasks() }
            }
            TaskListEvent.Retry -> retryToken.value += 1
            is TaskListEvent.OpenTaskEditor -> emitEffect(TaskListEffect.OpenTaskEditor(event.taskId))
            TaskListEvent.OpenCategoryManagement -> emitEffect(TaskListEffect.OpenCategoryManagement)
            TaskListEvent.OpenCalendar -> emitEffect(TaskListEffect.OpenCalendar)
            TaskListEvent.OpenHistory -> emitEffect(TaskListEffect.OpenHistory)
            TaskListEvent.OpenDataPortability -> emitEffect(TaskListEffect.OpenDataPortability)
        }
    }

    private fun undoDelete() {
        val snapshot = pendingDeletedSnapshot ?: return
        pendingDeletedSnapshot = null
        launchMutation(
            onFailure = { pendingDeletedSnapshot = snapshot }
        ) {
            restoreDeletedTask(snapshot)
        }
    }

    private fun emitEffect(effect: TaskListEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private suspend fun showMessage(messageRes: Int) {
        effectChannel.send(TaskListEffect.ShowMessage(messageRes))
    }

    private fun launchMutation(
        onFailure: () -> Unit = {},
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                onFailure()
                showMessage(R.string.task_list_operation_failed)
            }
        }
    }
}

private data class ObservedState(
    val isLoading: Boolean,
    val error: TaskListError?,
    val sections: TaskSections,
    val categories: List<Category>,
    val sort: TaskSort,
    val filter: TaskFilter
)

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Data<T>(val value: T) : LoadState<T>
    data object Failed : LoadState<Nothing>
}

private fun <T> LoadState<T>.valueOr(default: T): T = when (this) {
    is LoadState.Data -> value
    LoadState.Failed,
    LoadState.Loading -> default
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T, R> Flow<T>.restartable(source: (T) -> Flow<R>): Flow<LoadState<R>> =
    flatMapLatest { value ->
        flow { emitAll(source(value)) }.asLoadState()
    }

private fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> =
    map<T, LoadState<T>> { LoadState.Data(it) }
        .onStart { emit(LoadState.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(LoadState.Failed)
        }
