package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec()

    @Test
    fun encode_usesV1FormatAndPreservesEveryPersistedPlanningField() {
        val backup = sampleBackup()

        val encoded = codec.encode(backup).decodeToString()
        val decoded = codec.decode(encoded.encodeToByteArray())

        assertTrue(encoded.contains("\"format\":\"now-do-this-backup\""))
        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.contains("\"description\":\"Detailed description\""))
        assertTrue(encoded.contains("\"reminderStatus\":\"SCHEDULED\""))
        assertTrue(encoded.contains("\"recurrence\":\"WEEKLY\""))
        assertTrue(encoded.contains("\"seriesId\":\"weekly-series\""))
        assertEquals(backup.sorted(), decoded)
    }

    @Test
    fun encode_sortsCategoriesTasksAndSubtasksDeterministically() {
        val backup = sampleBackup().copy(
            categories = listOf(
                category(id = 9, position = 2, customName = "Later"),
                category(id = 4, position = 0, defaultKey = "WORK")
            ),
            tasks = listOf(
                task(id = 8, subtasks = listOf(subtask(id = 9, taskId = 8, position = 2), subtask(id = 7, taskId = 8, position = 0))),
                task(id = 3, subtasks = listOf(subtask(id = 6, taskId = 3, position = 0)))
            )
        )

        val decoded = codec.decode(codec.encode(backup))

        assertEquals(listOf(4, 9), decoded.categories.map(PlanningCategory::id))
        assertEquals(listOf(3, 8), decoded.tasks.map(PlanningTask::id))
        assertEquals(listOf(7, 9), decoded.tasks.last().subtasks.map(PlanningSubtask::id))
        assertEquals(codec.encode(backup).toList(), codec.encode(backup.sorted()).toList())
    }

    @Test
    fun decode_ignoresUnknownRootAndNestedKeys() {
        val encoded = codec.encode(sampleBackup()).decodeToString()
            .replace("\"format\":", "\"futureRootField\":true,\"format\":")
            .replace("\"title\":\"Task\"", "\"futureTaskField\":42,\"title\":\"Task\"")
            .replace("\"title\":\"First\"", "\"futureSubtaskField\":\"value\",\"title\":\"First\"")

        assertEquals(sampleBackup().sorted(), codec.decode(encoded.encodeToByteArray()))
    }

    private fun sampleBackup() = PlanningBackup(
        format = "now-do-this-backup",
        version = 1,
        createdAtEpochMillis = 1_726_000_000_000,
        categories = listOf(category(id = 4, position = 0, defaultKey = "WORK")),
        tasks = listOf(
            task(
                id = 3,
                categoryId = 4,
                subtasks = listOf(
                    subtask(id = 6, taskId = 3, position = 0, completed = true),
                    subtask(id = 7, taskId = 3, position = 1)
                )
            )
        )
    )

    private fun category(
        id: Int,
        position: Int,
        customName: String? = null,
        defaultKey: String? = null
    ) = PlanningCategory(
        id = id,
        customName = customName,
        defaultKey = defaultKey,
        colorToken = "BLUE",
        position = position,
        createdAt = 10
    )

    private fun task(
        id: Int,
        categoryId: Int? = null,
        subtasks: List<PlanningSubtask> = emptyList()
    ) = PlanningTask(
        id = id,
        title = "Task",
        description = "Detailed description",
        priority = "HIGH",
        categoryId = categoryId,
        isCompleted = true,
        completedAt = 21,
        dueAt = 30,
        reminderAt = 25,
        reminderStatus = "SCHEDULED",
        recurrence = "WEEKLY",
        recurrenceEndAt = 40,
        seriesId = "weekly-series",
        createdAt = 11,
        updatedAt = 12,
        subtasks = subtasks
    )

    private fun subtask(
        id: Int,
        taskId: Int,
        position: Int,
        completed: Boolean = false
    ) = PlanningSubtask(
        id = id,
        taskId = taskId,
        title = if (position == 0) "First" else "Second",
        isCompleted = completed,
        completedAt = if (completed) 20 else null,
        position = position
    )

    private fun PlanningBackup.sorted() = copy(
        categories = categories.sortedWith(compareBy(PlanningCategory::position, PlanningCategory::id)),
        tasks = tasks.sortedBy(PlanningTask::id).map { task ->
            task.copy(subtasks = task.subtasks.sortedWith(compareBy(PlanningSubtask::position, PlanningSubtask::id)))
        }
    )
}
