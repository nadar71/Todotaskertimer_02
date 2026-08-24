package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickCaptureWidgetCoordinatorTest {
    @Test
    fun applicationStart_observesOnceAndInvalidatesInitialAndDistinctSnapshots() = runTest {
        val sections = MutableSharedFlow<TaskSections>(replay = 1)
        sections.emit(TaskSections())
        var updateCount = 0
        val scope = applicationScope()
        val coordinator = coordinator(
            source = QuickCaptureTaskSource { sections },
            scope = scope,
            onUpdate = { updateCount++ }
        )

        coordinator.onApplicationStart()
        coordinator.onApplicationStart()
        runCurrent()
        assertEquals(1, updateCount)

        sections.emit(TaskSections())
        runCurrent()
        assertEquals(1, updateCount)

        sections.emit(TaskSections(today = listOf(task(id = 1, title = "Saved"))))
        runCurrent()
        assertEquals(2, updateCount)

        sections.emit(TaskSections(today = listOf(task(id = 1, title = "Saved"))))
        runCurrent()
        assertEquals(2, updateCount)

        sections.emit(TaskSections(today = listOf(task(id = 2, title = "Restored"))))
        runCurrent()
        assertEquals(3, updateCount)

        scope.cancel()
    }

    @Test
    fun observer_retriesAfterANonCancellationSourceFailure() = runTest {
        var collections = 0
        var updateCount = 0
        val source = QuickCaptureTaskSource {
            flow {
                collections++
                if (collections == 1) {
                    emit(TaskSections())
                    throw IllegalStateException("transient Room flow failure")
                }
                emit(TaskSections(today = listOf(task(id = 7, title = "Recovered"))))
                awaitCancellation()
            }
        }
        val scope = applicationScope()
        val coordinator = coordinator(source, scope) { updateCount++ }

        coordinator.onApplicationStart()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(2, collections)
        assertEquals(2, updateCount)
        scope.cancel()
    }

    @Test
    fun observer_retriesWhenSourceConstructionFails() = runTest {
        var observations = 0
        var updateCount = 0
        val source = QuickCaptureTaskSource {
            observations++
            if (observations == 1) {
                throw IllegalStateException("source construction failed")
            }
            flow {
                emit(TaskSections(today = listOf(task(id = 8, title = "Constructed"))))
                awaitCancellation()
            }
        }
        val scope = applicationScope()
        val coordinator = coordinator(source, scope) { updateCount++ }

        coordinator.onApplicationStart()
        runCurrent()
        advanceTimeBy(RETRY_DELAY_MILLIS)
        runCurrent()

        assertEquals(2, observations)
        assertEquals(1, updateCount)
        scope.cancel()
    }

    @Test
    fun observer_retriesWhenUpdaterFailsAndRefreshesALaterMutation() = runTest {
        val sections = MutableSharedFlow<TaskSections>(replay = 1)
        sections.emit(TaskSections())
        var updateAttempts = 0
        val successfulUpdates = mutableListOf<Int>()
        val scope = applicationScope()
        val coordinator = coordinator(
            source = QuickCaptureTaskSource { sections },
            scope = scope,
            onUpdate = {
                updateAttempts++
                if (updateAttempts == 1) throw IllegalStateException("widget host unavailable")
                successfulUpdates += updateAttempts
            }
        )

        coordinator.onApplicationStart()
        runCurrent()
        advanceTimeBy(RETRY_DELAY_MILLIS)
        runCurrent()
        sections.emit(TaskSections(today = listOf(task(id = 9, title = "Later mutation"))))
        runCurrent()

        assertEquals(3, updateAttempts)
        assertEquals(listOf(2, 3), successfulUpdates)
        scope.cancel()
    }

    @Test
    fun persistentSourceFailures_useCappedExponentialBackoff() = runTest {
        var observations = 0
        val scope = applicationScope()
        val coordinator = coordinator(
            source = QuickCaptureTaskSource {
                observations++
                throw IllegalStateException("persistent source failure")
            },
            scope = scope,
            onUpdate = {}
        )

        try {
            coordinator.onApplicationStart()
            runCurrent()
            assertEquals(1, observations)

            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L)
                .forEachIndexed { index, delayMillis ->
                    advanceTimeBy(delayMillis - 1)
                    runCurrent()
                    assertEquals(index + 1, observations)

                    advanceTimeBy(1)
                    runCurrent()
                    assertEquals(index + 2, observations)
                }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun persistentUpdaterFailures_useExponentialBackoff() = runTest {
        var updateAttempts = 0
        val scope = applicationScope()
        val coordinator = coordinator(
            source = QuickCaptureTaskSource { flow { emit(TaskSections()) } },
            scope = scope,
            onUpdate = {
                updateAttempts++
                throw IllegalStateException("persistent widget host failure")
            }
        )

        try {
            coordinator.onApplicationStart()
            runCurrent()
            assertEquals(1, updateAttempts)

            listOf(1_000L, 2_000L, 4_000L).forEachIndexed { index, delayMillis ->
                advanceTimeBy(delayMillis - 1)
                runCurrent()
                assertEquals(index + 1, updateAttempts)

                advanceTimeBy(1)
                runCurrent()
                assertEquals(index + 2, updateAttempts)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun successfulUpdate_resetsBackoffToInitialDelay() = runTest {
        var observations = 0
        var updateCount = 0
        val scope = applicationScope()
        val coordinator = coordinator(
            source = QuickCaptureTaskSource {
                observations++
                when (observations) {
                    1, 2 -> throw IllegalStateException("transient source failure")
                    else -> flow {
                        emit(TaskSections(today = listOf(task(observations, "Recovered"))))
                        throw IllegalStateException("fail after successful update")
                    }
                }
            },
            scope = scope,
            onUpdate = { updateCount++ }
        )

        try {
            coordinator.onApplicationStart()
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(3, observations)
            assertEquals(1, updateCount)

            advanceTimeBy(999L)
            runCurrent()
            assertEquals(3, observations)
            advanceTimeBy(1L)
            runCurrent()

            assertEquals(4, observations)
            assertEquals(2, updateCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun observer_preservesCancellationAndCanBeStartedAgainAfterItStops() = runTest {
        var collections = 0
        var updateCount = 0
        val source = QuickCaptureTaskSource {
            flow {
                collections++
                emit(TaskSections())
                if (collections == 1) {
                    throw CancellationException("stop only the observer")
                }
                awaitCancellation()
            }
        }
        val scope = applicationScope()
        val sibling = scope.launch { awaitCancellation() }
        val coordinator = coordinator(source, scope) { updateCount++ }

        coordinator.onApplicationStart()
        runCurrent()

        coordinator.onApplicationStart()
        runCurrent()

        assertEquals(2, collections)
        assertEquals(2, updateCount)
        assertTrue(requireNotNull(scope.coroutineContext[Job]).isActive)
        assertTrue(sibling.isActive)
        scope.cancel()
    }

    private fun TestScope.applicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(testScheduler)
    )

    private fun coordinator(
        source: QuickCaptureTaskSource,
        scope: CoroutineScope,
        onUpdate: suspend () -> Unit
    ) = QuickCaptureWidgetCoordinator(
        loadTasks = LoadQuickCaptureTasks(source),
        updater = QuickCaptureWidgetUpdater(onUpdate),
        scope = scope
    )

    private fun task(id: Int, title: String) = Task(
        id = id,
        title = title,
        description = "Description",
        priority = TaskPriority.MEDIUM,
        dueAt = 1_000,
        createdAt = 0,
        updatedAt = 0
    )

    private companion object {
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
