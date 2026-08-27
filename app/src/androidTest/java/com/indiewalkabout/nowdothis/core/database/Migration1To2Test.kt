package com.indiewalkabout.nowdothis.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate1To2_preservesEveryLegacyFieldAndInitializesNewState() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO todo_table (id, title, description, priority) " +
                    "VALUES (7, 'Legacy', 'Keep me', 'HIGH')"
            )
            execSQL(
                "INSERT INTO todo_table (id, title, description, priority) " +
                    "VALUES (8, 'Second', 'Also keep me', 'LOW')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT id, title, description, priority, category_id, is_completed, " +
                "completed_at, due_at, reminder_at, reminder_status, recurrence, " +
                "recurrence_end_at, series_id, created_at, updated_at " +
                "FROM tasks ORDER BY id"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals("Legacy", cursor.getString(1))
            assertEquals("Keep me", cursor.getString(2))
            assertEquals("HIGH", cursor.getString(3))
            assertTrue(cursor.isNull(4))
            assertEquals(0, cursor.getInt(5))
            assertTrue(cursor.isNull(6))
            assertTrue(cursor.isNull(7))
            assertTrue(cursor.isNull(8))
            assertEquals("NONE", cursor.getString(9))
            assertEquals("NONE", cursor.getString(10))
            assertTrue(cursor.isNull(11))
            assertTrue(cursor.isNull(12))
            assertEquals(0L, cursor.getLong(13))
            assertEquals(0L, cursor.getLong(14))

            assertTrue(cursor.moveToNext())
            assertEquals(8, cursor.getInt(0))
            assertEquals("Second", cursor.getString(1))
            assertEquals("Also keep me", cursor.getString(2))
            assertEquals("LOW", cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun migrate1To2_seedsStableDefaultCategories() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT id, custom_name, default_key, color_token, position, created_at " +
                "FROM categories ORDER BY position"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
            assertEquals("WORK", cursor.getString(2))
            assertEquals("BLUE", cursor.getString(3))
            assertEquals(0, cursor.getInt(4))
            assertEquals(0L, cursor.getLong(5))

            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals("PERSONAL", cursor.getString(2))
            assertEquals("GREEN", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))

            assertTrue(cursor.moveToNext())
            assertEquals(3, cursor.getInt(0))
            assertEquals("WISHLIST", cursor.getString(2))
            assertEquals("PINK", cursor.getString(3))
            assertEquals(2, cursor.getInt(4))
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun migrate2To3_convertsEveryLegacyRecurrenceAndUsesUpgradeTimezoneForMonthlyAnchor() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
            helper.createDatabase(TEST_DB, 2).apply {
                insertV2Task(1, "None", "NONE", null)
                insertV2Task(2, "Daily", "DAILY", 1_000)
                insertV2Task(3, "Weekly", "WEEKLY", 2_000)
                insertV2Task(
                    4,
                    "Monthly",
                    "MONTHLY",
                    Instant.parse("2026-01-30T23:30:00Z").toEpochMilli()
                )
                close()
            }

            val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

            db.query(
                "SELECT recurrence_kind, recurrence_interval_unit, recurrence_interval_count, " +
                    "recurrence_basis, recurrence_weekday_mask, recurrence_anchor_day, " +
                    "recurrence_ordinal, recurrence_ordinal_weekday FROM tasks ORDER BY id"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertRecurrence(cursor, "NONE", null, null, null, null, null, null, null)

                assertTrue(cursor.moveToNext())
                assertRecurrence(
                    cursor, "INTERVAL", "DAYS", 1, "SCHEDULED_DATE", null, null, null, null
                )

                assertTrue(cursor.moveToNext())
                assertRecurrence(
                    cursor, "INTERVAL", "WEEKS", 1, "SCHEDULED_DATE", null, null, null, null
                )

                assertTrue(cursor.moveToNext())
                assertRecurrence(
                    cursor, "MONTHLY_DAY", null, 1, "SCHEDULED_DATE", null, 31, null, null
                )
                assertFalse(cursor.moveToNext())
            }
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun migrate1To2To3_preservesTasksCategoriesAndSubtaskRelations() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO todo_table (id, title, description, priority) " +
                    "VALUES (7, 'Legacy', 'Keep me', 'HIGH')"
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).apply {
            execSQL(
                "INSERT INTO categories " +
                    "(id, custom_name, default_key, color_token, position, created_at) " +
                    "VALUES (20, 'Custom', NULL, 'RED', 3, 1234)"
            )
            execSQL(
                "UPDATE tasks SET category_id = 20, recurrence = 'WEEKLY', " +
                    "due_at = 5000 WHERE id = 7"
            )
            execSQL(
                "INSERT INTO subtasks " +
                    "(id, task_id, title, is_completed, completed_at, position) " +
                    "VALUES (30, 7, 'Child', 1, 4000, 2)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query(
            "SELECT title, description, priority, category_id, due_at, recurrence_kind " +
                "FROM tasks WHERE id = 7"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy", cursor.getString(0))
            assertEquals("Keep me", cursor.getString(1))
            assertEquals("HIGH", cursor.getString(2))
            assertEquals(20, cursor.getInt(3))
            assertEquals(5_000L, cursor.getLong(4))
            assertEquals("INTERVAL", cursor.getString(5))
        }
        db.query(
            "SELECT custom_name, color_token, position, created_at FROM categories WHERE id = 20"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Custom", cursor.getString(0))
            assertEquals("RED", cursor.getString(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals(1_234L, cursor.getLong(3))
        }
        db.query(
            "SELECT task_id, title, is_completed, completed_at, position FROM subtasks WHERE id = 30"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals("Child", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(4_000L, cursor.getLong(3))
            assertEquals(2, cursor.getInt(4))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertV2Task(
        id: Int,
        title: String,
        recurrence: String,
        dueAt: Long?
    ) {
        execSQL(
            "INSERT INTO tasks " +
                "(id, title, description, priority, due_at, recurrence) " +
                "VALUES (?, ?, '', 'MEDIUM', ?, ?)",
            arrayOf<Any?>(id, title, dueAt, recurrence)
        )
    }

    private fun assertRecurrence(
        cursor: android.database.Cursor,
        kind: String,
        unit: String?,
        count: Int?,
        basis: String?,
        mask: Int?,
        anchorDay: Int?,
        ordinal: String?,
        ordinalWeekday: String?
    ) {
        assertEquals(kind, cursor.getString(0))
        assertNullableString(cursor, 1, unit)
        assertNullableInt(cursor, 2, count)
        assertNullableString(cursor, 3, basis)
        assertNullableInt(cursor, 4, mask)
        assertNullableInt(cursor, 5, anchorDay)
        assertNullableString(cursor, 6, ordinal)
        assertNullableString(cursor, 7, ordinalWeekday)
    }

    private fun assertNullableString(
        cursor: android.database.Cursor,
        index: Int,
        expected: String?
    ) {
        if (expected == null) {
            assertTrue(cursor.isNull(index))
        } else {
            assertEquals(expected, cursor.getString(index))
        }
    }

    private fun assertNullableInt(cursor: android.database.Cursor, index: Int, expected: Int?) {
        if (expected == null) {
            assertTrue(cursor.isNull(index))
        } else {
            assertEquals(expected, cursor.getInt(index))
        }
    }

    private companion object {
        const val TEST_DB = "app-database-migration"
    }
}
