package com.indiewalkabout.nowdothis.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReconcileReceiver : BroadcastReceiver() {
    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var workLauncher: ReminderWorkLauncher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACCEPTED_ACTIONS) return

        val pendingResult = goAsync()
        workLauncher.launch(onFinished = pendingResult::finish) {
            reminderScheduler.reconcile()
        }
    }

    private companion object {
        val ACCEPTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
