package com.indiewalkabout.nowdothis.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val taskRepository: TaskRepository
) {
    suspend fun publish(taskId: Int): Boolean {
        if (!canPostNotifications()) return false

        val content = ReminderNotificationContent.from(taskRepository.getTask(taskId))
            ?: return false
        val taskTitle = when (content) {
            ReminderNotificationContent.Fallback ->
                context.getString(R.string.reminder_notification_task_fallback)
            is ReminderNotificationContent.TaskTitle -> content.value
        }

        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_light)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(
                context.getString(R.string.reminder_notification_body, taskTitle)
            )
            .setContentIntent(contentIntent(taskId))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return try {
            notificationManager.notify(taskId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun contentIntent(taskId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        taskId,
        Intent()
            .setClassName(context, MAIN_ACTIVITY_CLASS_NAME)
            .setAction(OPEN_TASK_ACTION)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val OPEN_TASK_ACTION = "com.indiewalkabout.nowdothis.OPEN_TASK"
        private const val MAIN_ACTIVITY_CLASS_NAME =
            "com.indiewalkabout.nowdothis.app.MainActivity"
    }
}
