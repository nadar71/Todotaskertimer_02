package com.indiewalkabout.nowdothis.feature.task.di

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteAllTasks
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.DeleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ObserveTaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.RestoreDeletedTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ValidateTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object TaskUseCaseModule {
    @Provides
    fun provideObserveTaskSections(
        repository: TaskRepository,
        preferences: TaskPreferencesRepository,
        clock: AppClock,
        zoneIdProvider: ZoneIdProvider
    ) = ObserveTaskSections(repository, preferences, clock, zoneIdProvider)

    @Provides
    fun provideCompleteTask(
        repository: TaskRepository,
        scheduler: ReminderScheduler,
        clock: AppClock,
        zoneIdProvider: ZoneIdProvider
    ) = CompleteTask(repository, scheduler, CalculateNextOccurrence(zoneIdProvider), clock)

    @Provides
    fun provideDeleteTask(repository: TaskRepository, scheduler: ReminderScheduler) =
        DeleteTask(repository, scheduler)

    @Provides
    fun provideDeleteAllTasks(repository: TaskRepository, scheduler: ReminderScheduler) =
        DeleteAllTasks(repository, scheduler)

    @Provides
    fun provideRestoreDeletedTask(
        repository: TaskRepository,
        scheduler: ReminderScheduler,
        clock: AppClock
    ) = RestoreDeletedTask(repository, scheduler, clock)

    @Provides
    fun provideSaveTask(
        repository: TaskRepository,
        scheduler: ReminderScheduler,
        clock: AppClock
    ) = SaveTask(repository, scheduler, ValidateTask(), clock)
}
