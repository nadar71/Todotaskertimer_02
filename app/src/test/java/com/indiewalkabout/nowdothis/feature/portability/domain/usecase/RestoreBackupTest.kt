package com.indiewalkabout.nowdothis.feature.portability.domain.usecase

import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityException
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.RestoreFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.repository.PortabilityRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBackupTest {
    @Test
    fun restore_replacesBeforeCancellingOldAlarmsThenReconciles() = runTest {
        val events = mutableListOf<String>()
        val candidate = candidate()
        val result = RestoreBackup(
            repository = FakePortabilityRepository(events, replacedIds = setOf(4, 8)),
            reminderScheduler = FakeReminderScheduler(events)
        )(candidate)

        assertEquals(
            listOf("replace", "cancel:4", "cancel:8", "reconcile"),
            events
        )
        assertEquals(PortabilityResult.Restored(candidate.summary), result)
    }

    @Test
    fun restore_doesNotTouchAlarmsWhenReplacementFails() = runTest {
        val events = mutableListOf<String>()
        val result = RestoreBackup(
            repository = FakePortabilityRepository(events, replacementFailure = PortabilityException(RestoreFailed)),
            reminderScheduler = FakeReminderScheduler(events)
        )(candidate())

        assertEquals(listOf("replace"), events)
        assertEquals(PortabilityResult.Failed(RestoreFailed), result)
    }

    @Test
    fun restore_returnsWarningWhenReminderWorkFailsWithoutRollingBackData() = runTest {
        val events = mutableListOf<String>()
        val candidate = candidate()
        val repository = FakePortabilityRepository(events, replacedIds = setOf(4))
        val result = RestoreBackup(
            repository = repository,
            reminderScheduler = FakeReminderScheduler(events, failReconcile = true)
        )(candidate)

        assertEquals(listOf("replace", "cancel:4", "reconcile"), events)
        assertEquals(1, repository.replaceCalls)
        assertEquals(PortabilityResult.RestoredWithReminderWarning(candidate.summary), result)
    }

    @Test
    fun restore_attemptsRemainingCancelsAndReconcileAfterACancelFailure() = runTest {
        val events = mutableListOf<String>()
        val candidate = candidate()
        val result = RestoreBackup(
            repository = FakePortabilityRepository(events, replacedIds = setOf(4, 8)),
            reminderScheduler = FakeReminderScheduler(events, failingCancelId = 4)
        )(candidate)

        assertEquals(
            listOf("replace", "cancel:4", "cancel:8", "reconcile"),
            events
        )
        assertTrue(result is PortabilityResult.RestoredWithReminderWarning)
    }
}

private class FakePortabilityRepository(
    private val events: MutableList<String>,
    private val replacedIds: Set<Int> = emptySet(),
    private val replacementFailure: Throwable? = null
) : PortabilityRepository {
    var replaceCalls = 0

    override suspend fun createBackup(
        reference: com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
    ): PortabilityResult.Exported = error("unused")

    override suspend fun inspectBackup(
        reference: com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
    ): BackupCandidate = error("unused")

    override suspend fun replaceAll(candidate: BackupCandidate): Set<Int> {
        replaceCalls += 1
        events += "replace"
        replacementFailure?.let { throw it }
        return replacedIds
    }
}

private class FakeReminderScheduler(
    private val events: MutableList<String>,
    private val failingCancelId: Int? = null,
    private val failReconcile: Boolean = false
) : ReminderScheduler {
    override suspend fun schedule(
        taskId: Int,
        triggerAt: Long
    ) = error("unused")

    override suspend fun cancel(taskId: Int) {
        events += "cancel:$taskId"
        if (taskId == failingCancelId) error("cancel failed")
    }

    override suspend fun reconcile() {
        events += "reconcile"
        if (failReconcile) error("reconcile failed")
    }
}

private fun candidate() = BackupCandidate(
    backup = PlanningBackup(
        format = "now-do-this-backup",
        version = 1,
        createdAtEpochMillis = 10L,
        categories = emptyList(),
        tasks = emptyList()
    ),
    summary = BackupSummary(10L, 0, 0, 0, 0)
)
