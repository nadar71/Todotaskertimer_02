package com.indiewalkabout.nowdothis.core.notifications

import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderStartupCoordinator @Inject constructor(
    private val reminderScheduler: ReminderScheduler,
    private val workLauncher: ReminderWorkLauncher
) {
    fun onApplicationStart() {
        workLauncher.launch { reminderScheduler.reconcile() }
    }
}
