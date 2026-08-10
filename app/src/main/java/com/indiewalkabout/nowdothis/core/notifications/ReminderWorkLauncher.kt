package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.core.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Singleton
class ReminderWorkLauncher @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    fun launch(
        onFinished: () -> Unit = {},
        work: suspend () -> Unit
    ): Job = applicationScope.launch {
        try {
            work()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Receiver/startup work is best-effort; a later trigger must remain usable.
        } finally {
            onFinished()
        }
    }
}
