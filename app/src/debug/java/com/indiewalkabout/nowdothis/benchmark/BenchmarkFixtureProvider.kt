package com.indiewalkabout.nowdothis.benchmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal const val BENCHMARK_FIXTURE_AUTHORITY =
    "com.indiewalkabout.nowdothis.benchmark-fixture"
internal const val PREPARE_BENCHMARK_FIXTURE_METHOD = "prepare"
internal const val RESET_BENCHMARK_FIXTURE_METHOD = "reset"
internal const val PREPARE_QUICK_CAPTURE_FIXTURE_METHOD = "prepare_quick_capture"
internal const val QUERY_QUICK_CAPTURE_FIXTURE_METHOD = "query_quick_capture"
internal const val BENCHMARK_FIXTURE_TASK_COUNT_KEY = "task_count"

class BenchmarkFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(
            method == PREPARE_BENCHMARK_FIXTURE_METHOD ||
                method == RESET_BENCHMARK_FIXTURE_METHOD ||
                method == PREPARE_QUICK_CAPTURE_FIXTURE_METHOD ||
                method == QUERY_QUICK_CAPTURE_FIXTURE_METHOD
        ) { "Unsupported method: $method" }
        val appContext = requireNotNull(context).applicationContext
        val database = EntryPointAccessors.fromApplication(
            appContext,
            BenchmarkDatabaseEntryPoint::class.java
        ).database()
        val quickCaptureState = runBlocking(Dispatchers.IO) {
            when (method) {
                RESET_BENCHMARK_FIXTURE_METHOD -> database.clearAllTables()
                PREPARE_BENCHMARK_FIXTURE_METHOD -> BenchmarkTaskFixture(
                    database = database,
                    clock = AppClock { FIXED_NOW_MILLIS }
                ).prepare()
                PREPARE_QUICK_CAPTURE_FIXTURE_METHOD -> {
                    database.clearAllTables()
                    val now = System.currentTimeMillis()
                    database.taskDao().insertTask(
                        TaskEntity(
                            id = QUICK_CAPTURE_TASK_ID,
                            title = QUICK_CAPTURE_TASK_TITLE,
                            description = "Process lifecycle fixture",
                            priority = TaskPriority.MEDIUM.name,
                            dueAt = now + 60_000,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
            if (method == QUERY_QUICK_CAPTURE_FIXTURE_METHOD) {
                database.taskDao().getAllTaskEntities()
            } else {
                null
            }
        }
        return Bundle().apply {
            putInt(
                BENCHMARK_FIXTURE_TASK_COUNT_KEY,
                if (method == RESET_BENCHMARK_FIXTURE_METHOD) 0 else 750
            )
            quickCaptureState?.let { tasks ->
                putBoolean(
                    "original_completed",
                    tasks.firstOrNull { it.id == QUICK_CAPTURE_TASK_ID }?.isCompleted == true
                )
                putInt("pending_count", tasks.count { !it.isCompleted })
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BenchmarkDatabaseEntryPoint {
    fun database(): AppDatabase
}

private const val QUICK_CAPTURE_TASK_ID = 991
private const val QUICK_CAPTURE_TASK_TITLE = "Process absent task"
