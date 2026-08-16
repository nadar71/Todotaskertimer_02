package com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase

import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTaskResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CompleteQuickCaptureResult {
    data object Completed : CompleteQuickCaptureResult
    data object Ignored : CompleteQuickCaptureResult
    data object Failed : CompleteQuickCaptureResult
}

class CompleteQuickCaptureTask(
    private val completeTask: CompleteTask,
    private val updater: QuickCaptureWidgetUpdater
) {
    private val mutex = Mutex()
    private val mutableInFlightTaskIds = MutableStateFlow<Set<Int>>(emptySet())

    val inFlightTaskIds: StateFlow<Set<Int>> = mutableInFlightTaskIds.asStateFlow()

    suspend operator fun invoke(taskId: Int): CompleteQuickCaptureResult {
        val claimed = mutex.withLock {
            if (taskId <= 0 || taskId in mutableInFlightTaskIds.value) {
                false
            } else {
                mutableInFlightTaskIds.value += taskId
                true
            }
        }
        if (!claimed) return refresh(CompleteQuickCaptureResult.Ignored)

        var cancellation: CancellationException? = null
        var result: CompleteQuickCaptureResult = CompleteQuickCaptureResult.Failed
        try {
            result = try {
                when (completeTask(taskId)) {
                    CompleteTaskResult.NotFound,
                    CompleteTaskResult.AlreadyCompleted -> CompleteQuickCaptureResult.Ignored

                    is CompleteTaskResult.Completed -> CompleteQuickCaptureResult.Completed
                }
            } catch (exception: CancellationException) {
                cancellation = exception
                CompleteQuickCaptureResult.Failed
            } catch (_: Exception) {
                CompleteQuickCaptureResult.Failed
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    mutableInFlightTaskIds.value -= taskId
                }
                try {
                    updater.updateAll()
                } catch (exception: CancellationException) {
                    cancellation = cancellation ?: exception
                    result = CompleteQuickCaptureResult.Failed
                } catch (_: Exception) {
                    result = CompleteQuickCaptureResult.Failed
                }
            }
        }
        cancellation?.let { throw it }
        return result
    }

    private suspend fun refresh(result: CompleteQuickCaptureResult): CompleteQuickCaptureResult = try {
        updater.updateAll()
        result
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        CompleteQuickCaptureResult.Failed
    }
}
