package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceParserTest {

    private val parser = RecurrenceParser()

    @Test
    fun explicitIntervals_returnTypedRulesAndExactRanges() {
        val cases = listOf(
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Plan every 2 weeks",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 2, RecurrenceBasis.COMPLETION_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Piano ogni 3 giorni",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 3, RecurrenceBasis.COMPLETION_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Budget every 4 months",
                RecurrenceRule.MonthlyDay(27, 4, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Budget ogni 5 mesi",
                RecurrenceRule.MonthlyDay(27, 5, RecurrenceBasis.SCHEDULED_DATE)
            )
        )

        cases.forEach { case ->
            val result = parser.parse(input(case.raw, case.language), DUE_AT)

            assertEquals(case.raw, case.expected, result.rule)
            assertEquals(case.raw, listOf(case.expected), result.candidates.map { it.rule })
            assertEquals(
                case.raw,
                SourceMatch(case.raw.indexOf(if (case.language == ParserLanguage.ENGLISH) "every" else "ogni"), case.raw.length, RecognizedField.RECURRENCE),
                result.matches.single()
            )
            assertTrue(case.raw, result.issues.isEmpty())
        }
    }

    @Test
    fun legacyDailyWeeklyMonthly_keepScheduledCompatibility() {
        val cases = listOf(
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Task every day",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Task ogni settimana",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Task every month",
                RecurrenceRule.MonthlyDay(27, 1, RecurrenceBasis.SCHEDULED_DATE)
            )
        )

        cases.forEach { case ->
            assertEquals(
                case.raw,
                case.expected,
                parser.parse(input(case.raw, case.language), DUE_AT).rule
            )
        }
    }

    @Test
    fun selectedWeekdayLists_areAccentInsensitiveAndScheduled() {
        val cases = listOf(
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Training every Monday and Friday",
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                )
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Palestra ogni lunedi e venerdi",
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                )
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Turni OGNI LUNEDÌ, mercoledì E VENERDÌ",
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                )
            ),
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Shifts every Monday, Wednesday, and Friday",
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                )
            )
        )

        cases.forEach { case ->
            assertEquals(
                case.raw,
                case.expected,
                parser.parse(input(case.raw, case.language), DUE_AT).rule
            )
        }
    }

    @Test
    fun everySupportedMonthlyOrdinal_mapsInBothLanguages() {
        val ordinals = listOf(
            Triple("first", "primo", MonthlyOrdinalValue.FIRST),
            Triple("second", "secondo", MonthlyOrdinalValue.SECOND),
            Triple("third", "terzo", MonthlyOrdinalValue.THIRD),
            Triple("fourth", "quarto", MonthlyOrdinalValue.FOURTH),
            Triple("last", "ultimo", MonthlyOrdinalValue.LAST)
        )

        ordinals.forEach { (english, italian, expectedOrdinal) ->
            assertEquals(
                english,
                RecurrenceRule.MonthlyOrdinal(
                    expectedOrdinal,
                    DayOfWeek.FRIDAY,
                    1,
                    RecurrenceBasis.SCHEDULED_DATE
                ),
                parser.parse(
                    input("Task $english Friday of every month", ParserLanguage.ENGLISH),
                    DUE_AT
                ).rule
            )
            assertEquals(
                italian,
                RecurrenceRule.MonthlyOrdinal(
                    expectedOrdinal,
                    DayOfWeek.FRIDAY,
                    1,
                    RecurrenceBasis.SCHEDULED_DATE
                ),
                parser.parse(
                    input("Task $italian venerdì del mese", ParserLanguage.ITALIAN),
                    DUE_AT
                ).rule
            )
        }
    }

    @Test
    fun explicitBasisSuffixes_overrideOnlyCompleteUnambiguousPhrases() {
        val cases = listOf(
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Task every 2 weeks from the scheduled date",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 2, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Task every Monday based on the completion date",
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY),
                    RecurrenceBasis.COMPLETION_DATE
                )
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Task ogni 2 settimane dalla data programmata",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 2, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Task ogni lunedì in base alla data di completamento",
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY),
                    RecurrenceBasis.COMPLETION_DATE
                )
            )
        )

        cases.forEach { case ->
            assertEquals(
                case.raw,
                case.expected,
                parser.parse(input(case.raw, case.language), DUE_AT).rule
            )
        }
    }

    @Test
    fun punctuationBoundaries_matchButEmbeddedWordsDoNot() {
        val punctuated = "Task, every 2 weeks"
        val parsed = parser.parse(input(punctuated, ParserLanguage.ENGLISH), DUE_AT)

        assertEquals(
            RecurrenceRule.Interval(IntervalUnit.WEEKS, 2, RecurrenceBasis.COMPLETION_DATE),
            parsed.rule
        )
        assertEquals(SourceMatch(6, 19, RecognizedField.RECURRENCE), parsed.matches.single())

        listOf("Task forevery 2 weeks", "Task every 2 weeksfoo").forEach { raw ->
            val result = parser.parse(input(raw, ParserLanguage.ENGLISH), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.matches.isEmpty())
        }
    }

    @Test
    fun duplicateContradictoryPartialMalformedAndFifthAttempts_applyNoRule() {
        val malformed = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every day every week"),
            Pair(ParserLanguage.ITALIAN, "Task ogni lunedi e lunedi"),
            Pair(
                ParserLanguage.ENGLISH,
                "Task every 2 weeks from the completion date from the scheduled date"
            ),
            Pair(ParserLanguage.ITALIAN, "Task ogni 2"),
            Pair(ParserLanguage.ENGLISH, "Task every Monday and"),
            Pair(ParserLanguage.ENGLISH, "Task fifth Monday of every month"),
            Pair(ParserLanguage.ITALIAN, "Task quinto lunedi del mese")
        )

        malformed.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.matches.isEmpty())
            assertTrue(raw, result.ownedRanges.isNotEmpty())
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        }
    }

    @Test
    fun malformedOwnershipCanShieldInnerTemporalGrammarWithoutConsumption() {
        val raw = "Task every Monday and at 5 pm"
        val result = parser.parse(input(raw, ParserLanguage.ENGLISH), DUE_AT)

        assertNull(result.rule)
        assertTrue(result.matches.isEmpty())
        assertEquals(
            "every Monday and at 5 pm",
            raw.substring(result.ownedRanges.single().start, result.ownedRanges.single().endExclusive)
        )
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), result.issues)
    }

    @Test
    fun excludedOwnershipRejectsIntersectingRecurrenceCandidate() {
        val raw = "Task every 2 weeks"
        val exclusion = SourceMatch(5, raw.length, RecognizedField.CATEGORY)

        val result = parser.parse(
            input(raw, ParserLanguage.ENGLISH),
            DUE_AT,
            listOf(exclusion)
        )

        assertNull(result.rule)
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.matches.isEmpty())
        assertTrue(result.issues.isEmpty())
    }

    private fun input(raw: String, language: ParserLanguage) = NaturalLanguageInput(
        rawText = raw,
        language = language,
        nowEpochMillis = NOW,
        zoneId = ROME,
        categories = emptyList()
    )

    private data class RecurrenceCase(
        val language: ParserLanguage,
        val raw: String,
        val expected: RecurrenceRule
    )

    private companion object {
        val ROME: ZoneId = ZoneId.of("Europe/Rome")
        val DUE_AT: Long = ZonedDateTime.parse("2026-08-27T18:00:00+02:00")
            .toInstant()
            .toEpochMilli()
        val NOW: Long = ZonedDateTime.parse("2026-08-26T12:00:00+02:00")
            .toInstant()
            .toEpochMilli()
    }
}
