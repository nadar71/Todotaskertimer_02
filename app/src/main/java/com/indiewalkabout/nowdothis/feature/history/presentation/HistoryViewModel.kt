package com.indiewalkabout.nowdothis.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.history.domain.usecase.CompletionHistorySection
import com.indiewalkabout.nowdothis.feature.history.domain.usecase.ObserveCompletionHistory
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observeHistory: ObserveCompletionHistory,
    private val categoryRepository: CategoryRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val filter = MutableStateFlow(TaskFilter())
    private val retryToken = MutableStateFlow(0)
    private val overlays = MutableStateFlow(HistoryOverlays())
    private val effectChannel = Channel<HistoryEffect>(Channel.BUFFERED)

    val effects: Flow<HistoryEffect> = effectChannel.receiveAsFlow()

    private val history = combine(filter, retryToken) { current, _ -> current }
        .restartable(observeHistory::invoke)
    private val categories = retryToken.restartable { categoryRepository.observeAll() }

    private val observed = combine(history, categories, filter) { historyState, categoryState, currentFilter ->
        HistoryObserved(
            isLoading = historyState is HistoryLoadState.Loading || categoryState is HistoryLoadState.Loading,
            error = HistoryError.LOAD_FAILED.takeIf {
                historyState is HistoryLoadState.Failed || categoryState is HistoryLoadState.Failed
            },
            sections = historyState.valueOr(emptyList()),
            categories = categoryState.valueOr(emptyList()),
            filter = currentFilter
        )
    }

    val uiState = combine(observed, overlays) { current, overlay ->
        HistoryUiState(
            isLoading = current.isLoading,
            error = current.error,
            sections = current.sections,
            categories = current.categories,
            query = current.filter.query,
            selectedCategoryId = current.filter.categoryId,
            inspectedTask = overlay.inspectedTask,
            pendingDelete = overlay.pendingDelete,
            isDeleting = overlay.isDeleting
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    fun onEvent(event: HistoryEvent) {
        when (event) {
            is HistoryEvent.UpdateQuery -> filter.value = filter.value.copy(query = event.query)
            is HistoryEvent.SelectCategory -> filter.value = filter.value.copy(categoryId = event.categoryId)
            is HistoryEvent.Inspect -> findTask(event.taskId)?.let { task ->
                overlays.value = overlays.value.copy(inspectedTask = task)
            }
            HistoryEvent.DismissInspection -> overlays.value = overlays.value.copy(inspectedTask = null)
            is HistoryEvent.RequestDelete -> findTask(event.taskId)?.let { task ->
                overlays.value = overlays.value.copy(pendingDelete = task)
            }
            HistoryEvent.ConfirmDelete -> confirmDelete()
            HistoryEvent.DismissDelete -> if (!overlays.value.isDeleting) {
                overlays.value = overlays.value.copy(pendingDelete = null)
            }
            HistoryEvent.Retry -> retryToken.value += 1
        }
    }

    private fun findTask(taskId: Int): Task? = overlays.value.inspectedTask
        ?.takeIf { it.id == taskId }
        ?: uiState.value.sections.asSequence().flatMap { it.tasks.asSequence() }
            .firstOrNull { it.id == taskId }

    private fun confirmDelete() {
        val task = overlays.value.pendingDelete ?: return
        if (overlays.value.isDeleting) return
        overlays.value = overlays.value.copy(isDeleting = true)
        viewModelScope.launch {
            try {
                taskRepository.deleteCompleted(task.id)
                overlays.value = HistoryOverlays()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                overlays.value = overlays.value.copy(isDeleting = false)
                effectChannel.send(HistoryEffect.ShowMessage(R.string.history_delete_failed))
            }
        }
    }
}

private data class HistoryOverlays(
    val inspectedTask: Task? = null,
    val pendingDelete: Task? = null,
    val isDeleting: Boolean = false
)

private data class HistoryObserved(
    val isLoading: Boolean,
    val error: HistoryError?,
    val sections: List<CompletionHistorySection>,
    val categories: List<Category>,
    val filter: TaskFilter
)

private sealed interface HistoryLoadState<out T> {
    data object Loading : HistoryLoadState<Nothing>
    data class Data<T>(val value: T) : HistoryLoadState<T>
    data object Failed : HistoryLoadState<Nothing>
}

private fun <T> HistoryLoadState<T>.valueOr(default: T): T = when (this) {
    is HistoryLoadState.Data -> value
    HistoryLoadState.Failed,
    HistoryLoadState.Loading -> default
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T, R> Flow<T>.restartable(source: (T) -> Flow<R>): Flow<HistoryLoadState<R>> =
    flatMapLatest { value ->
        flow { emitAll(source(value)) }
            .map<R, HistoryLoadState<R>> { HistoryLoadState.Data(it) }
            .onStart { emit(HistoryLoadState.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(HistoryLoadState.Failed)
            }
    }
