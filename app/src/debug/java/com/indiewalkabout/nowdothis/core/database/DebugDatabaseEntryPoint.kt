package com.indiewalkabout.nowdothis.core.database

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugDatabaseEntryPoint {
    fun database(): AppDatabase
}
