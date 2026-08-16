package com.indiewalkabout.nowdothis.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.indiewalkabout.nowdothis.app.navigation.AppNavigation
import com.indiewalkabout.nowdothis.app.navigation.TaskEditorRequest
import com.indiewalkabout.nowdothis.app.navigation.notificationTaskId
import com.indiewalkabout.nowdothis.core.designsystem.theme.ToDoComposeTheme
import com.indiewalkabout.nowdothis.core.notifications.NotificationPublisher
import com.indiewalkabout.nowdothis.core.notifications.ReminderReceiver
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetIntents
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val taskEditorRequests = Channel<TaskEditorRequest>(Channel.BUFFERED)
    private val taskEditorRequestFlow = taskEditorRequests.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            ToDoComposeTheme {
                AppNavigation(taskEditorRequestFlow)
            }
        }
        if (savedInstanceState == null) {
            routeNavigationIntent(intent, deferConsumption = true)
        } else {
            clearNavigationIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeNavigationIntent(intent)
    }

    private fun routeNavigationIntent(intent: Intent?, deferConsumption: Boolean = false) {
        navigationRequest(intent)?.let { request ->
            taskEditorRequests.trySend(request)
            if (deferConsumption) {
                // Route through the buffered channel now, then clear after initial lifecycle dispatch.
                window.decorView.post { clearNavigationIntent(intent) }
            } else {
                clearNavigationIntent(intent)
            }
        }
    }
}

fun consumeNavigationIntent(intent: Intent?): TaskEditorRequest? {
    return navigationRequest(intent)?.also { clearNavigationIntent(intent) }
}

private fun navigationRequest(intent: Intent?): TaskEditorRequest? =
    QuickCaptureWidgetIntents.parse(intent)
        ?: notificationTaskId(
            action = intent?.action,
            hasTaskId = intent?.hasExtra(ReminderReceiver.EXTRA_TASK_ID) == true,
            taskId = intent?.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0) ?: 0
        )?.let(TaskEditorRequest::Open)

private fun clearNavigationIntent(intent: Intent?) {
    intent?.action = null
    intent?.data = null
    intent?.removeExtra(QuickCaptureWidgetIntents.EXTRA_TASK_ID)
    intent?.removeExtra(ReminderReceiver.EXTRA_TASK_ID)
}
