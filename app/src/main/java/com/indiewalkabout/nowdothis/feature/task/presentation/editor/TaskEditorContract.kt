package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import androidx.annotation.StringRes
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import kotlinx.serialization.Serializable

data class TaskEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val taskId: Int? = null,
    val title: String = "",
    val description: String = "",
    val priority: TaskPriority = TaskPriority.LOW,
    val categoryId: Int? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val reminderStatus: ReminderStatus = ReminderStatus.NONE,
    val notificationPermissionDenied: Boolean = false,
    val exactTimingUnavailable: Boolean = false,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val recurrenceEndAt: Long? = null,
    val subtasks: List<TaskEditorSubtask> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryReadiness: CategoryReadiness = CategoryReadiness.LOADING,
    val quickEntryInput: String = "",
    val quickEntrySummary: List<QuickEntrySummaryField> = emptyList(),
    val quickEntryIssues: List<QuickEntryIssue> = emptyList(),
    val errors: TaskEditorErrors = TaskEditorErrors()
)

enum class CategoryReadiness {
    LOADING,
    READY,
    ERROR
}

@Serializable
enum class QuickEntrySummaryField {
    TITLE,
    DUE_DATE,
    REMINDER,
    PRIORITY,
    CATEGORY,
    RECURRENCE
}

@Serializable
enum class QuickEntryIssue {
    EMPTY_INPUT,
    UNKNOWN_CATEGORY,
    AMBIGUOUS_CATEGORY,
    DUPLICATE_FIELD,
    RELATIVE_REMINDER_WITHOUT_DUE_DATE,
    PARSE_FAILED
}

@Serializable
data class TaskEditorSubtask(
    val draftId: Long,
    val persistedId: Int = 0,
    val title: String = "",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

data class TaskEditorErrors(
    val title: TaskEditorFieldError? = null,
    val description: TaskEditorFieldError? = null,
    val reminder: TaskEditorFieldError? = null,
    val recurrence: TaskEditorFieldError? = null,
    val recurrenceEnd: TaskEditorFieldError? = null
)

enum class TaskEditorFieldError {
    REQUIRED,
    REMINDER_AFTER_DUE,
    REMINDER_IN_PAST,
    DUE_REQUIRED,
    END_BEFORE_DUE
}

sealed interface TaskEditorEvent {
    data class UpdateQuickEntry(val value: String) : TaskEditorEvent
    data object ParseQuickEntry : TaskEditorEvent
    data object RetryCategoryLoad : TaskEditorEvent
    data class UpdateTitle(val value: String) : TaskEditorEvent
    data class UpdateDescription(val value: String) : TaskEditorEvent
    data class SelectPriority(val value: TaskPriority) : TaskEditorEvent
    data class SelectCategory(val categoryId: Int?) : TaskEditorEvent
    data class UpdateDueAt(val value: Long?) : TaskEditorEvent
    data class UpdateReminderAt(val value: Long?) : TaskEditorEvent
    data class NotificationPermissionResult(val granted: Boolean) : TaskEditorEvent
    data object RefreshExactAlarmAccess : TaskEditorEvent
    data class SelectRecurrence(val value: RecurrenceType) : TaskEditorEvent
    data class UpdateRecurrenceEndAt(val value: Long?) : TaskEditorEvent
    data object AddSubtask : TaskEditorEvent
    data class RenameSubtask(val draftId: Long, val value: String) : TaskEditorEvent
    data class ToggleSubtask(val draftId: Long) : TaskEditorEvent
    data class MoveSubtask(val draftId: Long, val offset: Int) : TaskEditorEvent
    data class DeleteSubtask(val draftId: Long) : TaskEditorEvent
    data object Save : TaskEditorEvent
}

sealed interface TaskEditorEffect {
    data class ShowMessage(@param:StringRes val messageRes: Int) : TaskEditorEffect
    data object RequestNotificationPermission : TaskEditorEffect
    data object RequestExactAlarmAccess : TaskEditorEffect
    data object NavigateBack : TaskEditorEffect
}
