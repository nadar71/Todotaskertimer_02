package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import com.indiewalkabout.nowdothis.core.di.ApplicationScope
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

@Singleton
class QuickCaptureWidgetCoordinator @Inject constructor(
    private val loadTasks: LoadQuickCaptureTasks,
    private val updater: QuickCaptureWidgetUpdater,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean(false)

    fun onApplicationStart() {
        if (!started.compareAndSet(false, true)) return

        scope.launch { observeUntilCancelled() }
            .invokeOnCompletion { started.set(false) }
    }

    private suspend fun observeUntilCancelled() {
        while (coroutineContext.isActive) {
            try {
                loadTasks.observe(MAX_WIDGET_CAPACITY)
                    .distinctUntilChanged()
                    .collect { updater.updateAll() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Room and widget-host failures are transient; rebuild the full pipeline.
            }
            delay(RETRY_DELAY_MILLIS)
        }
    }

    private companion object {
        const val MAX_WIDGET_CAPACITY = 8
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
