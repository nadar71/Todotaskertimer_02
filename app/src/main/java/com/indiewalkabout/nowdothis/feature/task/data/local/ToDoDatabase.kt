package com.indiewalkabout.nowdothis.feature.task.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.indiewalkabout.nowdothis.feature.task.domain.model.ToDoTask

@Database(entities = [ToDoTask::class], version = 1, exportSchema = true)
abstract class ToDoDatabase: RoomDatabase() {

    abstract fun toDoDao(): ToDoDao

}
