package com.indiewalkabout.nowdothis.app.navigation

import androidx.navigation3.runtime.NavKey
import com.indiewalkabout.nowdothis.feature.calendar.navigation.CalendarKey
import com.indiewalkabout.nowdothis.feature.category.navigation.CategoryManagementKey
import com.indiewalkabout.nowdothis.feature.history.navigation.CompletionHistoryKey
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskListKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun selectRoot_replacesTheWholeStack() {
        val navigator = AppNavigator(mutableListOf(TaskListKey, CategoryManagementKey))

        navigator.selectRoot(CalendarKey)

        assertEquals(listOf(CalendarKey), navigator.backStack)
    }

    @Test
    fun openTaskEditor_preservesArgumentsAndCoversRoot() {
        val navigator = AppNavigator(mutableListOf(TaskListKey))

        navigator.openTaskEditor(taskId = 42, initialDueAt = 8_000L)

        assertEquals(TaskEditorKey(42, 8_000L), navigator.backStack.last())
        assertFalse(navigator.isRootDestination)
    }

    @Test
    fun openCategoryAndHistory_pushTheirDestinations() {
        val navigator = AppNavigator(mutableListOf(TaskListKey))

        navigator.openCategoryManagement()
        navigator.navigateBack()
        navigator.openCompletionHistory()

        assertEquals(CompletionHistoryKey, navigator.backStack.last())
    }

    @Test
    fun navigateBack_popsOverlayButNeverRemovesRoot() {
        val navigator = AppNavigator(mutableListOf(TaskListKey, CategoryManagementKey))

        assertTrue(navigator.navigateBack())
        assertEquals(listOf(TaskListKey), navigator.backStack)
        assertFalse(navigator.navigateBack())
        assertEquals(listOf(TaskListKey), navigator.backStack)
    }

    @Test
    fun notificationTaskId_acceptsOnlyOpenTaskActionsWithPositiveIds() {
        assertEquals(7, notificationTaskId(OPEN_TASK_ACTION, true, 7))
        assertNull(notificationTaskId("another-action", true, 7))
        assertNull(notificationTaskId(OPEN_TASK_ACTION, false, 7))
        assertNull(notificationTaskId(OPEN_TASK_ACTION, true, 0))
        assertNull(notificationTaskId(OPEN_TASK_ACTION, true, -1))
    }

    @Test
    fun rootKeysAreRecognized() {
        val roots: List<NavKey> = listOf(TaskListKey, CalendarKey)

        roots.forEach { key ->
            assertTrue(AppNavigator(mutableListOf(key)).isRootDestination)
        }
    }
}
