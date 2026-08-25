package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model

import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority

data class ParsedTaskDraft(
    val title: String?,
    val dueAt: Long?,
    val reminderAt: Long?,
    val priority: TaskPriority?,
    val categoryId: Int?,
    val recurrence: RecurrenceType?
)

enum class RecognizedField {
    TITLE,
    DUE_DATE,
    REMINDER,
    PRIORITY,
    CATEGORY,
    RECURRENCE
}

sealed interface ParseIssue {
    data object EmptyInput : ParseIssue

    data class UnknownCategory(val marker: String) : ParseIssue

    data class AmbiguousCategory(val marker: String) : ParseIssue

    data class DuplicateField(val field: RecognizedField) : ParseIssue

    data object RelativeReminderWithoutDueDate : ParseIssue
}

data class NaturalLanguageParseResult(
    val draft: ParsedTaskDraft,
    val recognized: Set<RecognizedField>,
    val issues: List<ParseIssue>,
    val consumed: List<SourceMatch>
)

data class SourceMatch(
    val start: Int,
    val endExclusive: Int,
    val field: RecognizedField
)
