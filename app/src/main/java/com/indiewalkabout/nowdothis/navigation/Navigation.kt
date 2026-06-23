package com.indiewalkabout.nowdothis.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.indiewalkabout.nowdothis.navigation.destinations.listComposable
import com.indiewalkabout.nowdothis.navigation.destinations.taskComposable
import com.indiewalkabout.nowdothis.ui.viewmodels.SharedViewModel

@ExperimentalAnimationApi
@Composable
fun SetupNavigation(
    sharedViewModel: SharedViewModel
) {
    val backStack = rememberNavBackStack(Screen.List())

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Screen.List> { key ->
                listComposable(
                    screen = key,
                    navigateToTaskScreen = { taskId ->
                        backStack.add(Screen.Task(id = taskId))
                    },
                    sharedViewModel = sharedViewModel
                )
            }

            entry<Screen.Task> { key ->
                taskComposable(
                    screen = key,
                    navigateToListScreen = { action ->
                        backStack.clear()
                        backStack.add(Screen.List(action = action))
                    },
                    sharedViewModel = sharedViewModel
                )
            }
        }
    )
}
