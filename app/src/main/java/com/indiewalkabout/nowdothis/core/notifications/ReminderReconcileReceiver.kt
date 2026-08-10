package com.indiewalkabout.nowdothis.core.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.indiewalkabout.nowdothis.core.di.ApplicationScope
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderReconcileReceiver : BroadcastReceiver() {
    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACCEPTED_ACTIONS) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                reminderScheduler.reconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val ACCEPTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
