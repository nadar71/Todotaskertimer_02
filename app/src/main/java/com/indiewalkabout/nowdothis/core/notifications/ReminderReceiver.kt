package com.indiewalkabout.nowdothis.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.indiewalkabout.nowdothis.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notificationPublisher: NotificationPublisher

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.hasExtra(EXTRA_TASK_ID)) return
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, INVALID_TASK_ID)
        if (taskId <= INVALID_TASK_ID) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                notificationPublisher.publish(taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        private const val INVALID_TASK_ID = 0
    }
}
