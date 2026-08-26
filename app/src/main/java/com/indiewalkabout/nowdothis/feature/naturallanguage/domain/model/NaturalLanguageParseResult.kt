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

@ConsistentCopyVisibility
data class NaturalLanguageParseResult private constructor(
    val draft: ParsedTaskDraft,
    private val recognizedSnapshot: Set<RecognizedField>,
    private val issueSnapshot: List<ParseIssue>,
    private val consumedSnapshot: List<SourceMatch>
) {
    val recognized: Set<RecognizedField>
        get() = recognizedSnapshot

    val issues: List<ParseIssue>
        get() = issueSnapshot

    val consumed: List<SourceMatch>
        get() = consumedSnapshot

    override fun toString(): String = "NaturalLanguageParseResult(" +
        "draft=$draft, recognized=$recognized, issues=$issues, consumed=$consumed)"

    companion object {
        operator fun invoke(
            draft: ParsedTaskDraft,
            recognized: Set<RecognizedField>,
            issues: List<ParseIssue>,
            consumed: List<SourceMatch>
        ): NaturalLanguageParseResult = NaturalLanguageParseResult(
            draft = draft,
            recognizedSnapshot = immutableSetSnapshot(recognized),
            issueSnapshot = immutableListSnapshot(issues),
            consumedSnapshot = immutableListSnapshot(consumed)
        )
    }
}

data class SourceMatch(
    val start: Int,
    val endExclusive: Int,
    val field: RecognizedField
)
