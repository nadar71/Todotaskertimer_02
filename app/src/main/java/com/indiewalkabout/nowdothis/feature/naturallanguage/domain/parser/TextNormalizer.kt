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
        .normalize(normalizeWhitespace(raw), Normalizer.Form.NFD)
        .filterNot { character -> Character.getType(character) == Character.NON_SPACING_MARK.toInt() }
        .lowercase(Locale.ROOT)

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
}
