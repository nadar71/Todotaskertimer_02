package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser

import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.RecognizedField
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.SourceMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun normalizeWhitespace_trimsAndCollapsesWhitespace() {
        assertEquals(
            "Compra latte domani",
            TextNormalizer.normalizeWhitespace("  Compra\n\tlatte   domani  ")
        )
    }

    @Test
    fun remainingTitle_removesOnlySuccessfulRanges() {
        val raw = "  Compra latte, domani alle 18  #Sconosciuta "
        val consumed = listOf(SourceMatch(16, 23, RecognizedField.DUE_DATE))

        assertEquals(
            "Compra latte, alle 18 #Sconosciuta",
            TextNormalizer.remainingTitle(raw, consumed)
        )
    }

    @Test
    fun remainingTitle_removesAdjacentRangesOnce() {
        val raw = "Compra latte domani alle 18"
        val consumed = listOf(
            SourceMatch(13, 20, RecognizedField.DUE_DATE),
            SourceMatch(20, 27, RecognizedField.DUE_DATE)
        )

        assertEquals("Compra latte", TextNormalizer.remainingTitle(raw, consumed))
    }

    @Test
    fun remainingTitle_rejectsOverlappingRanges() {
        val raw = "Compra latte domani"
        val consumed = listOf(
            SourceMatch(13, 19, RecognizedField.DUE_DATE),
            SourceMatch(16, 20, RecognizedField.DUE_DATE)
        )

        assertThrows(IllegalArgumentException::class.java) {
            TextNormalizer.remainingTitle(raw, consumed)
        }
    }

    @Test
    fun remainingTitle_rejectsRangesOutsideRawInput() {
        assertThrows(IllegalArgumentException::class.java) {
            TextNormalizer.remainingTitle(
                raw = "Compra latte",
                consumed = listOf(SourceMatch(0, 13, RecognizedField.DUE_DATE))
            )
        }
    }

    @Test
    fun categoryMarkerValue_unquotesQuotedCategoryMarker() {
        assertEquals(
            "Progetti Casa",
            TextNormalizer.categoryMarkerValue("#\"Progetti Casa\"")
        )
    }

    @Test
    fun remainingTitle_preservesPunctuationAndUnsuccessfulMarker() {
        val raw = "Compra latte, domani! #Sconosciuta"
        val consumed = listOf(SourceMatch(14, 20, RecognizedField.DUE_DATE))

        assertEquals(
            "Compra latte, ! #Sconosciuta",
            TextNormalizer.remainingTitle(raw, consumed)
        )
    }

    @Test
    fun matchingKey_ignoresCaseDiacriticsAndWhitespace() {
        assertEquals(
            "progetti casa",
            TextNormalizer.matchingKey("  PRoGétti   Càsa ")
        )
    }

    @Test
    fun matchingKey_removesCombiningSpacingMarks() {
        assertEquals("a", TextNormalizer.matchingKey("a\u0903"))
    }

    @Test
    fun matchingKey_removesEnclosingMarks() {
        assertEquals("a", TextNormalizer.matchingKey("a\u20dd"))
    }
}
