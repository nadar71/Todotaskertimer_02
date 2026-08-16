package com.indiewalkabout.nowdothis.feature.quickcapture.di

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.indiewalkabout.nowdothis.feature.quickcapture.data.TaskSectionsQuickCaptureSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidget
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QuickCaptureModule {
    @Provides
    @Singleton
    fun provideQuickCaptureTaskSource(
        source: TaskSectionsQuickCaptureSource
    ): QuickCaptureTaskSource = source

    @Provides
    @Singleton
    fun provideLoadQuickCaptureTasks(source: QuickCaptureTaskSource) =
        LoadQuickCaptureTasks(source)

    @Provides
    @Singleton
    fun provideQuickCaptureWidgetUpdater(
        @ApplicationContext context: Context
    ): QuickCaptureWidgetUpdater = QuickCaptureWidgetUpdater {
        QuickCaptureWidget().updateAll(context)
    }

    @Provides
    @Singleton
    fun provideCompleteQuickCaptureTask(
        completeTask: CompleteTask,
        updater: QuickCaptureWidgetUpdater
    ) = CompleteQuickCaptureTask(completeTask, updater)
}
