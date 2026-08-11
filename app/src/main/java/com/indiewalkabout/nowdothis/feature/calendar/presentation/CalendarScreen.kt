package com.indiewalkabout.nowdothis.feature.calendar.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.calendar_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(CalendarEvent.AddTask) },
                modifier = Modifier.testTag("calendar-add")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.calendar_add_task))
            }
        }
    ) { padding ->
        if (state.error != null) {
            CalendarLoadError(
                modifier = Modifier.padding(padding),
                onRetry = { onEvent(CalendarEvent.Retry) }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                MonthHeader(state, onEvent)
                MonthGrid(
                    visibleMonth = state.visibleMonth,
                    selectedDate = state.selectedDate,
                    today = state.today,
                    taskCounts = state.taskCounts,
                    onSelectDate = { onEvent(CalendarEvent.SelectDate(it)) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Text(
                    text = state.selectedDate.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale())
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.selectedTasks.isEmpty() -> Text(
                            text = stringResource(R.string.calendar_no_tasks),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                        else -> SelectedTaskList(state.selectedTasks, onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(state: CalendarUiState, onEvent: (CalendarEvent) -> Unit) {
    val monthLabel = state.visibleMonth.format(
        DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale())
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onEvent(CalendarEvent.PreviousMonth) },
            modifier = Modifier.testTag("calendar-prev")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_previous_month)
            )
        }
        Text(
            text = monthLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(
            onClick = { onEvent(CalendarEvent.Today) },
            modifier = Modifier.testTag("calendar-today")
        ) {
            Text(stringResource(R.string.calendar_today))
        }
        IconButton(
            onClick = { onEvent(CalendarEvent.NextMonth) },
            modifier = Modifier.testTag("calendar-next")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month)
            )
        }
    }
}

@Composable
private fun SelectedTaskList(tasks: List<Task>, onEvent: (CalendarEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        items(tasks, key = Task::id) { task ->
            CalendarTaskRow(task) { onEvent(CalendarEvent.OpenTask(task.id)) }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun CalendarTaskRow(task: Task, onClick: () -> Unit) {
    val time = task.dueAt?.let { dueAt ->
        Instant.ofEpochMilli(dueAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()))
    }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("calendar-task-${task.id}")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (task.isCompleted) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.calendar_task_completed),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = TextDecoration.LineThrough.takeIf { task.isCompleted }
            )
            Text(
                text = stringResource(
                    when (task.priority) {
                        com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority.HIGH ->
                            R.string.task_priority_high
                        com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority.MEDIUM ->
                            R.string.task_priority_medium
                        com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority.LOW ->
                            R.string.task_priority_low
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(time, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CalendarLoadError(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.calendar_load_failed))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.calendar_retry))
        }
    }
}
