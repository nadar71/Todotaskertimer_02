package com.indiewalkabout.nowdothis.feature.quickcapture.di

import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetRefreshSignal
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuickCaptureWidgetEntryPoint {
    fun loadQuickCaptureTasks(): LoadQuickCaptureTasks
    fun completeQuickCaptureTask(): CompleteQuickCaptureTask
    fun quickCaptureWidgetUpdater(): QuickCaptureWidgetUpdater
    fun quickCaptureWidgetRefreshSignal(): QuickCaptureWidgetRefreshSignal
}
