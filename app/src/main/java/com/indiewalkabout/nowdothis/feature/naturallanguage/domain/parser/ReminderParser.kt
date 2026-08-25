package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.immutableListSnapshot

@ConsistentCopyVisibility
data class ReminderParse private constructor(
    val reminderAt: Long?,
    private val matchSnapshot: List<SourceMatch>,
    private val issueSnapshot: List<ParseIssue>
) {
    val matches: List<SourceMatch>
        get() = matchSnapshot

    val issues: List<ParseIssue>
        get() = issueSnapshot

    companion object {
        operator fun invoke(
            reminderAt: Long?,
            matches: List<SourceMatch>,
            issues: List<ParseIssue>
        ): ReminderParse = ReminderParse(
            reminderAt = reminderAt,
            matchSnapshot = immutableListSnapshot(matches),
            issueSnapshot = immutableListSnapshot(issues)
        )
    }
}

class ReminderParser {

    fun parse(input: NaturalLanguageInput, dueAt: Long?): ReminderParse {
        val candidates = findCandidates(input)
        val successful = mutableListOf<ResolvedReminder>()
        var missingDueDate = false

        candidates.forEach { candidate ->
            when (candidate) {
                is ReminderCandidate.Absolute -> successful += ResolvedReminder(
                    reminderAt = candidate.reminderAt,
                    match = candidate.match
                )

                is ReminderCandidate.Relative -> if (dueAt == null) {
                    missingDueDate = true
                } else {
                    subtractDuration(dueAt, candidate.durationMillis)?.let { reminderAt ->
                        successful += ResolvedReminder(reminderAt, candidate.match)
                    }
                }
            }
        }

        return ReminderParse(
            reminderAt = successful.lastOrNull()?.reminderAt,
            matches = successful.map(ResolvedReminder::match),
            issues = buildList {
                if (missingDueDate) add(ParseIssue.RelativeReminderWithoutDueDate)
                if (successful.size > 1) {
                    add(ParseIssue.DuplicateField(RecognizedField.REMINDER))
                }
            }
        )
    }

    internal fun claimedRanges(input: NaturalLanguageInput): List<SourceMatch> =
        findCandidates(input).map(ReminderCandidate::match)

    private fun findCandidates(input: NaturalLanguageInput): List<ReminderCandidate> = buildList {
        relativePattern(input.language).findAll(input.rawText).forEach { match ->
            parseDurationMillis(match.groupValues[1], match.groupValues[2])?.let { duration ->
                add(ReminderCandidate.Relative(duration, match.toSourceMatch()))
            }
        }
        absolutePattern(input.language).findAll(input.rawText).forEach { match ->
            parseAbsoluteReminder(input, match)?.let(::add)
        }
    }.sortedBy { candidate -> candidate.match.start }

    private fun parseAbsoluteReminder(
        input: NaturalLanguageInput,
        match: MatchResult
    ): ReminderCandidate.Absolute? {
        val body = match.groupValues[1]
        val temporal = TemporalParser().parse(
            NaturalLanguageInput(
                rawText = body,
                language = input.language,
                nowEpochMillis = input.nowEpochMillis,
                zoneId = input.zoneId,
                categories = emptyList()
            )
        )
        val reminderAt = temporal.dueAt ?: return null
        if (TextNormalizer.remainingTitle(body, temporal.matches).isNotBlank()) return null
        return ReminderCandidate.Absolute(reminderAt, match.toSourceMatch())
    }

    private fun parseDurationMillis(amountText: String, unitText: String): Long? {
        val amount = amountText.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val unitMillis = when (unitText.lowercase()) {
            "h" -> MILLIS_PER_HOUR
            "m" -> MILLIS_PER_MINUTE
            else -> return null
        }
        return try {
            Math.multiplyExact(amount, unitMillis)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun subtractDuration(dueAt: Long, durationMillis: Long): Long? = try {
        Math.subtractExact(dueAt, durationMillis)
    } catch (_: ArithmeticException) {
        null
    }

    private fun MatchResult.toSourceMatch(): SourceMatch = SourceMatch(
        start = range.first,
        endExclusive = range.last + 1,
        field = RecognizedField.REMINDER
    )

    private sealed interface ReminderCandidate {
        val match: SourceMatch

        data class Absolute(
            val reminderAt: Long,
            override val match: SourceMatch
        ) : ReminderCandidate

        data class Relative(
            val durationMillis: Long,
            override val match: SourceMatch
        ) : ReminderCandidate
    }

    private data class ResolvedReminder(
        val reminderAt: Long,
        val match: SourceMatch
    )

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val WORD_BOUNDARY_START = "(?<![\\p{L}\\p{N}_])"
        const val WORD_BOUNDARY_END = "(?![\\p{L}\\p{N}_])"
        const val NUMERIC_DATE =
            "\\d{1,2}/\\d{1,2}(?:/\\d{4})?(?![\\p{L}\\p{N}_/]|[.:](?=\\d))"
        const val ITALIAN_DATE = "(?:oggi|domani|$NUMERIC_DATE)"
        const val ENGLISH_DATE = "(?:today|tomorrow|$NUMERIC_DATE)"
        const val ITALIAN_TIME = "alle\\s+\\d{1,2}(?::\\d{2})?"
        const val ENGLISH_TIME = "at\\s+\\d{1,2}(?::\\d{2})?\\s+[ap]\\.?m\\.?"
        val ITALIAN_RELATIVE_PATTERN = Regex(
            "${WORD_BOUNDARY_START}promemoria\\s+(\\d+)\\s*([hm])\\s+prima$WORD_BOUNDARY_END",
            RegexOption.IGNORE_CASE
        )
        val ENGLISH_RELATIVE_PATTERN = Regex(
            "${WORD_BOUNDARY_START}remind\\s+(\\d+)\\s*([hm])\\s+before$WORD_BOUNDARY_END",
            RegexOption.IGNORE_CASE
        )
        val ITALIAN_ABSOLUTE_PATTERN = Regex(
            "${WORD_BOUNDARY_START}promemoria\\s+(($ITALIAN_DATE)(?:\\s+$ITALIAN_TIME)?|$ITALIAN_TIME)$WORD_BOUNDARY_END",
            RegexOption.IGNORE_CASE
        )
        val ENGLISH_ABSOLUTE_PATTERN = Regex(
            "${WORD_BOUNDARY_START}remind\\s+(($ENGLISH_DATE)(?:\\s+$ENGLISH_TIME)?|$ENGLISH_TIME)$WORD_BOUNDARY_END",
            RegexOption.IGNORE_CASE
        )

        fun relativePattern(language: ParserLanguage): Regex = when (language) {
            ParserLanguage.ITALIAN -> ITALIAN_RELATIVE_PATTERN
            ParserLanguage.ENGLISH -> ENGLISH_RELATIVE_PATTERN
        }

        fun absolutePattern(language: ParserLanguage): Regex = when (language) {
            ParserLanguage.ITALIAN -> ITALIAN_ABSOLUTE_PATTERN
            ParserLanguage.ENGLISH -> ENGLISH_ABSOLUTE_PATTERN
        }
    }
}
