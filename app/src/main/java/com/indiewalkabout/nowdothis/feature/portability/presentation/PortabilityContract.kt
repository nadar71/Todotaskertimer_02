package com.indiewalkabout.nowdothis.feature.portability.presentation

import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityError

data class PortabilityUiState(
    val isBusy: Boolean = false,
    val candidate: BackupSummary? = null,
    val showRestoreConfirmation: Boolean = false,
    val result: PortabilityUiResult? = null,
    val error: PortabilityError? = null
)

sealed interface PortabilityUiResult {
    data class Exported(val summary: BackupSummary) : PortabilityUiResult

    data class Restored(val summary: BackupSummary) : PortabilityUiResult

    data class RestoredWithReminderWarning(val summary: BackupSummary) : PortabilityUiResult
}

sealed interface PortabilityEvent {
    data object CreateBackup : PortabilityEvent

    data class BackupDestinationSelected(val reference: DocumentReference?) : PortabilityEvent

    data object RestoreBackup : PortabilityEvent

    data class BackupSourceSelected(val reference: DocumentReference?) : PortabilityEvent

    data object ConfirmRestore : PortabilityEvent

    data object DismissRestore : PortabilityEvent

    data object ClearResult : PortabilityEvent
}

sealed interface PortabilityEffect {
    data class LaunchCreateDocument(val suggestedName: String) : PortabilityEffect

    data object LaunchOpenDocument : PortabilityEffect

    data class ShowMessage(val message: PortabilityMessage) : PortabilityEffect
}

sealed interface PortabilityMessage {
    data class Error(val error: PortabilityError) : PortabilityMessage

    data object ReminderWarning : PortabilityMessage
}
