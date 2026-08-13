package com.indiewalkabout.nowdothis.benchmark

import androidx.room.withTransaction
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity

internal class BenchmarkTaskFixture(
    private val database: AppDatabase,
    private val clock: AppClock
) {
    suspend fun prepare() {
        val now = clock.nowMillis()
        val categories = categories(now)
        database.clearAllTables()
        database.withTransaction {
            categories.forEach { database.categoryDao().insert(it) }
            repeat(TASK_COUNT) { offset ->
                val ordinal = offset + 1
                val id = FIRST_TASK_ID + offset
                database.taskDao().insertTask(
                    TaskEntity(
                        id = id,
                        title = "Benchmark task ${ordinal.toString().padStart(4, '0')}",
                        description = "Deterministic fixture item $ordinal",
                        priority = PRIORITIES[offset % PRIORITIES.size],
                        categoryId = categories[offset % categories.size].id,
                        dueAt = dueAtFor(ordinal, now),
                        createdAt = now - id,
                        updatedAt = now - id
                    )
                )
            }
        }
    }

    private fun dueAtFor(ordinal: Int, now: Long): Long? {
        if (ordinal % UNSCHEDULED_INTERVAL == 0) return null
        val dayOffset = (ordinal % DUE_DATE_SPAN_DAYS) - OVERDUE_DAYS
        val hourOffset = ordinal % HOURS_PER_DAY
        return now + dayOffset * DAY_MILLIS + hourOffset * HOUR_MILLIS
    }

    private fun categories(createdAt: Long) = listOf(
        CategoryEntity(
            id = 1,
            defaultKey = "WORK",
            colorToken = "BLUE",
            position = 0,
            createdAt = createdAt
        ),
        CategoryEntity(
            id = 2,
            defaultKey = "PERSONAL",
            colorToken = "GREEN",
            position = 1,
            createdAt = createdAt
        ),
        CategoryEntity(
            id = 3,
            defaultKey = "WISHLIST",
            colorToken = "PINK",
            position = 2,
            createdAt = createdAt
        ),
        CategoryEntity(
            id = 4,
            customName = "Benchmark",
            colorToken = "BLUE",
            position = 3,
            createdAt = createdAt
        )
    )

    private companion object {
        const val TASK_COUNT = 750
        const val FIRST_TASK_ID = 10_001
        const val UNSCHEDULED_INTERVAL = 5
        const val DUE_DATE_SPAN_DAYS = 21
        const val OVERDUE_DAYS = 7
        const val HOURS_PER_DAY = 24
        const val HOUR_MILLIS = 60L * 60L * 1_000L
        const val DAY_MILLIS = HOURS_PER_DAY * HOUR_MILLIS

        val PRIORITIES = listOf("HIGH", "MEDIUM", "LOW")
    }
}

internal const val FIXED_NOW_MILLIS = 1_786_525_200_000L
