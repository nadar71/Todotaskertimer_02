package com.indiewalkabout.nowdothis.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAlarmGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager
) : AlarmGateway {
    override val canScheduleExact: Boolean
        get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            platformCall { alarmManager.canScheduleExactAlarms() }
        }

    override fun setExact(taskId: Int, triggerAt: Long): Boolean = platformCall {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            reminderOperation(taskId)
        )
        true
    }

    override fun setInexact(taskId: Int, triggerAt: Long): Boolean = platformCall {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            reminderOperation(taskId)
        )
        true
    }

    override fun cancel(taskId: Int) {
        val operation = reminderOperation(taskId)
        try {
            alarmManager.cancel(operation)
        } catch (_: RuntimeException) {
            // A missing or unavailable platform alarm is already effectively cancelled.
        } finally {
            try {
                operation.cancel()
            } catch (_: RuntimeException) {
                // PendingIntent cancellation is best-effort at this process boundary.
            }
        }
    }

    private fun reminderOperation(taskId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId,
        Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
        PENDING_INTENT_FLAGS
    )

    private inline fun platformCall(block: () -> Boolean): Boolean = try {
        block()
    } catch (_: RuntimeException) {
        false
    }

    private companion object {
        const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
