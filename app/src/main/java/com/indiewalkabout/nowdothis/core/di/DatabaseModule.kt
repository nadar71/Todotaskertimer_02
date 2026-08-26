package com.indiewalkabout.nowdothis.core.di

import android.content.Context
import androidx.room.Room
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DEFAULT_CATEGORIES_CALLBACK
import com.indiewalkabout.nowdothis.core.database.MIGRATION_1_2
import com.indiewalkabout.nowdothis.core.database.MIGRATION_2_3
import com.indiewalkabout.nowdothis.core.util.Constants.DATABASE_NAME
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryDao
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(DEFAULT_CATEGORIES_CALLBACK)
            .build()

    @Provides
    @Singleton
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()
}
