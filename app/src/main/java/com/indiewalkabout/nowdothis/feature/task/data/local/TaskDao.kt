package com.indiewalkabout.nowdothis.feature.task.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun observeTask(taskId: Int): Flow<TaskWithSubtasks?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTask(taskId: Int): TaskWithSubtasks?

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 0
          AND due_at < :endExclusive
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY due_at ASC, id ASC
        """
    )
    fun observeOverdue(
        endExclusive: Long,
        query: String,
        categoryId: Int?
    ): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 0
          AND due_at >= :start
          AND due_at < :end
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY due_at ASC, id ASC
        """
    )
    fun observeDueBetween(
        start: Long,
        end: Long,
        query: String,
        categoryId: Int?
    ): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 0
          AND due_at >= :startInclusive
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY due_at ASC, id ASC
        """
    )
    fun observeUpcoming(
        startInclusive: Long,
        query: String,
        categoryId: Int?
    ): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 0
          AND due_at IS NULL
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY id ASC
        """
    )
    fun observeUnscheduled(
        query: String,
        categoryId: Int?
    ): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 1
          AND completed_at >= :start
          AND completed_at < :end
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY completed_at DESC, id DESC
        """
    )
    fun observeCompletedBetween(
        start: Long,
        end: Long,
        query: String,
        categoryId: Int?
    ): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE (
                is_completed = 0
                OR (is_completed = 1 AND completed_at >= :start AND completed_at < :end)
              )
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY
            is_completed ASC,
            CASE WHEN is_completed = 0 THEN due_at END ASC,
            CASE WHEN is_completed = 1 THEN completed_at END DESC,
            CASE WHEN is_completed = 1 THEN id END DESC,
            id ASC
        """
    )
    fun observeSectionCandidates(
        start: Long,
        end: Long,
        query: String,
        categoryId: Int?
    ): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE due_at >= :start
          AND due_at < :end
        ORDER BY due_at ASC, id ASC
        """
    )
    fun observeMonth(start: Long, end: Long): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 1
          AND completed_at < :before
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY completed_at DESC, id DESC
        """
    )
    fun observeHistory(
        before: Long,
        query: String,
        categoryId: Int?
    ): Flow<List<TaskWithSubtasks>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(entity: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTasks(entities: List<TaskEntity>)

    @Update
    suspend fun updateTask(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Int)

    @Transaction
    suspend fun deleteTaskWithSnapshot(taskId: Int): TaskWithSubtasks? {
        val snapshot = getTask(taskId) ?: return null
        deleteTaskById(taskId)
        return snapshot
    }

    @Transaction
    suspend fun restoreTask(snapshot: TaskWithSubtasks) {
        insertTask(snapshot.task)
        if (snapshot.subtasks.isNotEmpty()) {
            insertRestoredSubtasks(snapshot.subtasks)
        }
    }

    @Transaction
    suspend fun replaceSubtasks(taskId: Int, subtasks: List<SubtaskEntity>) {
        deleteSubtasks(taskId)
        if (subtasks.isNotEmpty()) {
            insertSubtasks(subtasks.map { it.copy(taskId = taskId) })
        }
    }

    @Query("DELETE FROM subtasks WHERE task_id = :taskId")
    suspend fun deleteSubtasks(taskId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRestoredSubtasks(subtasks: List<SubtaskEntity>)

    @Query("SELECT id FROM subtasks WHERE id IN (:subtaskIds)")
    suspend fun existingSubtaskIds(subtaskIds: List<Int>): List<Int>

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun observeAllTaskEntities(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAllTaskEntities(): List<TaskEntity>

    @Query("SELECT * FROM subtasks ORDER BY task_id ASC, position ASC, id ASC")
    suspend fun getAllSubtaskEntities(): List<SubtaskEntity>

    @Query("SELECT id FROM tasks ORDER BY id ASC")
    suspend fun getAllTaskIds(): List<Int>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}
