package com.indiewalkabout.nowdothis.feature.task.domain.model

sealed interface AtomicCompletionResult {
    data object NotFound : AtomicCompletionResult
    data object AlreadyCompleted : AtomicCompletionResult

    data class Completed(
        val completed: Task,
        val nextOccurrence: Task?
    ) : AtomicCompletionResult
}
