package com.indiewalkabout.nowdothis.feature.portability.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityError
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.CreateBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.InspectBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.RestoreBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PortabilityViewModel @Inject constructor(
    private val createBackup: CreateBackup,
    private val inspectBackup: InspectBackup,
    private val restoreBackup: RestoreBackup,
    private val clock: AppClock,
    private val zoneIdProvider: ZoneIdProvider
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PortabilityUiState())
    private val effectChannel = Channel<PortabilityEffect>(Channel.BUFFERED)
    private var validatedCandidate: BackupCandidate? = null
    private var operationJob: Job? = null

    val uiState = mutableUiState.asStateFlow()
    val effects: Flow<PortabilityEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: PortabilityEvent) {
        when (event) {
            PortabilityEvent.CreateBackup -> requestBackupDestination()
            is PortabilityEvent.BackupDestinationSelected -> exportTo(event.reference)
            PortabilityEvent.RestoreBackup -> requestBackupSource()
            is PortabilityEvent.BackupSourceSelected -> inspectSource(event.reference)
            PortabilityEvent.ConfirmRestore -> restoreCandidate()
            PortabilityEvent.DismissRestore -> dismissRestore()
            PortabilityEvent.ClearResult -> mutableUiState.value = mutableUiState.value.copy(
                result = null,
                error = null
            )
        }
    }

    private fun requestBackupDestination() {
        if (isOperationRunning()) return
        emitEffect(PortabilityEffect.LaunchCreateDocument(suggestedBackupName()))
    }

    private fun exportTo(reference: DocumentReference?) {
        if (reference == null || isOperationRunning()) return
        launchOperation {
            when (val result = createBackup(reference)) {
                is PortabilityResult.Exported -> mutableUiState.value = mutableUiState.value.copy(
                    result = PortabilityUiResult.Exported(result.summary),
                    error = null
                )
                is PortabilityResult.Failed -> reportError(result.error)
                else -> Unit
            }
        }
    }

    private fun requestBackupSource() {
        if (isOperationRunning()) return
        emitEffect(PortabilityEffect.LaunchOpenDocument)
    }

    private fun inspectSource(reference: DocumentReference?) {
        if (reference == null || isOperationRunning()) return
        launchOperation {
            when (val result = inspectBackup(reference)) {
                is PortabilityResult.Inspected -> acceptCandidate(result.candidate)
                is PortabilityResult.Failed -> reportError(result.error)
                else -> Unit
            }
        }
    }

    private fun acceptCandidate(candidate: BackupCandidate) {
        validatedCandidate = candidate
        mutableUiState.value = mutableUiState.value.copy(
            candidate = candidate.summary,
            showRestoreConfirmation = true,
            result = null,
            error = null
        )
    }

    private fun restoreCandidate() {
        val candidate = validatedCandidate ?: return
        if (isOperationRunning()) return
        launchOperation {
            when (val result = restoreBackup(candidate)) {
                is PortabilityResult.Restored -> finishRestore(PortabilityUiResult.Restored(result.summary))
                is PortabilityResult.RestoredWithReminderWarning -> {
                    finishRestore(PortabilityUiResult.RestoredWithReminderWarning(result.summary))
                    effectChannel.send(PortabilityEffect.ShowMessage(PortabilityMessage.ReminderWarning))
                }
                is PortabilityResult.Failed -> reportError(result.error)
                else -> Unit
            }
        }
    }

    private fun finishRestore(result: PortabilityUiResult) {
        validatedCandidate = null
        mutableUiState.value = mutableUiState.value.copy(
            candidate = null,
            showRestoreConfirmation = false,
            result = result,
            error = null
        )
    }

    private fun dismissRestore() {
        if (isOperationRunning()) return
        mutableUiState.value = mutableUiState.value.copy(showRestoreConfirmation = false)
    }

    private fun launchOperation(block: suspend () -> Unit) {
        if (isOperationRunning()) return
        mutableUiState.value = mutableUiState.value.copy(isBusy = true, result = null, error = null)
        operationJob = viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                mutableUiState.value = mutableUiState.value.copy(isBusy = false)
            }
        }
    }

    private suspend fun reportError(error: PortabilityError) {
        mutableUiState.value = mutableUiState.value.copy(error = error)
        effectChannel.send(PortabilityEffect.ShowMessage(PortabilityMessage.Error(error)))
    }

    private fun emitEffect(effect: PortabilityEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private fun isOperationRunning(): Boolean = operationJob?.isActive == true

    private fun suggestedBackupName(): String {
        val date = Instant.ofEpochMilli(clock.nowMillis())
            .atZone(zoneIdProvider.zoneId())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "now-do-this-backup-$date.json"
    }
}
