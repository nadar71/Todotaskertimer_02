package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalLanguageContractsTest {

    @Test
    fun naturalLanguageInput_snapshotsCallerOwnedCategories() {
        val category = CategoryCandidate(id = 7, displayName = "Casa")
        val callerCategories = mutableListOf(category)

        val input = NaturalLanguageInput(
            rawText = "Compra latte #Casa",
            language = ParserLanguage.ITALIAN,
            nowEpochMillis = 0,
            zoneId = ZoneId.of("Europe/Rome"),
            categories = callerCategories
        )
        callerCategories.clear()

        assertEquals(listOf(category), input.categories)
    }

    @Test
    fun naturalLanguageParseResult_snapshotsCallerOwnedCollections() {
        val recognized = mutableSetOf(RecognizedField.DUE_DATE)
        val issues = mutableListOf<ParseIssue>(ParseIssue.EmptyInput)
        val consumed = mutableListOf(SourceMatch(0, 7, RecognizedField.DUE_DATE))

        val result = NaturalLanguageParseResult(
            draft = ParsedTaskDraft(
                title = "Compra",
                dueAt = null,
                reminderAt = null,
                priority = null,
                categoryId = null,
                recurrenceRule = null
            ),
            recognized = recognized,
            issues = issues,
            consumed = consumed
        )
        recognized.clear()
        issues.clear()
        consumed.clear()

        assertEquals(setOf(RecognizedField.DUE_DATE), result.recognized)
        assertEquals(listOf(ParseIssue.EmptyInput), result.issues)
        assertEquals(
            listOf(SourceMatch(0, 7, RecognizedField.DUE_DATE)),
            result.consumed
        )
    }
}
