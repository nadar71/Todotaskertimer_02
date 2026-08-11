package com.indiewalkabout.nowdothis.feature.category.presentation

import androidx.annotation.StringRes
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor

data class CategoryUiState(
    val isLoading: Boolean = true,
    val error: CategoryScreenError? = null,
    val categories: List<CategoryItem> = emptyList(),
    val editor: CategoryEditorState? = null,
    val pendingDelete: CategoryItem? = null,
    val isDeleting: Boolean = false
)

data class CategoryItem(
    val id: Int,
    val name: String,
    val color: CategoryColor,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

data class CategoryEditorState(
    val categoryId: Int? = null,
    val name: String = "",
    val selectedColor: CategoryColor = CategoryColor.BLUE,
    val nameError: CategoryNameError? = null,
    val isSaving: Boolean = false,
    internal val originalName: String? = null,
    internal val originalColor: CategoryColor? = null
)

enum class CategoryNameError {
    BLANK,
    DUPLICATE
}

enum class CategoryScreenError {
    LOAD_FAILED
}

sealed interface CategoryEvent {
    data object Add : CategoryEvent
    data class Edit(val categoryId: Int) : CategoryEvent
    data class ChangeName(val name: String) : CategoryEvent
    data class SelectColor(val color: CategoryColor) : CategoryEvent
    data object ConfirmEditor : CategoryEvent
    data object DismissEditor : CategoryEvent
    data class MoveUp(val categoryId: Int) : CategoryEvent
    data class MoveDown(val categoryId: Int) : CategoryEvent
    data class RequestDelete(val categoryId: Int) : CategoryEvent
    data object ConfirmDelete : CategoryEvent
    data object DismissDelete : CategoryEvent
    data object Retry : CategoryEvent
}

sealed interface CategoryEffect {
    data class ShowMessage(@param:StringRes val messageRes: Int) : CategoryEffect
}
