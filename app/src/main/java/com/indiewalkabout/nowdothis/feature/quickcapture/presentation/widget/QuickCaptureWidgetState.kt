package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureSnapshot

sealed interface QuickCaptureWidgetState {
    data object Loading : QuickCaptureWidgetState

    data object Empty : QuickCaptureWidgetState

    data class Content(
        val snapshot: QuickCaptureSnapshot,
        val inFlightTaskIds: Set<Int> = emptySet()
    ) : QuickCaptureWidgetState

    data object Unavailable : QuickCaptureWidgetState
}

val CompactWidgetSize = DpSize(180.dp, 200.dp)
val MediumWidgetSize = DpSize(250.dp, 320.dp)
val ExpandedWidgetSize = DpSize(250.dp, 464.dp)

fun capacityFor(size: DpSize): Int = when (size) {
    CompactWidgetSize -> 3
    MediumWidgetSize -> 5
    ExpandedWidgetSize -> 8
    else -> throw IllegalArgumentException("Unsupported Quick Capture widget size: $size")
}
