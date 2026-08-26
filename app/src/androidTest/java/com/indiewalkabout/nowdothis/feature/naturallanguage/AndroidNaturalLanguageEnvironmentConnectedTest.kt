package com.indiewalkabout.nowdothis.feature.naturallanguage

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.category.presentation.DefaultCategoryNameResolver
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.presentation.AndroidNaturalLanguageEnvironment
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNaturalLanguageEnvironmentConnectedTest {
    @Test
    fun activeLocaleList_usesFirstSupportedLanguageOrItalian() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val previousLocales = localeManager.applicationLocales
        val environment = AndroidNaturalLanguageEnvironment(
            context = context,
            clock = AppClock { 10L },
            zoneIdProvider = ZoneIdProvider { ZoneId.of("Europe/Rome") },
            defaultCategoryNameResolver = DefaultCategoryNameResolver { "unused" }
        )

        try {
            localeManager.applicationLocales = LocaleList.forLanguageTags("fr-CH,en-US")
            instrumentation.waitForIdleSync()

            val englishFallback = environment.snapshot(emptyList())

            assertEquals(ParserLanguage.ENGLISH, englishFallback.language)

            localeManager.applicationLocales = LocaleList.forLanguageTags("fr-CH,de-DE")
            instrumentation.waitForIdleSync()

            val unsupportedOnly = environment.snapshot(emptyList())

            assertEquals(ParserLanguage.ITALIAN, unsupportedOnly.language)
        } finally {
            localeManager.applicationLocales = previousLocales
            instrumentation.waitForIdleSync()
        }
    }
}
