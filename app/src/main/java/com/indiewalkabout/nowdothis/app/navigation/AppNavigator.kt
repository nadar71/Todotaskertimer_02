package com.indiewalkabout.nowdothis.app.navigation

import androidx.navigation3.runtime.NavKey
import com.indiewalkabout.nowdothis.core.notifications.NotificationPublisher
import com.indiewalkabout.nowdothis.feature.calendar.navigation.CalendarKey
import com.indiewalkabout.nowdothis.feature.category.navigation.CategoryManagementKey
import com.indiewalkabout.nowdothis.feature.history.navigation.CompletionHistoryKey
import com.indiewalkabout.nowdothis.feature.portability.navigation.DataPortabilityKey
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskListKey

const val OPEN_TASK_ACTION: String = NotificationPublisher.OPEN_TASK_ACTION

class AppNavigator(
    val backStack: MutableList<NavKey>
) {
    init {
        require(backStack.isNotEmpty())
    }

    val currentDestination: NavKey
        get() = backStack.last()

    val isRootDestination: Boolean
        get() = currentDestination == TaskListKey || currentDestination == CalendarKey

    fun selectRoot(destination: NavKey) {
        require(destination == TaskListKey || destination == CalendarKey)
        backStack[0] = destination
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openTaskEditor(taskId: Int?, initialDueAt: Long?) {
        backStack.add(TaskEditorKey(taskId, initialDueAt))
    }

    fun openNewTask() {
        openTaskEditor(taskId = null, initialDueAt = null)
    }

    fun openTask(taskId: Int) {
        openTaskEditor(taskId = taskId, initialDueAt = null)
    }

    fun openCategoryManagement() {
        backStack.add(CategoryManagementKey)
    }

    fun openCompletionHistory() {
        backStack.add(CompletionHistoryKey)
    }

    fun openDataPortability() {
        backStack.add(DataPortabilityKey)
    }

    fun navigateBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

fun notificationTaskId(action: String?, hasTaskId: Boolean, taskId: Int): Int? =
    taskId.takeIf { action == OPEN_TASK_ACTION && hasTaskId && it > 0 }
