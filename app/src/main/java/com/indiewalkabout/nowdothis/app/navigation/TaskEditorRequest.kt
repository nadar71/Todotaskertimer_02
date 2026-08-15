package com.indiewalkabout.nowdothis.app.navigation

sealed interface TaskEditorRequest {
    data object Add : TaskEditorRequest

    data class Open(val taskId: Int) : TaskEditorRequest
}
