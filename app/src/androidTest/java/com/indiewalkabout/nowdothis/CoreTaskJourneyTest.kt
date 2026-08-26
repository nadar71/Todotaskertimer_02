package com.indiewalkabout.nowdothis

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.app.MainActivity
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class CoreTaskJourneyTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(ApplicationLocaleRule("it"))
        .around(composeRule)

    private val database: AppDatabase by lazy {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext
        EntryPointAccessors.fromApplication(application, DebugDatabaseEntryPoint::class.java)
            .database()
    }

    @Before
    fun resetLocalState() {
        runBlocking { withContext(Dispatchers.IO) { database.clearAllTables() } }
        composeRule.waitForIdle()
    }

    @Test
    fun createCompleteRecurringTask_thenBrowseCalendarAndHistory() {
        val title = "Invia rapporto"
        val archivedTitle = "Archiviata"
        val tomorrow = LocalDate.now().plusDays(1)

        openMoreActions()
        composeRule.onNodeWithText(text(R.string.task_manage_categories)).performClick()
        composeRule.onNodeWithTag("category-add").performClick()
        composeRule.onNodeWithTag("category-name").performTextReplacement("Clienti")
        composeRule.onNodeWithTag("category-palette-green").performClick()
        composeRule.onNodeWithText(text(R.string.category_save)).performClick()
        composeRule.onNodeWithText("Clienti").assertExists()
        composeRule.onNodeWithTag("category-back").performClick()

        composeRule.onNodeWithTag("navigation-calendar").performClick()
        composeRule.onNodeWithTag("calendar-day-$tomorrow").performClick()
        composeRule.onNodeWithTag("calendar-add").performClick()
        composeRule.onNodeWithTag("task-title").performTextReplacement(title)
        composeRule.onNodeWithTag("task-description")
            .performTextReplacement("Rapporto mensile")
        scrollEditorTo("task-category-field")
        composeRule.onNodeWithTag("task-category-field").performClick()
        composeRule.onNodeWithText("Clienti").performClick()
        closeSoftKeyboard()
        scrollEditorTo("task-recurrence-field")
        composeRule.onNodeWithTag("task-recurrence-field").performClick()
        composeRule.onNodeWithTag(
            "task-recurrence-option-monthly",
            useUnmergedTree = true
        ).performClick()
        scrollEditorTo("subtask-add")
        composeRule.onNodeWithTag("subtask-add").performClick()
        composeRule.onNodeWithTag("subtask-title--1")
            .performTextReplacement("Controlla dati")
        composeRule.onNodeWithTag("task-editor-save").performClick()

        waitForText(title)
        composeRule.onNodeWithTag("navigation-tasks").performClick()
        waitForText(title)
        composeRule.onNode(
            hasContentDescription(text(R.string.task_complete_description, title))
        ).performClick()
        waitForText(text(R.string.task_section_completed_today))

        val nextOccurrence = waitForNextOccurrence(title)
        val seriesRows = runBlocking(Dispatchers.IO) {
            database.taskDao().observeAllTaskEntities().first().filter { it.title == title }
        }
        val completedOccurrence = seriesRows.single(TaskEntity::isCompleted)
        assertEquals(2, seriesRows.size)
        assertEquals(1, seriesRows.count(TaskEntity::isCompleted))
        assertEquals(1, seriesRows.count { !it.isCompleted })
        assertNotEquals(completedOccurrence.id, nextOccurrence.id)
        assertNotNull(completedOccurrence.seriesId)
        assertEquals(completedOccurrence.seriesId, nextOccurrence.seriesId)
        assertEquals("MONTHLY_DAY", completedOccurrence.recurrenceKind)
        assertEquals(completedOccurrence.recurrenceKind, nextOccurrence.recurrenceKind)
        assertEquals(completedOccurrence.recurrenceEndAt, nextOccurrence.recurrenceEndAt)
        val nextDate = Instant.ofEpochMilli(requireNotNull(nextOccurrence.dueAt))
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        composeRule.onNodeWithTag("navigation-calendar").performClick()
        if (YearMonth.from(nextDate) != YearMonth.now()) {
            composeRule.onNodeWithTag("calendar-next").performClick()
        }
        composeRule.onNodeWithTag("calendar-day-$nextDate").performClick()
        waitForText(title)

        seedArchivedTask(archivedTitle)
        composeRule.onNodeWithTag("navigation-tasks").performClick()
        openMoreActions()
        composeRule.onNodeWithText(text(R.string.task_open_history)).performClick()
        waitForText(archivedTitle)
    }

    private fun openMoreActions() {
        composeRule.onNode(
            hasContentDescription(text(R.string.task_more_actions))
        ).performClick()
    }

    private fun scrollEditorTo(tag: String) {
        composeRule.onNodeWithTag("task-editor-form")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun waitForText(value: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNextOccurrence(title: String): TaskEntity {
        var result: TaskEntity? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            result = runBlocking(Dispatchers.IO) {
                database.taskDao().observeAllTaskEntities().first()
                    .firstOrNull { it.title == title && !it.isCompleted }
            }
            result != null
        }
        return requireNotNull(result)
    }

    private fun seedArchivedTask(title: String) {
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        runBlocking(Dispatchers.IO) {
            database.taskDao().insertTask(
                TaskEntity(
                    title = title,
                    description = "Completata ieri",
                    priority = "LOW",
                    isCompleted = true,
                    completedAt = startOfToday - 1,
                    createdAt = startOfToday - 2,
                    updatedAt = startOfToday - 1
                )
            )
        }
    }

    private fun text(id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)

    private class ApplicationLocaleRule(private val languageTags: String) : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val localeManager = instrumentation.targetContext.applicationContext
                        .getSystemService(LocaleManager::class.java)
                    localeManager.applicationLocales = LocaleList.forLanguageTags(languageTags)
                    instrumentation.waitForIdleSync()
                    try {
                        base.evaluate()
                    } finally {
                        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
                        instrumentation.waitForIdleSync()
                    }
                }
            }
    }
}
