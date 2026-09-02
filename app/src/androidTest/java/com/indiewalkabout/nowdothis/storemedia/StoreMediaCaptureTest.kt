package com.indiewalkabout.nowdothis.storemedia

import android.app.LocaleManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.app.MainActivity
import com.indiewalkabout.nowdothis.feature.ads.data.AdsConsentManager
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class StoreMediaCaptureTest {
    private val adsRule = StoreMediaAdsRule()
    private val localeRule = StoreMediaLocaleRule(::localeForTest)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(adsRule).around(localeRule).around(composeRule)

    @Test
    fun captureItalianPhoneStory() = capturePhoneStory(
        StoreMediaLocale(
            tag = "it-IT",
            naturalLanguageInput = "Pianifica rilascio 31/12/2037 alle 18 ogni lunedi e venerdi",
            tasksTitle = "Attività",
            createTitle = "Nuova attività",
            quickEntryLabel = "Inserimento rapido",
            recurrenceLabel = "Ripetizione",
            historyTitle = "Cronologia",
            portabilityTitle = "Backup e ripristino",
            moreActions = "Altre azioni",
            completionHistoryAction = "Cronologia completate",
            portabilityAction = "Backup e ripristino"
        )
    )

    @Test
    fun captureEnglishPhoneStory() = capturePhoneStory(
        StoreMediaLocale(
            tag = "en-US",
            naturalLanguageInput = "Plan release 12/31/2037 at 6 pm every Monday and Friday",
            tasksTitle = "Tasks",
            createTitle = "New task",
            quickEntryLabel = "Quick entry",
            recurrenceLabel = "Repeat",
            historyTitle = "History",
            portabilityTitle = "Backup and restore",
            moreActions = "More actions",
            completionHistoryAction = "Completion history",
            portabilityAction = "Backup and restore"
        )
    )

    private fun capturePhoneStory(locale: StoreMediaLocale) {
        assertCaptureEnvironment()
        assertActiveLocale(locale.tag)
        waitForTag("task-list")
        capture(locale, "01-focus", locale.tasksTitle, "task-list")

        composeRule.onNodeWithTag("task-add").performClick()
        waitForTag("quick-entry-section")
        capture(
            locale,
            "02-quick-capture",
            locale.createTitle,
            "quick-entry-section"
        )

        composeRule.onNodeWithTag("quick-entry-input")
            .performTextInput(locale.naturalLanguageInput)
        closeSoftKeyboard()
        composeRule.onNodeWithTag("quick-entry-parse").performClick()
        waitForTag("quick-entry-summary")
        capture(
            locale,
            "03-natural-language",
            locale.quickEntryLabel,
            "quick-entry-summary"
        )

        composeRule.onNodeWithTag("task-editor-back").performClick()
        waitForTag("task-list")
        composeRule.onNodeWithTag("task-list")
            .performScrollToNode(hasTestTag("task-row-$RECURRING_TASK_ID"))
        composeRule.onNodeWithTag("task-row-$RECURRING_TASK_ID").performClick()
        waitForTag("task-editor-form")
        composeRule.onNodeWithTag("task-editor-form")
            .performScrollToNode(hasTestTag("task-recurrence-editor"))
        capture(
            locale,
            "04-recurrence",
            locale.recurrenceLabel,
            "task-recurrence-editor"
        )

        composeRule.onNodeWithTag("task-editor-back").performClick()
        waitForTag("task-list")
        openOverflowAction(locale.moreActions, locale.completionHistoryAction)
        waitForTag("history-list")
        composeRule.onNodeWithTag("history-category-1").assertIsDisplayed()
        composeRule.onNodeWithTag("history-task-$COMPLETED_TASK_ID").assertIsDisplayed()
        capture(locale, "05-organize", locale.historyTitle, "history-list")

        composeRule.onNodeWithTag("history-back").performClick()
        waitForTag("task-list")
        openOverflowAction(locale.moreActions, locale.portabilityAction)
        waitForTag("portability-create")
        capture(locale, "06-portability", locale.portabilityTitle, "portability-create")
    }

    private fun assertActiveLocale(expectedTag: String) {
        val localeManager = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext.getSystemService(LocaleManager::class.java)
        assertEquals(expectedTag, localeManager.applicationLocales.toLanguageTags())
    }

    private fun assertCaptureEnvironment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = context.resources.configuration
        val displayMetrics = context.resources.displayMetrics

        assertEquals(1.0f, configuration.fontScale)
        assertEquals(
            Configuration.UI_MODE_NIGHT_NO,
            configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        )
        assertEquals(420, displayMetrics.densityDpi)
        assertEquals("Europe/Rome", ZoneId.systemDefault().id)
    }

    private fun openOverflowAction(moreActions: String, actionLabel: String) {
        composeRule.onNodeWithContentDescription(moreActions).performClick()
        composeRule.onNodeWithText(actionLabel).performClick()
    }

    private fun capture(
        locale: StoreMediaLocale,
        name: String,
        title: String,
        requiredTag: String
    ) {
        composeRule.onAllNodesWithText(title)[0].assertIsDisplayed()
        composeRule.onNodeWithTag(requiredTag).assertIsDisplayed()
        composeRule.waitForIdle()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(
            requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "store-media-captures/${locale.tag}/$name.png"
        )
        val parent = requireNotNull(output.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Unable to create ${output.parent}" }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(2400, bitmap.height)
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Unable to write ${output.absolutePath}"
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val RECURRING_TASK_ID = 40_003
        const val COMPLETED_TASK_ID = 40_006
        const val WAIT_TIMEOUT_MILLIS = 15_000L
    }
}

private class StoreMediaAdsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                AdsConsentManager.setAdsSuppressedForTesting(true)
                try {
                    base.evaluate()
                } finally {
                    AdsConsentManager.setAdsSuppressedForTesting(false)
                }
            }
        }
}

private data class StoreMediaLocale(
    val tag: String,
    val naturalLanguageInput: String,
    val tasksTitle: String,
    val createTitle: String,
    val quickEntryLabel: String,
    val recurrenceLabel: String,
    val historyTitle: String,
    val portabilityTitle: String,
    val moreActions: String,
    val completionHistoryAction: String,
    val portabilityAction: String
)

private fun localeForTest(methodName: String): String = when (methodName) {
    "captureItalianPhoneStory" -> "it-IT"
    "captureEnglishPhoneStory" -> "en-US"
    else -> error("No store-media locale for $methodName")
}

private class StoreMediaLocaleRule(
    private val localeForTest: (String) -> String
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val localeManager = instrumentation.targetContext.applicationContext
                    .getSystemService(LocaleManager::class.java)
                val previousLocales = localeManager.applicationLocales
                try {
                    localeManager.applicationLocales = LocaleList.forLanguageTags(
                        localeForTest(description.methodName)
                    )
                    instrumentation.waitForIdleSync()
                    base.evaluate()
                } finally {
                    localeManager.applicationLocales = previousLocales
                    instrumentation.waitForIdleSync()
                }
            }
        }
}
