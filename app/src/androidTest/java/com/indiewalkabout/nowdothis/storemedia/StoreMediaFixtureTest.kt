package com.indiewalkabout.nowdothis.storemedia

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreMediaFixtureTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val entryPoint: DebugDatabaseEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugDatabaseEntryPoint::class.java
        )
    }
    private val database: AppDatabase
        get() = entryPoint.database()

    @Test
    fun italianFixture_containsLocalizedStoreStory() = runBlocking {
        providerCall("it-IT")
        providerCall("it-IT")

        assertFixtureGraph(
            listOf(
                "Preparare la presentazione",
                "Chiamare il dentista",
                "Rivedere il piano di rilascio",
                "Comprare i biglietti del treno",
                "Allenamento del mattino",
                "Inviare la nota spese"
            )
        )
    }

    @Test
    fun englishFixture_containsLocalizedStoreStory() = runBlocking {
        providerCall("en-US")
        providerCall("en-US")

        assertFixtureGraph(
            listOf(
                "Prepare the presentation",
                "Call the dentist",
                "Review the release plan",
                "Buy train tickets",
                "Morning workout",
                "Submit the expense report"
            )
        )
    }

    @Test
    fun fixture_resetsTaskSortToDefault() = runBlocking {
        entryPoint.taskPreferencesRepository().setTaskSort(TaskSort.HIGH_FIRST)

        providerCall("en-US")

        assertEquals(TaskSort.DEFAULT, entryPoint.taskPreferencesRepository().taskSort.first())
    }

    private suspend fun assertFixtureGraph(expectedTitles: List<String>) {
        val categories = withContext(Dispatchers.IO) { database.categoryDao().getAll() }
        val tasks = withContext(Dispatchers.IO) { database.taskDao().getAllTaskEntities() }
        val subtasks = withContext(Dispatchers.IO) { database.taskDao().getAllSubtaskEntities() }

        assertEquals(listOf(1, 2, 3), categories.map { it.id })
        assertEquals(listOf("WORK", "PERSONAL", "WISHLIST"), categories.map { it.defaultKey })
        assertEquals(listOf(40_001, 40_002, 40_003, 40_004, 40_005, 40_006), tasks.map { it.id })
        assertEquals(expectedTitles, tasks.map { it.title })
        assertEquals(1, tasks.count { it.reminderAt != null })
        assertEquals(1, tasks.count { it.recurrenceKind == "SELECTED_WEEKDAYS" })
        assertEquals(0b001_0101, tasks.single { it.recurrenceKind == "SELECTED_WEEKDAYS" }.recurrenceWeekdayMask)
        assertEquals(1, tasks.count { it.isCompleted })
        assertNotNull(tasks.single { it.isCompleted }.completedAt)
        assertEquals(
            listOf(
                Triple(50_001, 40_001, 0),
                Triple(50_002, 40_001, 1),
                Triple(50_003, 40_002, 0)
            ),
            subtasks.map { Triple(it.id, it.taskId, it.position) }
        )
    }

    private fun providerCall(localeTag: String) {
        val result = context.contentResolver.call(
            Uri.parse("content://com.indiewalkabout.nowdothis.store-media-fixture"),
            "prepare_store_media",
            localeTag,
            null
        )

        assertNotNull(result)
        assertEquals(6, requireNotNull(result).getInt("task_count"))
    }
}
