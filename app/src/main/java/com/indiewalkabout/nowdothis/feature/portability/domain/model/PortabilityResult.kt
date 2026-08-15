package com.indiewalkabout.nowdothis.feature.portability.domain.model

sealed interface PortabilityResult {
    data class Exported(val summary: BackupSummary) : PortabilityResult

    data class Inspected(val candidate: BackupCandidate) : PortabilityResult

    data class Restored(val summary: BackupSummary) : PortabilityResult

    data class RestoredWithReminderWarning(val summary: BackupSummary) : PortabilityResult

    data class Failed(val error: PortabilityError) : PortabilityResult
}

class PortabilityException(val error: PortabilityError) : RuntimeException()
