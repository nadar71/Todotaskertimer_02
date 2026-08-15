package com.indiewalkabout.nowdothis.feature.portability.presentation

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityException
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.repository.PortabilityRepository
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.CreateBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.InspectBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.RestoreBackup
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortabilityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val rome = ZoneId.of("Europe/Rome")
    private val now = Instant.parse("2026-08-13T18:30:00Z").toEpochMilli()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createBackup_requestsDatedDocumentDestination() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val effect = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }

        viewModel.onEvent(PortabilityEvent.CreateBackup)

        assertEquals(
            PortabilityEffect.LaunchCreateDocument("now-do-this-backup-2026-08-13.json"),
            effect.await()
        )
    }

    @Test
    fun cancelledBackupDestination_doesNotExportOrReportFailure() = runTest(dispatcher) {
        val repository = FakePortabilityRepository()
        val viewModel = createViewModel(repository)

        viewModel.onEvent(PortabilityEvent.CreateBackup)
        assertTrue(viewModel.uiState.value.isBusy)
        viewModel.onEvent(PortabilityEvent.BackupDestinationSelected(null))
        advanceUntilIdle()

        assertTrue(repository.createdReferences.isEmpty())
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.result)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun selectedBackupDestination_exportsAndExposesResult() = runTest(dispatcher) {
        val summary = summary(taskCount = 3)
        val repository = FakePortabilityRepository().apply { exportedSummary = summary }
        val viewModel = createViewModel(repository)

        viewModel.onEvent(PortabilityEvent.CreateBackup)
        viewModel.onEvent(PortabilityEvent.BackupDestinationSelected(DocumentReference("content://backup")))
        advanceUntilIdle()

        assertEquals(listOf(DocumentReference("content://backup")), repository.createdReferences)
        assertEquals(PortabilityUiResult.Exported(summary), viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun restoreBackup_requestsSourcePicker() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val effect = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }

        viewModel.onEvent(PortabilityEvent.RestoreBackup)

        assertEquals(PortabilityEffect.LaunchOpenDocument, effect.await())
    }

    @Test
    fun cancelledBackupSource_keepsIdleState() = runTest(dispatcher) {
        val repository = FakePortabilityRepository()
        val viewModel = createViewModel(repository)

        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        assertTrue(viewModel.uiState.value.isBusy)
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(null))
        advanceUntilIdle()

        assertTrue(repository.inspectedReferences.isEmpty())
        assertNull(viewModel.uiState.value.candidate)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.showRestoreConfirmation)
    }

    @Test
    fun validSource_previewsCandidateAndShowsConfirmation() = runTest(dispatcher) {
        val candidate = candidate(summary(taskCount = 5))
        val repository = FakePortabilityRepository().apply { inspectedCandidate = candidate }
        val viewModel = createViewModel(repository)

        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(DocumentReference("content://restore")))
        advanceUntilIdle()

        assertEquals(listOf(DocumentReference("content://restore")), repository.inspectedReferences)
        assertEquals(candidate.summary, viewModel.uiState.value.candidate)
        assertTrue(viewModel.uiState.value.showRestoreConfirmation)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun invalidSource_exposesErrorWithoutReplacingExistingPreview() = runTest(dispatcher) {
        val original = candidate(summary(taskCount = 2))
        val repository = FakePortabilityRepository().apply { inspectedCandidate = original }
        val viewModel = createViewModel(repository)
        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(DocumentReference("content://valid")))
        advanceUntilIdle()

        repository.inspectionFailure = PortabilityException(InvalidBackup)
        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(DocumentReference("content://invalid")))
        advanceUntilIdle()

        assertEquals(original.summary, viewModel.uiState.value.candidate)
        assertEquals(InvalidBackup, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.showRestoreConfirmation)
    }

    @Test
    fun dismissRestore_revokesCandidateAndConfirmDoesNotRestore() = runTest(dispatcher) {
        val candidate = candidate(summary())
        val repository = FakePortabilityRepository().apply { inspectedCandidate = candidate }
        val viewModel = createViewModel(repository)
        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(DocumentReference("content://restore")))
        advanceUntilIdle()

        viewModel.onEvent(PortabilityEvent.DismissRestore)
        viewModel.onEvent(PortabilityEvent.ConfirmRestore)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showRestoreConfirmation)
        assertNull(viewModel.uiState.value.candidate)
        assertTrue(repository.restoredCandidates.isEmpty())
    }

    @Test
    fun confirmRestore_replacesValidatedCandidateAndExposesResult() = runTest(dispatcher) {
        val candidate = candidate(summary(taskCount = 4))
        val repository = FakePortabilityRepository().apply { inspectedCandidate = candidate }
        val viewModel = createViewModel(repository)
        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(DocumentReference("content://restore")))
        advanceUntilIdle()

        viewModel.onEvent(PortabilityEvent.ConfirmRestore)
        advanceUntilIdle()

        assertEquals(listOf(candidate), repository.restoredCandidates)
        assertEquals(PortabilityUiResult.Restored(candidate.summary), viewModel.uiState.value.result)
        assertNull(viewModel.uiState.value.candidate)
        assertFalse(viewModel.uiState.value.showRestoreConfirmation)
    }

    @Test
    fun pendingPicker_suppressesRepeatedAndCrossCommandTapsUntilCancellation() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val effects = mutableListOf<PortabilityEffect>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect(effects::add)
        }

        viewModel.onEvent(PortabilityEvent.CreateBackup)
        viewModel.onEvent(PortabilityEvent.CreateBackup)
        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(
            listOf(PortabilityEffect.LaunchCreateDocument("now-do-this-backup-2026-08-13.json")),
            effects
        )

        viewModel.onEvent(PortabilityEvent.BackupDestinationSelected(null))
        assertFalse(viewModel.uiState.value.isBusy)
        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        advanceUntilIdle()

        assertEquals(
            listOf(
                PortabilityEffect.LaunchCreateDocument("now-do-this-backup-2026-08-13.json"),
                PortabilityEffect.LaunchOpenDocument
            ),
            effects
        )
        collector.cancel()
    }

    @Test
    fun concurrentCommands_areSuppressedWhileOneOperationIsBusy() = runTest(dispatcher) {
        val repository = FakePortabilityRepository().apply { createGate = CompletableDeferred() }
        val viewModel = createViewModel(repository)

        viewModel.onEvent(PortabilityEvent.CreateBackup)
        viewModel.onEvent(PortabilityEvent.BackupDestinationSelected(DocumentReference("content://backup")))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBusy)

        viewModel.onEvent(PortabilityEvent.CreateBackup)
        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(DocumentReference("content://restore")))
        viewModel.onEvent(PortabilityEvent.ConfirmRestore)
        advanceUntilIdle()

        assertEquals(listOf(DocumentReference("content://backup")), repository.createdReferences)
        assertTrue(repository.inspectedReferences.isEmpty())
        assertTrue(repository.restoredCandidates.isEmpty())

        repository.createGate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun reminderWarning_exposesTerminalResultAndOneMessage() = runTest(dispatcher) {
        val candidate = candidate(summary(taskCount = 4))
        val repository = FakePortabilityRepository().apply { inspectedCandidate = candidate }
        val viewModel = createViewModel(repository, ReminderWarningScheduler)
        val openPicker = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }

        viewModel.onEvent(PortabilityEvent.RestoreBackup)
        assertEquals(PortabilityEffect.LaunchOpenDocument, openPicker.await())
        viewModel.onEvent(PortabilityEvent.BackupSourceSelected(DocumentReference("content://restore")))
        advanceUntilIdle()
        val warning = async(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.first() }

        viewModel.onEvent(PortabilityEvent.ConfirmRestore)
        advanceUntilIdle()

        assertEquals(
            PortabilityUiResult.RestoredWithReminderWarning(candidate.summary),
            viewModel.uiState.value.result
        )
        assertEquals(
            PortabilityEffect.ShowMessage(PortabilityMessage.ReminderWarning),
            warning.await()
        )
        val secondMessage = async(UnconfinedTestDispatcher(testScheduler)) {
            withTimeoutOrNull(1) { viewModel.effects.first() }
        }
        advanceTimeBy(1)
        assertNull(secondMessage.await())
    }

    @Test
    fun clearResult_removesOnlyTerminalResult() = runTest(dispatcher) {
        val repository = FakePortabilityRepository().apply { exportedSummary = summary() }
        val viewModel = createViewModel(repository)
        viewModel.onEvent(PortabilityEvent.CreateBackup)
        viewModel.onEvent(PortabilityEvent.BackupDestinationSelected(DocumentReference("content://backup")))
        advanceUntilIdle()

        viewModel.onEvent(PortabilityEvent.ClearResult)

        assertNull(viewModel.uiState.value.result)
        assertNull(viewModel.uiState.value.error)
    }

    private fun createViewModel(
        repository: FakePortabilityRepository = FakePortabilityRepository(),
        reminderScheduler: ReminderScheduler = NoOpReminderScheduler
    ) = PortabilityViewModel(
        createBackup = CreateBackup(repository),
        inspectBackup = InspectBackup(repository),
        restoreBackup = RestoreBackup(repository, reminderScheduler),
        clock = AppClock { now },
        zoneIdProvider = ZoneIdProvider { rome }
    )
}

private class FakePortabilityRepository : PortabilityRepository {
    val createdReferences = mutableListOf<DocumentReference>()
    val inspectedReferences = mutableListOf<DocumentReference>()
    val restoredCandidates = mutableListOf<BackupCandidate>()
    var exportedSummary: BackupSummary = summary()
    var inspectedCandidate: BackupCandidate = candidate(summary())
    var inspectionFailure: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun createBackup(reference: DocumentReference): PortabilityResult.Exported {
        createdReferences += reference
        createGate?.await()
        return PortabilityResult.Exported(exportedSummary)
    }

    override suspend fun inspectBackup(reference: DocumentReference): BackupCandidate {
        inspectedReferences += reference
        inspectionFailure?.let { throw it }
        return inspectedCandidate
    }

    override suspend fun replaceAll(candidate: BackupCandidate): Set<Int> {
        restoredCandidates += candidate
        return emptySet()
    }
}

private object NoOpReminderScheduler : ReminderScheduler {
    override suspend fun schedule(taskId: Int, triggerAt: Long) = ReminderScheduleResult.EXACT

    override suspend fun cancel(taskId: Int) = Unit

    override suspend fun reconcile() = Unit
}

private object ReminderWarningScheduler : ReminderScheduler {
    override suspend fun schedule(taskId: Int, triggerAt: Long) = ReminderScheduleResult.EXACT

    override suspend fun cancel(taskId: Int) = Unit

    override suspend fun reconcile() = error("Reminder reconciliation failed")
}

private fun summary(taskCount: Int = 1) = BackupSummary(
    createdAtEpochMillis = 1,
    categoryCount = 1,
    taskCount = taskCount,
    completedTaskCount = 0,
    subtaskCount = 0
)

private fun candidate(summary: BackupSummary) = BackupCandidate(
    backup = PlanningBackup(
        format = "now-do-this-backup",
        version = 1,
        createdAtEpochMillis = summary.createdAtEpochMillis,
        categories = emptyList(),
        tasks = emptyList()
    ),
    summary = summary
)
