package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.immutableListSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.util.Locale

@ConsistentCopyVisibility
data class AttributeParse private constructor(
    val priority: TaskPriority?,
    val categoryId: Int?,
    val recurrence: RecurrenceType?,
    private val matchSnapshot: List<SourceMatch>,
    private val issueSnapshot: List<ParseIssue>
) {
    val matches: List<SourceMatch>
        get() = matchSnapshot

    val issues: List<ParseIssue>
        get() = issueSnapshot

    companion object {
        operator fun invoke(
            priority: TaskPriority?,
            categoryId: Int?,
            recurrence: RecurrenceType?,
            matches: List<SourceMatch>,
            issues: List<ParseIssue>
        ): AttributeParse = AttributeParse(
            priority = priority,
            categoryId = categoryId,
            recurrence = recurrence,
            matchSnapshot = immutableListSnapshot(matches),
            issueSnapshot = immutableListSnapshot(issues)
        )
    }
}

class AttributeParser {

    fun parse(input: NaturalLanguageInput): AttributeParse = parse(
        input = input,
        excludedRanges = emptyList()
    )

    internal fun parse(
        input: NaturalLanguageInput,
        excludedRanges: List<SourceMatch>
    ): AttributeParse {
        val markerRanges = ownedMarkerRanges(input)
        val lowerGrammarExclusions = markerRanges + excludedRanges
        val categoryResult = parseCategories(input)
        val priorities = parsePriorities(input)
            .rejectIntersecting(lowerGrammarExclusions)
        val recurrences = parseRecurrences(input)
            .rejectIntersecting(lowerGrammarExclusions)
        val issues = buildList {
            addAll(categoryResult.issues)
            addDuplicateIssue(priorities, RecognizedField.PRIORITY)
            addDuplicateIssue(categoryResult.candidates, RecognizedField.CATEGORY)
            addDuplicateIssue(recurrences, RecognizedField.RECURRENCE)
        }
        val matches = buildList {
            addAll(priorities.map(AttributeCandidate<TaskPriority>::match))
            addAll(categoryResult.candidates.map(AttributeCandidate<Int>::match))
            addAll(recurrences.map(AttributeCandidate<RecurrenceType>::match))
        }.sortedBy(SourceMatch::start)

        return AttributeParse(
            priority = priorities.lastOrNull()?.value,
            categoryId = categoryResult.candidates.lastOrNull()?.value,
            recurrence = recurrences.lastOrNull()?.value,
            matches = matches,
            issues = issues
        )
    }

    internal fun ownedMarkerRanges(input: NaturalLanguageInput): List<SourceMatch> =
        categoryOwnershipPattern.findAll(input.rawText)
            .map { match -> match.toSourceMatch(RecognizedField.CATEGORY) }
            .toList()

    private fun parsePriorities(
        input: NaturalLanguageInput
    ): List<AttributeCandidate<TaskPriority>> {
        val values = when (input.language) {
            ParserLanguage.ITALIAN -> mapOf(
                "alta" to TaskPriority.HIGH,
                "media" to TaskPriority.MEDIUM,
                "bassa" to TaskPriority.LOW
            )

            ParserLanguage.ENGLISH -> mapOf(
                "high" to TaskPriority.HIGH,
                "medium" to TaskPriority.MEDIUM,
                "low" to TaskPriority.LOW
            )
        }
        return priorityPattern(input.language).findAll(input.rawText).map { match ->
            AttributeCandidate(
                value = requireNotNull(values[match.groupValues[1].lowercase(Locale.ROOT)]),
                match = match.toSourceMatch(RecognizedField.PRIORITY)
            )
        }.toList()
    }

    private fun parseRecurrences(
        input: NaturalLanguageInput
    ): List<AttributeCandidate<RecurrenceType>> = recurrencePattern(input.language)
        .findAll(input.rawText)
        .map { match ->
            val recurrence = when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "giorno", "day" -> RecurrenceType.DAILY
                "settimana", "week" -> RecurrenceType.WEEKLY
                "mese", "month" -> RecurrenceType.MONTHLY
                else -> error("Unexpected recurrence token")
            }
            AttributeCandidate(
                value = recurrence,
                match = match.toSourceMatch(RecognizedField.RECURRENCE)
            )
        }
        .toList()

    private fun parseCategories(input: NaturalLanguageInput): CategoryParse {
        val candidates = mutableListOf<AttributeCandidate<Int>>()
        val issues = mutableListOf<ParseIssue>()
        val categoriesByKey = input.categories.groupBy { candidate ->
            TextNormalizer.matchingKey(candidate.displayName)
        }

        categoryPattern.findAll(input.rawText).forEach { match ->
            val marker = match.value
            val sourceMatch = match.toSourceMatch(RecognizedField.CATEGORY)
            val markerValue = requireNotNull(TextNormalizer.categoryMarkerValue(marker))
            val matches = categoriesByKey[TextNormalizer.matchingKey(markerValue)].orEmpty()
            when (matches.size) {
                0 -> issues += ParseIssue.UnknownCategory(marker)
                1 -> {
                    candidates += AttributeCandidate(
                        value = matches.single().id,
                        match = sourceMatch
                    )
                }

                else -> issues += ParseIssue.AmbiguousCategory(marker)
            }
        }
        return CategoryParse(candidates = candidates, issues = issues)
    }

    private fun <T> List<AttributeCandidate<T>>.rejectIntersecting(
        excludedRanges: List<SourceMatch>
    ): List<AttributeCandidate<T>> = filterNot { candidate ->
        excludedRanges.any { excluded -> candidate.match.intersects(excluded) }
    }

    private fun SourceMatch.intersects(other: SourceMatch): Boolean =
        start < other.endExclusive && other.start < endExclusive

    private fun <T> MutableList<ParseIssue>.addDuplicateIssue(
        candidates: List<AttributeCandidate<T>>,
        field: RecognizedField
    ) {
        if (candidates.size > 1) add(ParseIssue.DuplicateField(field))
    }

    private fun MatchResult.toSourceMatch(field: RecognizedField): SourceMatch = SourceMatch(
        start = range.first,
        endExclusive = range.last + 1,
        field = field
    )

    private data class AttributeCandidate<T>(
        val value: T,
        val match: SourceMatch
    )

    private data class CategoryParse(
        val candidates: List<AttributeCandidate<Int>>,
        val issues: List<ParseIssue>
    )

    private companion object {
        val categoryPattern = Regex(
            "(?<![\\p{L}\\p{N}_#])#(?:\"[^\"\\r\\n]+\"|[^\\s#\"]+)"
        )
        val categoryOwnershipPattern = Regex(
            "(?<![\\p{L}\\p{N}_#])#+(?:\"[^\"\\r\\n]*(?:\"|(?=[\\r\\n]|$))|[^\\s#\"]+)"
        )
        val ITALIAN_PRIORITY_PATTERN = markerPattern("alta|media|bassa")
        val ENGLISH_PRIORITY_PATTERN = markerPattern("high|medium|low")
        val ITALIAN_RECURRENCE_PATTERN = phrasePattern("ogni\\s+(giorno|settimana|mese)")
        val ENGLISH_RECURRENCE_PATTERN = phrasePattern("every\\s+(day|week|month)")

        fun priorityPattern(language: ParserLanguage): Regex = when (language) {
            ParserLanguage.ITALIAN -> ITALIAN_PRIORITY_PATTERN
            ParserLanguage.ENGLISH -> ENGLISH_PRIORITY_PATTERN
        }

        fun recurrencePattern(language: ParserLanguage): Regex = when (language) {
            ParserLanguage.ITALIAN -> ITALIAN_RECURRENCE_PATTERN
            ParserLanguage.ENGLISH -> ENGLISH_RECURRENCE_PATTERN
        }

        fun markerPattern(values: String): Regex = Regex(
            "(?<![\\p{L}\\p{N}_!])!($values)(?![\\p{L}\\p{N}_])",
            RegexOption.IGNORE_CASE
        )

        fun phrasePattern(value: String): Regex = Regex(
            "(?<![\\p{L}\\p{N}_])$value(?![\\p{L}\\p{N}_])",
            RegexOption.IGNORE_CASE
        )
    }
}
