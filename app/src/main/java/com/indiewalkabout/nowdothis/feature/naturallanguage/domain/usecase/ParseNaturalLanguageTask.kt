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
        }.disjointConsumedRanges(input.rawText)
        val dueAt = temporal.dueAt.takeIf {
            consumed.any { match -> match.field == RecognizedField.DUE_DATE }
        }
        val reminderAt = reminder.reminderAt.takeIf {
            consumed.any { match -> match.field == RecognizedField.REMINDER }
        }
        val priority = attributes.priority.takeIf {
            consumed.any { match -> match.field == RecognizedField.PRIORITY }
        }
        val categoryId = attributes.categoryId.takeIf {
            consumed.any { match -> match.field == RecognizedField.CATEGORY }
        }
        val recurrence = attributes.recurrence.takeIf {
            consumed.any { match -> match.field == RecognizedField.RECURRENCE }
        }
        val title = TextNormalizer.remainingTitle(input.rawText, consumed).takeIf(String::isNotBlank)
        val recognized = buildSet {
            if (title != null) add(RecognizedField.TITLE)
            if (dueAt != null) add(RecognizedField.DUE_DATE)
            if (reminderAt != null) add(RecognizedField.REMINDER)
            if (priority != null) add(RecognizedField.PRIORITY)
            if (categoryId != null) add(RecognizedField.CATEGORY)
            if (recurrence != null) add(RecognizedField.RECURRENCE)
        }

        return NaturalLanguageParseResult(
            draft = ParsedTaskDraft(
                title = title,
                dueAt = dueAt,
                reminderAt = reminderAt,
                priority = priority,
                categoryId = categoryId,
                recurrence = recurrence
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

    private fun List<SourceMatch>.disjointConsumedRanges(raw: String): List<SourceMatch> {
        val valid = filter { match ->
            match.start >= 0 && match.endExclusive <= raw.length && match.start < match.endExclusive
        }
        return valid.filterIndexed { index, candidate ->
            valid.withIndex().none { (otherIndex, other) ->
                index != otherIndex && candidate.intersects(other)
            }
        }.sortedBy(SourceMatch::start)
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
