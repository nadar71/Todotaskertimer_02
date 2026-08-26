package com.indiewalkabout.nowdothis.feature.naturallanguage

import android.app.LocaleManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.LocaleList
import android.text.format.DateUtils
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.app.MainActivity
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.core.notifications.AndroidAlarmGateway
import com.indiewalkabout.nowdothis.core.notifications.ReminderReceiver
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class NaturalLanguageEntryJourneyTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val localeRule = ApplicationLocaleRule(::journeyFixtureFor)
    private val appStateRule = AppStateRule(::journeyFixtureFor)

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(localeRule)
        .around(appStateRule)
        .around(composeRule)

    private val database: AppDatabase
        get() = appStateRule.database

    @Test
    fun italianJourney_parseCorrectDescribeSave_persistsTaskAndReminder() {
        val fixture = ITALIAN_SAVE

        assertActiveLocale(fixture)
        openNewTask()
        enterAndParse(fixture.phrase)
        assertInferredControls(fixture)

        correctPriorityAndDescribe(fixture.description)
        composeRule.onNodeWithTag("task-editor-save").performClick()

        val task = waitForPersistedTask(fixture.expectedTitle)
        assertPersistedJourney(task, fixture)
        waitForText(fixture.expectedTitle)
    }

    @Test
    fun englishJourney_parseCorrectDescribeSave_persistsTaskAndReminder() {
        val fixture = ENGLISH_SAVE

        assertActiveLocale(fixture)
        openNewTask()
        enterAndParse(fixture.phrase)
        assertInferredControls(fixture)

        correctPriorityAndDescribe(fixture.description)
        composeRule.onNodeWithTag("task-editor-save").performClick()

        val task = waitForPersistedTask(fixture.expectedTitle)
        assertPersistedJourney(task, fixture)
        waitForText(fixture.expectedTitle)
    }

    @Test
    fun recreation_restoresRelativeDatePreview_withoutReparsingChangedInput() {
        val fixture = ITALIAN_RECREATION
        val zoneId = ZoneId.systemDefault()
        val parseDate = LocalDate.now(zoneId)

        assertActiveLocale(fixture)
        openNewTask()
        enterAndParse(fixture.phrase)
        val expectedDueAt = parseDate.plusDays(1)
            .atTime(LocalTime.of(18, 0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        assertEquals(parseDate, LocalDate.now(zoneId))
        assertRestoredPreview(fixture, expectedDueAt)

        composeRule.onNodeWithTag("quick-entry-input")
            .performTextReplacement(fixture.changedWithoutParse)
        closeSoftKeyboard()
        assertEditableText("quick-entry-input", fixture.changedWithoutParse)

        composeRule.activityRule.scenario.recreate()

        waitForTag("quick-entry-input")
        assertActiveLocale(fixture)
        assertEditableText("quick-entry-input", fixture.changedWithoutParse)
        assertRestoredPreview(fixture, expectedDueAt)
        assertEquals(emptyList<TaskEntity>(), readTasks())
    }

    private fun openNewTask() {
        waitForTag("task-add")
        composeRule.onNodeWithTag("task-add").performClick()
        waitForTag("quick-entry-input")
    }

    private fun enterAndParse(phrase: String) {
        composeRule.onNodeWithTag("quick-entry-input").performTextReplacement(phrase)
        closeSoftKeyboard()
        composeRule.onNodeWithTag("quick-entry-parse").performClick()
        waitForTag("quick-entry-summary")
    }

    private fun assertInferredControls(fixture: JourneyFixture) {
        assertRestoredPreview(fixture, fixture.expectedDueAt())
        scrollEditorTo("task-description")
        assertEditableText("task-description", "")
    }

    private fun assertRestoredPreview(fixture: JourneyFixture, expectedDueAt: Long) {
        val expectedReminderAt = expectedDueAt - REMINDER_LEAD_MILLIS
        val summary = summaryText()

        composeRule.onNodeWithTag("quick-entry-summary")
            .assertTextEquals(summary)
            .assertIsDisplayed()
        scrollEditorTo("task-title")
        assertEditableText("task-title", fixture.expectedTitle)
        scrollEditorTo("task-priority-high")
        composeRule.onNodeWithTag("task-priority-high").assertIsSelected()
        scrollEditorTo("task-category-field")
        composeRule.onNodeWithTag("task-category-field")
            .assertTextEquals(fixture.categoryName)
        assertDateTimeControl("task-due-section", expectedDueAt)
        assertDateTimeControl("task-reminder-section", expectedReminderAt)
        scrollEditorTo("task-recurrence-field")
        composeRule.onNodeWithTag("task-recurrence-field")
            .assertTextEquals(text(R.string.task_recurrence_weekly))
    }

    private fun correctPriorityAndDescribe(description: String) {
        scrollEditorTo("task-priority-medium")
        composeRule.onNodeWithTag("task-priority-medium").performClick().assertIsSelected()
        scrollEditorTo("task-description")
        composeRule.onNodeWithTag("task-description").performTextReplacement(description)
        closeSoftKeyboard()
        assertEditableText("task-description", description)
    }

    private fun assertDateTimeControl(tag: String, value: Long) {
        scrollEditorTo(tag)
        val formatted = DateUtils.formatDateTime(
            composeRule.activity,
            value,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
        )
        composeRule.onNodeWithTag(tag).assert(hasAnyDescendant(hasText(formatted)))
    }

    private fun assertPersistedJourney(task: TaskEntity, fixture: JourneyFixture) {
        val expectedDueAt = fixture.expectedDueAt()
        assertEquals(fixture.expectedTitle, task.title)
        assertEquals(fixture.description, task.description)
        assertEquals("MEDIUM", task.priority)
        assertEquals(fixture.categoryId, task.categoryId)
        assertEquals(expectedDueAt, task.dueAt)
        assertEquals(expectedDueAt - REMINDER_LEAD_MILLIS, task.reminderAt)
        assertEquals("WEEKLY", task.recurrence)
        assertEquals("SCHEDULED", task.reminderStatus)
        assertTrue(appStateRule.hasReminderOperation(task.id))
    }

    private fun waitForPersistedTask(title: String): TaskEntity {
        var persisted: TaskEntity? = null
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            persisted = readTasks().singleOrNull { task ->
                task.title == title &&
                    task.reminderStatus == "SCHEDULED" &&
                    appStateRule.hasReminderOperation(task.id)
            }
            persisted != null
        }
        return requireNotNull(persisted)
    }

    private fun readTasks(): List<TaskEntity> = runBlocking(Dispatchers.IO) {
        database.taskDao().getAllTaskEntities()
    }

    private fun assertEditableText(tag: String, expected: String) {
        composeRule.onNodeWithTag(tag).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString(expected)
            )
        )
    }

    private fun assertActiveLocale(fixture: JourneyFixture) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localeManager = context.applicationContext.getSystemService(LocaleManager::class.java)
        assertEquals(fixture.languageTag, localeManager.applicationLocales[0].language)
        assertEquals(
            fixture.languageTag,
            composeRule.activity.resources.configuration.locales[0].language
        )
    }

    private fun summaryText(): String = text(
        R.string.quick_entry_summary,
        listOf(
            R.string.quick_entry_field_title,
            R.string.quick_entry_field_due_date,
            R.string.quick_entry_field_reminder,
            R.string.quick_entry_field_priority,
            R.string.quick_entry_field_category,
            R.string.quick_entry_field_recurrence
        ).joinToString { text(it) }
    )

    private fun scrollEditorTo(tag: String) {
        composeRule.onNodeWithTag("task-editor-form")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(value: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)
}

private data class JourneyFixture(
    val languageTag: String,
    val phrase: String,
    val changedWithoutParse: String = "",
    val expectedTitle: String,
    val description: String,
    val categoryId: Int,
    val categoryName: String,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime = LocalTime.of(18, 0)
) {
    fun expectedDueAt(zoneId: ZoneId = ZoneId.systemDefault()): Long = requireNotNull(dueDate)
        .atTime(dueTime)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}

private val ITALIAN_SAVE = JourneyFixture(
    languageTag = "it",
    phrase = "Compra latte 31/12/2037 alle 18 #Casa !alta ogni settimana promemoria 1h prima",
    expectedTitle = "Compra latte",
    description = "Scorta per la colazione",
    categoryId = 101,
    categoryName = "Casa",
    dueDate = LocalDate.of(2037, 12, 31)
)

private val ENGLISH_SAVE = JourneyFixture(
    languageTag = "en",
    phrase = "Buy milk 12/30/2037 at 6 pm #Home !high every week remind 1h before",
    expectedTitle = "Buy milk",
    description = "Breakfast supplies",
    categoryId = 102,
    categoryName = "Home",
    dueDate = LocalDate.of(2037, 12, 30)
)

private val ITALIAN_RECREATION = JourneyFixture(
    languageTag = "it",
    phrase = "Paga bollette domani alle 18 #Casa !alta ogni settimana promemoria 1h prima",
    changedWithoutParse = "Paga bollette oggi alle 07 #Casa !bassa ogni mese promemoria 30m prima",
    expectedTitle = "Paga bollette",
    description = "",
    categoryId = 103,
    categoryName = "Casa"
)

private fun journeyFixtureFor(testMethod: String): JourneyFixture = when (testMethod) {
    "italianJourney_parseCorrectDescribeSave_persistsTaskAndReminder" -> ITALIAN_SAVE
    "englishJourney_parseCorrectDescribeSave_persistsTaskAndReminder" -> ENGLISH_SAVE
    "recreation_restoresRelativeDatePreview_withoutReparsingChangedInput" -> {
        ITALIAN_RECREATION
    }
    else -> error("No Natural-Language Entry fixture for $testMethod")
}

private class ApplicationLocaleRule(
    private val fixtureForTest: (String) -> JourneyFixture
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val localeManager = instrumentation.targetContext.applicationContext
                .getSystemService(LocaleManager::class.java)
            val previousLocales = localeManager.applicationLocales
            try {
                val fixture = fixtureForTest(description.methodName)
                localeManager.applicationLocales = LocaleList.forLanguageTags(fixture.languageTag)
                instrumentation.waitForIdleSync()
                base.evaluate()
            } finally {
                localeManager.applicationLocales = previousLocales
                instrumentation.waitForIdleSync()
            }
        }
    }
}

private class AppStateRule(
    private val fixtureForTest: (String) -> JourneyFixture
) : TestRule {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    val database: AppDatabase by lazy {
        EntryPointAccessors.fromApplication(context, DebugDatabaseEntryPoint::class.java)
            .database()
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val snapshot = snapshotState()
            prepareFixture(fixtureForTest(description.methodName))
            try {
                base.evaluate()
            } finally {
                restoreState(snapshot)
            }
        }
    }

    fun hasReminderOperation(taskId: Int): Boolean = reminderOperation(taskId) != null

    private fun snapshotState(): AppStateSnapshot {
        val (categories, tasks, subtasks) = runBlocking(Dispatchers.IO) {
            Triple(
                database.categoryDao().getAll(),
                database.taskDao().getAllTaskEntities(),
                database.taskDao().getAllSubtaskEntities()
            )
        }
        return AppStateSnapshot(
            categories = categories,
            tasks = tasks,
            subtasks = subtasks,
            alarmTaskIds = tasks.mapNotNull { task ->
                task.id.takeIf(::hasReminderOperation)
            }.toSet()
        )
    }

    private fun prepareFixture(fixture: JourneyFixture) {
        val existingTasks = runBlocking(Dispatchers.IO) {
            database.taskDao().getAllTaskEntities()
        }
        existingTasks.forEach { alarmGateway().cancel(it.id) }
        runBlocking {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                database.categoryDao().insert(
                    CategoryEntity(
                        id = fixture.categoryId,
                        customName = fixture.categoryName,
                        colorToken = "GREEN",
                        position = 0,
                        createdAt = FIXTURE_CREATED_AT
                    )
                )
            }
        }
    }

    private fun restoreState(snapshot: AppStateSnapshot) {
        val testTasks = runBlocking(Dispatchers.IO) {
            database.taskDao().getAllTaskEntities()
        }
        testTasks.forEach { alarmGateway().cancel(it.id) }
        runBlocking {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                if (snapshot.categories.isNotEmpty()) {
                    database.categoryDao().insertAll(snapshot.categories)
                }
                if (snapshot.tasks.isNotEmpty()) {
                    database.taskDao().insertTasks(snapshot.tasks)
                }
                if (snapshot.subtasks.isNotEmpty()) {
                    database.taskDao().insertRestoredSubtasks(snapshot.subtasks)
                }
            }
        }
        snapshot.tasks.filter { it.id in snapshot.alarmTaskIds }.forEach { task ->
            val reminderAt = task.reminderAt ?: return@forEach
            val gateway = alarmGateway()
            if (!gateway.setExact(task.id, reminderAt)) {
                gateway.setInexact(task.id, reminderAt)
            }
        }
        assertEquals(snapshot.categories, runBlocking(Dispatchers.IO) {
            database.categoryDao().getAll()
        })
        assertEquals(snapshot.tasks, runBlocking(Dispatchers.IO) {
            database.taskDao().getAllTaskEntities()
        })
        assertEquals(snapshot.subtasks, runBlocking(Dispatchers.IO) {
            database.taskDao().getAllSubtaskEntities()
        })
        snapshot.tasks.forEach { task ->
            assertEquals(task.id in snapshot.alarmTaskIds, hasReminderOperation(task.id))
        }
    }

    private fun alarmGateway(): AndroidAlarmGateway = AndroidAlarmGateway(
        context = context,
        alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
    )

    private fun reminderOperation(taskId: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        taskId,
        Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
    )
}

private data class AppStateSnapshot(
    val categories: List<CategoryEntity>,
    val tasks: List<TaskEntity>,
    val subtasks: List<SubtaskEntity>,
    val alarmTaskIds: Set<Int>
)

private const val REMINDER_LEAD_MILLIS = 60 * 60 * 1_000L
private const val WAIT_TIMEOUT_MILLIS = 10_000L
private const val FIXTURE_CREATED_AT = 1_788_044_400_000L
