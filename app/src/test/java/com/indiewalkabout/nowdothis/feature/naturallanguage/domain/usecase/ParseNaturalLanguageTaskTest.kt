package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.usecase

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.CategoryCandidate
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.AttributeParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.RecurrenceParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.ReminderParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.TemporalParser
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseNaturalLanguageTaskTest {

    private val parser = ParseNaturalLanguageTask(
        temporalParser = TemporalParser(),
        attributeParser = AttributeParser(),
        reminderParser = ReminderParser(),
        recurrenceParser = RecurrenceParser()
    )

    @Test
    fun italianHybridInput_returnsCorrectedTypedDraft() {
        val raw = "Compra latte domani alle 18 #Casa !alta ogni settimana promemoria 1h prima"

        val result = parse(
            raw = raw,
            language = ParserLanguage.ITALIAN,
            categories = listOf(CategoryCandidate(7, "Casa"))
        )

        assertEquals("Compra latte", result.draft.title)
        assertEquals(epoch("2026-08-27T18:00:00+02:00"), result.draft.dueAt)
        assertEquals(epoch("2026-08-27T17:00:00+02:00"), result.draft.reminderAt)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(7, result.draft.categoryId)
        assertEquals(
            RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE),
            result.draft.recurrenceRule
        )
        assertEquals(RecognizedField.entries.toSet(), result.recognized)
        assertTrue(result.issues.isEmpty())
        assertEquals(
            listOf(
                "domani",
                "alle 18",
                "#Casa",
                "!alta",
                "ogni settimana",
                "promemoria 1h prima"
            ),
            consumedText(raw, result.consumed)
        )
    }

    @Test
    fun englishHybridInput_returnsCorrectedTypedDraft() {
        val raw = "Buy milk tomorrow at 6 pm #Home !high every week remind 30m before"

        val result = parse(
            raw = raw,
            language = ParserLanguage.ENGLISH,
            categories = listOf(CategoryCandidate(8, "Home"))
        )

        assertEquals("Buy milk", result.draft.title)
        assertEquals(epoch("2026-08-27T18:00:00+02:00"), result.draft.dueAt)
        assertEquals(epoch("2026-08-27T17:30:00+02:00"), result.draft.reminderAt)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(8, result.draft.categoryId)
        assertEquals(
            RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE),
            result.draft.recurrenceRule
        )
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun english24HourInput_appliesDateAndRelativeReminderAndReconstructsTitle() {
        val raw = "Deploy tomorrow at 18:45 remind 1h before"

        val result = parse(raw, ParserLanguage.ENGLISH)

        assertEquals("Deploy", result.draft.title)
        assertEquals(epoch("2026-08-27T18:45:00+02:00"), result.draft.dueAt)
        assertEquals(epoch("2026-08-27T17:45:00+02:00"), result.draft.reminderAt)
        assertEquals(
            listOf("tomorrow", "at 18:45", "remind 1h before"),
            consumedText(raw, result.consumed)
        )
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun english24HourAbsoluteReminder_isRecognizedAlongsideTwelveHourDueTime() {
        val raw = "Deploy tomorrow at 6 pm remind today at 17:30"

        val result = parse(raw, ParserLanguage.ENGLISH)

        assertEquals("Deploy", result.draft.title)
        assertEquals(epoch("2026-08-27T18:00:00+02:00"), result.draft.dueAt)
        assertEquals(epoch("2026-08-26T17:30:00+02:00"), result.draft.reminderAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun duplicateEnglishTwelveAndTwentyFourHourTimes_useLastValidOccurrence() {
        val raw = "Deploy tomorrow at 6 pm at 20"

        val result = parse(raw, ParserLanguage.ENGLISH)

        assertEquals("Deploy", result.draft.title)
        assertEquals(epoch("2026-08-27T20:00:00+02:00"), result.draft.dueAt)
        assertEquals(
            listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE)),
            result.issues
        )
    }

    @Test
    fun malformedEnglishTwentyFourHourAttempts_remainVisibleAndDoNotChangeDefaultTime() {
        val malformed = listOf("at 24", "at 18:60", "at 18:3", "at 18 pm")

        malformed.forEach { attempt ->
            val raw = "Deploy tomorrow $attempt"
            val result = parse(raw, ParserLanguage.ENGLISH)

            assertEquals(raw, "Deploy $attempt", result.draft.title)
            assertEquals(raw, epoch("2026-08-27T09:00:00+02:00"), result.draft.dueAt)
            assertEquals(
                raw,
                listOf("tomorrow"),
                consumedText(raw, result.consumed)
            )
        }
    }

    @Test
    fun malformedEnglishMeridiemAttempts_remainWholeAndDoNotSetDueTime() {
        val malformed = listOf(
            "at 5 pmx",
            "at 5 amfoo",
            "at 5 p.m.x",
            "at 5:30 A.M.foo"
        )

        malformed.forEach { attempt ->
            val raw = "Task $attempt"
            val result = parse(raw, ParserLanguage.ENGLISH)

            assertEquals(raw, raw, result.draft.title)
            assertEquals(raw, null, result.draft.dueAt)
            assertEquals(raw, emptyList<SourceMatch>(), result.consumed)
        }
    }

    @Test
    fun allPriorityMarkers_mapByParserLanguage() {
        val cases = listOf(
            Triple(ParserLanguage.ITALIAN, "!alta", TaskPriority.HIGH),
            Triple(ParserLanguage.ITALIAN, "!media", TaskPriority.MEDIUM),
            Triple(ParserLanguage.ITALIAN, "!bassa", TaskPriority.LOW),
            Triple(ParserLanguage.ENGLISH, "!high", TaskPriority.HIGH),
            Triple(ParserLanguage.ENGLISH, "!medium", TaskPriority.MEDIUM),
            Triple(ParserLanguage.ENGLISH, "!low", TaskPriority.LOW)
        )

        cases.forEach { (language, marker, priority) ->
            val result = parse("Task $marker", language)

            assertEquals(marker, priority, result.draft.priority)
            assertEquals(marker, "Task", result.draft.title)
            assertEquals(marker, setOf(RecognizedField.TITLE, RecognizedField.PRIORITY), result.recognized)
        }
    }

    @Test
    fun allRecurrencePhrases_mapByParserLanguage() {
        val cases = listOf(
            Triple(
                ParserLanguage.ITALIAN,
                "ogni giorno",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ITALIAN,
                "ogni settimana",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ITALIAN,
                "ogni mese",
                RecurrenceRule.MonthlyDay(26, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ENGLISH,
                "every day",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ENGLISH,
                "every week",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ENGLISH,
                "every month",
                RecurrenceRule.MonthlyDay(26, 1, RecurrenceBasis.SCHEDULED_DATE)
            )
        )

        cases.forEach { (language, phrase, recurrence) ->
            val result = parse("Task $phrase", language)

            assertEquals(phrase, recurrence, result.draft.recurrenceRule)
            assertEquals(phrase, "Task", result.draft.title)
        }
    }

    @Test
    fun advancedRecurrencePhrases_flowIntoTypedDraftAndExactTitle() {
        val cases = listOf(
            AdvancedRecurrenceCase(
                ParserLanguage.ENGLISH,
                "Plan every 2 weeks",
                "Plan",
                RecurrenceRule.Interval(
                    IntervalUnit.WEEKS,
                    2,
                    RecurrenceBasis.COMPLETION_DATE
                )
            ),
            AdvancedRecurrenceCase(
                ParserLanguage.ITALIAN,
                "Palestra ogni lunedi e venerdi",
                "Palestra",
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                )
            ),
            AdvancedRecurrenceCase(
                ParserLanguage.ENGLISH,
                "Report last Friday of every month",
                "Report",
                RecurrenceRule.MonthlyOrdinal(
                    MonthlyOrdinalValue.LAST,
                    DayOfWeek.FRIDAY,
                    1,
                    RecurrenceBasis.SCHEDULED_DATE
                )
            ),
            AdvancedRecurrenceCase(
                ParserLanguage.ITALIAN,
                "Report ultimo venerdì del mese",
                "Report",
                RecurrenceRule.MonthlyOrdinal(
                    MonthlyOrdinalValue.LAST,
                    DayOfWeek.FRIDAY,
                    1,
                    RecurrenceBasis.SCHEDULED_DATE
                )
            )
        )

        cases.forEach { case ->
            val result = parse(case.raw, case.language)

            assertEquals(case.raw, case.title, result.draft.title)
            assertEquals(case.raw, case.rule, result.draft.recurrenceRule)
            assertEquals(
                case.raw,
                setOf(RecognizedField.TITLE, RecognizedField.RECURRENCE),
                result.recognized
            )
            assertTrue(case.raw, result.issues.isEmpty())
        }
    }

    @Test
    fun monthlyIntervalUsesParsedDueInInputZoneAsAnchor() {
        val result = parse(
            "Rent tomorrow at 00:30 every 3 months",
            ParserLanguage.ENGLISH
        )

        assertEquals("Rent", result.draft.title)
        assertEquals(
            RecurrenceRule.MonthlyDay(27, 3, RecurrenceBasis.SCHEDULED_DATE),
            result.draft.recurrenceRule
        )
    }

    @Test
    fun legacyRecurrenceBeforeCommaProseConsumesOnlyTheRecurrence() {
        val cases = listOf(
            Triple(
                ParserLanguage.ENGLISH,
                "Task every day, notes",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ENGLISH,
                "Task every week, notes",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ENGLISH,
                "Task every month, notes",
                RecurrenceRule.MonthlyDay(26, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ITALIAN,
                "Task ogni giorno, note",
                RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ITALIAN,
                "Task ogni settimana, note",
                RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE)
            ),
            Triple(
                ParserLanguage.ITALIAN,
                "Task ogni mese, note",
                RecurrenceRule.MonthlyDay(26, 1, RecurrenceBasis.SCHEDULED_DATE)
            )
        )

        cases.forEach { (language, raw, expectedRule) ->
            val result = parse(raw, language)
            val expectedMatch = SourceMatch(5, raw.indexOf(','), RecognizedField.RECURRENCE)
            val expectedTitle = if (language == ParserLanguage.ENGLISH) {
                "Task , notes"
            } else {
                "Task , note"
            }

            assertEquals(raw, expectedTitle, result.draft.title)
            assertEquals(raw, expectedRule, result.draft.recurrenceRule)
            assertEquals(raw, listOf(expectedMatch), result.consumed)
            assertEquals(
                raw,
                setOf(RecognizedField.TITLE, RecognizedField.RECURRENCE),
                result.recognized
            )
            assertTrue(raw, result.issues.isEmpty())
        }

        val malformedContinuations = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every month / Friday"),
            Pair(ParserLanguage.ITALIAN, "Task ogni mese / venerdì"),
            Pair(ParserLanguage.ENGLISH, "Task every month, Mondays"),
            Pair(ParserLanguage.ITALIAN, "Task ogni mese, lunedìs")
        )
        malformedContinuations.forEach { (language, raw) ->
            val result = parse(raw, language)

            assertEquals(raw, raw, result.draft.title)
            assertNull(raw, result.draft.recurrenceRule)
            assertTrue(raw, result.consumed.isEmpty())
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        }
    }

    @Test
    fun malformedOrdinalPreservesWholeTitleAndShieldsInnerTime() {
        val raw = "Report fifth Monday of every month at 5 pm"

        val result = parse(raw, ParserLanguage.ENGLISH)

        assertEquals(raw, result.draft.title)
        assertNull(result.draft.dueAt)
        assertNull(result.draft.recurrenceRule)
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), result.issues)
        assertTrue(result.consumed.isEmpty())
    }

    @Test
    fun unsupportedAndContradictoryContinuationsPreserveExactTitleWithoutConsumption() {
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
            val result = parse(raw, language)

            assertEquals(raw, raw, result.draft.title)
            assertNull(raw, result.draft.recurrenceRule)
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
            assertTrue(raw, result.consumed.isEmpty())
            assertEquals(raw, setOf(RecognizedField.TITLE), result.recognized)
        }
    }

    @Test
    fun malformedOuterOrdinalsCannotBacktrackIntoNestedMonthlyRule() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Task fifth Mondays of every month"),
            Pair(ParserLanguage.ENGLISH, "Task sixth Monday of every month"),
            Pair(ParserLanguage.ENGLISH, "Task eleventh Monday of every month"),
            Pair(ParserLanguage.ITALIAN, "Task sesto lunedì di ogni mese"),
            Pair(ParserLanguage.ITALIAN, "Task undicesimo lunedì di ogni mese")
        )

        cases.forEach { (language, raw) ->
            val result = parse(raw, language)

            assertEquals(raw, raw, result.draft.title)
            assertNull(raw, result.draft.recurrenceRule)
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
            assertTrue(raw, result.consumed.isEmpty())
        }
    }

    @Test
    fun normalizedItalianAcuteWeekdayConsumesTheOriginalSourceRange() {
        val raw = "Task ogni lunedí e venerdì"

        val result = parse(raw, ParserLanguage.ITALIAN)

        assertEquals("Task", result.draft.title)
        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            result.draft.recurrenceRule
        )
        assertEquals(
            listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
            result.consumed
        )
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun malformedRecurrenceStopsBeforeIndependentPriorityCategoryAndPunctuation() {
        val priorityRaw = "Task every nonsense !high"
        val priority = parse(priorityRaw, ParserLanguage.ENGLISH)
        assertEquals("Task every nonsense", priority.draft.title)
        assertEquals(TaskPriority.HIGH, priority.draft.priority)
        assertNull(priority.draft.recurrenceRule)
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), priority.issues)
        assertEquals(
            listOf(
                SourceMatch(
                    priorityRaw.indexOf("!high"),
                    priorityRaw.length,
                    RecognizedField.PRIORITY
                )
            ),
            priority.consumed
        )

        val categoryRaw = "Task every nonsense #Home"
        val category = parse(
            categoryRaw,
            ParserLanguage.ENGLISH,
            listOf(CategoryCandidate(8, "Home"))
        )
        assertEquals("Task every nonsense", category.draft.title)
        assertEquals(8, category.draft.categoryId)
        assertNull(category.draft.recurrenceRule)
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), category.issues)
        assertEquals(
            listOf(
                SourceMatch(
                    categoryRaw.indexOf("#Home"),
                    categoryRaw.length,
                    RecognizedField.CATEGORY
                )
            ),
            category.consumed
        )

        val punctuationRaw = "Task every nonsense, !high"
        val punctuation = parse(punctuationRaw, ParserLanguage.ENGLISH)
        assertEquals("Task every nonsense,", punctuation.draft.title)
        assertEquals(TaskPriority.HIGH, punctuation.draft.priority)
        assertNull(punctuation.draft.recurrenceRule)
        assertEquals(listOf(ParseIssue.AmbiguousRecurrence), punctuation.issues)
        assertEquals(
            listOf(
                SourceMatch(
                    punctuationRaw.indexOf("!high"),
                    punctuationRaw.length,
                    RecognizedField.PRIORITY
                )
            ),
            punctuation.consumed
        )
    }

    @Test
    fun validCommaSeparatedWeekdaysStillConsumeRecurrenceAndIndependentPriority() {
        val raw = "Task every Monday, Friday !high"

        val result = parse(raw, ParserLanguage.ENGLISH)

        assertEquals("Task", result.draft.title)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            result.draft.recurrenceRule
        )
        assertEquals(
            listOf(
                SourceMatch(5, raw.indexOf(" !high"), RecognizedField.RECURRENCE),
                SourceMatch(raw.indexOf("!high"), raw.length, RecognizedField.PRIORITY)
            ),
            result.consumed
        )
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun validOrdinalAndOrdinaryTrailingPunctuationRemainCorrectable() {
        val ordinalRaw = "Task fourth Monday of every month"
        val ordinal = parse(ordinalRaw, ParserLanguage.ENGLISH)
        assertEquals("Task", ordinal.draft.title)
        assertEquals(
            RecurrenceRule.MonthlyOrdinal(
                MonthlyOrdinalValue.FOURTH,
                DayOfWeek.MONDAY,
                1,
                RecurrenceBasis.SCHEDULED_DATE
            ),
            ordinal.draft.recurrenceRule
        )
        assertEquals(
            listOf(SourceMatch(5, ordinalRaw.length, RecognizedField.RECURRENCE)),
            ordinal.consumed
        )
        assertTrue(ordinal.issues.isEmpty())

        val punctuationRaw = "Task every Monday, notes"
        val punctuation = parse(punctuationRaw, ParserLanguage.ENGLISH)
        assertEquals("Task , notes", punctuation.draft.title)
        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            punctuation.draft.recurrenceRule
        )
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    punctuationRaw.indexOf(','),
                    RecognizedField.RECURRENCE
                )
            ),
            punctuation.consumed
        )
        assertTrue(punctuation.issues.isEmpty())
    }

    @Test
    fun punctuationWeekdayContinuationsPreserveExactTitleWithoutConsumption() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Task every Monday / Friday"),
            Pair(ParserLanguage.ITALIAN, "Task ogni lunedì / venerdì")
        )

        cases.forEach { (language, raw) ->
            val result = parse(raw, language)

            assertEquals(raw, raw, result.draft.title)
            assertNull(raw, result.draft.recurrenceRule)
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
            assertTrue(raw, result.consumed.isEmpty())
            assertEquals(raw, setOf(RecognizedField.TITLE), result.recognized)
        }

        val ordinaryRaw = "Task every Monday / notes"
        val ordinary = parse(ordinaryRaw, ParserLanguage.ENGLISH)
        assertEquals("Task / notes", ordinary.draft.title)
        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            ordinary.draft.recurrenceRule
        )
        assertEquals(
            listOf(
                SourceMatch(
                    5,
                    ordinaryRaw.indexOf(" /"),
                    RecognizedField.RECURRENCE
                )
            ),
            ordinary.consumed
        )
        assertTrue(ordinary.issues.isEmpty())
    }

    @Test
    fun unsuffixedNumericOrdinalShellsPreserveExactTitleAndBlockNestedMonth() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Task 11 Monday of every month"),
            Pair(ParserLanguage.ITALIAN, "Task 11 lunedì di ogni mese")
        )

        cases.forEach { (language, raw) ->
            val result = parse(raw, language)

            assertEquals(raw, raw, result.draft.title)
            assertNull(raw, result.draft.recurrenceRule)
            assertEquals(raw, listOf(ParseIssue.AmbiguousRecurrence), result.issues)
            assertTrue(raw, result.consumed.isEmpty())
            assertEquals(raw, setOf(RecognizedField.TITLE), result.recognized)
        }
    }

    @Test
    fun ordinaryMonthlyTitlesRemainIssueFree() {
        val cases = listOf(
            Pair(ParserLanguage.ENGLISH, "Best photo of the month"),
            Pair(ParserLanguage.ITALIAN, "Foto preferita del mese")
        )

        cases.forEach { (language, raw) ->
            val result = parse(raw, language)

            assertEquals(raw, raw, result.draft.title)
            assertNull(raw, result.draft.recurrenceRule)
            assertTrue(raw, result.issues.isEmpty())
            assertTrue(raw, result.consumed.isEmpty())
            assertEquals(raw, setOf(RecognizedField.TITLE), result.recognized)
        }
    }

    @Test
    fun normalizedWholeItalianWeekdayTokenRetainsExactConsumedRange() {
        val variants = listOf("sábato", "sa\u0301bato")

        variants.forEach { saturday ->
            val raw = "Task ogni $saturday e domenica"
            val result = parse(raw, ParserLanguage.ITALIAN)

            assertEquals(raw, "Task", result.draft.title)
            assertEquals(
                raw,
                RecurrenceRule.SelectedWeekdays(
                    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    RecurrenceBasis.SCHEDULED_DATE
                ),
                result.draft.recurrenceRule
            )
            assertEquals(
                raw,
                listOf(SourceMatch(5, raw.length, RecognizedField.RECURRENCE)),
                result.consumed
            )
            assertTrue(raw, result.issues.isEmpty())
        }
    }

    @Test
    fun categories_resolveCustomLocalizedQuotedAndNormalizedNames() {
        val cases = listOf(
            CategoryCase("Task #Casa", CategoryCandidate(7, "Casa")),
            CategoryCase("Task #Lavoro", CategoryCandidate(8, "Lavoro")),
            CategoryCase("Task #\"Progetti Casa\"", CategoryCandidate(9, "Progetti Casa")),
            CategoryCase("Task #CAFFE", CategoryCandidate(10, "Caffè"))
        )

        cases.forEach { case ->
            val result = parse(
                raw = case.raw,
                language = ParserLanguage.ITALIAN,
                categories = listOf(case.candidate)
            )

            assertEquals(case.raw, case.candidate.id, result.draft.categoryId)
            assertEquals(case.raw, "Task", result.draft.title)
            assertTrue(case.raw, result.issues.isEmpty())
        }
    }

    @Test
    fun categoryMatching_preservesDevanagariMarksAndNormalizesNbsp() {
        val distinctRaw = "Task #कम"
        val distinct = parse(
            raw = distinctRaw,
            language = ParserLanguage.ENGLISH,
            categories = listOf(CategoryCandidate(41, "काम"))
        )

        assertNull(distinct.draft.categoryId)
        assertEquals(distinctRaw, distinct.draft.title)
        assertEquals(listOf(ParseIssue.UnknownCategory("#कम")), distinct.issues)

        val spacedRaw = "Task #\"Progetti\u00a0Casa\""
        val spaced = parse(
            raw = spacedRaw,
            language = ParserLanguage.ITALIAN,
            categories = listOf(CategoryCandidate(42, "Progetti Casa"))
        )

        assertEquals(42, spaced.draft.categoryId)
        assertEquals("Task", spaced.draft.title)
        assertTrue(spaced.issues.isEmpty())
    }

    @Test
    fun crossGrammarSlashTimeOverlaps_areRecoverableAndRemainVisible() {
        val cases = listOf(
            "Task alle 1/2",
            "Task domani alle 1/2 promemoria 1h prima",
            "Task promemoria alle 1/2"
        )

        cases.forEach { raw ->
            val result = parse(raw, ParserLanguage.ITALIAN)

            assertPairwiseDisjoint(raw, result.consumed)
            assertTrue(raw, result.draft.title?.contains("alle 1/2") == true)
            assertTrue(
                raw,
                result.consumed.none { match ->
                    raw.substring(match.start, match.endExclusive).contains("1/2")
                }
            )
        }
    }

    @Test
    fun unknownAndAmbiguousCategories_remainWhollyUnconsumed() {
        val raw = "Task #Unknown #CAFFE"

        val result = parse(
            raw = raw,
            language = ParserLanguage.ENGLISH,
            categories = listOf(
                CategoryCandidate(1, "Caffè"),
                CategoryCandidate(2, "CAFFE")
            )
        )

        assertNull(result.draft.categoryId)
        assertEquals(raw, result.draft.title)
        assertEquals(
            listOf(
                ParseIssue.UnknownCategory("#Unknown"),
                ParseIssue.AmbiguousCategory("#CAFFE")
            ),
            result.issues
        )
        assertTrue(result.consumed.none { it.field == RecognizedField.CATEGORY })
    }

    @Test
    fun categoryMarkers_ownGrammarBearingContentsForEveryResolutionOutcome() {
        val contents = listOf(
            CategoryCollision("#today", "today", RecognizedField.DUE_DATE),
            CategoryCollision("#\"at 5 pm\"", "at 5 pm", RecognizedField.DUE_DATE),
            CategoryCollision("#\"!high\"", "!high", RecognizedField.PRIORITY),
            CategoryCollision("#\"every week\"", "every week", RecognizedField.RECURRENCE),
            CategoryCollision(
                "#\"remind tomorrow at 5 pm\"",
                "remind tomorrow at 5 pm",
                RecognizedField.REMINDER
            )
        )

        contents.forEach { collision ->
            CategoryResolution.entries.forEach { resolution ->
                val raw = "Task ${collision.marker}"
                val categories = when (resolution) {
                    CategoryResolution.KNOWN -> listOf(CategoryCandidate(7, collision.displayName))
                    CategoryResolution.UNKNOWN -> emptyList()
                    CategoryResolution.AMBIGUOUS -> listOf(
                        CategoryCandidate(7, collision.displayName),
                        CategoryCandidate(8, collision.displayName.uppercase())
                    )
                }

                val result = parse(raw, ParserLanguage.ENGLISH, categories)

                assertFalse("$resolution $raw", collision.leakedField in result.recognized)
                assertTrue(
                    "$resolution $raw",
                    result.consumed.none { it.field == collision.leakedField }
                )
                when (resolution) {
                    CategoryResolution.KNOWN -> {
                        assertEquals(raw, 7, result.draft.categoryId)
                        assertEquals(raw, "Task", result.draft.title)
                        assertTrue(raw, result.issues.isEmpty())
                    }

                    CategoryResolution.UNKNOWN -> {
                        assertNull(raw, result.draft.categoryId)
                        assertEquals(raw, raw, result.draft.title)
                        assertEquals(raw, listOf(ParseIssue.UnknownCategory(collision.marker)), result.issues)
                    }

                    CategoryResolution.AMBIGUOUS -> {
                        assertNull(raw, result.draft.categoryId)
                        assertEquals(raw, raw, result.draft.title)
                        assertEquals(
                            raw,
                            listOf(ParseIssue.AmbiguousCategory(collision.marker)),
                            result.issues
                        )
                    }
                }
            }
        }
    }

    @Test
    fun categoryMarkersInsideLowerGrammar_areAuthoritativeOwnershipBarriers() {
        val cases = listOf(
            MarkerBridgeCase(
                raw = "Task every #\"Home\" week",
                markerStart = 11,
                markerEndExclusive = 18,
                blockedField = RecognizedField.RECURRENCE,
                knownTitle = "Task every week"
            ),
            MarkerBridgeCase(
                raw = "Task at #\"Home\" 5 pm",
                markerStart = 8,
                markerEndExclusive = 15,
                blockedField = RecognizedField.DUE_DATE,
                knownTitle = "Task at 5 pm"
            ),
            MarkerBridgeCase(
                raw = "Task remind #\"Home\" tomorrow at 5 pm",
                markerStart = 12,
                markerEndExclusive = 19,
                blockedField = RecognizedField.REMINDER,
                knownTitle = "Task remind",
                unresolvedTitle = "Task remind #\"Home\"",
                expectedDueAt = epoch("2026-08-27T17:00:00+02:00"),
                additionalConsumed = listOf(
                    SourceMatch(20, 28, RecognizedField.DUE_DATE),
                    SourceMatch(29, 36, RecognizedField.DUE_DATE)
                )
            )
        )

        cases.forEach { case ->
            CategoryResolution.entries.forEach { resolution ->
                val categories = when (resolution) {
                    CategoryResolution.KNOWN -> listOf(CategoryCandidate(7, "Home"))
                    CategoryResolution.UNKNOWN -> emptyList()
                    CategoryResolution.AMBIGUOUS -> listOf(
                        CategoryCandidate(7, "Home"),
                        CategoryCandidate(8, "HOME")
                    )
                }
                val input = input(case.raw, ParserLanguage.ENGLISH, categories)
                val ownedRange = SourceMatch(
                    case.markerStart,
                    case.markerEndExclusive,
                    RecognizedField.CATEGORY
                )
                val recurrenceIssues = if (case.blockedField == RecognizedField.RECURRENCE) {
                    listOf(ParseIssue.AmbiguousRecurrence)
                } else {
                    emptyList()
                }

                assertEquals(
                    "$resolution ${case.raw}",
                    listOf(ownedRange),
                    AttributeParser().ownedMarkerRanges(input)
                )

                val result = parser(input)

                assertPairwiseDisjoint(case.raw, result.consumed)
                assertFalse(
                    "$resolution ${case.raw}",
                    case.blockedField in result.recognized
                )
                assertTrue(
                    "$resolution ${case.raw}",
                    result.consumed.none { it.field == case.blockedField }
                )
                assertEquals(case.raw, case.expectedDueAt, result.draft.dueAt)
                when (resolution) {
                    CategoryResolution.KNOWN -> {
                        assertEquals(case.raw, case.knownTitle, result.draft.title)
                        assertEquals(case.raw, 7, result.draft.categoryId)
                        assertEquals(
                            case.raw,
                            (listOf(ownedRange) + case.additionalConsumed)
                                .sortedBy(SourceMatch::start),
                            result.consumed
                        )
                        assertEquals(case.raw, recurrenceIssues, result.issues)
                    }

                    CategoryResolution.UNKNOWN -> {
                        assertEquals(
                            case.raw,
                            case.unresolvedTitle ?: case.raw,
                            result.draft.title
                        )
                        assertNull(case.raw, result.draft.categoryId)
                        assertEquals(case.raw, case.additionalConsumed, result.consumed)
                        assertEquals(
                            case.raw,
                            listOf(ParseIssue.UnknownCategory("#\"Home\"")) + recurrenceIssues,
                            result.issues
                        )
                    }

                    CategoryResolution.AMBIGUOUS -> {
                        assertEquals(
                            case.raw,
                            case.unresolvedTitle ?: case.raw,
                            result.draft.title
                        )
                        assertNull(case.raw, result.draft.categoryId)
                        assertEquals(case.raw, case.additionalConsumed, result.consumed)
                        assertEquals(
                            case.raw,
                            listOf(ParseIssue.AmbiguousCategory("#\"Home\"")) + recurrenceIssues,
                            result.issues
                        )
                    }
                }
            }
        }
    }

    @Test
    fun reminderRangesInsideTemporalPhrases_blockIntersectingDueCandidates() {
        val validRaw = "Task at remind tomorrow 5 pm"
        val validInput = input(validRaw, ParserLanguage.ENGLISH)
        val validRange = SourceMatch(8, 23, RecognizedField.REMINDER)

        assertEquals(
            listOf(validRange),
            ReminderParser().shieldingRanges(validInput)
        )

        val validResult = parser(validInput)

        assertEquals("Task at 5 pm", validResult.draft.title)
        assertNull(validResult.draft.dueAt)
        assertEquals(epoch("2026-08-27T09:00:00+02:00"), validResult.draft.reminderAt)
        assertEquals(listOf(validRange), validResult.consumed)
        assertPairwiseDisjoint(validRaw, validResult.consumed)

        val relativeRaw = "Task at remind 1h before 5 pm"
        val relativeInput = input(relativeRaw, ParserLanguage.ENGLISH)
        val relativeRange = SourceMatch(8, 24, RecognizedField.REMINDER)

        assertEquals(
            listOf(relativeRange),
            ReminderParser().shieldingRanges(relativeInput)
        )

        val relativeResult = parser(relativeInput)

        assertEquals(relativeRaw, relativeResult.draft.title)
        assertNull(relativeResult.draft.dueAt)
        assertNull(relativeResult.draft.reminderAt)
        assertTrue(relativeResult.consumed.isEmpty())
        assertEquals(listOf(ParseIssue.RelativeReminderWithoutDueDate), relativeResult.issues)
        assertPairwiseDisjoint(relativeRaw, relativeResult.consumed)
    }

    @Test
    fun multilineAndDoubledMalformedMarkers_shieldEveryLowerGrammar() {
        val grammarContents = listOf(
            "today",
            "!high",
            "every week",
            "at 5 pm",
            "remind tomorrow at 5 pm"
        )

        grammarContents.forEach { content ->
            val multilineRaw = "Task #\"$content\nnotes"
            val multilineInput = input(multilineRaw, ParserLanguage.ENGLISH)
            val lineEnd = multilineRaw.indexOf('\n')
            val multilineRange = SourceMatch(5, lineEnd, RecognizedField.CATEGORY)

            assertEquals(
                multilineRaw,
                listOf(multilineRange),
                AttributeParser().ownedMarkerRanges(multilineInput)
            )

            val multilineResult = parser(multilineInput)

            assertEquals(multilineRaw, "Task #\"$content notes", multilineResult.draft.title)
            assertTrue(multilineRaw, multilineResult.consumed.isEmpty())
            assertTrue(multilineRaw, multilineResult.issues.isEmpty())
            assertNoParsedFields(multilineRaw, multilineResult)
            assertPairwiseDisjoint(multilineRaw, multilineResult.consumed)

            val doubledMarker = if (content.any(Char::isWhitespace)) {
                "##\"$content\""
            } else {
                "##$content"
            }
            val doubledRaw = "Task $doubledMarker"
            val doubledInput = input(doubledRaw, ParserLanguage.ENGLISH)
            val doubledRange = SourceMatch(5, doubledRaw.length, RecognizedField.CATEGORY)

            assertEquals(
                doubledRaw,
                listOf(doubledRange),
                AttributeParser().ownedMarkerRanges(doubledInput)
            )

            val doubledResult = parser(doubledInput)

            assertEquals(doubledRaw, doubledRaw, doubledResult.draft.title)
            assertTrue(doubledRaw, doubledResult.consumed.isEmpty())
            assertTrue(doubledRaw, doubledResult.issues.isEmpty())
            assertNoParsedFields(doubledRaw, doubledResult)
            assertPairwiseDisjoint(doubledRaw, doubledResult.consumed)
        }
    }

    @Test
    fun localizedAbsoluteReminders_doNotBecomeDueDateTokens() {
        val cases = listOf(
            AbsoluteReminderCase(
                language = ParserLanguage.ITALIAN,
                raw = "Visita domani alle 18 promemoria 27/08/2026 alle 17",
                reminderPhrase = "promemoria 27/08/2026 alle 17"
            ),
            AbsoluteReminderCase(
                language = ParserLanguage.ENGLISH,
                raw = "Visit tomorrow at 6 pm remind 08/27/2026 at 5 pm",
                reminderPhrase = "remind 08/27/2026 at 5 pm"
            )
        )

        cases.forEach { case ->
            val result = parse(case.raw, case.language)

            assertEquals(case.raw, "Visita".takeIf { case.language == ParserLanguage.ITALIAN } ?: "Visit", result.draft.title)
            assertEquals(case.raw, epoch("2026-08-27T18:00:00+02:00"), result.draft.dueAt)
            assertEquals(case.raw, epoch("2026-08-27T17:00:00+02:00"), result.draft.reminderAt)
            assertEquals(
                case.raw,
                listOf(case.reminderPhrase),
                consumedText(case.raw, result.consumed.filter { it.field == RecognizedField.REMINDER })
            )
            assertEquals(
                case.raw,
                2,
                result.consumed.count { it.field == RecognizedField.DUE_DATE }
            )
        }
    }

    @Test
    fun absoluteReminderWithoutDue_recognizesOnlyReminderDateAndTime() {
        val raw = "Task remind tomorrow at 5 pm"

        val result = parse(raw, ParserLanguage.ENGLISH)

        assertEquals("Task", result.draft.title)
        assertNull(result.draft.dueAt)
        assertEquals(epoch("2026-08-27T17:00:00+02:00"), result.draft.reminderAt)
        assertFalse(RecognizedField.DUE_DATE in result.recognized)
        assertEquals(
            listOf("remind tomorrow at 5 pm"),
            consumedText(raw, result.consumed)
        )
    }

    @Test
    fun continuedAbsoluteReminderDate_remainsWhollyUnconsumed() {
        val raw = "Task remind 1/2/2026.0"

        val result = parse(raw, ParserLanguage.ENGLISH)

        assertEquals(raw, result.draft.title)
        assertNull(result.draft.dueAt)
        assertNull(result.draft.reminderAt)
        assertTrue(result.consumed.isEmpty())
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun invalidAbsoluteReminders_shieldValidInnerTemporalTokensWithoutConsumption() {
        val cases = listOf(
            InvalidReminderCase(
                raw = "Task remind tomorrow at 25 pm",
                expectedTitle = "Task remind tomorrow at 25 pm",
                expectedDueAt = null,
                expectedDueTokens = emptyList()
            ),
            InvalidReminderCase(
                raw = "Task today remind tomorrow at 25 pm",
                expectedTitle = "Task remind tomorrow at 25 pm",
                expectedDueAt = epoch("2026-08-26T09:00:00+02:00"),
                expectedDueTokens = listOf("today")
            ),
            InvalidReminderCase(
                raw = "Task remind 13/40/2026 at 5 pm",
                expectedTitle = "Task remind 13/40/2026 at 5 pm",
                expectedDueAt = null,
                expectedDueTokens = emptyList()
            ),
            InvalidReminderCase(
                raw = "Task remind 13/40/2026.0 at 5 pm",
                expectedTitle = "Task remind 13/40/2026.0 at 5 pm",
                expectedDueAt = null,
                expectedDueTokens = emptyList()
            )
        )

        cases.forEach { case ->
            val result = parse(case.raw, ParserLanguage.ENGLISH)

            assertEquals(case.raw, case.expectedTitle, result.draft.title)
            assertEquals(case.raw, case.expectedDueAt, result.draft.dueAt)
            assertNull(case.raw, result.draft.reminderAt)
            assertEquals(
                case.raw,
                case.expectedDueTokens,
                consumedText(
                    case.raw,
                    result.consumed.filter { it.field == RecognizedField.DUE_DATE }
                )
            )
            assertTrue(
                case.raw,
                result.consumed.none { it.field == RecognizedField.REMINDER }
            )
            assertTrue(case.raw, result.issues.isEmpty())
        }
    }

    @Test
    fun malformedAbsoluteTimeSuffix_ownsFullAttemptWithoutDateOnlyBacktracking() {
        val cases = listOf(
            MalformedReminderSuffixCase(
                raw = "Task remind tomorrow at 5 pmx",
                language = ParserLanguage.ENGLISH
            ),
            MalformedReminderSuffixCase(
                raw = "Task remind tomorrow at 5 xm",
                language = ParserLanguage.ENGLISH
            ),
            MalformedReminderSuffixCase(
                raw = "Task remind tomorrow at 5 pm.0",
                language = ParserLanguage.ENGLISH
            ),
            MalformedReminderSuffixCase(
                raw = "Task promemoria domani alle 17x",
                language = ParserLanguage.ITALIAN
            ),
            MalformedReminderSuffixCase(
                raw = "Task promemoria domani alle 17:30:1",
                language = ParserLanguage.ITALIAN
            )
        )

        cases.forEach { case ->
            val input = input(case.raw, case.language)
            val ownedRange = SourceMatch(5, case.raw.length, RecognizedField.REMINDER)

            assertEquals(
                case.raw,
                listOf(ownedRange),
                ReminderParser().shieldingRanges(input)
            )

            val result = parser(input)

            assertEquals(case.raw, case.raw, result.draft.title)
            assertNull(case.raw, result.draft.dueAt)
            assertNull(case.raw, result.draft.reminderAt)
            assertTrue(case.raw, result.consumed.isEmpty())
            assertTrue(case.raw, result.issues.isEmpty())
            assertPairwiseDisjoint(case.raw, result.consumed)
        }
    }

    @Test
    fun duplicateReminderOrders_consumeBothAndUseLastValidSourceValue() {
        val cases = listOf(
            ReminderOrderCase(
                raw = "Task today at 6 pm remind tomorrow at 5 pm remind 1h before",
                expectedReminderAt = epoch("2026-08-26T17:00:00+02:00")
            ),
            ReminderOrderCase(
                raw = "Task today at 6 pm remind 1h before remind tomorrow at 5 pm",
                expectedReminderAt = epoch("2026-08-27T17:00:00+02:00")
            )
        )

        cases.forEach { case ->
            val result = parse(case.raw, ParserLanguage.ENGLISH)

            assertEquals(case.raw, "Task", result.draft.title)
            assertEquals(case.raw, epoch("2026-08-26T18:00:00+02:00"), result.draft.dueAt)
            assertEquals(case.raw, case.expectedReminderAt, result.draft.reminderAt)
            assertEquals(
                case.raw,
                2,
                result.consumed.count { it.field == RecognizedField.REMINDER }
            )
            assertEquals(
                case.raw,
                listOf(ParseIssue.DuplicateField(RecognizedField.REMINDER)),
                result.issues
            )
        }
    }

    @Test
    fun embeddedMalformedAndContinuedTokens_areNotRecognized() {
        val cases = listOf(
            "Task x!high !!high !highway",
            "Task x#Home ##Home",
            "Task #\"today",
            "Task every weekdays every weeks",
            "Task xremind 1h before reminder 1h before remindful"
        )

        cases.forEach { raw ->
            val result = parse(
                raw = raw,
                language = ParserLanguage.ENGLISH,
                categories = listOf(CategoryCandidate(7, "Home"))
            )

            assertEquals(raw, raw, result.draft.title)
            assertNull(raw, result.draft.dueAt)
            assertNull(raw, result.draft.reminderAt)
            assertNull(raw, result.draft.priority)
            assertNull(raw, result.draft.categoryId)
            assertNull(raw, result.draft.recurrenceRule)
            assertTrue(raw, result.consumed.isEmpty())
            assertEquals(
                raw,
                if (raw == "Task every weekdays every weeks") {
                    listOf(ParseIssue.AmbiguousRecurrence)
                } else {
                    emptyList<ParseIssue>()
                },
                result.issues
            )
        }
    }

    @Test
    fun absoluteReminderDefaultsAndPastValues_passThroughAsTypedInstants() {
        val cases = listOf(
            AbsoluteEdgeCase(
                raw = "Task remind tomorrow",
                language = ParserLanguage.ENGLISH,
                expectedReminderAt = epoch("2026-08-27T09:00:00+02:00")
            ),
            AbsoluteEdgeCase(
                raw = "Task promemoria alle 8",
                language = ParserLanguage.ITALIAN,
                expectedReminderAt = epoch("2026-08-26T08:00:00+02:00")
            ),
            AbsoluteEdgeCase(
                raw = "Task remind 08/25/2026 at 5 pm",
                language = ParserLanguage.ENGLISH,
                expectedReminderAt = epoch("2026-08-25T17:00:00+02:00")
            )
        )

        cases.forEach { case ->
            val result = parse(case.raw, case.language)

            assertEquals(case.raw, "Task", result.draft.title)
            assertNull(case.raw, result.draft.dueAt)
            assertEquals(case.raw, case.expectedReminderAt, result.draft.reminderAt)
            assertEquals(
                case.raw,
                1,
                result.consumed.count { it.field == RecognizedField.REMINDER }
            )
            assertTrue(case.raw, result.issues.isEmpty())
        }
    }

    @Test
    fun reminderArithmeticOverflow_keepsReminderSyntaxVisible() {
        val cases = listOf(
            OverflowCase(
                raw = "Task today remind 2562047788016h before",
                expectedTitle = "Task remind 2562047788016h before"
            ),
            OverflowCase(
                raw = "Task 01/01/0001 remind 153722867280912m before",
                expectedTitle = "Task remind 153722867280912m before"
            )
        )

        cases.forEach { case ->
            val result = parse(case.raw, ParserLanguage.ENGLISH)

            assertEquals(case.raw, case.expectedTitle, result.draft.title)
            assertTrue(case.raw, RecognizedField.DUE_DATE in result.recognized)
            assertNull(case.raw, result.draft.reminderAt)
            assertTrue(
                case.raw,
                result.consumed.none { it.field == RecognizedField.REMINDER }
            )
            assertTrue(case.raw, result.issues.isEmpty())
        }
    }

    @Test
    fun parserStageAndOrchestrationResults_areUnmodifiable() {
        val attributeResult = AttributeParser().parse(
            input("Task !high", ParserLanguage.ENGLISH)
        )
        val reminderResult = ReminderParser().parse(
            input("Task remind tomorrow", ParserLanguage.ENGLISH),
            dueAt = null
        )
        val result = parse("Task today !high", ParserLanguage.ENGLISH)

        assertThrows(UnsupportedOperationException::class.java) {
            (attributeResult.matches as MutableList<SourceMatch>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (attributeResult.issues as MutableList<ParseIssue>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (reminderResult.matches as MutableList<SourceMatch>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (reminderResult.issues as MutableList<ParseIssue>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.consumed as MutableList<SourceMatch>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.issues as MutableList<ParseIssue>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.recognized as MutableSet<RecognizedField>).clear()
        }
    }

    @Test
    fun dependencyInvalidRelativeReminder_reportsIssueAndRemainsInTitle() {
        val raw = "Chiama promemoria 30m prima !alta"

        val result = parse(raw, ParserLanguage.ITALIAN)

        assertEquals("Chiama promemoria 30m prima", result.draft.title)
        assertNull(result.draft.reminderAt)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(listOf(ParseIssue.RelativeReminderWithoutDueDate), result.issues)
        assertTrue(result.consumed.none { it.field == RecognizedField.REMINDER })
    }

    @Test
    fun duplicateRecurrencePreservesBothPhrasesAndAppliesNoRule() {
        val raw = "Task !low !high #Home #Work every day every month tomorrow today " +
            "remind 1h before remind 30m before"

        val result = parse(
            raw = raw,
            language = ParserLanguage.ENGLISH,
            categories = listOf(
                CategoryCandidate(1, "Home"),
                CategoryCandidate(2, "Work")
            )
        )

        assertEquals("Task every day every month", result.draft.title)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(2, result.draft.categoryId)
        assertNull(result.draft.recurrenceRule)
        assertEquals(epoch("2026-08-26T09:00:00+02:00"), result.draft.dueAt)
        assertEquals(epoch("2026-08-26T08:30:00+02:00"), result.draft.reminderAt)
        assertEquals(
            listOf(
                ParseIssue.DuplicateField(RecognizedField.DUE_DATE),
                ParseIssue.DuplicateField(RecognizedField.REMINDER),
                ParseIssue.DuplicateField(RecognizedField.PRIORITY),
                ParseIssue.DuplicateField(RecognizedField.CATEGORY),
                ParseIssue.AmbiguousRecurrence
            ).toSet(),
            result.issues.toSet()
        )
        assertEquals(8, result.consumed.size)
        assertEquals(2, result.consumed.count { it.field == RecognizedField.DUE_DATE })
        assertEquals(2, result.consumed.count { it.field == RecognizedField.REMINDER })
        assertEquals(2, result.consumed.count { it.field == RecognizedField.PRIORITY })
        assertEquals(2, result.consumed.count { it.field == RecognizedField.CATEGORY })
        assertEquals(0, result.consumed.count { it.field == RecognizedField.RECURRENCE })
    }

    @Test
    fun invalidAttributeCandidates_stayVisibleWhileValidValuesAreApplied() {
        val raw = "Task !urgent !high #Unknown #Home every year every week"

        val result = parse(
            raw = raw,
            language = ParserLanguage.ENGLISH,
            categories = listOf(CategoryCandidate(1, "Home"))
        )

        assertEquals("Task !urgent #Unknown every year every week", result.draft.title)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(1, result.draft.categoryId)
        assertNull(result.draft.recurrenceRule)
        assertEquals(
            listOf(ParseIssue.UnknownCategory("#Unknown"), ParseIssue.AmbiguousRecurrence),
            result.issues
        )
    }

    @Test
    fun blankInput_returnsOnlyEmptyInputIssue() {
        val result = parse("  \n\t ", ParserLanguage.ITALIAN)

        assertNull(result.draft.title)
        assertNull(result.draft.dueAt)
        assertNull(result.draft.reminderAt)
        assertNull(result.draft.priority)
        assertNull(result.draft.categoryId)
        assertNull(result.draft.recurrenceRule)
        assertTrue(result.recognized.isEmpty())
        assertTrue(result.consumed.isEmpty())
        assertEquals(listOf(ParseIssue.EmptyInput), result.issues)
    }

    private fun parse(
        raw: String,
        language: ParserLanguage,
        categories: List<CategoryCandidate> = emptyList()
    ) = parser(input(raw, language, categories))

    private fun input(
        raw: String,
        language: ParserLanguage,
        categories: List<CategoryCandidate> = emptyList()
    ) = NaturalLanguageInput(
        rawText = raw,
        language = language,
        nowEpochMillis = NOW,
        zoneId = ROME,
        categories = categories
    )

    private fun consumedText(raw: String, matches: List<SourceMatch>) =
        matches.sortedBy { it.start }.map { raw.substring(it.start, it.endExclusive) }

    private fun assertNoParsedFields(
        message: String,
        result: com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageParseResult
    ) {
        assertNull(message, result.draft.dueAt)
        assertNull(message, result.draft.reminderAt)
        assertNull(message, result.draft.priority)
        assertNull(message, result.draft.categoryId)
        assertNull(message, result.draft.recurrenceRule)
        assertEquals(message, setOf(RecognizedField.TITLE), result.recognized)
    }

    private fun assertPairwiseDisjoint(message: String, matches: List<SourceMatch>) {
        matches.sortedBy(SourceMatch::start).zipWithNext().forEach { (current, next) ->
            assertTrue(message, current.endExclusive <= next.start)
        }
    }

    private data class CategoryCase(
        val raw: String,
        val candidate: CategoryCandidate
    )

    private data class AdvancedRecurrenceCase(
        val language: ParserLanguage,
        val raw: String,
        val title: String,
        val rule: RecurrenceRule
    )

    private data class AbsoluteReminderCase(
        val language: ParserLanguage,
        val raw: String,
        val reminderPhrase: String
    )

    private data class InvalidReminderCase(
        val raw: String,
        val expectedTitle: String,
        val expectedDueAt: Long?,
        val expectedDueTokens: List<String>
    )

    private data class ReminderOrderCase(
        val raw: String,
        val expectedReminderAt: Long
    )

    private data class AbsoluteEdgeCase(
        val raw: String,
        val language: ParserLanguage,
        val expectedReminderAt: Long
    )

    private data class OverflowCase(
        val raw: String,
        val expectedTitle: String
    )

    private data class MarkerBridgeCase(
        val raw: String,
        val markerStart: Int,
        val markerEndExclusive: Int,
        val blockedField: RecognizedField,
        val knownTitle: String,
        val unresolvedTitle: String? = null,
        val expectedDueAt: Long? = null,
        val additionalConsumed: List<SourceMatch> = emptyList()
    )

    private data class MalformedReminderSuffixCase(
        val raw: String,
        val language: ParserLanguage
    )

    private data class CategoryCollision(
        val marker: String,
        val displayName: String,
        val leakedField: RecognizedField
    )

    private enum class CategoryResolution {
        KNOWN,
        UNKNOWN,
        AMBIGUOUS
    }

    private companion object {
        val ROME: ZoneId = ZoneId.of("Europe/Rome")
        val NOW: Long = epoch("2026-08-26T10:15:00+02:00")

        fun epoch(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    }
}
