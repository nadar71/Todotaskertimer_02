package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderStartupCoordinatorTest {
    @Test
    fun onApplicationStart_reconcilesPersistedRowsThroughInexactFallbackWithoutBlocking() =
        runTest {
            val repository = FakeTaskRepository(
                reminders = listOf(
                    task(41, 2_000, ReminderStatus.SCHEDULED),
                    task(42, 3_000, ReminderStatus.UNAVAILABLE)
                )
            )
            val gateway = FakeAlarmGateway(canScheduleExact = false)
            val scheduler = AlarmManagerReminderScheduler(
                gateway = gateway,
                taskRepository = repository,
                clock = AppClock { 1_000 }
            )
            val coordinator = ReminderStartupCoordinator(
                reminderScheduler = scheduler,
                workLauncher = ReminderWorkLauncher(this)
            )

            coordinator.onApplicationStart()

            assertNull(repository.requestedAfter)
            advanceUntilIdle()
            assertEquals(1_000L, repository.requestedAfter)
            assertEquals(
                listOf(
                    AlarmCall.Inexact(41, 2_000),
                    AlarmCall.Inexact(42, 3_000)
                ),
                gateway.calls
            )
            assertEquals(
                listOf(
                    41 to ReminderStatus.SCHEDULED,
                    42 to ReminderStatus.SCHEDULED
                ),
                repository.statusUpdates
            )
        }

    private fun task(id: Int, reminderAt: Long, status: ReminderStatus) = Task(
        id = id,
        title = "Task $id",
        description = "Description",
        priority = TaskPriority.MEDIUM,
        reminderAt = reminderAt,
        reminderStatus = status,
        createdAt = 100,
        updatedAt = 100
    )
}
