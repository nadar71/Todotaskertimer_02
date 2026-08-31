package com.indiewalkabout.nowdothis.storemedia

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
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
        val localeTag = requireStoreMediaLocale(arg)
        val emulator = isStoreMediaEmulator(
            hardware = Build.HARDWARE,
            fingerprint = Build.FINGERPRINT,
            model = Build.MODEL,
            product = Build.PRODUCT
        )
        if (!emulator) {
            throw SecurityException("Store-media fixtures require an Android emulator")
        }
        val appContext = requireNotNull(context).applicationContext
        val callerAuthorized = isStoreMediaFixtureCaller(
            callingUid = Binder.getCallingUid(),
            appUid = appContext.applicationInfo.uid,
            shellUid = Process.SHELL_UID
        )
        if (!callerAuthorized) {
            throw SecurityException("Store-media fixture caller is not authorized")
        }
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            DebugDatabaseEntryPoint::class.java
        )
        runBlocking(Dispatchers.IO) {
            prepareStoreMediaFixture(
                localeTag = localeTag,
                resetTaskSort = {
                    entryPoint.taskPreferencesRepository().setTaskSort(TaskSort.DEFAULT)
                },
                prepareFixture = { supportedLocale ->
                    StoreMediaFixture(entryPoint.database()).prepare(supportedLocale)
                }
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

internal fun isStoreMediaFixtureCaller(
    callingUid: Int,
    appUid: Int,
    shellUid: Int
): Boolean = callingUid == appUid || callingUid == shellUid

internal fun isStoreMediaEmulator(
    hardware: String,
    fingerprint: String,
    model: String,
    product: String
): Boolean {
    val emulatorHardware = hardware.lowercase() in setOf("ranchu", "goldfish")
    val identity = listOf(fingerprint, model, product).joinToString(" ").lowercase()
    val emulatorIdentity = listOf(
        "sdk_gphone",
        "android sdk built for",
        "generic/sdk",
        "generic_x86",
        "emulator"
    ).any(identity::contains)
    return emulatorHardware && emulatorIdentity
}

internal fun requireStoreMediaLocale(localeTag: String?): String {
    val requiredLocale = requireNotNull(localeTag) { "$STORE_MEDIA_LOCALE_ARG is required" }
    require(requiredLocale in STORE_MEDIA_SUPPORTED_LOCALES) {
        "Unsupported locale: $requiredLocale"
    }
    return requiredLocale
}

internal suspend fun prepareStoreMediaFixture(
    localeTag: String?,
    resetTaskSort: suspend () -> Unit,
    prepareFixture: suspend (String) -> Unit
) {
    val supportedLocale = requireStoreMediaLocale(localeTag)
    resetTaskSort()
    prepareFixture(supportedLocale)
}
