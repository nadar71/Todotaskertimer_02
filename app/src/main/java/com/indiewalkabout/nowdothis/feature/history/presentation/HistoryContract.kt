package com.indiewalkabout.nowdothis.feature.history.presentation

import androidx.annotation.StringRes
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.history.domain.usecase.CompletionHistorySection
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task

data class HistoryUiState(
    val isLoading: Boolean = true,
    val error: HistoryError? = null,
    val sections: List<CompletionHistorySection> = emptyList(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val selectedCategoryId: Int? = null,
    val inspectedTask: Task? = null,
    val pendingDelete: Task? = null,
    val isDeleting: Boolean = false
)

enum class HistoryError {
    LOAD_FAILED
}

sealed interface HistoryEvent {
    data class UpdateQuery(val query: String) : HistoryEvent
    data class SelectCategory(val categoryId: Int?) : HistoryEvent
    data class Inspect(val taskId: Int) : HistoryEvent
    data object DismissInspection : HistoryEvent
    data class RequestDelete(val taskId: Int) : HistoryEvent
    data object ConfirmDelete : HistoryEvent
    data object DismissDelete : HistoryEvent
    data object Retry : HistoryEvent
}

sealed interface HistoryEffect {
    data class ShowMessage(@param:StringRes val messageRes: Int) : HistoryEffect
}
