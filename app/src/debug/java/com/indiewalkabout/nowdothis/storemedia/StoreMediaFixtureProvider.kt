package com.indiewalkabout.nowdothis.storemedia

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal const val STORE_MEDIA_FIXTURE_AUTHORITY =
    "com.indiewalkabout.nowdothis.store-media-fixture"
internal const val STORE_MEDIA_FIXTURE_METHOD = "prepare_store_media"
internal const val STORE_MEDIA_LOCALE_ARG = "localeTag"
internal const val STORE_MEDIA_FIXTURE_TASK_COUNT_KEY = "task_count"

class StoreMediaFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == STORE_MEDIA_FIXTURE_METHOD) { "Unsupported method: $method" }
        val appContext = requireNotNull(context).applicationContext
        val database = EntryPointAccessors.fromApplication(
            appContext,
            DebugDatabaseEntryPoint::class.java
        ).database()
        runBlocking(Dispatchers.IO) {
            StoreMediaFixture(database).prepare(
                requireNotNull(arg) { "$STORE_MEDIA_LOCALE_ARG is required" }
            )
        }
        return Bundle().apply {
            putInt(STORE_MEDIA_FIXTURE_TASK_COUNT_KEY, STORE_MEDIA_FIXTURE_TASK_COUNT)
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

    private companion object {
        const val STORE_MEDIA_FIXTURE_TASK_COUNT = 6
    }
}
