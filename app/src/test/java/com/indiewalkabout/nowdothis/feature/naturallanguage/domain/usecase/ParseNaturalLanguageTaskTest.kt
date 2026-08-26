package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.usecase

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.CategoryCandidate
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
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
import org.junit.Assert.assertThrows
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
            assertNull(raw, result.draft.recurrence)
            assertTrue(raw, result.consumed.isEmpty())
            assertTrue(raw, result.issues.isEmpty())
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

    private data class CategoryCase(
        val raw: String,
        val candidate: CategoryCandidate
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
