package com.indiewalkabout.nowdothis.feature.history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import com.indiewalkabout.nowdothis.feature.history.domain.usecase.CompletionHistorySection
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale as JavaLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (HistoryEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("history-back")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.history_back)
                            )
                        }
                    }
                )
                HistorySearch(state.query, onEvent)
                HistoryCategoryFilters(state, onEvent)
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> HistoryLoadError(
                modifier = Modifier.padding(padding),
                onRetry = { onEvent(HistoryEvent.Retry) }
            )
            state.sections.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.history_empty))
            }
            else -> HistoryList(state, padding, onEvent)
        }
    }

    state.inspectedTask?.let { task ->
        HistoryInspectionSheet(
            task = task,
            category = state.categories.firstOrNull { it.id == task.categoryId },
            onClose = { onEvent(HistoryEvent.DismissInspection) },
            onDelete = { onEvent(HistoryEvent.RequestDelete(task.id)) }
        )
    }
    state.pendingDelete?.let { task ->
        HistoryDeleteDialog(task, state.isDeleting, onEvent)
    }
}

@Composable
private fun HistorySearch(query: String, onEvent: (HistoryEvent) -> Unit) {
    TextField(
        value = query,
        onValueChange = { onEvent(HistoryEvent.UpdateQuery(it)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("history-search"),
        placeholder = { Text(stringResource(R.string.history_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun HistoryCategoryFilters(state: HistoryUiState, onEvent: (HistoryEvent) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = state.selectedCategoryId == null,
                onClick = { onEvent(HistoryEvent.SelectCategory(null)) },
                label = { Text(stringResource(R.string.history_category_all)) },
                modifier = Modifier.testTag("history-category-all")
            )
        }
        items(state.categories, key = Category::id) { category ->
            FilterChip(
                selected = state.selectedCategoryId == category.id,
                onClick = { onEvent(HistoryEvent.SelectCategory(category.id)) },
                label = { Text(category.displayName(), maxLines = 1) },
                modifier = Modifier.testTag("history-category-${category.id}")
            )
        }
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    padding: PaddingValues,
    onEvent: (HistoryEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("history-list"),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp
        )
    ) {
        state.sections.forEach { section ->
            historySection(
                section = section,
                categories = state.categories,
                onEvent = onEvent
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historySection(
    section: CompletionHistorySection,
    categories: List<Category>,
    onEvent: (HistoryEvent) -> Unit
) {
    item(key = "date-${section.date}") {
        Text(
            text = section.date.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(historyJavaLocale())
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("history-date-${section.date}"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
    items(section.tasks, key = Task::id) { task ->
        val category = categories.firstOrNull { it.id == task.categoryId }
        HistoryTaskRow(task, category) { onEvent(HistoryEvent.Inspect(task.id)) }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun HistoryTaskRow(task: Task, category: Category?, onClick: () -> Unit) {
    val completedTime = task.completedAt?.let { formatDateTime(it) }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .testTag("history-task-${task.id}")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.history_completed),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f)) {
            Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = category?.displayName() ?: stringResource(R.string.task_uncategorized),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(completedTime, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryInspectionSheet(
    task: Task,
    category: Category?,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onClose, modifier = Modifier.testTag("history-inspection-close")) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_close))
                }
            }
            if (task.description.isNotBlank()) {
                Text(task.description, style = MaterialTheme.typography.bodyLarge)
            }
            HistoryDetail(
                label = stringResource(R.string.history_completed_at),
                value = task.completedAt?.let { formatDateTime(it) }.orEmpty()
            )
            HistoryDetail(
                label = stringResource(R.string.history_category),
                value = category?.displayName() ?: stringResource(R.string.task_uncategorized)
            )
            HistoryDetail(
                label = stringResource(R.string.history_priority),
                value = priorityLabel(task.priority)
            )
            if (task.subtasks.isNotEmpty()) {
                HistoryDetail(
                    label = stringResource(R.string.history_subtasks),
                    value = pluralStringResource(
                        R.plurals.subtask_progress,
                        task.subtasks.count { it.isCompleted },
                        task.subtasks.count { it.isCompleted },
                        task.subtasks.size
                    )
                )
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.End).testTag("history-inspection-delete")
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.history_delete_action))
            }
        }
    }
}

@Composable
private fun HistoryDetail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryDeleteDialog(task: Task, isDeleting: Boolean, onEvent: (HistoryEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onEvent(HistoryEvent.DismissDelete) },
        title = { Text(stringResource(R.string.history_delete_title, task.title)) },
        text = { Text(stringResource(R.string.history_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(HistoryEvent.ConfirmDelete) },
                enabled = !isDeleting
            ) {
                Text(stringResource(R.string.history_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(HistoryEvent.DismissDelete) },
                enabled = !isDeleting
            ) {
                Text(stringResource(R.string.history_cancel))
            }
        }
    )
}

@Composable
private fun HistoryLoadError(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.history_load_failed))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.history_retry))
        }
    }
}

@Composable
private fun Category.displayName(): String = customName ?: when (defaultKey) {
    DefaultCategoryKey.WORK -> stringResource(R.string.category_work)
    DefaultCategoryKey.PERSONAL -> stringResource(R.string.category_personal)
    DefaultCategoryKey.WISHLIST -> stringResource(R.string.category_wishlist)
    null -> stringResource(R.string.task_uncategorized)
}

@Composable
private fun priorityLabel(priority: TaskPriority): String = stringResource(
    when (priority) {
        TaskPriority.HIGH -> R.string.task_priority_high
        TaskPriority.MEDIUM -> R.string.task_priority_medium
        TaskPriority.LOW -> R.string.task_priority_low
    }
)

@Composable
private fun formatDateTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(historyJavaLocale()))

@Composable
private fun historyJavaLocale(): JavaLocale = JavaLocale.forLanguageTag(
    LocalLocale.current.toLanguageTag()
)
