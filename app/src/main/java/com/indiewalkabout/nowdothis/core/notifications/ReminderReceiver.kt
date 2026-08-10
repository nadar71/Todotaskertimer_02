package com.indiewalkabout.nowdothis.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notificationPublisher: NotificationPublisher

    @Inject
    lateinit var workLauncher: ReminderWorkLauncher

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.hasExtra(EXTRA_TASK_ID)) return
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, INVALID_TASK_ID)
        if (taskId <= INVALID_TASK_ID) return

        val pendingResult = goAsync()
        workLauncher.launch(onFinished = pendingResult::finish) {
            notificationPublisher.publish(taskId)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        private const val INVALID_TASK_ID = 0
    }
}
