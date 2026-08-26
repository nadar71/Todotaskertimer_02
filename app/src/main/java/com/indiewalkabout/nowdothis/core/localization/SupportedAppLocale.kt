package com.indiewalkabout.nowdothis.core.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.core.os.ConfigurationCompat
import java.util.Locale

fun Context.activeSupportedLocale(): Locale {
    activeApplicationLocales().forEach { locale ->
        if (locale.language.lowercase(Locale.ROOT) in SUPPORTED_LANGUAGES) return locale
    }
    return Locale.ITALIAN
}

private fun Context.activeApplicationLocales(): List<Locale> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val applicationLocales = getSystemService(LocaleManager::class.java).applicationLocales
        if (!applicationLocales.isEmpty) {
            return List(applicationLocales.size()) { index -> applicationLocales[index] }
        }
    }
    val configuredLocales = ConfigurationCompat.getLocales(resources.configuration)
    return List(configuredLocales.size()) { index -> requireNotNull(configuredLocales[index]) }
}

private val SUPPORTED_LANGUAGES = setOf("it", "en")
