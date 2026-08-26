package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalizeWhitespace(raw: String): String = buildString(raw.length) {
        var pendingSpace = false
        var index = 0
        while (index < raw.length) {
            val codePoint = Character.codePointAt(raw, index)
            if (codePoint.isUnicodeSpace()) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                appendCodePoint(codePoint)
                pendingSpace = false
            }
            index += Character.charCount(codePoint)
        }
    }

    fun matchingKey(raw: String): String = Normalizer
        .normalize(normalizeWhitespace(raw).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .withoutFoldableLatinAccents()

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

    private fun String.withoutFoldableLatinAccents(): String = buildString(length) {
        var previousBase: Int? = null
        var index = 0
        while (index < this@withoutFoldableLatinAccents.length) {
            val codePoint = Character.codePointAt(this@withoutFoldableLatinAccents, index)
            val type = Character.getType(codePoint)
            val fold = type == Character.NON_SPACING_MARK.toInt() &&
                previousBase?.let { base -> isCanonicalLatinAccent(base, codePoint) } == true
            if (!fold) appendCodePoint(codePoint)
            previousBase = when (type) {
                Character.NON_SPACING_MARK.toInt() -> previousBase.takeIf { fold }
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt() -> null
                else -> codePoint.takeIf { it.isLatinLetter() }
            }
            index += Character.charCount(codePoint)
        }
    }

    private fun isCanonicalLatinAccent(base: Int, mark: Int): Boolean {
        val composed = Normalizer.normalize(
            buildString {
                appendCodePoint(base)
                appendCodePoint(mark)
            },
            Normalizer.Form.NFC
        )
        return composed.codePointCount(0, composed.length) == 1 &&
            composed.codePointAt(0).isLatinLetter()
    }

    private fun Int.isLatinLetter(): Boolean = Character.isLetter(this) &&
        (this in 0x0041..0x024F || this in 0x1E00..0x1EFF)

    private fun Int.isUnicodeSpace(): Boolean = Character.isWhitespace(this) ||
        Character.getType(this) == Character.SPACE_SEPARATOR.toInt() ||
        Character.getType(this) == Character.LINE_SEPARATOR.toInt() ||
        Character.getType(this) == Character.PARAGRAPH_SEPARATOR.toInt()
}
