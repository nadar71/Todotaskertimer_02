package com.indiewalkabout.nowdothis.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryDao
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskDao
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity

@Database(
    entities = [TaskEntity::class, SubtaskEntity::class, CategoryEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun categoryDao(): CategoryDao
}
