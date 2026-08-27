package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    state: TaskEditorUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (TaskEditorEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.taskId == null) {
                                R.string.task_editor_create_title
                            } else {
                                R.string.task_editor_edit_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("task-editor-back")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.task_editor_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onEvent(TaskEditorEvent.Save) },
                        enabled = !state.isLoading && !state.isSaving,
                        modifier = Modifier.testTag("task-editor-save")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(
                            text = stringResource(R.string.task_editor_save),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            EditorForm(state, contentPadding, onEvent)
        }
    }
}

@Composable
private fun EditorForm(
    state: TaskEditorUiState,
    contentPadding: PaddingValues,
    onEvent: (TaskEditorEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("task-editor-form"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.taskId == null) {
            item {
                QuickEntrySection(
                    input = state.quickEntryInput,
                    summary = state.quickEntrySummary,
                    issues = state.quickEntryIssues,
                    onEvent = onEvent,
                    categoryReadiness = state.categoryReadiness,
                    enabled = !state.isSaving
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.title,
                onValueChange = { onEvent(TaskEditorEvent.UpdateTitle(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task-title"),
                label = { Text(stringResource(R.string.task_editor_title_label)) },
                isError = state.errors.title != null,
                supportingText = state.errors.title?.let {
                    { Text(stringResource(R.string.task_editor_error_title_required)) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
        item {
            OutlinedTextField(
                value = state.description,
                onValueChange = { onEvent(TaskEditorEvent.UpdateDescription(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task-description"),
                label = { Text(stringResource(R.string.task_editor_description_label)) },
                isError = state.errors.description != null,
                supportingText = state.errors.description?.let {
                    { Text(stringResource(R.string.task_editor_error_description_required)) }
                },
                minLines = 3
            )
        }
        item {
            EditorSectionLabel(stringResource(R.string.task_editor_priority_label))
            PriorityControl(state.priority, onEvent)
        }
        item {
            CategoryControl(state.categoryId, state.categories, onEvent)
        }
        item {
            DateTimeControl(
                label = stringResource(R.string.task_editor_due_label),
                value = state.dueAt,
                sectionTag = "task-due-section",
                clearTag = "task-due-clear",
                onValueChange = { onEvent(TaskEditorEvent.UpdateDueAt(it)) }
            )
        }
        item {
            DateTimeControl(
                label = stringResource(R.string.task_editor_reminder_label),
                value = state.reminderAt,
                sectionTag = "task-reminder-section",
                clearTag = "task-reminder-clear",
                error = reminderError(state.errors.reminder),
                onValueChange = { onEvent(TaskEditorEvent.UpdateReminderAt(it)) }
            )
            if (state.reminderStatus == ReminderStatus.UNAVAILABLE) {
                val reminderUnavailable = stringResource(R.string.task_editor_reminder_not_active)
                Text(
                    text = reminderUnavailable,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("task-reminder-status")
                        .semantics {
                            stateDescription = reminderUnavailable
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.notificationPermissionDenied) {
                ReminderNotice(stringResource(R.string.task_editor_notification_denied))
            }
            if (state.exactTimingUnavailable) {
                ReminderNotice(stringResource(R.string.task_editor_inexact_notice))
            }
        }
        item {
            RecurrenceEditor(
                state = state.recurrence,
                recurrenceError = state.errors.recurrence,
                recurrenceEndError = state.errors.recurrenceEnd,
                onEvent = onEvent,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            EditorSectionLabel(stringResource(R.string.task_editor_subtasks_label))
            SubtaskEditor(subtasks = state.subtasks, onEvent = onEvent)
        }
    }
}

@Composable
private fun ReminderNotice(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun PriorityControl(
    selected: TaskPriority,
    onEvent: (TaskEditorEvent) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TaskPriority.entries.forEachIndexed { index, priority ->
            SegmentedButton(
                selected = selected == priority,
                onClick = { onEvent(TaskEditorEvent.SelectPriority(priority)) },
                shape = SegmentedButtonDefaults.itemShape(index, TaskPriority.entries.size),
                modifier = Modifier
                    .weight(1f)
                    .testTag("task-priority-${priority.name.lowercase()}")
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected == priority
                    }
            ) {
                Text(priorityLabel(priority))
            }
        }
    }
}

@Composable
private fun CategoryControl(
    selectedId: Int?,
    categories: List<Category>,
    onEvent: (TaskEditorEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        EditorSectionLabel(stringResource(R.string.task_editor_category_label))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("task-category-field")
        ) {
            Text(
                categories.firstOrNull { it.id == selectedId }?.displayName()
                    ?: stringResource(R.string.task_uncategorized)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_uncategorized)) },
                onClick = {
                    expanded = false
                    onEvent(TaskEditorEvent.SelectCategory(null))
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName()) },
                    onClick = {
                        expanded = false
                        onEvent(TaskEditorEvent.SelectCategory(category.id))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeControl(
    label: String,
    value: Long?,
    sectionTag: String,
    clearTag: String,
    onValueChange: (Long?) -> Unit,
    error: String? = null
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val context = LocalContext.current
    Column(modifier = Modifier.testTag(sectionTag)) {
        EditorSectionLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Text(
                    text = value?.let {
                        DateUtils.formatDateTime(
                            context,
                            it,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
                        )
                    } ?: stringResource(R.string.task_editor_not_set),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (value != null) {
                IconButton(
                    onClick = { onValueChange(null) },
                    modifier = Modifier.testTag(clearTag)
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.task_editor_clear_value, label)
                    )
                }
            }
        }
        error?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = value?.toDatePickerMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = dateState.selectedDateMillis?.utcDate()
                    showDatePicker = false
                }) { Text(stringResource(R.string.task_editor_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.task_editor_cancel))
                }
            }
        ) { DatePicker(state = dateState) }
    }
    selectedDate?.let { date ->
        TimePickerDialog(
            initialValue = value,
            onDismiss = { selectedDate = null },
            onConfirm = { time ->
                onValueChange(
                    date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
                selectedDate = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialValue: Long?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val initial = initialValue?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
    } ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initial.hour, initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_editor_select_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.task_editor_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.task_editor_cancel))
            }
        }
    )
}

private fun Long.utcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun Long.toDatePickerMillis(): Long =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

@Composable
private fun reminderError(error: TaskEditorFieldError?): String? = when (error) {
    TaskEditorFieldError.REMINDER_AFTER_DUE -> {
        stringResource(R.string.task_editor_error_reminder_after_due)
    }
    TaskEditorFieldError.REMINDER_IN_PAST -> {
        stringResource(R.string.task_editor_error_reminder_in_past)
    }
    else -> null
}

@Composable
private fun priorityLabel(priority: TaskPriority): String = when (priority) {
    TaskPriority.LOW -> stringResource(R.string.task_priority_low)
    TaskPriority.MEDIUM -> stringResource(R.string.task_priority_medium)
    TaskPriority.HIGH -> stringResource(R.string.task_priority_high)
}

@Composable
private fun Category.displayName(): String = customName ?: when (defaultKey) {
    DefaultCategoryKey.WORK -> stringResource(R.string.category_work)
    DefaultCategoryKey.PERSONAL -> stringResource(R.string.category_personal)
    DefaultCategoryKey.WISHLIST -> stringResource(R.string.category_wishlist)
    null -> stringResource(R.string.task_uncategorized)
}
