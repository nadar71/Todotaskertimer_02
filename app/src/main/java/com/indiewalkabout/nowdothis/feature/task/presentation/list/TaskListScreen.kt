package com.indiewalkabout.nowdothis.feature.task.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    state: TaskListUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (TaskListEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TaskListTopBar(state.sort, onEvent)
                TaskSearchField(state.query) { onEvent(TaskListEvent.UpdateQuery(it)) }
                CategoryFilters(state, onEvent)
            }
        }
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(contentPadding))
            state.error != null -> ErrorState(
                modifier = Modifier.padding(contentPadding),
                onRetry = { onEvent(TaskListEvent.Retry) }
            )
            state.sections.isEmpty() -> EmptyState(Modifier.padding(contentPadding))
            else -> TaskSectionsList(state, contentPadding, onEvent)
        }
    }

    if (state.showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { onEvent(TaskListEvent.DismissDeleteAll) },
            title = { Text(stringResource(R.string.task_delete_all_title)) },
            text = { Text(stringResource(R.string.task_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(TaskListEvent.ConfirmDeleteAll) }) {
                    Text(stringResource(R.string.task_delete_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(TaskListEvent.DismissDeleteAll) }) {
                    Text(stringResource(R.string.task_delete_all_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListTopBar(sort: TaskSort, onEvent: (TaskListEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.tasks_title)) },
        actions = {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.task_more_actions)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_manage_categories)) },
                    onClick = {
                        expanded = false
                        onEvent(TaskListEvent.OpenCategoryManagement)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_open_history)) },
                    onClick = {
                        expanded = false
                        onEvent(TaskListEvent.OpenHistory)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_open_calendar)) },
                    onClick = {
                        expanded = false
                        onEvent(TaskListEvent.OpenCalendar)
                    }
                )
                TaskSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(option)) },
                        leadingIcon = {
                            if (sort == option) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            expanded = false
                            onEvent(TaskListEvent.SelectSort(option))
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_all_action)) },
                    onClick = {
                        expanded = false
                        onEvent(TaskListEvent.RequestDeleteAll)
                    }
                )
            }
        }
    )
}

@Composable
private fun TaskSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("task-search"),
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close_icon)
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun CategoryFilters(state: TaskListUiState, onEvent: (TaskListEvent) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = state.selectedCategoryId == null,
                onClick = { onEvent(TaskListEvent.SelectCategory(null)) },
                label = { Text(stringResource(R.string.task_category_all)) },
                modifier = Modifier.testTag("category-chip-all")
            )
        }
        items(state.categories, key = Category::id) { category ->
            FilterChip(
                selected = state.selectedCategoryId == category.id,
                onClick = { onEvent(TaskListEvent.SelectCategory(category.id)) },
                label = {
                    Text(
                        text = category.displayName(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("category-chip-${category.id}")
            )
        }
    }
}

@Composable
private fun TaskSectionsList(
    state: TaskListUiState,
    contentPadding: PaddingValues,
    onEvent: (TaskListEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.testTag("task-list"),
        contentPadding = contentPadding
    ) {
        section(
            title = { stringResource(R.string.task_section_overdue) },
            tasks = state.sections.overdue,
            state = state,
            onEvent = onEvent
        )
        section(
            title = { stringResource(R.string.task_section_today) },
            tasks = state.sections.today,
            state = state,
            onEvent = onEvent
        )
        section(
            title = { stringResource(R.string.task_section_upcoming) },
            tasks = state.sections.upcoming,
            state = state,
            onEvent = onEvent
        )
        section(
            title = { stringResource(R.string.task_section_unscheduled) },
            tasks = state.sections.unscheduled,
            state = state,
            onEvent = onEvent
        )
        section(
            title = { stringResource(R.string.task_section_completed_today) },
            tasks = state.sections.completedToday,
            state = state,
            onEvent = onEvent,
            showSeeAll = true
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: @Composable () -> String,
    tasks: List<Task>,
    state: TaskListUiState,
    onEvent: (TaskListEvent) -> Unit,
    showSeeAll: Boolean = false
) {
    if (tasks.isEmpty()) return
    item(key = "header-${tasks.first().id}-$showSeeAll") {
        TaskSectionHeader(
            title = title(),
            showSeeAll = showSeeAll,
            seeAllLabel = stringResource(R.string.task_see_all),
            onSeeAll = { onEvent(TaskListEvent.OpenHistory) }
        )
    }
    items(tasks, key = Task::id) { task ->
        val category = state.categories.firstOrNull { it.id == task.categoryId }
        TaskRow(
            task = task,
            categoryLabel = category?.displayName() ?: stringResource(R.string.task_uncategorized),
            categoryColor = category?.color.toComposeColor(),
            onOpen = { onEvent(TaskListEvent.OpenTaskEditor(task.id)) },
            onComplete = { onEvent(TaskListEvent.CompleteTask(task.id)) },
            onDelete = { onEvent(TaskListEvent.DeleteTask(task.id)) }
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.task_list_load_failed))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.task_list_retry))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.task_list_empty),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

private fun CategoryColor?.toComposeColor(): Color = when (this) {
    CategoryColor.BLUE -> Color(0xFF2F6FED)
    CategoryColor.GREEN -> Color(0xFF2E7D32)
    CategoryColor.PINK -> Color(0xFFC2185B)
    null -> Color(0xFF6B7280)
}

@Composable
private fun sortLabel(sort: TaskSort): String = when (sort) {
    TaskSort.DEFAULT -> stringResource(R.string.task_sort_default)
    TaskSort.LOW_FIRST -> stringResource(R.string.task_sort_low_first)
    TaskSort.HIGH_FIRST -> stringResource(R.string.task_sort_high_first)
}

private fun com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections.isEmpty(): Boolean =
    overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty() && unscheduled.isEmpty() &&
        completedToday.isEmpty()
