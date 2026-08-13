package com.indiewalkabout.nowdothis.benchmark

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkTaskFixtureTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun prepare_createsExactly750DeterministicTasksAndIsIdempotent() = runTest {
        val fixture = BenchmarkTaskFixture(
            database = database,
            clock = AppClock { FIXED_NOW_MILLIS }
        )

        fixture.prepare()
        fixture.prepare()

        val tasks = database.taskDao().observeAllTaskEntities().first()
        val categories = database.categoryDao().observeAll().first()

        assertEquals(750, tasks.size)
        assertEquals((10_001..10_750).toList(), tasks.map { it.id })
        assertEquals(listOf(1, 2, 3, 4), categories.map { it.id })
        assertEquals(setOf(1, 2, 3, 4), tasks.mapNotNull { it.categoryId }.toSet())
        assertEquals(
            mapOf("HIGH" to 250, "MEDIUM" to 250, "LOW" to 250),
            tasks.groupingBy { it.priority }.eachCount()
        )
        assertEquals(FIXED_NOW_MILLIS - 10_001L, tasks.first().createdAt)
        assertEquals(FIXED_NOW_MILLIS - 10_750L, tasks.last().createdAt)
    }

    @Test
    fun prepare_derivesTaskAndCategoryDatesFromTheInjectedClock() = runTest {
        val injectedNow = 1_700_000_000_000L
        BenchmarkTaskFixture(
            database = database,
            clock = AppClock { injectedNow }
        ).prepare()

        val tasks = database.taskDao().observeAllTaskEntities().first()
        val categories = database.categoryDao().observeAll().first()

        assertEquals(injectedNow - 6 * DAY_MILLIS + HOUR_MILLIS, tasks[0].dueAt)
        assertEquals(null, tasks[4].dueAt)
        assertEquals(setOf(injectedNow), categories.map { it.createdAt }.toSet())
    }

    private companion object {
        const val FIXED_NOW_MILLIS = 1_786_525_200_000L
        const val HOUR_MILLIS = 60L * 60L * 1_000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}
