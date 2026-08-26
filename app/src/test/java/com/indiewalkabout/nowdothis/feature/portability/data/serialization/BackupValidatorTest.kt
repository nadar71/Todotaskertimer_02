package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupValidationResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentTooLarge
import com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.UnsupportedFutureVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {
    private val validator = BackupValidator()

    @Test
    fun validate_returnsSummaryForValidBackupAtExactSizeLimit() {
        val result = validator.validate(validBackup(), BackupValidator.MAX_DOCUMENT_SIZE_BYTES)

        assertEquals(
            BackupValidationResult.Valid(
                summary = com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary(
                    createdAtEpochMillis = 100,
                    categoryCount = 1,
                    taskCount = 2,
                    completedTaskCount = 1,
                    subtaskCount = 2
                )
            ),
            result
        )
    }

    @Test
    fun validate_rejectsDocumentLargerThanTenMiB() {
        assertError<DocumentTooLarge>(validator.validate(validBackup(), BackupValidator.MAX_DOCUMENT_SIZE_BYTES + 1))
    }

    @Test
    fun validate_rejectsWrongAndFutureVersions() {
        assertError<InvalidBackup>(validate(validBackup().copy(format = "another-app")))
        assertError<InvalidBackup>(validate(validBackup().copy(version = 0)))
        val future = validate(validBackup().copy(version = 2))
        assertEquals(BackupValidationResult.Invalid(UnsupportedFutureVersion(2)), future)
    }

    @Test
    fun validate_acceptsZeroBasedPositions() {
        assertTrue(validate(validBackup()) is BackupValidationResult.Valid)
    }

    @Test
    fun validate_rejectsDuplicateOrNonPositiveIdsAndNegativePositions() {
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(), category()))))
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(id = 0)))))
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(position = -1)))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(id = 0)))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(id = 1), task(id = 1)))) )
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(subtasks = listOf(subtask(id = 0)))))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(subtasks = listOf(subtask(id = 1), subtask(id = 1, position = 1)))))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(subtasks = listOf(subtask(position = -1)))))))
    }

    @Test
    fun validate_rejectsBrokenReferencesAndInvalidStableNames() {
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(categoryId = 99)))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(subtasks = listOf(subtask(taskId = 99)))))))
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(colorToken = "PURPLE")))))
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(defaultKey = "HOME")))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(priority = "URGENT")))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(reminderStatus = "BROKEN")))))
    }

    @Test
    fun validate_rejectsInvalidNamesCompletionAndRecurrenceRange() {
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(customName = null, defaultKey = null)))))
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(customName = " ", defaultKey = null)))))
        assertError<InvalidBackup>(validate(validBackup().copy(categories = listOf(category(customName = "Custom", defaultKey = "WORK")))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(title = " ")))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(isCompleted = true, completedAt = null)))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(isCompleted = false, completedAt = 1)))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(subtasks = listOf(subtask(isCompleted = true, completedAt = null)))))))
        assertError<InvalidBackup>(validate(validBackup().copy(tasks = listOf(task(dueAt = 20, recurrenceEndAt = 19)))))
        assertError<InvalidBackup>(
            validate(
                validBackup().copy(
                    tasks = listOf(task(recurrenceRule = weeklyRule, dueAt = null))
                )
            )
        )
        assertError<InvalidBackup>(
            validate(
                validBackup().copy(
                    tasks = listOf(task(recurrenceRule = RecurrenceRule.None, recurrenceEndAt = 20))
                )
            )
        )
    }

    private inline fun <reified T> assertError(result: BackupValidationResult) {
        assertTrue(result is BackupValidationResult.Invalid && result.error is T)
    }

    private fun validate(backup: PlanningBackup) = validator.validate(backup, documentSizeBytes = 0)

    private fun validBackup() = PlanningBackup(
        format = "now-do-this-backup",
        version = 1,
        createdAtEpochMillis = 100,
        categories = listOf(category()),
        tasks = listOf(
            task(
                id = 1,
                categoryId = 1,
                isCompleted = true,
                completedAt = 10,
                subtasks = listOf(subtask(id = 1, taskId = 1, position = 0), subtask(id = 2, taskId = 1, position = 1))
            ),
            task(id = 2, categoryId = 1)
        )
    )

    private fun category(
        id: Int = 1,
        customName: String? = null,
        defaultKey: String? = "WORK",
        colorToken: String = "BLUE",
        position: Int = 0
    ) = PlanningCategory(id, customName, defaultKey, colorToken, position, createdAt = 1)

    private fun task(
        id: Int = 1,
        title: String = "Task",
        priority: String = "HIGH",
        categoryId: Int? = 1,
        isCompleted: Boolean = false,
        completedAt: Long? = null,
        reminderStatus: String = "NONE",
        recurrenceRule: RecurrenceRule = RecurrenceRule.None,
        dueAt: Long? = null,
        recurrenceEndAt: Long? = null,
        subtasks: List<PlanningSubtask> = emptyList()
    ) = PlanningTask(
        id = id,
        title = title,
        description = "Description",
        priority = priority,
        categoryId = categoryId,
        isCompleted = isCompleted,
        completedAt = completedAt,
        dueAt = dueAt,
        reminderAt = null,
        reminderStatus = reminderStatus,
        recurrenceRule = recurrenceRule,
        recurrenceEndAt = recurrenceEndAt,
        seriesId = null,
        createdAt = 1,
        updatedAt = 2,
        subtasks = subtasks
    )

    private fun subtask(
        id: Int = 1,
        taskId: Int = 1,
        position: Int = 0,
        isCompleted: Boolean = false,
        completedAt: Long? = null
    ) = PlanningSubtask(id, taskId, "Subtask", isCompleted, completedAt, position)

    private val weeklyRule = RecurrenceRule.Interval(
        IntervalUnit.WEEKS,
        1,
        RecurrenceBasis.SCHEDULED_DATE
    )
}
