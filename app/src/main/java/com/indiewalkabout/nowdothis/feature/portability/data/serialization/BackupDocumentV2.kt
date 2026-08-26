@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.time.DayOfWeek
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
internal data class BackupDocumentV2(
    val format: String,
    val version: Int,
    val createdAtEpochMillis: Long,
    val categories: List<BackupCategoryV2>,
    val tasks: List<BackupTaskV2>
) {
    fun toDomain(): PlanningBackup = PlanningBackup(
        format = format,
        version = version,
        createdAtEpochMillis = createdAtEpochMillis,
        categories = categories.map(BackupCategoryV2::toDomain),
        tasks = tasks.map(BackupTaskV2::toDomain)
    )

    companion object {
        const val VERSION = PlanningBackup.CURRENT_VERSION

        fun fromDomain(backup: PlanningBackup): BackupDocumentV2 = BackupDocumentV2(
            format = PlanningBackup.FORMAT,
            version = VERSION,
            createdAtEpochMillis = backup.createdAtEpochMillis,
            categories = backup.categories
                .sortedWith(compareBy(PlanningCategory::position, PlanningCategory::id))
                .map(BackupCategoryV2::fromDomain),
            tasks = backup.tasks
                .sortedBy(PlanningTask::id)
                .map(BackupTaskV2::fromDomain)
        )
    }
}

@Serializable
internal data class BackupCategoryV2(
    val id: Int,
    val customName: String?,
    val defaultKey: String?,
    val colorToken: String,
    val position: Int,
    val createdAt: Long
) {
    fun toDomain(): PlanningCategory = PlanningCategory(id, customName, defaultKey, colorToken, position, createdAt)

    companion object {
        fun fromDomain(category: PlanningCategory) = BackupCategoryV2(
            id = category.id,
            customName = category.customName,
            defaultKey = category.defaultKey,
            colorToken = category.colorToken,
            position = category.position,
            createdAt = category.createdAt
        )
    }
}

@Serializable
internal data class BackupTaskV2(
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
    val recurrence: BackupRecurrenceV2,
    val recurrenceEndAt: Long?,
    val seriesId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val subtasks: List<BackupSubtaskV2>
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
        recurrenceRule = recurrence.toDomain(),
        recurrenceEndAt = recurrenceEndAt,
        seriesId = seriesId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        subtasks = subtasks.map(BackupSubtaskV2::toDomain)
    )

    companion object {
        fun fromDomain(task: PlanningTask) = BackupTaskV2(
            id = task.id,
            title = task.title,
            description = task.description,
            priority = task.priority,
            categoryId = task.categoryId,
            isCompleted = task.isCompleted,
            completedAt = task.completedAt,
            dueAt = task.dueAt,
            reminderAt = task.reminderAt,
            reminderStatus = task.reminderStatus,
            recurrence = BackupRecurrenceV2.fromDomain(task.recurrenceRule),
            recurrenceEndAt = task.recurrenceEndAt,
            seriesId = task.seriesId,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            subtasks = task.subtasks
                .sortedWith(compareBy(PlanningSubtask::position, PlanningSubtask::id))
                .map(BackupSubtaskV2::fromDomain)
        )
    }
}

@Serializable
internal data class BackupRecurrenceV2(
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val every: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val basis: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val weekdays: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val anchorDay: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val everyMonths: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val ordinal: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val weekday: String? = null
) {
    fun toDomain(): RecurrenceRule = when (kind) {
        "NONE" -> RecurrenceRule.None
        "INTERVAL" -> RecurrenceRule.Interval(
            unit = requiredEnum(unit, "unit"),
            every = required(every, "every"),
            basis = requiredEnum(basis, "basis")
        )
        "SELECTED_WEEKDAYS" -> {
            val decodedWeekdays = required(weekdays, "weekdays")
                .map { value -> enumValue<DayOfWeek>(value, "weekdays") }
            require(decodedWeekdays.size == decodedWeekdays.toSet().size) {
                "Selected weekdays must not contain duplicates"
            }
            RecurrenceRule.SelectedWeekdays(
                weekdaySnapshot = decodedWeekdays.toSet(),
                basis = requiredEnum(basis, "basis")
            )
        }
        "MONTHLY_DAY" -> RecurrenceRule.MonthlyDay(
            anchorDay = required(anchorDay, "anchorDay"),
            everyMonths = required(everyMonths, "everyMonths"),
            basis = requiredEnum(basis, "basis")
        )
        "MONTHLY_ORDINAL" -> RecurrenceRule.MonthlyOrdinal(
            ordinal = requiredEnum(ordinal, "ordinal"),
            weekday = requiredEnum(weekday, "weekday"),
            everyMonths = required(everyMonths, "everyMonths"),
            basis = requiredEnum(basis, "basis")
        )
        else -> throw IllegalArgumentException("Unsupported v2 recurrence kind: $kind")
    }

    companion object {
        fun fromDomain(rule: RecurrenceRule): BackupRecurrenceV2 = when (rule) {
            RecurrenceRule.None -> BackupRecurrenceV2(kind = "NONE")
            is RecurrenceRule.Interval -> BackupRecurrenceV2(
                kind = "INTERVAL",
                unit = rule.unit.name,
                every = rule.every,
                basis = rule.basis.name
            )
            is RecurrenceRule.SelectedWeekdays -> BackupRecurrenceV2(
                kind = "SELECTED_WEEKDAYS",
                basis = rule.basis.name,
                weekdays = rule.weekdays.sortedBy(DayOfWeek::getValue).map(DayOfWeek::name)
            )
            is RecurrenceRule.MonthlyDay -> BackupRecurrenceV2(
                kind = "MONTHLY_DAY",
                basis = rule.basis.name,
                anchorDay = rule.anchorDay,
                everyMonths = rule.everyMonths
            )
            is RecurrenceRule.MonthlyOrdinal -> BackupRecurrenceV2(
                kind = "MONTHLY_ORDINAL",
                basis = rule.basis.name,
                everyMonths = rule.everyMonths,
                ordinal = rule.ordinal.name,
                weekday = rule.weekday.name
            )
        }
    }
}

@Serializable
internal data class BackupSubtaskV2(
    val id: Int,
    val taskId: Int,
    val title: String,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val position: Int
) {
    fun toDomain(): PlanningSubtask = PlanningSubtask(id, taskId, title, isCompleted, completedAt, position)

    companion object {
        fun fromDomain(subtask: PlanningSubtask) = BackupSubtaskV2(
            id = subtask.id,
            taskId = subtask.taskId,
            title = subtask.title,
            isCompleted = subtask.isCompleted,
            completedAt = subtask.completedAt,
            position = subtask.position
        )
    }
}

private fun <T : Any> required(value: T?, field: String): T =
    requireNotNull(value) { "V2 recurrence field $field is required" }

private inline fun <reified T : Enum<T>> requiredEnum(value: String?, field: String): T =
    enumValue(required(value, field), field)

private inline fun <reified T : Enum<T>> enumValue(value: String, field: String): T =
    enumValues<T>().firstOrNull { candidate -> candidate.name == value }
        ?: throw IllegalArgumentException("Unsupported v2 recurrence $field: $value")
