package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
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
    private val expectedMatches: List<String>,
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
        assertEquals(expectedMatches, result.matches.map { raw.substring(it.start, it.endExclusive) })
        assertEquals(
            List(expectedMatches.size) { RecognizedField.DUE_DATE },
            result.matches.map { it.field }
        )
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
                matches = listOf("oggi")
            ),
            case(
                raw = "Buy milk today",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T09:00:00+02:00"),
                matches = listOf("today")
            ),
            case(
                raw = "Compra latte domani",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T09:00:00+02:00"),
                matches = listOf("domani")
            ),
            case(
                raw = "Buy milk tomorrow",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T09:00:00+02:00"),
                matches = listOf("tomorrow")
            ),
            case(
                raw = "Visita 13/05",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-05-13T09:00:00+02:00"),
                matches = listOf("13/05")
            ),
            case(
                raw = "Visita 13/05/2027",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2027-05-13T09:00:00+02:00"),
                matches = listOf("13/05/2027")
            ),
            case(
                raw = "Appointment 05/13",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-05-13T09:00:00+02:00"),
                matches = listOf("05/13")
            ),
            case(
                raw = "Appointment 05/13/2027",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2027-05-13T09:00:00+02:00"),
                matches = listOf("05/13/2027")
            ),
            case(
                raw = "Chiama alle 18",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:00:00+02:00"),
                matches = listOf("alle 18")
            ),
            case(
                raw = "Call at 6 pm",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:00:00+02:00"),
                matches = listOf("at 6 pm")
            ),
            case(
                raw = "Compra latte domani alle 18",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T18:00:00+02:00"),
                matches = listOf("domani", "alle 18")
            ),
            case(
                raw = "Buy milk tomorrow at 6 pm",
                language = ParserLanguage.ENGLISH,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-27T18:00:00+02:00"),
                matches = listOf("tomorrow", "at 6 pm")
            ),
            case(
                raw = "Compra latte domani",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = epoch("2026-12-31T23:30:00+01:00"),
                dueAt = epoch("2027-01-01T09:00:00+01:00"),
                matches = listOf("domani")
            ),
            case(
                raw = "Call at 6 pm",
                language = ParserLanguage.ENGLISH,
                zoneId = newYork,
                now = epoch("2026-08-26T10:15:00-04:00"),
                dueAt = epoch("2026-08-26T18:00:00-04:00"),
                matches = listOf("at 6 pm")
            ),
            case(
                raw = "Appuntamento alle 2:30",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = epoch("2026-03-29T00:15:00+01:00"),
                dueAt = epoch("2026-03-29T03:30:00+02:00"),
                matches = listOf("alle 2:30")
            ),
            case(
                raw = "Appuntamento alle 2:30",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = epoch("2026-10-25T00:15:00+02:00"),
                dueAt = epoch("2026-10-25T02:30:00+02:00"),
                matches = listOf("alle 2:30")
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
                matches = listOf("oggi"),
                issues = listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE))
            ),
            case(
                raw = "Chiama alle 17 alle 18",
                language = ParserLanguage.ITALIAN,
                zoneId = rome,
                now = regularNow,
                dueAt = epoch("2026-08-26T18:00:00+02:00"),
                matches = listOf("alle 18"),
                issues = listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE))
            )
        )

        private fun case(
            raw: String,
            language: ParserLanguage,
            zoneId: ZoneId,
            now: Long,
            dueAt: Long?,
            matches: List<String>,
            issues: List<ParseIssue> = emptyList()
        ): Array<Any?> = arrayOf(raw, language, zoneId, now, dueAt, matches, issues)

        private fun epoch(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    }
}
