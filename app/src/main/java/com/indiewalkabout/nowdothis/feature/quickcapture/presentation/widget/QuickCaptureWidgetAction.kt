package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.indiewalkabout.nowdothis.feature.quickcapture.di.QuickCaptureWidgetEntryPoint

class QuickCaptureCompleteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runCompleteQuickCaptureAction(
            entryPoint = quickCaptureWidgetEntryPoint(context),
            parameters = parameters
        )
    }
}

class QuickCaptureRetryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runRetryQuickCaptureAction(quickCaptureWidgetEntryPoint(context))
    }
}

internal suspend fun runCompleteQuickCaptureAction(
    entryPoint: QuickCaptureWidgetEntryPoint,
    parameters: ActionParameters
) {
    val taskId = parameters[QuickCaptureWidgetActionParameters.taskId]
        ?.takeIf { it > 0 }
        ?: return
    entryPoint.completeQuickCaptureTask()(taskId)
}

internal suspend fun runRetryQuickCaptureAction(entryPoint: QuickCaptureWidgetEntryPoint) {
    entryPoint.quickCaptureWidgetUpdater().updateAll()
}
