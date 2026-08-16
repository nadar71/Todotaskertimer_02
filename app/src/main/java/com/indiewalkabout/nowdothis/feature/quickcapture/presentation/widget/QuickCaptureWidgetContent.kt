package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureDueState
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureTask
import androidx.compose.ui.unit.dp

val QuickCaptureWidgetSizeMode = SizeMode.Responsive(
    setOf(CompactWidgetSize, MediumWidgetSize, ExpandedWidgetSize)
)

object QuickCaptureWidgetActionParameters {
    val taskId = androidx.glance.action.ActionParameters.Key<Int>("quick_capture_task_id")
}

@Composable
fun QuickCaptureWidgetContent(state: QuickCaptureWidgetState) {
    QuickCaptureWidgetTheme { colors ->
        val context = LocalContext.current
        val capacity = capacityFor(LocalSize.current)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.background)
                .padding(horizontal = 8.dp)
        ) {
            QuickCaptureWidgetHeader(
                context = context,
                colors = colors,
                showAdd = state is QuickCaptureWidgetState.Empty || state is QuickCaptureWidgetState.Content
            )

            when (state) {
                QuickCaptureWidgetState.Loading -> QuickCaptureWidgetLoadingContent(capacity, colors)
                QuickCaptureWidgetState.Empty -> QuickCaptureWidgetMessage(
                    tag = "quick-capture-empty",
                    message = context.getString(R.string.quick_capture_widget_empty),
                    colors = colors
                )
                is QuickCaptureWidgetState.Content -> state.snapshot.tasks
                    .take(capacity)
                    .forEach { task ->
                        QuickCaptureWidgetTaskRow(
                            context = context,
                            colors = colors,
                            task = task,
                            completionInFlight = task.id in state.inFlightTaskIds
                        )
                    }
                QuickCaptureWidgetState.Unavailable -> QuickCaptureWidgetUnavailableContent(context, colors)
            }
        }
    }
}

@Composable
private fun QuickCaptureWidgetHeader(
    context: Context,
    colors: QuickCaptureWidgetPalette,
    showAdd: Boolean
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(QuickCaptureWidgetDimensions.rowHeight)
            .semantics { testTag = "quick-capture-header" }
    ) {
        Text(
            text = context.getString(R.string.quick_capture_widget_title),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = colors.onSurface),
            maxLines = QuickCaptureWidgetDimensions.titleMaxLines
        )
        if (showAdd) {
            QuickCaptureWidgetIconAction(
                imageRes = R.drawable.ic_quick_capture_add,
                contentDescription = context.getString(R.string.quick_capture_widget_add_description),
                modifier = GlanceModifier
                    .clickable(actionStartActivity(QuickCaptureWidgetIntents.add(context)))
                    .semantics { testTag = "quick-capture-add" }
            )
        }
    }
}

@Composable
private fun QuickCaptureWidgetLoadingContent(capacity: Int, colors: QuickCaptureWidgetPalette) {
    repeat(capacity) { index ->
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(QuickCaptureWidgetDimensions.rowHeight)
                .background(colors.surface)
                .semantics { testTag = if (index == 0) "quick-capture-loading" else "quick-capture-loading-$index" }
        ) {}
    }
}

@Composable
private fun QuickCaptureWidgetMessage(
    tag: String,
    message: String,
    colors: QuickCaptureWidgetPalette
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(QuickCaptureWidgetDimensions.rowHeight)
            .semantics { testTag = tag }
    ) {
        Text(
            text = message,
            style = TextStyle(color = colors.muted),
            maxLines = QuickCaptureWidgetDimensions.titleMaxLines
        )
    }
}

@Composable
private fun QuickCaptureWidgetUnavailableContent(
    context: Context,
    colors: QuickCaptureWidgetPalette
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(QuickCaptureWidgetDimensions.rowHeight)
            .semantics { testTag = "quick-capture-unavailable" }
    ) {
        Text(
            text = context.getString(R.string.quick_capture_widget_unavailable),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = colors.muted),
            maxLines = QuickCaptureWidgetDimensions.titleMaxLines
        )
        QuickCaptureWidgetIconAction(
            imageRes = R.drawable.ic_quick_capture_retry,
            contentDescription = context.getString(R.string.quick_capture_widget_retry_description),
            modifier = GlanceModifier
                .clickable(actionRunCallback<QuickCaptureRetryAction>())
                .semantics { testTag = "quick-capture-retry" }
        )
    }
}

@Composable
private fun QuickCaptureWidgetTaskRow(
    context: Context,
    colors: QuickCaptureWidgetPalette,
    task: QuickCaptureTask,
    completionInFlight: Boolean
) {
    val dueLabel = dueLabel(context, task.dueState)
    val dueColor = if (colorRoleFor(task.dueState) == QuickCaptureWidgetColorRole.Overdue) {
        colors.overdue
    } else {
        colors.muted
    }
    val openDescription = context.getString(R.string.quick_capture_widget_open_description, task.title)
    val completeDescription = if (completionInFlight) {
        context.getString(R.string.quick_capture_widget_completing_description, task.title)
    } else {
        context.getString(R.string.quick_capture_widget_complete_description, task.title)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(QuickCaptureWidgetDimensions.rowHeight)
            .background(colors.surface)
            .semantics { testTag = "quick-capture-row-${task.id}" }
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(actionStartActivity(QuickCaptureWidgetIntents.open(context, task.id)))
                .semantics {
                    testTag = "quick-capture-title-${task.id}"
                    contentDescription = openDescription
                }
        ) {
            Text(
                text = task.title,
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .semantics { testTag = "quick-capture-title-text-${task.id}" },
                style = TextStyle(color = colors.onSurface),
                maxLines = QuickCaptureWidgetDimensions.titleMaxLines
            )
            Text(
                text = dueLabel,
                modifier = GlanceModifier
                    .padding(horizontal = 4.dp)
                    .semantics { testTag = "quick-capture-due-${task.id}" },
                style = TextStyle(color = dueColor),
                maxLines = QuickCaptureWidgetDimensions.titleMaxLines
            )
        }
        QuickCaptureWidgetIconAction(
            imageRes = R.drawable.ic_quick_capture_complete,
            contentDescription = completeDescription,
            modifier = completionModifier(task.id, completionInFlight)
                .semantics { testTag = "quick-capture-complete-${task.id}" }
        )
    }
}

private fun completionModifier(taskId: Int, completionInFlight: Boolean): GlanceModifier {
    val modifier = GlanceModifier
    return if (completionInFlight) {
        modifier
    } else {
        modifier.clickable(
            actionRunCallback<QuickCaptureCompleteAction>(
                actionParametersOf(QuickCaptureWidgetActionParameters.taskId to taskId)
            )
        )
    }
}

@Composable
private fun QuickCaptureWidgetIconAction(
    imageRes: Int,
    contentDescription: String,
    modifier: GlanceModifier
) {
    Image(
        provider = ImageProvider(imageRes),
        contentDescription = contentDescription,
        modifier = modifier.size(48.dp)
    )
}

private fun dueLabel(context: Context, dueState: QuickCaptureDueState): String = when (dueState) {
    QuickCaptureDueState.OVERDUE -> context.getString(R.string.quick_capture_widget_due_overdue)
    QuickCaptureDueState.TODAY -> context.getString(R.string.quick_capture_widget_due_today)
    QuickCaptureDueState.UPCOMING -> context.getString(R.string.quick_capture_widget_due_upcoming)
}
