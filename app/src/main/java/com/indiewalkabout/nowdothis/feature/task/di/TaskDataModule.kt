package com.indiewalkabout.nowdothis.feature.task.di

import com.indiewalkabout.nowdothis.feature.task.data.repository.DataStoreTaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.data.repository.OfflineTaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.CompletionHistoryReader
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskScheduleReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskDataModule {
    @Binds
    @Singleton
    abstract fun bindTaskRepository(implementation: OfflineTaskRepository): TaskRepository

    @Binds
    @Singleton
    abstract fun bindTaskScheduleReader(implementation: OfflineTaskRepository): TaskScheduleReader

    @Binds
    @Singleton
    abstract fun bindCompletionHistoryReader(
        implementation: OfflineTaskRepository
    ): CompletionHistoryReader

    @Binds
    @Singleton
    abstract fun bindTaskPreferencesRepository(
        implementation: DataStoreTaskPreferencesRepository
    ): TaskPreferencesRepository
}
