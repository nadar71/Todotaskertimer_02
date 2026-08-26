package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.usecase

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageParseResult
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParsedTaskDraft
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.AttributeParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.ReminderParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.TemporalParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.TextNormalizer

class ParseNaturalLanguageTask(
    private val temporalParser: TemporalParser,
    private val attributeParser: AttributeParser,
    private val reminderParser: ReminderParser
) {

    operator fun invoke(input: NaturalLanguageInput): NaturalLanguageParseResult {
        if (TextNormalizer.normalizeWhitespace(input.rawText).isBlank()) return emptyResult()

        val markerRanges = attributeParser.ownedMarkerRanges(input)
        val reminderClaims = reminderParser.shieldingRanges(input, markerRanges)
        val temporalExclusions = markerRanges + reminderClaims
        val temporal = temporalParser.parse(input.withBarrierRanges(temporalExclusions))
        val reminder = reminderParser.parse(input, temporal.dueAt, markerRanges)
        val attributes = attributeParser.parse(input, reminderClaims)
        val consumed = buildList {
            addAll(temporal.matches.rejectIntersecting(temporalExclusions))
            addAll(reminder.matches.rejectIntersecting(markerRanges))
            addAll(attributes.matches)
        }.validatedConsumedRanges(input.rawText)
        val title = TextNormalizer.remainingTitle(input.rawText, consumed).takeIf(String::isNotBlank)
        val recognized = buildSet {
            if (title != null) add(RecognizedField.TITLE)
            if (temporal.dueAt != null) add(RecognizedField.DUE_DATE)
            if (reminder.reminderAt != null) add(RecognizedField.REMINDER)
            if (attributes.priority != null) add(RecognizedField.PRIORITY)
            if (attributes.categoryId != null) add(RecognizedField.CATEGORY)
            if (attributes.recurrence != null) add(RecognizedField.RECURRENCE)
        }

        return NaturalLanguageParseResult(
            draft = ParsedTaskDraft(
                title = title,
                dueAt = temporal.dueAt,
                reminderAt = reminder.reminderAt,
                priority = attributes.priority,
                categoryId = attributes.categoryId,
                recurrence = attributes.recurrence
            ),
            recognized = recognized,
            issues = temporal.issues + reminder.issues + attributes.issues,
            consumed = consumed
        )
    }

    private fun NaturalLanguageInput.withBarrierRanges(ranges: List<SourceMatch>): NaturalLanguageInput {
        val shielded = rawText.toCharArray()
        ranges.forEach { range ->
            for (index in range.start until range.endExclusive) shielded[index] = RANGE_BARRIER
        }
        return NaturalLanguageInput(
            rawText = shielded.concatToString(),
            language = language,
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId,
            categories = categories
        )
    }

    private fun List<SourceMatch>.rejectIntersecting(
        excludedRanges: List<SourceMatch>
    ): List<SourceMatch> = filterNot { candidate ->
        excludedRanges.any { excluded -> candidate.intersects(excluded) }
    }

    private fun List<SourceMatch>.validatedConsumedRanges(raw: String): List<SourceMatch> =
        sortedBy(SourceMatch::start).also { sorted ->
            sorted.forEach { match ->
                require(match.start >= 0 && match.endExclusive <= raw.length && match.start < match.endExclusive) {
                    "Consumed range must be non-empty and within the raw input."
                }
            }
            sorted.zipWithNext().forEach { (current, next) ->
                require(!current.intersects(next)) { "Consumed ranges must be pairwise disjoint." }
            }
        }

    private fun SourceMatch.intersects(other: SourceMatch): Boolean =
        start < other.endExclusive && other.start < endExclusive

    private fun emptyResult(): NaturalLanguageParseResult = NaturalLanguageParseResult(
        draft = ParsedTaskDraft(
            title = null,
            dueAt = null,
            reminderAt = null,
            priority = null,
            categoryId = null,
            recurrence = null
        ),
        recognized = emptySet(),
        issues = listOf(ParseIssue.EmptyInput),
        consumed = emptyList()
    )

    private companion object {
        const val RANGE_BARRIER = '\uFFFF'
    }
}
