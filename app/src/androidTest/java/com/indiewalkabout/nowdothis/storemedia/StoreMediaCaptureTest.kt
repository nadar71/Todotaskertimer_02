package com.indiewalkabout.nowdothis.storemedia

import android.app.LocaleManager
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
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.app.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class StoreMediaCaptureTest {
    private val localeRule = StoreMediaLocaleRule(::localeForTest)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(localeRule).around(composeRule)

    @Test
    fun captureItalianPhoneStory() = capturePhoneStory(
        StoreMediaLocale(
            tag = "it-IT",
            naturalLanguageInput = "Pianifica rilascio 31/12/2037 alle 18 ogni lunedi e venerdi"
        )
    )

    @Test
    fun captureEnglishPhoneStory() = capturePhoneStory(
        StoreMediaLocale(
            tag = "en-US",
            naturalLanguageInput = "Plan release 12/31/2037 at 6 pm every Monday and Friday"
        )
    )

    private fun capturePhoneStory(locale: StoreMediaLocale) {
        waitForTag("task-list")
        capture(locale, "01-focus", R.string.tasks_title, "task-list")

        composeRule.onNodeWithTag("task-add").performClick()
        waitForTag("quick-entry-section")
        capture(
            locale,
            "02-quick-capture",
            R.string.task_editor_create_title,
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
            R.string.quick_entry_label,
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
            R.string.task_editor_recurrence_label,
            "task-recurrence-editor"
        )

        composeRule.onNodeWithTag("task-editor-back").performClick()
        waitForTag("task-list")
        openOverflowAction(R.string.task_manage_categories)
        waitForTag("category-list")
        capture(locale, "05-organize", R.string.category_title, "category-list")

        composeRule.onNodeWithTag("category-back").performClick()
        waitForTag("task-list")
        openOverflowAction(R.string.portability_menu_action)
        waitForTag("portability-create")
        capture(locale, "06-portability", R.string.portability_title, "portability-create")
    }

    private fun openOverflowAction(actionLabel: Int) {
        composeRule.onNodeWithContentDescription(text(R.string.task_more_actions)).performClick()
        composeRule.onNodeWithText(text(actionLabel)).performClick()
    }

    private fun capture(
        locale: StoreMediaLocale,
        name: String,
        title: Int,
        requiredTag: String
    ) {
        composeRule.onAllNodesWithText(text(title))[0].assertIsDisplayed()
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

    private fun text(id: Int): String = composeRule.activity.getString(id)

    private companion object {
        const val RECURRING_TASK_ID = 40_003
        const val WAIT_TIMEOUT_MILLIS = 15_000L
    }
}

private data class StoreMediaLocale(
    val tag: String,
    val naturalLanguageInput: String
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
