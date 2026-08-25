package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model

import java.time.ZoneId
import java.util.Collections

enum class ParserLanguage {
    ITALIAN,
    ENGLISH
}

@ConsistentCopyVisibility
data class NaturalLanguageInput private constructor(
    val rawText: String,
    val language: ParserLanguage,
    val nowEpochMillis: Long,
    val zoneId: ZoneId,
    private val categorySnapshot: List<CategoryCandidate>
) {
    val categories: List<CategoryCandidate>
        get() = categorySnapshot

    override fun toString(): String = "NaturalLanguageInput(" +
        "rawText=$rawText, language=$language, nowEpochMillis=$nowEpochMillis, " +
        "zoneId=$zoneId, categories=$categories)"

    companion object {
        operator fun invoke(
            rawText: String,
            language: ParserLanguage,
            nowEpochMillis: Long,
            zoneId: ZoneId,
            categories: List<CategoryCandidate>
        ): NaturalLanguageInput = NaturalLanguageInput(
            rawText = rawText,
            language = language,
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId,
            categorySnapshot = immutableListSnapshot(categories)
        )
    }
}

data class CategoryCandidate(
    val id: Int,
    val displayName: String
)

internal fun <T> immutableListSnapshot(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

internal fun <T> immutableSetSnapshot(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
