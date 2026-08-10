package com.indiewalkabout.nowdothis.core

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tasksTitle_resolvesInItalianAndEnglish() {
        fun title(language: String): String {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language))
            return context.createConfigurationContext(configuration)
                .getString(R.string.tasks_title)
        }

        assertEquals("Attività", title("it"))
        assertEquals("Tasks", title("en"))
    }
}
