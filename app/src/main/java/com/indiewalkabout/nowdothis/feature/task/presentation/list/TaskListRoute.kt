package com.indiewalkabout.nowdothis.feature.task.presentation.list

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indiewalkabout.nowdothis.R

@Composable
fun TaskListRoute(
    onOpenTaskEditor: (Int?) -> Unit,
    onOpenCategoryManagement: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.task_undo)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TaskListEffect.ShowUndoDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(effect.messageRes, effect.taskTitle),
                        actionLabel = undoLabel
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(TaskListEvent.UndoDelete)
                    }
                }
                is TaskListEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
                is TaskListEffect.OpenTaskEditor -> onOpenTaskEditor(effect.taskId)
                TaskListEffect.OpenCategoryManagement -> onOpenCategoryManagement()
                TaskListEffect.OpenCalendar -> onOpenCalendar()
                TaskListEffect.OpenHistory -> onOpenHistory()
            }
        }
    }

    TaskListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent
    )
}
