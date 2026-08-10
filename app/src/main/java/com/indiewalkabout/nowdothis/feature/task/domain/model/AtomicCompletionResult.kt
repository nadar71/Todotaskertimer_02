package com.indiewalkabout.nowdothis.feature.task.domain.model

data class AtomicCompletionResult(
    val completed: Task,
    val nextOccurrence: Task?
)
