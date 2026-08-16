package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.quickcapture.di.QuickCaptureWidgetEntryPoint
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class QuickCaptureWidget : GlanceAppWidget(
    errorUiLayout = R.layout.quick_capture_widget_error
) {
    override val sizeMode = QuickCaptureWidgetSizeMode

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = quickCaptureWidgetEntryPoint(context)
        val states = observeQuickCaptureWidgetState(
            loadTasks = entryPoint.loadQuickCaptureTasks(),
            capacity = widgetCapacity(context, id)
        )
        val inFlightTaskIds = entryPoint.completeQuickCaptureTask().inFlightTaskIds

        provideContent {
            val state by states.collectAsState(QuickCaptureWidgetState.Loading)
            val inFlight by inFlightTaskIds.collectAsState()
            val renderedState = if (state is QuickCaptureWidgetState.Content) {
                (state as QuickCaptureWidgetState.Content).copy(inFlightTaskIds = inFlight)
            } else {
                state
            }
            QuickCaptureWidgetContent(renderedState)
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        val errorViews = RemoteViews(context.packageName, R.layout.quick_capture_widget_error).apply {
            setOnClickPendingIntent(
                R.id.quick_capture_widget_error_retry,
                quickCaptureRetryPendingIntent(context, appWidgetId)
            )
        }
        AppWidgetManager.getInstance(context).updateAppWidget(
            appWidgetId,
            errorViews
        )
    }
}

internal fun quickCaptureRetryPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
    val intent = Intent(context, QuickCaptureWidgetReceiver::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        data = Uri.parse("nowdothis://quick-capture/widget/$appWidgetId/retry")
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
    }
    return PendingIntent.getBroadcast(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

internal suspend fun loadQuickCaptureWidgetState(
    loadTasks: LoadQuickCaptureTasks,
    capacity: Int,
    inFlightTaskIds: Set<Int>
): QuickCaptureWidgetState = try {
    val snapshot = loadTasks(capacity)
    if (snapshot.tasks.isEmpty()) {
        QuickCaptureWidgetState.Empty
    } else {
        QuickCaptureWidgetState.Content(snapshot, inFlightTaskIds)
    }
} catch (exception: CancellationException) {
    throw exception
} catch (_: Exception) {
    QuickCaptureWidgetState.Unavailable
}

internal fun observeQuickCaptureWidgetState(
    loadTasks: LoadQuickCaptureTasks,
    capacity: Int
) = loadTasks.observe(capacity)
    .map { snapshot ->
        if (snapshot.tasks.isEmpty()) {
            QuickCaptureWidgetState.Empty
        } else {
            QuickCaptureWidgetState.Content(snapshot)
        }
    }
    .catch { exception ->
        if (exception is CancellationException) throw exception
        emit(QuickCaptureWidgetState.Unavailable)
    }

internal fun quickCaptureWidgetEntryPoint(context: Context): QuickCaptureWidgetEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        QuickCaptureWidgetEntryPoint::class.java
    )

private suspend fun widgetCapacity(context: Context, glanceId: GlanceId): Int {
    val maxHeight = GlanceAppWidgetManager(context)
        .getAppWidgetSizes(glanceId)
        .maxOfOrNull { it.height.value.toInt() }
        ?: CompactWidgetSize.height.value.toInt()
    return quickCaptureCapacityForHeight(maxHeight)
}

internal fun quickCaptureCapacityForHeight(heightDp: Int): Int {
    return when {
        heightDp >= ExpandedWidgetSize.height.value -> EXPANDED_CAPACITY
        heightDp >= MediumWidgetSize.height.value -> MEDIUM_CAPACITY
        else -> COMPACT_CAPACITY
    }
}

private const val COMPACT_CAPACITY = 3
private const val MEDIUM_CAPACITY = 5
private const val EXPANDED_CAPACITY = 8
