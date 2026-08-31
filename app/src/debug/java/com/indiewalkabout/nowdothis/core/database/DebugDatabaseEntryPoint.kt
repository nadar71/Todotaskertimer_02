package com.indiewalkabout.nowdothis.core.database

import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugDatabaseEntryPoint {
    fun database(): AppDatabase
    fun taskPreferencesRepository(): TaskPreferencesRepository
}
