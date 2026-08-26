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
        val markerShieldedInput = input.withMaskedRanges(markerRanges)
        val reminderClaims = reminderParser.shieldingRanges(markerShieldedInput)
        val temporal = temporalParser.parse(
            input.withMaskedRanges(markerRanges + reminderClaims)
        )
        val reminder = reminderParser.parse(markerShieldedInput, temporal.dueAt)
        val attributes = attributeParser.parse(input)
        val consumed = buildList {
            addAll(temporal.matches)
            addAll(reminder.matches)
            addAll(attributes.matches)
        }.sortedBy(SourceMatch::start)
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

    private fun NaturalLanguageInput.withMaskedRanges(ranges: List<SourceMatch>): NaturalLanguageInput {
        val masked = rawText.toCharArray()
        ranges.forEach { range ->
            for (index in range.start until range.endExclusive) masked[index] = ' '
        }
        return NaturalLanguageInput(
            rawText = masked.concatToString(),
            language = language,
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId,
            categories = categories
        )
    }

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
}
