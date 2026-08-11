package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R

@Composable
internal fun SubtaskEditor(
    subtasks: List<TaskEditorSubtask>,
    onEvent: (TaskEditorEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(subtasks, key = { _, item -> item.draftId }) { index, subtask ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = subtask.isCompleted,
                        onCheckedChange = {
                            onEvent(TaskEditorEvent.ToggleSubtask(subtask.draftId))
                        },
                        modifier = Modifier.testTag("subtask-toggle-${subtask.draftId}")
                    )
                    OutlinedTextField(
                        value = subtask.title,
                        onValueChange = {
                            onEvent(TaskEditorEvent.RenameSubtask(subtask.draftId, it))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subtask-title-${subtask.draftId}"),
                        placeholder = { Text(stringResource(R.string.task_editor_subtask_hint)) },
                        singleLine = true
                    )
                    IconButton(
                        onClick = { onEvent(TaskEditorEvent.MoveSubtask(subtask.draftId, -1)) },
                        enabled = index > 0,
                        modifier = Modifier.testTag("subtask-up-${subtask.draftId}")
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.task_editor_subtask_up)
                        )
                    }
                    IconButton(
                        onClick = { onEvent(TaskEditorEvent.MoveSubtask(subtask.draftId, 1)) },
                        enabled = index < subtasks.lastIndex,
                        modifier = Modifier.testTag("subtask-down-${subtask.draftId}")
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.task_editor_subtask_down)
                        )
                    }
                    IconButton(
                        onClick = { onEvent(TaskEditorEvent.DeleteSubtask(subtask.draftId)) },
                        modifier = Modifier.testTag("subtask-delete-${subtask.draftId}")
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.task_editor_subtask_delete)
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = { onEvent(TaskEditorEvent.AddSubtask) },
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("subtask-add")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.task_editor_subtask_add),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
