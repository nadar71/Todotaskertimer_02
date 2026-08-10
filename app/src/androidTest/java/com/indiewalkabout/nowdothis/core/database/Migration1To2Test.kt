package com.indiewalkabout.nowdothis.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        const val TEST_DB = "migration-1-2"
    }
}
