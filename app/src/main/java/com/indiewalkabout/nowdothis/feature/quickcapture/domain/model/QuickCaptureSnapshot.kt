package com.indiewalkabout.nowdothis.feature.quickcapture.domain.model

enum class QuickCaptureDueState {
    OVERDUE,
    TODAY,
    UPCOMING
}

data class QuickCaptureTask(
    val id: Int,
    val title: String,
    val dueAt: Long,
    val dueState: QuickCaptureDueState
)

data class QuickCaptureSnapshot(val tasks: List<QuickCaptureTask>)
