package com.indiewalkabout.nowdothis.data.models

import androidx.compose.ui.graphics.Color
import com.indiewalkabout.nowdothis.ui.theme.HighPriorityColor
import com.indiewalkabout.nowdothis.ui.theme.LowPriorityColor
import com.indiewalkabout.nowdothis.ui.theme.MediumPriorityColor

enum class Priority(val color: Color) {
    HIGH(HighPriorityColor),
    MEDIUM(MediumPriorityColor),
    LOW(LowPriorityColor),
    NONE(Color.Transparent)
}