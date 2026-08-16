package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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

@Suppress("RestrictedApi")
object QuickCaptureWidgetColors {
    val background = ColorProvider(R.color.quick_capture_widget_background)
    val surface = ColorProvider(R.color.quick_capture_widget_surface)
    val onSurface = ColorProvider(R.color.quick_capture_widget_on_surface)
    val muted = ColorProvider(R.color.quick_capture_widget_muted)
    val overdue = ColorProvider(R.color.quick_capture_widget_overdue)

    val palette = QuickCaptureWidgetPalette(
        background = background,
        surface = surface,
        onSurface = onSurface,
        muted = muted,
        overdue = overdue
    )
}

@Composable
fun QuickCaptureWidgetTheme(content: @Composable (QuickCaptureWidgetPalette) -> Unit) {
    content(QuickCaptureWidgetColors.palette)
}

fun colorRoleFor(dueState: QuickCaptureDueState): QuickCaptureWidgetColorRole = when (dueState) {
    QuickCaptureDueState.OVERDUE -> QuickCaptureWidgetColorRole.Overdue
    QuickCaptureDueState.TODAY,
    QuickCaptureDueState.UPCOMING -> QuickCaptureWidgetColorRole.Neutral
}
