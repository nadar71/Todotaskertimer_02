package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.indiewalkabout.nowdothis.app.MainActivity
import com.indiewalkabout.nowdothis.app.navigation.TaskEditorRequest

object QuickCaptureWidgetIntents {
    const val ACTION_ADD = "com.indiewalkabout.nowdothis.action.QUICK_CAPTURE_ADD"
    const val ACTION_OPEN = "com.indiewalkabout.nowdothis.action.QUICK_CAPTURE_OPEN"
    const val EXTRA_TASK_ID = "quick_capture_task_id"

    fun add(context: Context): Intent = editorIntent(context, ACTION_ADD)
        .setData(Uri.parse("nowdothis://quick-capture/add"))

    fun open(context: Context, taskId: Int): Intent {
        require(taskId > 0)
        return editorIntent(context, ACTION_OPEN)
            .setData(Uri.parse("nowdothis://quick-capture/task/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId)
    }

    fun parse(intent: Intent?): TaskEditorRequest? = when (intent?.action) {
        ACTION_ADD -> TaskEditorRequest.Add
        ACTION_OPEN -> intent.getIntExtra(EXTRA_TASK_ID, 0)
            .takeIf { it > 0 }
            ?.let(TaskEditorRequest::Open)
        else -> null
    }

    private fun editorIntent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
