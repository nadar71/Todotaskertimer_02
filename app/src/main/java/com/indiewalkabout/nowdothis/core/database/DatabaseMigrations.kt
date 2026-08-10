package com.indiewalkabout.nowdothis.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
