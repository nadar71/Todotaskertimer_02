package com.indiewalkabout.nowdothis.core.notifications

import java.util.concurrent.CancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderWorkLauncherTest {
    @Test
    fun launch_containsChildFailureFinishesAndKeepsLaterWorkUsable() = runTest {
        val launcher = ReminderWorkLauncher(this)
        var finishCalls = 0
        var laterWorkCalls = 0

        val failed = launcher.launch(onFinished = { finishCalls += 1 }) {
            error("repository unavailable")
        }
        val later = launcher.launch { laterWorkCalls += 1 }
        joinAll(failed, later)

        assertTrue(failed.isCompleted)
        assertFalse(failed.isCancelled)
        assertEquals(1, finishCalls)
        assertEquals(1, laterWorkCalls)
    }

    @Test
    fun launch_preservesCancellationAndStillFinishes() = runTest {
        val launcher = ReminderWorkLauncher(this)
        var finishCalls = 0

        val cancelled = launcher.launch(onFinished = { finishCalls += 1 }) {
            throw CancellationException("receiver work cancelled")
        }
        cancelled.join()

        assertTrue(cancelled.isCancelled)
        assertEquals(1, finishCalls)
    }
}
