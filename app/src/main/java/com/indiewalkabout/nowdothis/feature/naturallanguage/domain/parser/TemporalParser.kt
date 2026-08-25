package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParseIssue
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.immutableListSnapshot
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

@ConsistentCopyVisibility
data class TemporalParse private constructor(
    val dueAt: Long?,
    private val matchSnapshot: List<SourceMatch>,
    private val issueSnapshot: List<ParseIssue>
) {
    val matches: List<SourceMatch>
        get() = matchSnapshot

    val issues: List<ParseIssue>
        get() = issueSnapshot

    companion object {
        operator fun invoke(
            dueAt: Long?,
            matches: List<SourceMatch>,
            issues: List<ParseIssue>
        ): TemporalParse = TemporalParse(
            dueAt = dueAt,
            matchSnapshot = immutableListSnapshot(matches),
            issueSnapshot = immutableListSnapshot(issues)
        )
    }
}

class TemporalParser {

    fun parse(input: NaturalLanguageInput): TemporalParse {
        val currentDate = Instant.ofEpochMilli(input.nowEpochMillis)
            .atZone(input.zoneId)
            .toLocalDate()
        val dates = parseDates(input.rawText, input.language, currentDate)
        val times = parseTimes(input.rawText, input.language)
        val selectedDate = dates.lastOrNull()
        val selectedTime = times.lastOrNull()
        val matches = listOfNotNull(selectedDate?.match, selectedTime?.match)
            .sortedBy(SourceMatch::start)
        val issues = if (dates.size > 1 || times.size > 1) {
            listOf(ParseIssue.DuplicateField(RecognizedField.DUE_DATE))
        } else {
            emptyList()
        }

        val dueAt = if (selectedDate == null && selectedTime == null) {
            null
        } else {
            resolve(
                localDateTime = LocalDateTime.of(
                    selectedDate?.date ?: currentDate,
                    selectedTime?.time ?: DEFAULT_TIME
                ),
                zoneId = input.zoneId
            )
        }
        return TemporalParse(dueAt = dueAt, matches = matches, issues = issues)
    }

    private fun parseDates(
        raw: String,
        language: ParserLanguage,
        currentDate: LocalDate
    ): List<DateCandidate> = buildList {
        relativeDatePattern(language).findAll(raw).forEach { match ->
            val date = when (match.value.lowercase(Locale.ROOT)) {
                "oggi", "today" -> currentDate
                "domani", "tomorrow" -> currentDate.plusDays(1)
                else -> error("Unexpected relative date token")
            }
            add(DateCandidate(date, match.toSourceMatch()))
        }
        numericDatePattern.findAll(raw).forEach { match ->
            parseNumericDate(match, language, currentDate.year)?.let { date ->
                add(DateCandidate(date, match.toSourceMatch()))
            }
        }
    }.sortedBy { it.match.start }

    private fun parseTimes(raw: String, language: ParserLanguage): List<TimeCandidate> = when (language) {
        ParserLanguage.ITALIAN -> italianTimePattern.findAll(raw)
            .mapNotNull { match ->
                parseItalianTime(match)?.let { time -> TimeCandidate(time, match.toSourceMatch()) }
            }
            .toList()

        ParserLanguage.ENGLISH -> englishTimePattern.findAll(raw)
            .mapNotNull { match ->
                parseEnglishTime(match)?.let { time -> TimeCandidate(time, match.toSourceMatch()) }
            }
            .toList()
    }

    private fun parseNumericDate(
        match: MatchResult,
        language: ParserLanguage,
        defaultYear: Int
    ): LocalDate? {
        val first = match.groupValues[1].toInt()
        val second = match.groupValues[2].toInt()
        val year = match.groupValues[3].takeIf(String::isNotEmpty)?.toInt() ?: defaultYear
        if (year !in 1..9999) return null

        val (month, day) = when (language) {
            ParserLanguage.ITALIAN -> second to first
            ParserLanguage.ENGLISH -> first to second
        }
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun parseItalianTime(match: MatchResult): LocalTime? {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: 0
        return localTimeOrNull(hour, minute)
    }

    private fun parseEnglishTime(match: MatchResult): LocalTime? {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: 0
        if (hour !in 1..12 || minute !in 0..59) return null

        val isPm = match.groupValues[3].lowercase(Locale.ROOT) == "p"
        val hour24 = when {
            hour == 12 && !isPm -> 0
            hour == 12 -> 12
            isPm -> hour + 12
            else -> hour
        }
        return LocalTime.of(hour24, minute)
    }

    private fun localTimeOrNull(hour: Int, minute: Int): LocalTime? = try {
        LocalTime.of(hour, minute)
    } catch (_: DateTimeException) {
        null
    }

    private fun resolve(localDateTime: LocalDateTime, zoneId: ZoneId): Long {
        val rules = zoneId.rules
        val offsets = rules.getValidOffsets(localDateTime)
        val instant = when (offsets.size) {
            0 -> {
                val transition = requireNotNull(rules.getTransition(localDateTime))
                localDateTime.plus(transition.duration).toInstant(transition.offsetAfter)
            }

            1 -> localDateTime.toInstant(offsets.single())
            else -> localDateTime.toInstant(offsets.first())
        }
        return instant.toEpochMilli()
    }

    private fun relativeDatePattern(language: ParserLanguage): Regex = when (language) {
        ParserLanguage.ITALIAN -> ITALIAN_RELATIVE_DATE_PATTERN
        ParserLanguage.ENGLISH -> ENGLISH_RELATIVE_DATE_PATTERN
    }

    private fun MatchResult.toSourceMatch(): SourceMatch = SourceMatch(
        start = range.first,
        endExclusive = range.last + 1,
        field = RecognizedField.DUE_DATE
    )

    private data class DateCandidate(val date: LocalDate, val match: SourceMatch)

    private data class TimeCandidate(val time: LocalTime, val match: SourceMatch)

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
        val ITALIAN_RELATIVE_DATE_PATTERN = wordPattern("oggi|domani")
        val ENGLISH_RELATIVE_DATE_PATTERN = wordPattern("today|tomorrow")
        val numericDatePattern = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?(?!\\d)")
        val italianTimePattern = Regex(
            "(?<![\\p{L}\\p{N}_])alle\\s+(\\d{1,2})(?::(\\d{2}))?(?![\\p{L}\\p{N}_:])",
            RegexOption.IGNORE_CASE
        )
        val englishTimePattern = Regex(
            "(?<![\\p{L}\\p{N}_])at\\s+(\\d{1,2})(?::(\\d{2}))?\\s+([ap])\\.?m\\.?(?![\\p{L}\\p{N}_])",
            RegexOption.IGNORE_CASE
        )

        fun wordPattern(words: String): Regex = Regex(
            "(?<![\\p{L}\\p{N}_])(?:$words)(?![\\p{L}\\p{N}_])",
            RegexOption.IGNORE_CASE
        )
    }
}
