package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

@Serializable
internal data class BackupDocumentV1(
    val format: String,
    val version: Int,
    val createdAtEpochMillis: Long,
    val categories: List<BackupCategoryV1>,
    val tasks: List<BackupTaskV1>
) {
    fun toDomain(): PlanningBackup = PlanningBackup(
        format = format,
        version = version,
        createdAtEpochMillis = createdAtEpochMillis,
        categories = categories.map(BackupCategoryV1::toDomain),
        tasks = tasks.map(BackupTaskV1::toDomain)
    )

    companion object {
        const val FORMAT = PlanningBackup.FORMAT
        const val VERSION = 1
    }
}

@Serializable
internal data class BackupCategoryV1(
    val id: Int,
    val customName: String?,
    val defaultKey: String?,
    val colorToken: String,
    val position: Int,
    val createdAt: Long
) {
    fun toDomain(): PlanningCategory = PlanningCategory(id, customName, defaultKey, colorToken, position, createdAt)
}

@Serializable
internal data class BackupTaskV1(
    val id: Int,
    val title: String,
    val description: String,
    val priority: String,
    val categoryId: Int?,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val dueAt: Long?,
    val reminderAt: Long?,
    val reminderStatus: String,
    val recurrence: String,
    val recurrenceEndAt: Long?,
    val seriesId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val subtasks: List<BackupSubtaskV1>
) {
    fun toDomain(): PlanningTask = PlanningTask(
        id = id,
        title = title,
        description = description,
        priority = priority,
        categoryId = categoryId,
        isCompleted = isCompleted,
        completedAt = completedAt,
        dueAt = dueAt,
        reminderAt = reminderAt,
        reminderStatus = reminderStatus,
        recurrenceRule = recurrence.toRecurrenceRule(dueAt),
        recurrenceEndAt = recurrenceEndAt,
        seriesId = seriesId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        subtasks = subtasks.map(BackupSubtaskV1::toDomain)
    )
}

@Serializable
internal data class BackupSubtaskV1(
    val id: Int,
    val taskId: Int,
    val title: String,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val position: Int
) {
    fun toDomain(): PlanningSubtask = PlanningSubtask(id, taskId, title, isCompleted, completedAt, position)
}

private fun String.toRecurrenceRule(dueAt: Long?): RecurrenceRule = when (this) {
    "NONE" -> RecurrenceRule.None
    "DAILY" -> {
        requireLegacyDueAt(dueAt, this)
        RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
    }
    "WEEKLY" -> {
        requireLegacyDueAt(dueAt, this)
        RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
    }
    "MONTHLY" -> RecurrenceRule.MonthlyDay(
        anchorDay = Instant.ofEpochMilli(requireLegacyDueAt(dueAt, this))
            .atZone(ZoneId.systemDefault())
            .dayOfMonth,
        everyMonths = 1,
        basis = RecurrenceBasis.SCHEDULED_DATE
    )
    else -> throw IllegalArgumentException("Unsupported v1 recurrence: $this")
}

private fun requireLegacyDueAt(dueAt: Long?, recurrence: String): Long =
    requireNotNull(dueAt) { "V1 $recurrence recurrence requires dueAt" }
