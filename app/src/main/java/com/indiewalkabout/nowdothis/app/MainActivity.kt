package com.indiewalkabout.nowdothis.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.indiewalkabout.nowdothis.app.navigation.AppNavigation
import com.indiewalkabout.nowdothis.app.navigation.notificationTaskId
import com.indiewalkabout.nowdothis.core.designsystem.theme.ToDoComposeTheme
import com.indiewalkabout.nowdothis.core.notifications.ReminderReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val openTaskIds = Channel<Int>(Channel.BUFFERED)
    private val openTaskIdFlow = openTaskIds.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            ToDoComposeTheme {
                AppNavigation(openTaskIdFlow)
            }
        }
        if (savedInstanceState == null) routeNavigationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeNavigationIntent(intent)
    }

    private fun routeNavigationIntent(intent: Intent?) {
        intent ?: return
        notificationTaskId(
            action = intent.action,
            hasTaskId = intent.hasExtra(ReminderReceiver.EXTRA_TASK_ID),
            taskId = intent.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0)
        )?.let { taskId ->
            openTaskIds.trySend(taskId)
            intent.action = null
            intent.removeExtra(ReminderReceiver.EXTRA_TASK_ID)
        }
    }
}
