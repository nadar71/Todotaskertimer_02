package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderNotificationContentTest {
    @Test
    fun from_returnsNullWhenTaskNoLongerExists() {
        assertNull(ReminderNotificationContent.from(null))
    }

    @Test
    fun from_returnsNullWhenTaskIsCompleted() {
        assertNull(ReminderNotificationContent.from(task(title = "Done", completed = true)))
    }

    @Test
    fun from_usesFallbackOnlyForEligibleTaskWithBlankTitle() {
        assertEquals(
            ReminderNotificationContent.Fallback,
            ReminderNotificationContent.from(task(title = " \t "))
        )
    }

    @Test
    fun from_trimsEligibleTaskTitle() {
        assertEquals(
            ReminderNotificationContent.TaskTitle("Buy milk"),
            ReminderNotificationContent.from(task(title = "  Buy milk  "))
        )
    }

    private fun task(title: String, completed: Boolean = false) = Task(
        id = 7,
        title = title,
        description = "Description",
        priority = TaskPriority.MEDIUM,
        isCompleted = completed,
        reminderAt = 2_000,
        createdAt = 100,
        updatedAt = 100
    )
}
