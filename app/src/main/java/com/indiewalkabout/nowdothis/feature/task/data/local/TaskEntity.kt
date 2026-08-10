package com.indiewalkabout.nowdothis.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["due_at"]),
        Index(value = ["completed_at"]),
        Index(value = ["is_completed"]),
        Index(value = ["series_id"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    val priority: String,
    @ColumnInfo(name = "category_id", defaultValue = "NULL")
    val categoryId: Int? = null,
    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at", defaultValue = "NULL")
    val completedAt: Long? = null,
    @ColumnInfo(name = "due_at", defaultValue = "NULL")
    val dueAt: Long? = null,
    @ColumnInfo(name = "reminder_at", defaultValue = "NULL")
    val reminderAt: Long? = null,
    @ColumnInfo(name = "reminder_status", defaultValue = "'NONE'")
    val reminderStatus: String = "NONE",
    @ColumnInfo(defaultValue = "'NONE'")
    val recurrence: String = "NONE",
    @ColumnInfo(name = "recurrence_end_at", defaultValue = "NULL")
    val recurrenceEndAt: Long? = null,
    @ColumnInfo(name = "series_id", defaultValue = "NULL")
    val seriesId: String? = null,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
