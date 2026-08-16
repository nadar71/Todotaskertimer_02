package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import com.indiewalkabout.nowdothis.core.di.ApplicationScope
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

@Singleton
class QuickCaptureWidgetCoordinator @Inject constructor(
    private val loadTasks: LoadQuickCaptureTasks,
    private val updater: QuickCaptureWidgetUpdater,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean(false)

    fun onApplicationStart() {
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            loadTasks.observe(MAX_WIDGET_CAPACITY)
                .distinctUntilChanged()
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        delay(RETRY_DELAY_MILLIS)
                        true
                    }
                }
                .collect { updater.updateAll() }
        }
    }

    private companion object {
        const val MAX_WIDGET_CAPACITY = 8
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
