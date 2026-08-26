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

        val malformedContinuations = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every month / Friday"),
            Pair(ParserLanguage.ITALIAN, "Task ogni mese / venerdì"),
            Pair(ParserLanguage.ENGLISH, "Task every month, Mondays"),
            Pair(ParserLanguage.ITALIAN, "Task ogni mese, lunedìs")
        )
        malformedContinuations.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.matches.isEmpty())
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.ownedRanges
            )
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
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
    fun legacyRecurrenceBeforeCommaProseRemainsValid() {
        val cases = listOf(
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Task every day, notes",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Task every week, notes",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ENGLISH,
                "Task every month, notes",
                RecurrenceRule.MonthlyDay(27, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Task ogni giorno, note",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Task ogni settimana, note",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            RecurrenceCase(
                ParserLanguage.ITALIAN,
                "Task ogni mese, note",
                RecurrenceRule.MonthlyDay(27, 1, RecurrenceBasis.SCHEDULED_DATE)
            )
        )

        cases.forEach { case ->
            val result = parser.parse(input(case.raw, case.language), DUE_AT)
            val expectedMatch = SourceMatch(
                5,
                case.raw.indexOf(','),
                RecognizedField.RECURRENCE
            )

            assertEquals(case.raw, case.expected, result.rule)
            assertEquals(case.raw, listOf(case.expected), result.candidates.map { it.rule })
            assertEquals(case.raw, listOf(expectedMatch), result.matches)
            assertEquals(case.raw, listOf(expectedMatch), result.ownedRanges)
            assertTrue(case.raw, result.issues.isEmpty())
        }
    }

    @Test
    fun legacyPluralWeekdayOwnershipStopsBeforeASecondComma() {
        val malformed = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every month, Mondays, tomorrow"),
            Pair(ParserLanguage.ITALIAN, "Task ogni mese, lunedìs, domani")
        )

        malformed.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)
            val recurrenceEnd = raw.lastIndexOf(',')

            assertNull(raw, result.rule)
            assertTrue(raw, result.candidates.isEmpty())
            assertTrue(raw, result.matches.isEmpty())
            assertEquals(
                raw,
                listOf(SourceMatch(5, recurrenceEnd, RecognizedField.RECURRENCE)),
                result.ownedRanges
            )
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        }

        val validControls = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every month, notes, tomorrow"),
            Pair(ParserLanguage.ITALIAN, "Task ogni mese, note, domani")
        )

        validControls.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)
            val expected = SourceMatch(5, raw.indexOf(','), RecognizedField.RECURRENCE)

            assertEquals(raw, RecurrenceRule.MonthlyDay(27, 1, RecurrenceBasis.SCHEDULED_DATE), result.rule)
            assertEquals(raw, listOf(expected), result.matches)
            assertEquals(raw, listOf(expected), result.ownedRanges)
            assertTrue(raw, result.issues.isEmpty())
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
    fun unsupportedAndPunctuationLedContradictoryContinuationsRejectTheWholeAttempt() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every Monday or Friday"),
            Pair(
                ParserLanguage.ENGLISH,
                "Task every 2 weeks from the scheduled date, from the completion date"
            ),
            Pair(ParserLanguage.ITALIAN, "Task ogni lunedì o venerdì"),
            Pair(
                ParserLanguage.ITALIAN,
                "Task ogni 2 settimane dalla data programmata, dalla data di completamento"
            )
        )

        cases.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.candidates.isEmpty())
            assertTrue(raw, result.matches.isEmpty())
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.ownedRanges
            )
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        }
    }

    @Test
    fun supportedCommaListAndOrdinaryTrailingPunctuationRemainValid() {
        val weekdayRaw = "Task every Monday, Friday"
        val weekdays = parser.parse(input(weekdayRaw, ParserLanguage.ENGLISH), DUE_AT)

        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            weekdays.rule
        )
        assertEquals(
            listOf(SourceMatch(5, weekdayRaw.length, RecognizedField.RECURRENCE)),
            weekdays.matches
        )
        assertTrue(weekdays.issues.isEmpty())

        val punctuationRaw = "Task every 2 weeks from the scheduled date, notes"
        val punctuation = parser.parse(
            input(punctuationRaw, ParserLanguage.ENGLISH),
            DUE_AT
        )

        assertEquals(
            RecurrenceRule.Interval(IntervalUnit.WEEKS, 2, RecurrenceBasis.SCHEDULED_DATE),
            punctuation.rule
        )
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    punctuationRaw.indexOf(','),
                    RecognizedField.RECURRENCE
                )
            ),
            punctuation.matches
        )
        assertTrue(punctuation.issues.isEmpty())

        val weekdayPunctuationRaw = "Task every Monday, notes"
        val weekdayPunctuation = parser.parse(
            input(weekdayPunctuationRaw, ParserLanguage.ENGLISH),
            DUE_AT
        )
        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            weekdayPunctuation.rule
        )
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    weekdayPunctuationRaw.indexOf(','),
                    RecognizedField.RECURRENCE
                )
            ),
            weekdayPunctuation.matches
        )
        assertTrue(weekdayPunctuation.issues.isEmpty())
    }

    @Test
    fun unsupportedOuterOrdinalsOwnNestedLegacyMonthlyPhrases() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Task fifth Mondays of every month"),
            Pair(ParserLanguage.ENGLISH, "Task sixth Monday of every month"),
            Pair(ParserLanguage.ENGLISH, "Task eleventh Monday of every month"),
            Pair(ParserLanguage.ITALIAN, "Task sesto lunedì di ogni mese"),
            Pair(ParserLanguage.ITALIAN, "Task undicesimo lunedì di ogni mese"),
            Pair(ParserLanguage.ITALIAN, "Task quinto lunedìs del mese")
        )

        cases.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.candidates.isEmpty())
            assertTrue(raw, result.matches.isEmpty())
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.ownedRanges
            )
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        }

        val validRaw = "Task fourth Monday of every month"
        val valid = parser.parse(input(validRaw, ParserLanguage.ENGLISH), DUE_AT)
        assertEquals(
            RecurrenceRule.MonthlyOrdinal(
                MonthlyOrdinalValue.FOURTH,
                DayOfWeek.MONDAY,
                1,
                RecurrenceBasis.SCHEDULED_DATE
            ),
            valid.rule
        )
        assertEquals(
            listOf(SourceMatch(5, validRaw.length, RecognizedField.RECURRENCE)),
            valid.matches
        )
        assertTrue(valid.issues.isEmpty())
    }

    @Test
    fun italianWeekdayCandidatesUseTextNormalizerAccentEquivalence() {
        val mondayVariants = listOf(
            "lunedì",
            "lunedí",
            "lunedî",
            "lunedï",
            "lunedi\u0300",
            "lunedi\u0301",
            "lunedi\u0302",
            "lunedi\u0308"
        )

        mondayVariants.forEach { monday ->
            val raw = "Task ogni $monday e venerdì"
            val result = parser.parse(input(raw, ParserLanguage.ITALIAN), DUE_AT)

            assertEquals(
                raw,
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                ),
                result.rule
            )
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.matches
            )
            assertTrue(raw, result.issues.isEmpty())
        }
    }

    @Test
    fun malformedOwnershipStopsBeforeIndependentMarkersAndPunctuation() {
        val priorityRaw = "Task every nonsense !high"
        val priority = parser.parse(input(priorityRaw, ParserLanguage.ENGLISH), DUE_AT)
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    priorityRaw.indexOf(" !high"),
                    RecognizedField.RECURRENCE
                )
            ),
            priority.ownedRanges
        )
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), priority.issues)

        val categoryRaw = "Task every nonsense #Home"
        val categoryStart = categoryRaw.indexOf("#Home")
        val category = parser.parse(
            input(categoryRaw, ParserLanguage.ENGLISH),
            DUE_AT,
            listOf(SourceMatch(categoryStart, categoryRaw.length, RecognizedField.CATEGORY))
        )
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    categoryRaw.indexOf(" #Home"),
                    RecognizedField.RECURRENCE
                )
            ),
            category.ownedRanges
        )
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), category.issues)

        val punctuationRaw = "Task every nonsense, notes"
        val punctuation = parser.parse(
            input(punctuationRaw, ParserLanguage.ENGLISH),
            DUE_AT
        )
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    punctuationRaw.indexOf(','),
                    RecognizedField.RECURRENCE
                )
            ),
            punctuation.ownedRanges
        )
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), punctuation.issues)
    }

    @Test
    fun punctuationFollowedByAValidWeekdayRejectsTheWholeAttempt() {
        val malformed = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every Monday / Friday"),
            Pair(ParserLanguage.ITALIAN, "Task ogni lunedì / venerdì")
        )

        malformed.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.candidates.isEmpty())
            assertTrue(raw, result.matches.isEmpty())
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.ownedRanges
            )
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        }

        val ordinaryRaw = "Task every Monday / notes"
        val ordinary = parser.parse(input(ordinaryRaw, ParserLanguage.ENGLISH), DUE_AT)
        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            ordinary.rule
        )
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    ordinaryRaw.indexOf(" /"),
                    RecognizedField.RECURRENCE
                )
            ),
            ordinary.matches
        )
        assertTrue(ordinary.issues.isEmpty())
    }

    @Test
    fun unsuffixedNumericOrdinalShellsOwnNestedMonthlyCandidates() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Task 11 Monday of every month"),
            Pair(ParserLanguage.ITALIAN, "Task 11 lunedì di ogni mese")
        )

        cases.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.candidates.isEmpty())
            assertTrue(raw, result.matches.isEmpty())
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.ownedRanges
            )
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        }
    }

    @Test
    fun ordinaryMonthlyTitlesDoNotCreateOrdinalAttempts() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Best photo of the month"),
            Pair(ParserLanguage.ITALIAN, "Foto preferita del mese")
        )

        cases.forEach { (language, raw) ->
            val result = parser.parse(input(raw, language), DUE_AT)

            assertNull(raw, result.rule)
            assertTrue(raw, result.candidates.isEmpty())
            assertTrue(raw, result.matches.isEmpty())
            assertTrue(raw, result.ownedRanges.isEmpty())
            assertTrue(raw, result.issues.isEmpty())
        }
    }

    @Test
    fun italianWeekdayMatchingNormalizesAccentsAcrossTheWholeToken() {
        val saturdayVariants = listOf(
            "sábato",
            "sa\u0301bato",
            "sabáto",
            "saba\u0301to"
        )

        saturdayVariants.forEach { saturday ->
            val raw = "Task ogni $saturday e domenica"
            val result = parser.parse(input(raw, ParserLanguage.ITALIAN), DUE_AT)

            assertEquals(
                raw,
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                ),
                result.rule
            )
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.matches
            )
            assertTrue(raw, result.issues.isEmpty())
        }
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
