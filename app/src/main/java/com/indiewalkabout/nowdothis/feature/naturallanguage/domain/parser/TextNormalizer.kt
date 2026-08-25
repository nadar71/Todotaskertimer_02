package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalizeWhitespace(raw: String): String = raw
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
        .joinToString(" ")

    fun matchingKey(raw: String): String = Normalizer
        .normalize(normalizeWhitespace(raw).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .withoutUnicodeMarks()

    fun categoryMarkerValue(marker: String): String? = when {
        marker.startsWith("#\"") && marker.endsWith('"') && marker.length > 3 -> {
            marker.substring(2, marker.length - 1)
        }

        marker.startsWith('#') && marker.length > 1 && !marker.drop(1).any(Char::isWhitespace) -> {
            marker.drop(1)
        }

        else -> null
    }

    fun remainingTitle(raw: String, consumed: List<SourceMatch>): String {
        val canonicalMatches = canonicalize(raw, consumed)
        if (canonicalMatches.isEmpty()) return normalizeWhitespace(raw)

        val title = buildString {
            var cursor = 0
            canonicalMatches.forEach { match ->
                append(raw, cursor, match.start)
                cursor = match.endExclusive
            }
            append(raw, cursor, raw.length)
        }
        return normalizeWhitespace(title)
    }

    private fun canonicalize(raw: String, consumed: List<SourceMatch>): List<SourceMatch> {
        val sorted = consumed.sortedBy(SourceMatch::start)
        sorted.forEach { match ->
            require(match.start >= 0 && match.endExclusive <= raw.length && match.start < match.endExclusive) {
                "Consumed range must be non-empty and within the raw input."
            }
        }
        sorted.zipWithNext().forEach { (current, next) ->
            require(current.endExclusive <= next.start) { "Consumed ranges must not overlap." }
        }
        return sorted
    }

    private fun String.withoutUnicodeMarks(): String = buildString(length) {
        var index = 0
        while (index < this@withoutUnicodeMarks.length) {
            val codePoint = Character.codePointAt(this@withoutUnicodeMarks, index)
            if (!isUnicodeMark(codePoint)) appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
    }

    private fun isUnicodeMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }
}
