package com.indiewalkabout.nowdothis.storemedia

import androidx.room.withTransaction
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.task.data.mapper.TaskEntityMapper
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.DayOfWeek

internal val STORE_MEDIA_SUPPORTED_LOCALES = setOf("it-IT", "en-US")

internal class StoreMediaFixture(
    private val database: AppDatabase
) {
    suspend fun prepare(localeTag: String) {
        val localizedTitles = requireNotNull(titles[localeTag]) { "Unsupported locale: $localeTag" }
        val localizedSubtasks = requireNotNull(subtaskTitles[localeTag]) {
            "Unsupported locale: $localeTag"
        }
        val tasks = tasks(localizedTitles, localizedSubtasks)

        database.withTransaction {
            database.taskDao().deleteAllTasks()
            database.categoryDao().deleteAll()
            database.categoryDao().insertAll(categories)

            val entities = tasks.map(TaskEntityMapper::toEntities)
            database.taskDao().insertTasks(entities.map { it.first })
            database.taskDao().insertSubtasks(entities.flatMap { it.second })
        }
    }

    private fun tasks(
        localizedTitles: List<String>,
        localizedSubtasks: List<String>
    ) = listOf(
        Task(
            id = 40_001,
            title = localizedTitles[0],
            description = "Store media planning",
            priority = TaskPriority.HIGH,
            categoryId = 1,
            dueAt = CAPTURE_DAY_MORNING_MILLIS,
            createdAt = CREATED_AT_MILLIS,
            updatedAt = CREATED_AT_MILLIS,
            subtasks = listOf(
                Subtask(50_001, 40_001, localizedSubtasks[0], position = 0),
                Subtask(50_002, 40_001, localizedSubtasks[1], position = 1)
            )
        ),
        Task(
            id = 40_002,
            title = localizedTitles[1],
            description = "Routine appointment",
            priority = TaskPriority.MEDIUM,
            categoryId = 2,
            dueAt = CAPTURE_DAY_AFTERNOON_MILLIS,
            reminderAt = CAPTURE_DAY_REMINDER_MILLIS,
            reminderStatus = ReminderStatus.SCHEDULED,
            createdAt = CREATED_AT_MILLIS,
            updatedAt = CREATED_AT_MILLIS,
            subtasks = listOf(Subtask(50_003, 40_002, localizedSubtasks[2], position = 0))
        ),
        Task(
            id = 40_003,
            title = localizedTitles[2],
            description = "Release readiness",
            priority = TaskPriority.HIGH,
            categoryId = 1,
            dueAt = CAPTURE_DAY_RECURRENCE_MILLIS,
            recurrenceRule = RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            seriesId = "store-media-release-plan",
            createdAt = CREATED_AT_MILLIS,
            updatedAt = CREATED_AT_MILLIS
        ),
        Task(
            id = 40_004,
            title = localizedTitles[3],
            description = "Weekend travel",
            priority = TaskPriority.MEDIUM,
            categoryId = 3,
            dueAt = CAPTURE_DAY_TOMORROW_MILLIS,
            createdAt = CREATED_AT_MILLIS,
            updatedAt = CREATED_AT_MILLIS
        ),
        Task(
            id = 40_005,
            title = localizedTitles[4],
            description = "Wellbeing routine",
            priority = TaskPriority.LOW,
            categoryId = 2,
            dueAt = CAPTURE_DAY_TOMORROW_MILLIS,
            createdAt = CREATED_AT_MILLIS,
            updatedAt = CREATED_AT_MILLIS
        ),
        Task(
            id = 40_006,
            title = localizedTitles[5],
            description = "August expenses",
            priority = TaskPriority.MEDIUM,
            categoryId = 1,
            isCompleted = true,
            completedAt = COMPLETED_AT_MILLIS,
            dueAt = COMPLETED_AT_MILLIS,
            createdAt = CREATED_AT_MILLIS,
            updatedAt = COMPLETED_AT_MILLIS
        )
    )

    private companion object {
        val categories = listOf(
            CategoryEntity(1, defaultKey = "WORK", colorToken = "BLUE", position = 0, createdAt = CREATED_AT_MILLIS),
            CategoryEntity(2, defaultKey = "PERSONAL", colorToken = "GREEN", position = 1, createdAt = CREATED_AT_MILLIS),
            CategoryEntity(3, defaultKey = "WISHLIST", colorToken = "PINK", position = 2, createdAt = CREATED_AT_MILLIS)
        )

        val titles = mapOf(
            "it-IT" to listOf("Preparare la presentazione", "Chiamare il dentista", "Rivedere il piano di rilascio", "Comprare i biglietti del treno", "Allenamento del mattino", "Inviare la nota spese"),
            "en-US" to listOf("Prepare the presentation", "Call the dentist", "Review the release plan", "Buy train tickets", "Morning workout", "Submit the expense report"),
        )

        val subtaskTitles = mapOf(
            "it-IT" to listOf("Raccogliere i punti chiave", "Verificare le slide", "Confermare l'appuntamento"),
            "en-US" to listOf("Gather key points", "Review the slides", "Confirm the appointment")
        )

        const val CREATED_AT_MILLIS = 1_787_814_000_000L
        const val COMPLETED_AT_MILLIS = 1_787_839_200_000L
        const val CAPTURE_DAY_MORNING_MILLIS = 1_787_900_400_000L
        const val CAPTURE_DAY_RECURRENCE_MILLIS = 1_787_904_000_000L
        const val CAPTURE_DAY_REMINDER_MILLIS = 1_787_918_400_000L
        const val CAPTURE_DAY_AFTERNOON_MILLIS = 1_787_922_000_000L
        const val CAPTURE_DAY_TOMORROW_MILLIS = 1_787_986_800_000L
    }
}
