package com.indiewalkabout.nowdothis.navigation

import androidx.navigation3.runtime.NavKey
import com.indiewalkabout.nowdothis.util.Action
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen : NavKey {
    @Serializable
    data class List(val action: Action = Action.NO_ACTION): Screen()

    @Serializable
    data class Task(val id: Int): Screen()
}
