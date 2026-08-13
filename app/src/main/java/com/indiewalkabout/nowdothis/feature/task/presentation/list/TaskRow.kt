package com.indiewalkabout.nowdothis.feature.task.presentation.list

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority

@Composable
internal fun TaskRow(
    task: Task,
    categoryLabel: String,
    categoryColor: Color,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completionDescription = if (task.isCompleted) {
        stringResource(R.string.task_completed_description, task.title)
    } else {
        stringResource(R.string.task_complete_description, task.title)
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.35f }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.testTag("task-swipe-${task.id}"),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.task_delete_action),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpen)
                        .semantics { role = Role.Button }
                        .testTag("task-row-${task.id}")
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { if (!task.isCompleted) onComplete() },
                        enabled = !task.isCompleted,
                        modifier = Modifier
                            .testTag("task-complete-${task.id}")
                            .semantics { contentDescription = completionDescription }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(categoryColor, MaterialTheme.shapes.extraSmall)
                            )
                            Text(
                                text = categoryLabel,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            PriorityLabel(task.priority)
                        }
                        TaskMetadata(task)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun PriorityLabel(priority: TaskPriority) {
    val label = when (priority) {
        TaskPriority.HIGH -> stringResource(R.string.task_priority_high)
        TaskPriority.MEDIUM -> stringResource(R.string.task_priority_medium)
        TaskPriority.LOW -> stringResource(R.string.task_priority_low)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (priority == TaskPriority.HIGH) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TaskMetadata(task: Task) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        task.dueAt?.let { dueAt ->
            val formatted = DateUtils.formatDateTime(
                context,
                dueAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
            )
            Text(
                text = stringResource(R.string.task_due_label, formatted),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (task.reminderStatus != ReminderStatus.NONE) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.task_reminder_indicator),
                modifier = Modifier.size(15.dp)
            )
        }
        if (task.recurrence != RecurrenceType.NONE) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.task_recurrence_indicator),
                modifier = Modifier.size(15.dp)
            )
        }
        if (task.subtasks.isNotEmpty()) {
            val completed = task.subtasks.count { it.isCompleted }
            Text(
                text = pluralStringResource(
                    R.plurals.subtask_progress,
                    task.subtasks.size,
                    completed,
                    task.subtasks.size
                ),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
