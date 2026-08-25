package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.usecase

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.CategoryCandidate
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.AttributeParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.ReminderParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.TemporalParser
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseNaturalLanguageTaskTest {

    private val parser = ParseNaturalLanguageTask(
        temporalParser = TemporalParser(),
        attributeParser = AttributeParser(),
        reminderParser = ReminderParser()
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
        assertEquals(RecurrenceType.WEEKLY, result.draft.recurrence)
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
        assertEquals(RecurrenceType.WEEKLY, result.draft.recurrence)
        assertTrue(result.issues.isEmpty())
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
            Triple(ParserLanguage.ITALIAN, "ogni giorno", RecurrenceType.DAILY),
            Triple(ParserLanguage.ITALIAN, "ogni settimana", RecurrenceType.WEEKLY),
            Triple(ParserLanguage.ITALIAN, "ogni mese", RecurrenceType.MONTHLY),
            Triple(ParserLanguage.ENGLISH, "every day", RecurrenceType.DAILY),
            Triple(ParserLanguage.ENGLISH, "every week", RecurrenceType.WEEKLY),
            Triple(ParserLanguage.ENGLISH, "every month", RecurrenceType.MONTHLY)
        )

        cases.forEach { (language, phrase, recurrence) ->
            val result = parse("Task $phrase", language)

            assertEquals(phrase, recurrence, result.draft.recurrence)
            assertEquals(phrase, "Task", result.draft.title)
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
    fun validDuplicates_areAllConsumedAndLastExplicitValuesWin() {
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

        assertEquals("Task", result.draft.title)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(2, result.draft.categoryId)
        assertEquals(RecurrenceType.MONTHLY, result.draft.recurrence)
        assertEquals(epoch("2026-08-26T09:00:00+02:00"), result.draft.dueAt)
        assertEquals(epoch("2026-08-26T08:30:00+02:00"), result.draft.reminderAt)
        assertEquals(
            listOf(
                ParseIssue.DuplicateField(RecognizedField.DUE_DATE),
                ParseIssue.DuplicateField(RecognizedField.REMINDER),
                ParseIssue.DuplicateField(RecognizedField.PRIORITY),
                ParseIssue.DuplicateField(RecognizedField.CATEGORY),
                ParseIssue.DuplicateField(RecognizedField.RECURRENCE)
            ).toSet(),
            result.issues.toSet()
        )
        assertEquals(10, result.consumed.size)
        assertEquals(2, result.consumed.count { it.field == RecognizedField.DUE_DATE })
        assertEquals(2, result.consumed.count { it.field == RecognizedField.REMINDER })
        assertEquals(2, result.consumed.count { it.field == RecognizedField.PRIORITY })
        assertEquals(2, result.consumed.count { it.field == RecognizedField.CATEGORY })
        assertEquals(2, result.consumed.count { it.field == RecognizedField.RECURRENCE })
    }

    @Test
    fun invalidAttributeCandidates_stayVisibleWhileValidValuesAreApplied() {
        val raw = "Task !urgent !high #Unknown #Home every year every week"

        val result = parse(
            raw = raw,
            language = ParserLanguage.ENGLISH,
            categories = listOf(CategoryCandidate(1, "Home"))
        )

        assertEquals("Task !urgent #Unknown every year", result.draft.title)
        assertEquals(TaskPriority.HIGH, result.draft.priority)
        assertEquals(1, result.draft.categoryId)
        assertEquals(RecurrenceType.WEEKLY, result.draft.recurrence)
        assertEquals(listOf(ParseIssue.UnknownCategory("#Unknown")), result.issues)
    }

    @Test
    fun blankInput_returnsOnlyEmptyInputIssue() {
        val result = parse("  \n\t ", ParserLanguage.ITALIAN)

        assertNull(result.draft.title)
        assertNull(result.draft.dueAt)
        assertNull(result.draft.reminderAt)
        assertNull(result.draft.priority)
        assertNull(result.draft.categoryId)
        assertNull(result.draft.recurrence)
        assertTrue(result.recognized.isEmpty())
        assertTrue(result.consumed.isEmpty())
        assertEquals(listOf(ParseIssue.EmptyInput), result.issues)
    }

    private fun parse(
        raw: String,
        language: ParserLanguage,
        categories: List<CategoryCandidate> = emptyList()
    ) = parser(
        NaturalLanguageInput(
            rawText = raw,
            language = language,
            nowEpochMillis = NOW,
            zoneId = ROME,
            categories = categories
        )
    )

    private fun consumedText(raw: String, matches: List<com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch>) =
        matches.sortedBy { it.start }.map { raw.substring(it.start, it.endExclusive) }

    private data class CategoryCase(
        val raw: String,
        val candidate: CategoryCandidate
    )

    private data class AbsoluteReminderCase(
        val language: ParserLanguage,
        val raw: String,
        val reminderPhrase: String
    )

    private companion object {
        val ROME: ZoneId = ZoneId.of("Europe/Rome")
        val NOW: Long = epoch("2026-08-26T10:15:00+02:00")

        fun epoch(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    }
}
