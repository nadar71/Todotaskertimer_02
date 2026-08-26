package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.immutableListSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.util.Locale

data class RecurrenceCandidate(
    val rule: RecurrenceRule,
    val match: SourceMatch
)

@ConsistentCopyVisibility
data class RecurrenceParse private constructor(
    val rule: RecurrenceRule?,
    private val candidateSnapshot: List<RecurrenceCandidate>,
    private val matchSnapshot: List<SourceMatch>,
    private val ownedRangeSnapshot: List<SourceMatch>,
    private val issueSnapshot: List<ParseIssue>
) {
    val candidates: List<RecurrenceCandidate>
        get() = candidateSnapshot

    val matches: List<SourceMatch>
        get() = matchSnapshot

    val ownedRanges: List<SourceMatch>
        get() = ownedRangeSnapshot

    val issues: List<ParseIssue>
        get() = issueSnapshot

    companion object {
        operator fun invoke(
            rule: RecurrenceRule?,
            candidates: List<RecurrenceCandidate>,
            matches: List<SourceMatch>,
            ownedRanges: List<SourceMatch>,
            issues: List<ParseIssue>
        ): RecurrenceParse = RecurrenceParse(
            rule = rule,
            candidateSnapshot = immutableListSnapshot(candidates),
            matchSnapshot = immutableListSnapshot(matches),
            ownedRangeSnapshot = immutableListSnapshot(ownedRanges),
            issueSnapshot = immutableListSnapshot(issues)
        )
    }
}

class RecurrenceParser {

    fun parse(
        input: NaturalLanguageInput,
        dueAt: Long? = null,
        excludedRanges: List<SourceMatch> = emptyList()
    ): RecurrenceParse {
        val attempts = findAttempts(input, dueAt)
            .filterNot { attempt ->
                excludedRanges.any { excluded -> attempt.ownedRange.intersects(excluded) }
            }
        val candidates = attempts.mapNotNull(Attempt::candidate)
        val unambiguous = attempts.isNotEmpty() &&
            attempts.none(Attempt::malformed) &&
            candidates.size == 1
        val selected = candidates.singleOrNull().takeIf { unambiguous }

        return RecurrenceParse(
            rule = selected?.rule,
            candidates = candidates,
            matches = listOfNotNull(selected?.match),
            ownedRanges = attempts.map(Attempt::ownedRange).distinct().sortedBy(SourceMatch::start),
            issues = if (attempts.isNotEmpty() && !unambiguous) {
                listOf(ParseIssue.AmbiguousRecurrence)
            } else {
                emptyList()
            }
        )
    }

    internal fun ownershipRanges(
        input: NaturalLanguageInput,
        excludedRanges: List<SourceMatch> = emptyList()
    ): List<SourceMatch> = parse(
        input = input,
        excludedRanges = excludedRanges
    ).ownedRanges

    private fun findAttempts(input: NaturalLanguageInput, dueAt: Long?): List<Attempt> {
        val grammar = Grammar.forLanguage(input.language)
        val parsed = buildList {
            grammar.ordinalPattern.findAll(input.rawText).forEach { match ->
                parseOrdinal(match, input.language)?.let(::add)
            }
            grammar.intervalPattern.findAll(input.rawText).forEach { match ->
                add(parseInterval(match, input, dueAt))
            }
            grammar.legacyPattern.findAll(input.rawText).forEach { match ->
                add(parseLegacy(match, input, dueAt))
            }
            grammar.weekdayListPattern.findAll(input.rawText).forEach { match ->
                add(parseWeekdayList(match, input.language))
            }
        }
            .removeContainedAttempts()
            .map { attempt ->
                if (attempt.malformed) {
                    attempt.extendMalformedOwnership(input.rawText)
                } else {
                    attempt.rejectMalformedContinuation(input.rawText, grammar, input.language)
                }
            }
        val parsedRanges = parsed.map(Attempt::ownedRange)
        val starts = grammar.attemptStartPattern.findAll(input.rawText)
            .filterNot { start -> parsedRanges.any { range -> start.range.first in range.start until range.endExclusive } }
            .toList()
        val malformed = starts.map { start ->
            val nextStart = starts.firstOrNull { it.range.first > start.range.first }?.range?.first
            val end = malformedAttemptEnd(input.rawText, start.range.first, nextStart)
                .trimOwnershipEnd(input.rawText, start.range.first)
            Attempt(
                candidate = null,
                ownedRange = SourceMatch(start.range.first, end, RecognizedField.RECURRENCE),
                malformed = true
            )
        }
        return (parsed + malformed)
            .removeContainedAttempts()
            .sortedBy { it.ownedRange.start }
    }

    private fun parseInterval(
        match: MatchResult,
        input: NaturalLanguageInput,
        dueAt: Long?
    ): Attempt {
        val amount = match.groups["amount"]?.value?.toIntOrNull()
        val unit = match.groups["unit"]?.value?.lowercase(Locale.ROOT)
        val basis = explicitBasis(match.value, input.language)
        val rule = when {
            amount !in 1..MAX_INTERVAL -> null
            unit in DAY_UNITS -> RecurrenceRule.Interval(
                IntervalUnit.DAYS,
                requireNotNull(amount),
                basis ?: RecurrenceBasis.COMPLETION_DATE
            )
            unit in WEEK_UNITS -> RecurrenceRule.Interval(
                IntervalUnit.WEEKS,
                requireNotNull(amount),
                basis ?: RecurrenceBasis.COMPLETION_DATE
            )
            unit in MONTH_UNITS -> RecurrenceRule.MonthlyDay(
                anchorDay(input, dueAt),
                requireNotNull(amount),
                basis ?: RecurrenceBasis.SCHEDULED_DATE
            )
            else -> null
        }
        return match.toAttempt(rule)
    }

    private fun parseLegacy(
        match: MatchResult,
        input: NaturalLanguageInput,
        dueAt: Long?
    ): Attempt {
        val unit = match.groups["legacyUnit"]?.value?.lowercase(Locale.ROOT)
        val basis = explicitBasis(match.value, input.language) ?: RecurrenceBasis.SCHEDULED_DATE
        val rule = when (unit) {
            in DAY_UNITS -> RecurrenceRule.Interval(IntervalUnit.DAYS, 1, basis)
            in WEEK_UNITS -> RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, basis)
            in MONTH_UNITS -> RecurrenceRule.MonthlyDay(anchorDay(input, dueAt), 1, basis)
            else -> null
        }
        return match.toAttempt(rule)
    }

    private fun parseWeekdayList(match: MatchResult, language: ParserLanguage): Attempt {
        val dayGroup = requireNotNull(match.groups["days"])
        val grammar = Grammar.forLanguage(language)
        val tokens = grammar.weekdayTokenPattern.findAll(dayGroup.value)
            .filterNot { token ->
                TextNormalizer.matchingKey(token.value) in grammar.weekdayConnectors
            }
            .toList()
        val weekdays = tokens.map { token -> weekday(token.value, language) }
        val invalidIndex = weekdays.indexOfFirst { it == null }

        if (invalidIndex < 0) {
            return match.toAttempt(
                selectedWeekdaysRule(
                    weekdays.filterNotNull(),
                    explicitBasis(match.value, language) ?: RecurrenceBasis.SCHEDULED_DATE
                )
            )
        }
        if (invalidIndex == 0 || weekdayLike(tokens[invalidIndex].value, language)) {
            return match.toAttempt(null)
        }

        val validPrefix = weekdays.take(invalidIndex).filterNotNull()
        val rule = selectedWeekdaysRule(validPrefix, RecurrenceBasis.SCHEDULED_DATE)
            ?: return match.toAttempt(null)
        val end = dayGroup.range.first + tokens[invalidIndex - 1].range.last + 1
        return sourceAttempt(match.range.first, end, rule)
    }

    private fun selectedWeekdaysRule(
        weekdays: List<DayOfWeek>,
        basis: RecurrenceBasis
    ): RecurrenceRule.SelectedWeekdays? = weekdays
        .takeIf { it.isNotEmpty() && it.size == it.distinct().size }
        ?.let { RecurrenceRule.SelectedWeekdays(it.toSet(), basis) }

    private fun parseOrdinal(match: MatchResult, language: ParserLanguage): Attempt? {
        val ordinalText = requireNotNull(match.groups["ordinal"]).value
        val weekdayText = requireNotNull(match.groups["weekday"]).value
        if (!ordinalLike(ordinalText, language) && !weekdayLike(weekdayText, language)) return null

        val ordinal = ordinal(ordinalText, language)
        val weekday = weekday(weekdayText, language)
        val rule = if (ordinal != null && weekday != null) {
            RecurrenceRule.MonthlyOrdinal(
                ordinal = ordinal,
                weekday = weekday,
                everyMonths = 1,
                basis = explicitBasis(match.value, language) ?: RecurrenceBasis.SCHEDULED_DATE
            )
        } else {
            null
        }
        return match.toAttempt(rule)
    }

    private fun ordinalLike(value: String, language: ParserLanguage): Boolean {
        val normalized = TextNormalizer.matchingKey(value)
        return when (language) {
            ParserLanguage.ENGLISH -> normalized in ENGLISH_ORDINAL_WORDS ||
                ENGLISH_NUMERIC_ORDINAL.matches(normalized)
            ParserLanguage.ITALIAN -> normalized in ITALIAN_ORDINAL_WORDS ||
                normalized.all(Char::isDigit)
        }
    }

    private fun weekdayLike(value: String, language: ParserLanguage): Boolean {
        val normalized = TextNormalizer.matchingKey(value)
        val weekdays = when (language) {
            ParserLanguage.ENGLISH -> ENGLISH_WEEKDAYS
            ParserLanguage.ITALIAN -> ITALIAN_WEEKDAYS
        }
        return normalized in weekdays || normalized.removeSuffix("s") in weekdays
    }

    private fun explicitBasis(value: String, language: ParserLanguage): RecurrenceBasis? {
        val normalized = value.lowercase(Locale.ROOT)
        return when (language) {
            ParserLanguage.ENGLISH -> when {
                ENGLISH_SCHEDULED_BASIS.containsMatchIn(normalized) -> RecurrenceBasis.SCHEDULED_DATE
                ENGLISH_COMPLETION_BASIS.containsMatchIn(normalized) -> RecurrenceBasis.COMPLETION_DATE
                else -> null
            }
            ParserLanguage.ITALIAN -> when {
                ITALIAN_SCHEDULED_BASIS.containsMatchIn(normalized) -> RecurrenceBasis.SCHEDULED_DATE
                ITALIAN_COMPLETION_BASIS.containsMatchIn(normalized) -> RecurrenceBasis.COMPLETION_DATE
                else -> null
            }
        }
    }

    private fun ordinal(value: String, language: ParserLanguage): MonthlyOrdinalValue? =
        when (value.lowercase(Locale.ROOT)) {
            "first", "primo" -> MonthlyOrdinalValue.FIRST
            "second", "secondo" -> MonthlyOrdinalValue.SECOND
            "third", "terzo" -> MonthlyOrdinalValue.THIRD
            "fourth", "quarto" -> MonthlyOrdinalValue.FOURTH
            "last", "ultimo" -> MonthlyOrdinalValue.LAST
            "fifth", "quinto" -> null
            else -> null
        }

    private fun weekday(value: String, language: ParserLanguage): DayOfWeek? {
        val normalized = TextNormalizer.matchingKey(value)
        return when (language) {
            ParserLanguage.ENGLISH -> ENGLISH_WEEKDAYS[normalized]
            ParserLanguage.ITALIAN -> ITALIAN_WEEKDAYS[normalized]
        }
    }

    private fun anchorDay(input: NaturalLanguageInput, dueAt: Long?): Int =
        Instant.ofEpochMilli(dueAt ?: input.nowEpochMillis)
            .atZone(input.zoneId)
            .dayOfMonth

    private fun MatchResult.toAttempt(rule: RecurrenceRule?): Attempt {
        return sourceAttempt(range.first, range.last + 1, rule)
    }

    private fun sourceAttempt(start: Int, endExclusive: Int, rule: RecurrenceRule?): Attempt {
        val source = SourceMatch(start, endExclusive, RecognizedField.RECURRENCE)
        return Attempt(
            candidate = rule?.let { RecurrenceCandidate(it, source) },
            ownedRange = source,
            malformed = rule == null
        )
    }

    private fun Attempt.rejectMalformedContinuation(
        raw: String,
        grammar: Grammar,
        language: ParserLanguage
    ): Attempt {
        val tail = raw.substring(ownedRange.endExclusive)
        val continuation = grammar.continuationPattern.find(tail)
            ?: WEEKDAY_CONTINUATION_PATTERN.find(tail)?.takeIf { match ->
                weekday(requireNotNull(match.groups["weekday"]).value, language) != null
            }
            ?: return this
        val continuationEnd = ownedRange.endExclusive + continuation.range.last + 1
        val extendedEnd = malformedAttemptEnd(
            raw = raw,
            start = ownedRange.start,
            nextStart = null,
            searchStart = continuationEnd
        ).trimOwnershipEnd(raw, ownedRange.start)
        return copy(
            candidate = null,
            ownedRange = ownedRange.copy(endExclusive = extendedEnd),
            malformed = true
        )
    }

    private fun Attempt.extendMalformedOwnership(raw: String): Attempt = copy(
        ownedRange = ownedRange.copy(
            endExclusive = malformedAttemptEnd(raw, ownedRange.start, null)
                .trimOwnershipEnd(raw, ownedRange.start)
        )
    )

    private fun List<Attempt>.removeContainedAttempts(): List<Attempt> = filterIndexed { index, attempt ->
        withIndex().none { (otherIndex, other) ->
            index != otherIndex &&
                other.ownedRange.start <= attempt.ownedRange.start &&
                other.ownedRange.endExclusive >= attempt.ownedRange.endExclusive &&
                other.ownedRange != attempt.ownedRange
        }
    }.distinctBy { it.ownedRange }

    private fun malformedAttemptEnd(
        raw: String,
        start: Int,
        nextStart: Int?,
        searchStart: Int = start
    ): Int {
        val hardStop = raw.indexOfAny(MALFORMED_STOP_CHARACTERS, startIndex = searchStart)
            .takeIf { it >= 0 }
        return listOfNotNull(nextStart, hardStop, raw.length)
            .filter { it > start }
            .minOrNull()
            ?: raw.length
    }

    private fun Int.trimOwnershipEnd(raw: String, start: Int): Int {
        var end = this
        while (end > start && raw[end - 1].isWhitespace()) end--
        return end
    }

    private fun SourceMatch.intersects(other: SourceMatch): Boolean =
        start < other.endExclusive && other.start < endExclusive

    private data class Attempt(
        val candidate: RecurrenceCandidate?,
        val ownedRange: SourceMatch,
        val malformed: Boolean
    )

    private data class Grammar(
        val intervalPattern: Regex,
        val legacyPattern: Regex,
        val weekdayListPattern: Regex,
        val ordinalPattern: Regex,
        val weekdayTokenPattern: Regex,
        val weekdayConnectors: Set<String>,
        val attemptStartPattern: Regex,
        val continuationPattern: Regex
    ) {
        companion object {
            fun forLanguage(language: ParserLanguage): Grammar = when (language) {
                ParserLanguage.ENGLISH -> ENGLISH_GRAMMAR
                ParserLanguage.ITALIAN -> ITALIAN_GRAMMAR
            }
        }
    }

    private companion object {
        const val MAX_INTERVAL = 999
        const val START_BOUNDARY = "(?<![\\p{L}\\p{M}\\p{N}_])"
        const val END_BOUNDARY = "(?![\\p{L}\\p{M}\\p{N}_])"
        const val WORD_TOKEN = "[\\p{L}\\p{M}]+"
        const val ENGLISH_WEEKDAY =
            "(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)"
        const val ENGLISH_ORDINAL_ATTEMPT =
            "(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|last|" +
                "\\d+(?:st|nd|rd|th))"
        const val ITALIAN_ORDINAL_ATTEMPT =
            "(?:primo|secondo|terzo|quarto|quinto|sesto|settimo|ottavo|nono|decimo|ultimo)"
        const val ENGLISH_BASIS_SUFFIX =
            "(?:\\s+(?:(?:from|based\\s+on)\\s+(?:the\\s+)?" +
                "(?:scheduled|completion)\\s+date))?"
        const val ITALIAN_BASIS_SUFFIX =
            "(?:\\s+(?:(?:dalla|in\\s+base\\s+alla)\\s+data\\s+" +
                "(?:programmata|di\\s+completamento)))?"
        val DAY_UNITS = setOf("day", "days", "giorno", "giorni")
        val WEEK_UNITS = setOf("week", "weeks", "settimana", "settimane")
        val MONTH_UNITS = setOf("month", "months", "mese", "mesi")
        val MALFORMED_STOP_CHARACTERS = charArrayOf(',', '.', ';', ':', '?', '!', '#', '\n', '\r')
        val ENGLISH_SCHEDULED_BASIS = Regex("scheduled\\s+date", RegexOption.IGNORE_CASE)
        val ENGLISH_COMPLETION_BASIS = Regex("completion\\s+date", RegexOption.IGNORE_CASE)
        val ITALIAN_SCHEDULED_BASIS = Regex("data\\s+programmata", RegexOption.IGNORE_CASE)
        val ITALIAN_COMPLETION_BASIS = Regex(
            "data\\s+di\\s+completamento",
            RegexOption.IGNORE_CASE
        )
        val ENGLISH_NUMERIC_ORDINAL = Regex("\\d+(?:st|nd|rd|th)?")
        val ENGLISH_ORDINAL_WORDS = setOf(
            "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth",
            "ninth", "tenth", "last"
        )
        val ITALIAN_ORDINAL_WORDS = setOf(
            "primo", "secondo", "terzo", "quarto", "quinto", "sesto", "settimo", "ottavo",
            "nono", "decimo", "ultimo"
        )
        val WEEKDAY_CONTINUATION_PATTERN = Regex(
            "^\\s*(?:[\\p{P}]+\\s*)?(?<weekday>$WORD_TOKEN)$END_BOUNDARY",
            RegexOption.IGNORE_CASE
        )
        val ENGLISH_WEEKDAYS = mapOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY
        )
        val ITALIAN_WEEKDAYS = mapOf(
            "lunedi" to DayOfWeek.MONDAY,
            "martedi" to DayOfWeek.TUESDAY,
            "mercoledi" to DayOfWeek.WEDNESDAY,
            "giovedi" to DayOfWeek.THURSDAY,
            "venerdi" to DayOfWeek.FRIDAY,
            "sabato" to DayOfWeek.SATURDAY,
            "domenica" to DayOfWeek.SUNDAY
        )
        val ENGLISH_GRAMMAR = Grammar(
            intervalPattern = grammarRegex(
                "every\\s+(?<amount>\\d+)\\s+(?<unit>days?|weeks?|months?)" +
                    ENGLISH_BASIS_SUFFIX
            ),
            legacyPattern = grammarRegex(
                "every\\s+(?<legacyUnit>day|week|month)$ENGLISH_BASIS_SUFFIX"
            ),
            weekdayListPattern = grammarRegex(
                "every\\s+(?<days>$WORD_TOKEN(?:" +
                    "(?:\\s*,\\s*(?:and\\s+)?|\\s+and\\s+)$WORD_TOKEN)*)" +
                    ENGLISH_BASIS_SUFFIX
            ),
            ordinalPattern = grammarRegex(
                "(?<ordinal>(?:$WORD_TOKEN|\\d+(?:st|nd|rd|th)?))\\s+" +
                    "(?<weekday>$WORD_TOKEN)\\s+of\\s+(?:every|the)\\s+month" +
                    ENGLISH_BASIS_SUFFIX
            ),
            weekdayTokenPattern = Regex(WORD_TOKEN, RegexOption.IGNORE_CASE),
            weekdayConnectors = setOf("and"),
            attemptStartPattern = Regex(
                START_BOUNDARY +
                    "(?:every|$ENGLISH_ORDINAL_ATTEMPT\\s+$WORD_TOKEN)" +
                    END_BOUNDARY,
                RegexOption.IGNORE_CASE
            ),
            continuationPattern = Regex(
                "^\\s*(?:(?:and|or)\\b|(?:from|based\\s+on)\\b|" +
                    "$ENGLISH_WEEKDAY\\b|" +
                    ",\\s*(?=(?:(?:and|or|from|based\\s+on)\\b)))",
                RegexOption.IGNORE_CASE
            )
        )
        val ITALIAN_GRAMMAR = Grammar(
            intervalPattern = grammarRegex(
                "ogni\\s+(?<amount>\\d+)\\s+" +
                    "(?<unit>giorn(?:o|i)|settiman(?:a|e)|mes(?:e|i))" +
                    ITALIAN_BASIS_SUFFIX
            ),
            legacyPattern = grammarRegex(
                "ogni\\s+(?<legacyUnit>giorno|settimana|mese)$ITALIAN_BASIS_SUFFIX"
            ),
            weekdayListPattern = grammarRegex(
                "ogni\\s+(?<days>$WORD_TOKEN(?:" +
                    "(?:\\s*,\\s*(?:e\\s+)?|\\s+e\\s+)$WORD_TOKEN)*)" +
                    ITALIAN_BASIS_SUFFIX
            ),
            ordinalPattern = grammarRegex(
                "(?<ordinal>(?:$WORD_TOKEN|\\d+))\\s+" +
                    "(?<weekday>$WORD_TOKEN)\\s+(?:del\\s+mese|di\\s+ogni\\s+mese)" +
                    ITALIAN_BASIS_SUFFIX
            ),
            weekdayTokenPattern = Regex(WORD_TOKEN, RegexOption.IGNORE_CASE),
            weekdayConnectors = setOf("e"),
            attemptStartPattern = Regex(
                START_BOUNDARY +
                    "(?:ogni|$ITALIAN_ORDINAL_ATTEMPT\\s+$WORD_TOKEN)" + END_BOUNDARY,
                RegexOption.IGNORE_CASE
            ),
            continuationPattern = Regex(
                "^\\s*(?:(?:e|o)\\b|(?:dalla|in\\s+base\\s+alla)\\b|" +
                    ",\\s*(?=(?:(?:e|o|dalla|in\\s+base\\s+alla)\\b)))",
                RegexOption.IGNORE_CASE
            )
        )

        fun grammarRegex(body: String): Regex = Regex(
            "$START_BOUNDARY$body$END_BOUNDARY",
            RegexOption.IGNORE_CASE
        )
    }
}
