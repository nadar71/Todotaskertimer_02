package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Test

@RunWith(Parameterized::class)
class TemporalParserTest(
    private val raw: String,
    private val language: ParserLanguage,
    private val zoneId: ZoneId,
    private val nowEpochMillis: Long,
    private val expectedDueAt: Long?,
    private val expectedMatches: List<SourceMatch>,
    private val expectedIssues: List<ParseIssue>
) {

    private val parser = TemporalParser()

    @Test
    fun parsesSupportedTemporalExpressionDeterministically() {
        val result = parser.parse(
            NaturalLanguageInput(
                rawText = raw,
                language = language,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
                categories = emptyList()
            )
        )

        assertEquals(expectedDueAt, result.dueAt)
        assertEquals(expectedMatches, result.matches)
        assertEquals(expectedIssues, result.issues)
    }

    companion object {
        private val rome = ZoneId.of("Europe/Rome")
        private val newYork = ZoneId.of("America/New_York")
        private val regularNow = epoch("2026-08-26T10:15:00+02:00")

        @JvmStatic
        @Parameterized.Parameters(name = "{0} [{1}]")
        fun cases(): List<Array<Any?>> = listOf(
            case(
                raw = "Compra latte oggi",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T09:00:00+02:00"),
                matches = listOf(match(13, 17))
            ),
            case(
                raw = "Buy milk today",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T09:00:00+02:00"),
                matches = listOf(match(9, 14))
            ),
            case(
                raw = "Compra latte domani",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T09:00:00+02:00"),
                matches = listOf(match(13, 19))
            ),
            case(
                raw = "Buy milk tomorrow",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T09:00:00+02:00"),
                matches = listOf(match(9, 17))
            ),
            case(
                raw = "Visita 13/05",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-05-13T09:00:00+02:00"),
                matches = listOf(match(7, 12))
            ),
            case(
                raw = "Visita 13/05/2027",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2027-05-13T09:00:00+02:00"),
                matches = listOf(match(7, 17))
            ),
            case(
                raw = "Appointment 05/13",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-05-13T09:00:00+02:00"),
                matches = listOf(match(12, 17))
            ),
            case(
                raw = "Appointment 05/13/2027",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2027-05-13T09:00:00+02:00"),
                matches = listOf(match(12, 22))
            ),
            case(
                raw = "Chiama alle 18",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:00:00+02:00"),
                matches = listOf(match(7, 14))
            ),
            case(
                raw = "Call at 6 pm",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:00:00+02:00"),
                matches = listOf(match(5, 12))
            ),
            case(
                raw = "Call at 18:45",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:45:00+02:00"),
                matches = listOf(match(5, 13))
            ),
            case(
                raw = "Compra latte domani alle 18",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T18:00:00+02:00"),
                matches = listOf(match(13, 19), match(20, 27))
            ),
            case(
                raw = "Buy milk tomorrow at 6 pm",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T18:00:00+02:00"),
                matches = listOf(match(9, 17), match(18, 25))
            ),
            case(
                raw = "Compra latte domani",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = epoch("2026-12-31T23:30:00+01:00"),
                dueAt = epoch("2027-01-01T09:00:00+01:00"),
                matches = listOf(match(13, 19))
            ),
            case(
                raw = "Call at 6 pm",
                language = ParserLanguage.ENGLISH,
                zoneId = newYork,
                now = epoch("2026-08-26T10:15:00-04:00"),
                dueAt = epoch("2026-08-26T18:00:00-04:00"),
                matches = listOf(match(5, 12))
            ),
            case(
                raw = "Appuntamento alle 2:30",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = epoch("2026-03-29T00:15:00+01:00"),
                dueAt = epoch("2026-03-29T03:30:00+02:00"),
                matches = listOf(match(13, 22))
            ),
            case(
                raw = "Appuntamento alle 2:30",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = epoch("2026-10-25T00:15:00+02:00"),
                dueAt = epoch("2026-10-25T02:30:00+02:00"),
                matches = listOf(match(13, 22))
            ),
            case(
                raw = "Visita 31/02/2026",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Appointment 02/30/2026",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Compra latte domani oggi",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T09:00:00+02:00"),
                matches = listOf(match(13, 19), match(20, 24)),
                issues = listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE))
            ),
            case(
                raw = "Chiama alle 17 alle 18",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:00:00+02:00"),
                matches = listOf(match(7, 14), match(15, 22)),
                issues = listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE))
            ),
            case(
                raw = "Call at 6 pm at 18",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:00:00+02:00"),
                matches = listOf(match(5, 12), match(13, 18)),
                issues = listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE))
            ),
            case(
                raw = "Task alle 1/2",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano 1/2/3",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano v1/2",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano _1/2",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano 1/2/2026x",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano 1/2/2026/nota",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano 1/2.3",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano 1/2:3",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Piano 1/2/2026.0",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = null,
                matches = emptyList()
            ),
            case(
                raw = "Visita 13/05.",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-05-13T09:00:00+02:00"),
                matches = listOf(match(7, 12))
            ),
            case(
                raw = "Visita (13/05),",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-05-13T09:00:00+02:00"),
                matches = listOf(match(8, 13))
            ),
            case(
                raw = "Visita 13/05!",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-05-13T09:00:00+02:00"),
                matches = listOf(match(7, 12))
            )
        )

        private fun case(
            raw: String,
            language: ParserLanguage,
            zoneId: ZoneId,
            now: Long,
            dueAt: Long?,
            matches: List<SourceMatch>,
            issues: List<ParseIssue> = emptyList()
        ): Array<Any?> = arrayOf(raw, language, zoneId, now, dueAt, matches, issues)

        private fun match(start: Int, endExclusive: Int): SourceMatch = SourceMatch(
            start = start,
            endExclusive = endExclusive,
            field = RecognizedField.DUE_DATE
        )

        private fun epoch(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    }
}

class TemporalParserSnapshotTest {

    @Test
    fun temporalParse_snapshotsAndProtectsCallerOwnedMatchesAndIssues() {
        val matches = mutableListOf(SourceMatch(0, 5, RecognizedField.DUE_DATE))
        val issues = mutableListOf<ParseIssue>(ParseIssue.DuplicateField(RecognizedField.DUE_DATE))

        val result = TemporalParse(dueAt = 1L, matches = matches, issues = issues)
        matches.clear()
        issues.clear()

        assertEquals(listOf(SourceMatch(0, 5, RecognizedField.DUE_DATE)), result.matches)
        assertEquals(listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE)), result.issues)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.matches as MutableList<SourceMatch>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.issues as MutableList<ParseIssue>).clear()
        }
    }
}
