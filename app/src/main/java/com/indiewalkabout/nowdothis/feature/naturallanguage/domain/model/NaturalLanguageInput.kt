package com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model

import java.time.ZoneId

enum class ParserLanguage {
    ITALIAN,
    ENGLISH
}

data class NaturalLanguageInput(
    val rawText: String,
    val language: ParserLanguage,
    val nowEpochMillis: Long,
    val zoneId: ZoneId,
    val categories: List<CategoryCandidate>
)

data class CategoryCandidate(
    val id: Int,
    val displayName: String
)
