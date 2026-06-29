package com.indiewalkabout.nowdothis.feature.task.domain.model

import androidx.compose.ui.graphics.Color
import com.indiewalkabout.nowdothis.core.designsystem.theme.HighPriorityColor
import com.indiewalkabout.nowdothis.core.designsystem.theme.LowPriorityColor
import com.indiewalkabout.nowdothis.core.designsystem.theme.MediumPriorityColor

enum class Priority(val color: Color) {
    HIGH(HighPriorityColor),
    MEDIUM(MediumPriorityColor),
    LOW(LowPriorityColor),
    NONE(Color.Transparent)
}