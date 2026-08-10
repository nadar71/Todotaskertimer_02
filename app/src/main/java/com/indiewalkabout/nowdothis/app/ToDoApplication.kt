package com.indiewalkabout.nowdothis.app

import android.app.Application
import com.indiewalkabout.nowdothis.core.notifications.ReminderStartupCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ToDoApplication : Application() {
    @Inject
    lateinit var reminderStartupCoordinator: ReminderStartupCoordinator

    override fun onCreate() {
        super.onCreate()
        reminderStartupCoordinator.onApplicationStart()
    }
}
