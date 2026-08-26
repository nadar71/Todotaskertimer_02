package com.indiewalkabout.nowdothis.feature.naturallanguage.presentation

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.presentation.DefaultCategoryNameResolver
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.CategoryCandidate
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class ParserEnvironment(
    val language: ParserLanguage,
    val nowEpochMillis: Long,
    val zoneId: ZoneId,
    val categories: List<CategoryCandidate>
)

fun interface NaturalLanguageEnvironment {
    fun snapshot(categories: List<Category>): ParserEnvironment
}

class AndroidNaturalLanguageEnvironment @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: AppClock,
    private val zoneIdProvider: ZoneIdProvider,
    private val defaultCategoryNameResolver: DefaultCategoryNameResolver
) : NaturalLanguageEnvironment {
    override fun snapshot(categories: List<Category>): ParserEnvironment = ParserEnvironment(
        language = activeLanguage(),
        nowEpochMillis = clock.nowMillis(),
        zoneId = zoneIdProvider.zoneId(),
        categories = categories.map { category ->
            CategoryCandidate(
                id = category.id,
                displayName = category.customName
                    ?: category.defaultKey?.let(defaultCategoryNameResolver::resolve)
                    .orEmpty()
            )
        }
    )

    private fun activeLanguage(): ParserLanguage {
        val primaryLanguage = ConfigurationCompat
            .getLocales(context.resources.configuration)
            .get(0)
            ?.language
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return when (primaryLanguage) {
            ENGLISH_LANGUAGE -> ParserLanguage.ENGLISH
            ITALIAN_LANGUAGE -> ParserLanguage.ITALIAN
            else -> ParserLanguage.ITALIAN
        }
    }

    private companion object {
        const val ITALIAN_LANGUAGE = "it"
        const val ENGLISH_LANGUAGE = "en"
    }
}
