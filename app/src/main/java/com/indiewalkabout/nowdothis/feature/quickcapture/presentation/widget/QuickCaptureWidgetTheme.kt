package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.LocalContext
import androidx.glance.unit.ColorProvider
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureDueState

enum class QuickCaptureWidgetColorRole {
    Neutral,
    Overdue
}

object QuickCaptureWidgetDimensions {
    const val titleMaxLines = 1
    val rowHeight = 48.dp
}

data class QuickCaptureWidgetPalette(
    val background: ColorProvider,
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val muted: ColorProvider,
    val overdue: ColorProvider
)

@Composable
fun QuickCaptureWidgetTheme(content: @Composable (QuickCaptureWidgetPalette) -> Unit) {
    content(quickCaptureWidgetPalette(LocalContext.current))
}

internal fun quickCaptureWidgetPalette(context: Context): QuickCaptureWidgetPalette =
    QuickCaptureWidgetPalette(
        background = colorProvider(context, R.color.quick_capture_widget_background),
        surface = colorProvider(context, R.color.quick_capture_widget_surface),
        onSurface = colorProvider(context, R.color.quick_capture_widget_on_surface),
        muted = colorProvider(context, R.color.quick_capture_widget_muted),
        overdue = colorProvider(context, R.color.quick_capture_widget_overdue)
    )

private fun colorProvider(context: Context, @ColorRes colorRes: Int): ColorProvider =
    ColorProvider(Color(ContextCompat.getColor(context, colorRes)))

fun colorRoleFor(dueState: QuickCaptureDueState): QuickCaptureWidgetColorRole = when (dueState) {
    QuickCaptureDueState.OVERDUE -> QuickCaptureWidgetColorRole.Overdue
    QuickCaptureDueState.TODAY,
    QuickCaptureDueState.UPCOMING -> QuickCaptureWidgetColorRole.Neutral
}
