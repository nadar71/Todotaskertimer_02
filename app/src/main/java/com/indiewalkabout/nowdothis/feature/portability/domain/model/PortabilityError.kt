package com.indiewalkabout.nowdothis.feature.portability.domain.model

sealed interface PortabilityError

data object InvalidBackup : PortabilityError

data class UnsupportedFutureVersion(val version: Int) : PortabilityError

data object DocumentTooLarge : PortabilityError

data object ReadFailed : PortabilityError

data object WriteFailed : PortabilityError

data object RestoreFailed : PortabilityError

sealed interface BackupValidationResult {
    data class Valid(val summary: BackupSummary) : BackupValidationResult

    data class Invalid(val error: PortabilityError) : BackupValidationResult
}
