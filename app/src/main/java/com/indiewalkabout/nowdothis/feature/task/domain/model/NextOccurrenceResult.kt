package com.indiewalkabout.nowdothis.feature.task.domain.model

sealed interface NextOccurrenceResult {
    data class Next(val dueAt: Long) : NextOccurrenceResult

    data object Ended : NextOccurrenceResult

    data class Invalid(val reason: Reason) : NextOccurrenceResult

    enum class Reason {
        MISSING_DUE_DATE,
        OVERFLOW,
        INVALID_RULE
    }
}
