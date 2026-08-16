package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
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
    fun observer_propagatesCancellationWithoutCancellingTheApplicationSupervisor() = runTest {
        var collections = 0
        var updateCount = 0
        val source = QuickCaptureTaskSource {
            flow {
                collections++
                emit(TaskSections())
                throw CancellationException("stop only the observer")
            }
        }
        val scope = applicationScope()
        val sibling = scope.launch { awaitCancellation() }
        val coordinator = coordinator(source, scope) { updateCount++ }

        coordinator.onApplicationStart()
        runCurrent()

        assertEquals(1, collections)
        assertEquals(1, updateCount)
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
}
