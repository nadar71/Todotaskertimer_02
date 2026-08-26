package com.indiewalkabout.nowdothis.feature.naturallanguage

import android.app.LocaleManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.LocaleList
import android.text.format.DateUtils
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import java.io.FileInputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
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
        assertReturnedToTaskList(fixture.expectedTitle)
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
        assertReturnedToTaskList(fixture.expectedTitle)
    }

    @Test
    fun recreation_restoresRelativeDatePreview_withoutReparsingChangedInput() {
        val fixture = ITALIAN_RECREATION

        assertActiveLocale(fixture)
        openNewTask()
        enterAndParse(fixture.phrase)
        assertNonTemporalPreview(fixture)
        val parsedSchedule = captureScheduleControls()

        composeRule.onNodeWithTag("quick-entry-input")
            .performTextReplacement(fixture.changedWithoutParse)
        closeSoftKeyboard()
        assertEditableText("quick-entry-input", fixture.changedWithoutParse)

        composeRule.activityRule.scenario.recreate()

        waitForTag("quick-entry-input")
        assertActiveLocale(fixture)
        assertEditableText("quick-entry-input", fixture.changedWithoutParse)
        assertNonTemporalPreview(fixture)
        assertScheduleControls(parsedSchedule)
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
        assertNonTemporalPreview(fixture)
        val expectedDueAt = fixture.expectedDueAt()
        assertDateTimeControl("task-due-section", expectedDueAt)
        assertDateTimeControl("task-reminder-section", expectedDueAt - REMINDER_LEAD_MILLIS)
        scrollEditorTo("task-description")
        assertEditableText("task-description", "")
    }

    private fun assertNonTemporalPreview(fixture: JourneyFixture) {
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
        scrollEditorTo("task-recurrence-field")
        composeRule.onNodeWithTag("task-recurrence-field")
            .assertTextEquals(text(R.string.task_recurrence_weekly))
    }

    private fun captureScheduleControls(): ScheduleControlSnapshot {
        val snapshot = ScheduleControlSnapshot(
            dueText = controlTextSnapshot("task-due-section"),
            reminderText = controlTextSnapshot("task-reminder-section")
        )
        assertTrue("Parsed due control did not expose text", snapshot.dueText.isNotEmpty())
        assertTrue("Parsed reminder control did not expose text", snapshot.reminderText.isNotEmpty())
        return snapshot
    }

    private fun assertScheduleControls(expected: ScheduleControlSnapshot) {
        assertEquals(expected.dueText, controlTextSnapshot("task-due-section"))
        assertEquals(expected.reminderText, controlTextSnapshot("task-reminder-section"))
    }

    private fun controlTextSnapshot(tag: String): List<String> {
        scrollEditorTo(tag)
        return composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true
        ).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map(AnnotatedString::text)
        }
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
        assertRegisteredReminder(task)
    }

    private fun assertRegisteredReminder(task: TaskEntity) {
        val expectedReminderAt = requireNotNull(task.reminderAt)
        val candidates = appStateRule.registeredReminders()
            .filter { alarm -> alarm.requestCode == task.id }
        assertEquals("Expected one registered reminder alarm: $candidates", 1, candidates.size)
        val alarm = candidates.single()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receiver = ComponentName(context, ReminderReceiver::class.java)
        assertEquals(context.packageName, alarm.packageName)
        assertTrue(
            "Unexpected reminder receiver ${alarm.receiverComponent}",
            alarm.receiverComponent == receiver.flattenToShortString() ||
                alarm.receiverComponent == receiver.flattenToString()
        )
        assertEquals(task.id, alarm.requestCode)
        assertEquals("RTC_WAKEUP", alarm.type)
        assertTrue(
            "Alarm trigger ${alarm.triggerAt} differed from $expectedReminderAt",
            abs(alarm.triggerAt - expectedReminderAt) <= ALARM_TRIGGER_TOLERANCE_MILLIS
        )
    }

    private fun waitForPersistedTask(title: String): TaskEntity {
        var persisted: TaskEntity? = null
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            persisted = readTasks().singleOrNull { task ->
                task.title == title &&
                    task.reminderStatus == "SCHEDULED"
            }
            persisted != null
        }
        return requireNotNull(persisted)
    }

    private fun assertReturnedToTaskList(title: String) {
        waitForTag("task-search")
        composeRule.onNodeWithTag("task-search").assertIsDisplayed()
        composeRule.onNodeWithTag("task-add").assertIsDisplayed()
        composeRule.onAllNodesWithTag("task-editor-form").assertCountEquals(0)
        composeRule.onAllNodesWithTag("quick-entry-input").assertCountEquals(0)
        waitForText(title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
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

private data class ScheduleControlSnapshot(
    val dueText: List<String>,
    val reminderText: List<String>
)

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

    fun registeredReminders(): List<RegisteredAlarm> = AlarmRegistryEvidence.parse(
        alarmDump = shell("dumpsys alarm"),
        pendingIntentDump = shell("dumpsys activity intents"),
        packageName = context.packageName
    )

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
            sequences = readSequences(),
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
                replaceSequences(SEQUENCE_TABLES.associateWith { null })
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
                replaceSequences(snapshot.sequences)
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
        assertEquals(snapshot.sequences, readSequences())
        snapshot.tasks.forEach { task ->
            assertEquals(task.id in snapshot.alarmTaskIds, hasReminderOperation(task.id))
        }
    }

    private fun readSequences(): Map<String, Long?> = runBlocking(Dispatchers.IO) {
        val observed = mutableMapOf<String, Long>()
        database.openHelper.writableDatabase.query(
            "SELECT name, seq FROM sqlite_sequence " +
                "WHERE name IN ('categories', 'tasks', 'subtasks') ORDER BY name"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                observed[cursor.getString(0)] = cursor.getLong(1)
            }
        }
        SEQUENCE_TABLES.associateWith(observed::get)
    }

    private fun replaceSequences(sequences: Map<String, Long?>) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.beginTransaction()
        try {
            SEQUENCE_TABLES.forEach { table ->
                sqlite.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(table))
                sequences[table]?.let { value ->
                    sqlite.execSQL(
                        "INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)",
                        arrayOf<Any>(table, value)
                    )
                }
            }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun shell(command: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
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
    val sequences: Map<String, Long?>,
    val alarmTaskIds: Set<Int>
)

private const val REMINDER_LEAD_MILLIS = 60 * 60 * 1_000L
private const val ALARM_TRIGGER_TOLERANCE_MILLIS = 1_000L
private const val WAIT_TIMEOUT_MILLIS = 10_000L
private const val FIXTURE_CREATED_AT = 1_788_044_400_000L
private val SEQUENCE_TABLES = listOf("categories", "tasks", "subtasks")
