package com.indiewalkabout.nowdothis.feature.task.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.indiewalkabout.nowdothis.feature.task.navigation.Screen
import com.indiewalkabout.nowdothis.feature.task.presentation.detail.TaskScreen
import com.indiewalkabout.nowdothis.feature.task.presentation.SharedViewModel
import com.indiewalkabout.nowdothis.feature.task.domain.model.Action

@ExperimentalAnimationApi
@Composable
fun taskComposable(
    screen: Screen.Task,
    sharedViewModel: SharedViewModel,
    navigateToListScreen: (Action) -> Unit
) {
    val taskId = screen.id
    LaunchedEffect(key1 = taskId) {
        if (taskId == -1) {
            sharedViewModel.clearSelectedTask()
        } else {
            sharedViewModel.getSelectedTask(taskId = taskId)
        }
    }

    val selectedTask by sharedViewModel.selectedTask.collectAsState()
    val taskScreenSelectedTask = if (taskId == -1) null else selectedTask

    LaunchedEffect(key1 = selectedTask) {
        if (taskScreenSelectedTask != null) {
            sharedViewModel.updateTaskFields(selectedTask = selectedTask)
        }
    }

    TaskScreen(
        selectedTask = taskScreenSelectedTask,
        sharedViewModel = sharedViewModel,
        navigateToListScreen = navigateToListScreen
    )
}
