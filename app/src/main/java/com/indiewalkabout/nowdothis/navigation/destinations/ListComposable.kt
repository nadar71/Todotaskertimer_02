package com.indiewalkabout.nowdothis.navigation.destinations

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.indiewalkabout.nowdothis.navigation.Screen
import com.indiewalkabout.nowdothis.ui.screens.list.ListScreen
import com.indiewalkabout.nowdothis.ui.viewmodels.SharedViewModel
import com.indiewalkabout.nowdothis.util.Action

@ExperimentalAnimationApi
@Composable
fun listComposable(
    screen: Screen.List,
    navigateToTaskScreen: (taskId: Int) -> Unit,
    sharedViewModel: SharedViewModel
) {
    val action = screen.action
    var myAction by rememberSaveable { mutableStateOf(Action.NO_ACTION) }

    LaunchedEffect(key1 = myAction) {
        if (action != myAction) {
            myAction = action
            sharedViewModel.updateAction(newAction = action)
        }
    }

    val databaseAction = sharedViewModel.action

    ListScreen(
        action = databaseAction,
        navigateToTaskScreen = navigateToTaskScreen,
        sharedViewModel = sharedViewModel
    )
}
