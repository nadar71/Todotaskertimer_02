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
        val syntaxes = findSyntaxes(input)
        val successful = mutableListOf<ResolvedReminder>()
        var missingDueDate = false

        syntaxes.forEach { syntax ->
            when (syntax) {
                is ReminderSyntax.Absolute -> resolveAbsoluteReminder(input, syntax.body)?.let { reminderAt ->
                    successful += ResolvedReminder(reminderAt, syntax.match)
                }

                is ReminderSyntax.Relative -> {
                    val duration = parseDurationMillis(syntax.amountText, syntax.unitText)
                    if (duration != null && dueAt == null) {
                        missingDueDate = true
                    } else if (duration != null && dueAt != null) {
                        subtractDuration(dueAt, duration)?.let { reminderAt ->
                            successful += ResolvedReminder(reminderAt, syntax.match)
                        }
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

    internal fun shieldingRanges(input: NaturalLanguageInput): List<SourceMatch> =
        findSyntaxes(input).map(ReminderSyntax::match)

    private fun findSyntaxes(input: NaturalLanguageInput): List<ReminderSyntax> = buildList {
        relativePattern(input.language).findAll(input.rawText).forEach { match ->
            add(
                ReminderSyntax.Relative(
                    amountText = match.groupValues[1],
                    unitText = match.groupValues[2],
                    match = match.toSourceMatch()
                )
            )
        }
        absolutePattern(input.language).findAll(input.rawText).forEach { match ->
            add(
                ReminderSyntax.Absolute(
                    body = match.groupValues[1],
                    match = match.toSourceMatch()
                )
            )
        }
    }.sortedBy { syntax -> syntax.match.start }

    private fun resolveAbsoluteReminder(
        input: NaturalLanguageInput,
        body: String
    ): Long? {
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
        return reminderAt
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

    private sealed interface ReminderSyntax {
        val match: SourceMatch

        data class Absolute(
            val body: String,
            override val match: SourceMatch
        ) : ReminderSyntax

        data class Relative(
            val amountText: String,
            val unitText: String,
            override val match: SourceMatch
        ) : ReminderSyntax
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
            "\\d{1,2}/\\d{1,2}(?:/[\\p{L}\\p{N}_/.:]+)?(?:[.:]\\d+)?"
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
