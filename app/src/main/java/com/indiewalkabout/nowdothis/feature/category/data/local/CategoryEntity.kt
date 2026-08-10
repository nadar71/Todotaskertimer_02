package com.indiewalkabout.nowdothis.feature.category.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "custom_name", defaultValue = "NULL")
    val customName: String? = null,
    @ColumnInfo(name = "default_key", defaultValue = "NULL")
    val defaultKey: String? = null,
    @ColumnInfo(name = "color_token")
    val colorToken: String,
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = 0L
)
