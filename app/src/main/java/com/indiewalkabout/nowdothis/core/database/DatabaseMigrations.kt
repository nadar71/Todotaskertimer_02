package com.indiewalkabout.nowdothis.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createCategories(db)
        seedDefaultCategories(db)
        createTasks(db)
        db.execSQL(
            """
            INSERT INTO tasks_new (
                id, title, description, priority, category_id, is_completed,
                completed_at, due_at, reminder_at, reminder_status, recurrence,
                recurrence_end_at, series_id, created_at, updated_at
            )
            SELECT
                id, title, description, priority, NULL, 0,
                NULL, NULL, NULL, 'NONE', 'NONE',
                NULL, NULL, 0, 0
            FROM todo_table
            """.trimIndent()
        )
        db.execSQL("DROP TABLE todo_table")
        db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
        createSubtasks(db)
        createIndices(db)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN recurrence_kind TEXT NOT NULL DEFAULT 'NONE'"
        )
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN recurrence_interval_unit TEXT DEFAULT NULL"
        )
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN recurrence_interval_count INTEGER DEFAULT NULL"
        )
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence_basis TEXT DEFAULT NULL")
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN recurrence_weekday_mask INTEGER DEFAULT NULL"
        )
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN recurrence_anchor_day INTEGER DEFAULT NULL"
        )
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence_ordinal TEXT DEFAULT NULL")
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN recurrence_ordinal_weekday TEXT DEFAULT NULL"
        )

        db.execSQL("UPDATE tasks SET recurrence_kind = recurrence")
        db.execSQL(
            """
            UPDATE tasks SET
                recurrence_kind = 'INTERVAL',
                recurrence_interval_unit = 'DAYS',
                recurrence_interval_count = 1,
                recurrence_basis = 'SCHEDULED_DATE'
            WHERE recurrence = 'DAILY'
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE tasks SET
                recurrence_kind = 'INTERVAL',
                recurrence_interval_unit = 'WEEKS',
                recurrence_interval_count = 1,
                recurrence_basis = 'SCHEDULED_DATE'
            WHERE recurrence = 'WEEKLY'
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE tasks SET
                recurrence_kind = 'MONTHLY_DAY',
                recurrence_interval_count = 1,
                recurrence_basis = 'SCHEDULED_DATE'
            WHERE recurrence = 'MONTHLY'
            """.trimIndent()
        )

        val anchorUpdate = db.compileStatement(
            "UPDATE tasks SET recurrence_anchor_day = ? WHERE id = ?"
        )
        db.query(
            "SELECT id, due_at FROM tasks WHERE recurrence = 'MONTHLY' AND due_at IS NOT NULL"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val anchorDay = Instant.ofEpochMilli(cursor.getLong(1))
                    .atZone(ZoneId.systemDefault())
                    .dayOfMonth
                anchorUpdate.clearBindings()
                anchorUpdate.bindLong(1, anchorDay.toLong())
                anchorUpdate.bindLong(2, cursor.getLong(0))
                anchorUpdate.executeUpdateDelete()
            }
        }
    }
}

val DEFAULT_CATEGORIES_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedDefaultCategories(db)
    }
}

private fun createCategories(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            custom_name TEXT DEFAULT NULL,
            default_key TEXT DEFAULT NULL,
            color_token TEXT NOT NULL,
            position INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent()
    )
}

private fun seedDefaultCategories(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        INSERT OR IGNORE INTO categories
            (id, custom_name, default_key, color_token, position, created_at)
        VALUES
            (1, NULL, 'WORK', 'BLUE', 0, 0),
            (2, NULL, 'PERSONAL', 'GREEN', 1, 0),
            (3, NULL, 'WISHLIST', 'PINK', 2, 0)
        """.trimIndent()
    )
}

private fun createTasks(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS tasks_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            priority TEXT NOT NULL,
            category_id INTEGER DEFAULT NULL,
            is_completed INTEGER NOT NULL DEFAULT 0,
            completed_at INTEGER DEFAULT NULL,
            due_at INTEGER DEFAULT NULL,
            reminder_at INTEGER DEFAULT NULL,
            reminder_status TEXT NOT NULL DEFAULT 'NONE',
            recurrence TEXT NOT NULL DEFAULT 'NONE',
            recurrence_end_at INTEGER DEFAULT NULL,
            series_id TEXT DEFAULT NULL,
            created_at INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (category_id) REFERENCES categories (id)
                ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
    )
}

private fun createSubtasks(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS subtasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            task_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            is_completed INTEGER NOT NULL DEFAULT 0,
            completed_at INTEGER DEFAULT NULL,
            position INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (task_id) REFERENCES tasks (id)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
}

private fun createIndices(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_category_id ON tasks (category_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_due_at ON tasks (due_at)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_completed_at ON tasks (completed_at)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_is_completed ON tasks (is_completed)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_series_id ON tasks (series_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_subtasks_task_id ON subtasks (task_id)")
}
