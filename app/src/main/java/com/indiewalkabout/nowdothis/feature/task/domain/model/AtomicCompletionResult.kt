package com.indiewalkabout.nowdothis.feature.task.domain.model

sealed interface AtomicCompletionResult {
    data object NotFound : AtomicCompletionResult
    data object AlreadyCompleted : AtomicCompletionResult
    data class Invalid(val reason: NextOccurrenceResult.Reason) : AtomicCompletionResult

    data class Completed(
        val completed: Task,
        val nextOccurrence: Task?
    ) : AtomicCompletionResult
}

sealed interface AtomicCompletionDecision {
    data class Create(val task: Task) : AtomicCompletionDecision
    data object CompleteOnly : AtomicCompletionDecision
    data class Invalid(val reason: NextOccurrenceResult.Reason) : AtomicCompletionDecision
}
