package com.indiewalkabout.nowdothis.app.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.feature.calendar.navigation.CalendarKey
import com.indiewalkabout.nowdothis.feature.category.navigation.CategoryManagementKey
import com.indiewalkabout.nowdothis.feature.history.navigation.CompletionHistoryKey
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskListKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @Test
    fun navigationKeys_roundTripThroughSerialization() {
        assertEquals(TaskListKey, Json.decodeFromString<TaskListKey>(Json.encodeToString(TaskListKey)))
        assertEquals(CalendarKey, Json.decodeFromString<CalendarKey>(Json.encodeToString(CalendarKey)))
        assertEquals(
            CategoryManagementKey,
            Json.decodeFromString<CategoryManagementKey>(Json.encodeToString(CategoryManagementKey))
        )
        assertEquals(
            CompletionHistoryKey,
            Json.decodeFromString<CompletionHistoryKey>(Json.encodeToString(CompletionHistoryKey))
        )
        val editor = TaskEditorKey(taskId = 9, initialDueAt = 12_000L)
        assertEquals(editor, Json.decodeFromString<TaskEditorKey>(Json.encodeToString(editor)))
    }

    @Test
    fun navigator_switchesRootsAndReturnsFromEditor() {
        val navigator = AppNavigator(mutableListOf(TaskListKey))

        navigator.selectRoot(CalendarKey)
        navigator.openTaskEditor(taskId = null, initialDueAt = 15_000L)
        navigator.navigateBack()

        assertEquals(listOf(CalendarKey), navigator.backStack)
    }
}
