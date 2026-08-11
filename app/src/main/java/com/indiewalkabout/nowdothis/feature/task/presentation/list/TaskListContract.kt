package com.indiewalkabout.nowdothis.feature.task.presentation.list

import androidx.annotation.StringRes
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort

data class TaskListUiState(
    val isLoading: Boolean = true,
    val error: TaskListError? = null,
    val sections: TaskSections = TaskSections(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val selectedCategoryId: Int? = null,
    val sort: TaskSort = TaskSort.DEFAULT,
    val showDeleteAllConfirmation: Boolean = false
)

enum class TaskListError {
    LOAD_FAILED
}

sealed interface TaskListEvent {
    data class UpdateQuery(val query: String) : TaskListEvent
    data class SelectCategory(val categoryId: Int?) : TaskListEvent
    data class SelectSort(val sort: TaskSort) : TaskListEvent
    data class CompleteTask(val taskId: Int) : TaskListEvent
    data class DeleteTask(val taskId: Int) : TaskListEvent
    data object UndoDelete : TaskListEvent
    data object RequestDeleteAll : TaskListEvent
    data object DismissDeleteAll : TaskListEvent
    data object ConfirmDeleteAll : TaskListEvent
    data object Retry : TaskListEvent
    data class OpenTaskEditor(val taskId: Int?) : TaskListEvent
    data object OpenCategoryManagement : TaskListEvent
    data object OpenCalendar : TaskListEvent
    data object OpenHistory : TaskListEvent
}

sealed interface TaskListEffect {
    data class ShowUndoDelete(
        @param:StringRes val messageRes: Int,
        val taskTitle: String
    ) : TaskListEffect

    data class ShowMessage(@param:StringRes val messageRes: Int) : TaskListEffect
    data class OpenTaskEditor(val taskId: Int?) : TaskListEffect
    data object OpenCategoryManagement : TaskListEffect
    data object OpenCalendar : TaskListEffect
    data object OpenHistory : TaskListEffect
}
