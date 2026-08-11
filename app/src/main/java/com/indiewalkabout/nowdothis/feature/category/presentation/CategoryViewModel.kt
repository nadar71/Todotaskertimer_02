package com.indiewalkabout.nowdothis.feature.category.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryError
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val defaultNameResolver: DefaultCategoryNameResolver
) : ViewModel() {
    private val mutableState = MutableStateFlow(CategoryUiState())
    private val effectChannel = Channel<CategoryEffect>(Channel.BUFFERED)
    private var observationJob: Job? = null
    private var reorderJob: Job? = null

    val uiState: StateFlow<CategoryUiState> = mutableState.asStateFlow()
    val effects: Flow<CategoryEffect> = effectChannel.receiveAsFlow()

    init {
        observeCategories()
    }

    fun onEvent(event: CategoryEvent) {
        when (event) {
            CategoryEvent.Add -> openAddEditor()
            is CategoryEvent.Edit -> openEditEditor(event.categoryId)
            is CategoryEvent.ChangeName -> updateEditor { copy(name = event.name, nameError = null) }
            is CategoryEvent.SelectColor -> updateEditor { copy(selectedColor = event.color) }
            CategoryEvent.ConfirmEditor -> confirmEditor()
            CategoryEvent.DismissEditor -> mutableState.value = mutableState.value.copy(editor = null)
            is CategoryEvent.MoveUp -> move(event.categoryId, -1)
            is CategoryEvent.MoveDown -> move(event.categoryId, 1)
            is CategoryEvent.RequestDelete -> requestDelete(event.categoryId)
            CategoryEvent.ConfirmDelete -> confirmDelete()
            CategoryEvent.DismissDelete -> mutableState.value = mutableState.value.copy(pendingDelete = null)
            CategoryEvent.Retry -> observeCategories()
        }
    }

    private fun observeCategories() {
        observationJob?.cancel()
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        observationJob = viewModelScope.launch {
            try {
                repository.observeAll().collect { categories ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        error = null,
                        categories = categories.toItems()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = CategoryScreenError.LOAD_FAILED
                )
            }
        }
    }

    private fun openAddEditor() {
        mutableState.value = mutableState.value.copy(editor = CategoryEditorState())
    }

    private fun openEditEditor(categoryId: Int) {
        val category = mutableState.value.categories.firstOrNull { it.id == categoryId } ?: return
        mutableState.value = mutableState.value.copy(
            editor = CategoryEditorState(
                categoryId = category.id,
                name = category.name,
                selectedColor = category.color,
                originalName = category.name,
                originalColor = category.color
            )
        )
    }

    private fun updateEditor(transform: CategoryEditorState.() -> CategoryEditorState) {
        val editor = mutableState.value.editor ?: return
        mutableState.value = mutableState.value.copy(editor = editor.transform())
    }

    private fun confirmEditor() {
        val editor = mutableState.value.editor ?: return
        if (editor.isSaving) return
        val name = editor.name.trim()
        if (name.isEmpty()) {
            updateEditor { copy(nameError = CategoryNameError.BLANK) }
            return
        }
        updateEditor { copy(isSaving = true, nameError = null) }
        viewModelScope.launch {
            try {
                val result = if (editor.categoryId == null) {
                    repository.create(name, editor.selectedColor)
                } else {
                    updateExisting(editor, name)
                }
                handleEditorResult(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                updateEditor { copy(isSaving = false) }
                effectChannel.send(CategoryEffect.ShowMessage(R.string.category_operation_failed))
            }
        }
    }

    private suspend fun updateExisting(
        editor: CategoryEditorState,
        name: String
    ): CategoryMutationResult {
        val id = requireNotNull(editor.categoryId)
        if (name != editor.originalName) {
            val renameResult = repository.rename(id, name)
            if (renameResult !is CategoryMutationResult.Success) return renameResult
        }
        return if (editor.selectedColor != editor.originalColor) {
            repository.recolor(id, editor.selectedColor)
        } else {
            CategoryMutationResult.Success
        }
    }

    private suspend fun handleEditorResult(result: CategoryMutationResult) {
        when (result) {
            CategoryMutationResult.Success -> {
                mutableState.value = mutableState.value.copy(editor = null)
            }
            is CategoryMutationResult.Failure -> when (result.error) {
                CategoryError.BlankName -> updateEditor {
                    copy(isSaving = false, nameError = CategoryNameError.BLANK)
                }
                CategoryError.DuplicateName -> updateEditor {
                    copy(isSaving = false, nameError = CategoryNameError.DUPLICATE)
                }
                CategoryError.InvalidOrder,
                CategoryError.NotFound -> {
                    updateEditor { copy(isSaving = false) }
                    effectChannel.send(CategoryEffect.ShowMessage(R.string.category_operation_failed))
                }
            }
        }
    }

    private fun move(categoryId: Int, offset: Int) {
        if (reorderJob?.isActive == true) return
        val categories = mutableState.value.categories
        val from = categories.indexOfFirst { it.id == categoryId }
        val to = from + offset
        if (from < 0 || to !in categories.indices) return
        val reordered = categories.toMutableList().apply {
            add(to, removeAt(from))
        }
        reorderJob = launchSimpleMutation { repository.reorder(reordered.map(CategoryItem::id)) }
    }

    private fun requestDelete(categoryId: Int) {
        val category = mutableState.value.categories.firstOrNull { it.id == categoryId } ?: return
        mutableState.value = mutableState.value.copy(pendingDelete = category)
    }

    private fun confirmDelete() {
        val category = mutableState.value.pendingDelete ?: return
        if (mutableState.value.isDeleting) return
        mutableState.value = mutableState.value.copy(isDeleting = true)
        launchSimpleMutation(
            onSuccess = {
                mutableState.value = mutableState.value.copy(pendingDelete = null, isDeleting = false)
            },
            onFailure = {
                mutableState.value = mutableState.value.copy(isDeleting = false)
            }
        ) { repository.delete(category.id) }
    }

    private fun launchSimpleMutation(
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
        mutation: suspend () -> CategoryMutationResult
    ): Job = viewModelScope.launch {
        try {
            when (mutation()) {
                CategoryMutationResult.Success -> onSuccess()
                is CategoryMutationResult.Failure -> {
                    onFailure()
                    effectChannel.send(CategoryEffect.ShowMessage(R.string.category_operation_failed))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            onFailure()
            effectChannel.send(CategoryEffect.ShowMessage(R.string.category_operation_failed))
        }
    }

    private fun List<Category>.toItems(): List<CategoryItem> = sortedBy(Category::position)
        .mapIndexed { index, category ->
            CategoryItem(
                id = category.id,
                name = category.customName
                    ?: category.defaultKey?.let(defaultNameResolver::resolve)
                    ?: "",
                color = category.color,
                canMoveUp = index > 0,
                canMoveDown = index < lastIndex
            )
        }
}
