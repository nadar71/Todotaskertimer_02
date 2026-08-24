package com.indiewalkabout.nowdothis.app

import android.app.Application
import android.content.res.Configuration
import com.indiewalkabout.nowdothis.core.notifications.ReminderStartupCoordinator
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ToDoApplication : Application() {
    @Inject
    lateinit var reminderStartupCoordinator: ReminderStartupCoordinator

    @Inject
    lateinit var quickCaptureWidgetCoordinator: QuickCaptureWidgetCoordinator

    override fun onCreate() {
        super.onCreate()
        reminderStartupCoordinator.onApplicationStart()
        quickCaptureWidgetCoordinator.onApplicationStart()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        quickCaptureWidgetCoordinator.onConfigurationChanged()
    }
}
