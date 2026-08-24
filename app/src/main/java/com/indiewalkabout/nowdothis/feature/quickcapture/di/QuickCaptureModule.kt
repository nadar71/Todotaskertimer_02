package com.indiewalkabout.nowdothis.feature.quickcapture.di

import com.indiewalkabout.nowdothis.feature.quickcapture.data.TaskSectionsQuickCaptureSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.CompleteQuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.GlanceQuickCaptureWidgetUpdater
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class QuickCaptureBindingsModule {
    @Binds
    @Singleton
    abstract fun bindQuickCaptureTaskSource(
        implementation: TaskSectionsQuickCaptureSource
    ): QuickCaptureTaskSource

    @Singleton
    @Binds
    abstract fun bindQuickCaptureWidgetUpdater(
        implementation: GlanceQuickCaptureWidgetUpdater
    ): QuickCaptureWidgetUpdater
}

@Module
@InstallIn(SingletonComponent::class)
object QuickCaptureModule {
    @Provides
    @Singleton
    fun provideLoadQuickCaptureTasks(source: QuickCaptureTaskSource) =
        LoadQuickCaptureTasks(source)

    @Provides
    @Singleton
    fun provideCompleteQuickCaptureTask(
        completeTask: CompleteTask,
        updater: QuickCaptureWidgetUpdater
    ) = CompleteQuickCaptureTask(completeTask, updater)
}
