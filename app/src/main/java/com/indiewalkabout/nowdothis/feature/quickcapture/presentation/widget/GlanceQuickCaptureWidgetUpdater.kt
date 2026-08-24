package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceQuickCaptureWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val refreshSignal: QuickCaptureWidgetRefreshSignal
) : QuickCaptureWidgetUpdater {
    override suspend fun updateAll() {
        refreshSignal.invalidate()
        QuickCaptureWidget().updateAll(context)
    }
}
