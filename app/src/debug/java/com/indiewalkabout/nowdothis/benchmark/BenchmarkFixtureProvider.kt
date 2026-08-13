package com.indiewalkabout.nowdothis.benchmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
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
internal const val BENCHMARK_FIXTURE_TASK_COUNT_KEY = "task_count"

class BenchmarkFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(
            method == PREPARE_BENCHMARK_FIXTURE_METHOD ||
                method == RESET_BENCHMARK_FIXTURE_METHOD
        ) { "Unsupported method: $method" }
        val appContext = requireNotNull(context).applicationContext
        val database = EntryPointAccessors.fromApplication(
            appContext,
            BenchmarkDatabaseEntryPoint::class.java
        ).database()
        runBlocking(Dispatchers.IO) {
            if (method == RESET_BENCHMARK_FIXTURE_METHOD) {
                database.clearAllTables()
            } else {
                BenchmarkTaskFixture(
                    database = database,
                    clock = AppClock { FIXED_NOW_MILLIS }
                ).prepare()
            }
        }
        return Bundle().apply {
            putInt(
                BENCHMARK_FIXTURE_TASK_COUNT_KEY,
                if (method == RESET_BENCHMARK_FIXTURE_METHOD) 0 else 750
            )
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
