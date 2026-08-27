package com.indiewalkabout.nowdothis.feature.naturallanguage

import android.app.AlarmManager
import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.app.MainActivity
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.core.notifications.AndroidAlarmGateway
import com.indiewalkabout.nowdothis.core.notifications.NotificationPermissionTestState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
    private val notificationPermissionRule = NotificationPermissionRule()
    private val appStateRule = AppStateRule(::journeyFixtureFor)

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(localeRule)
        .around(notificationPermissionRule)
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
        saveThroughExactAlarmFallback()

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
        assertExactAlarmUnavailable()
        saveThroughExactAlarmFallback()

        val task = waitForPersistedTask(fixture.expectedTitle)
        assertPersistedJourney(task, fixture)
        assertReturnedToTaskList(fixture.expectedTitle)
    }

    @Test
    fun supportedEnglishSecondaryLocale_matchesRenderedResourcesAndParser() {
        val fixture = ENGLISH_SECONDARY_LOCALE

        assertActiveLocale(fixture)
        assertEquals("Quick entry", text(R.string.quick_entry_label))
        val renderedCategoryName = text(R.string.category_work)
        openNewTask()
        enterAndParse(
            "Plan project tomorrow at 18 #\"$renderedCategoryName\" !high every week " +
                "remind 1h before"
        )
        assertNonTemporalPreview(fixture.copy(categoryName = renderedCategoryName))
    }

    @Test
    fun unsupportedOnlyLocales_useItalianResourcesCategoriesAndParser() {
        val fixture = UNSUPPORTED_ONLY_LOCALES

        assertActiveLocale(fixture)
        val renderedCategoryName = text(R.string.category_work)
        openNewTask()
        enterAndParse(
            "Pianifica lavoro domani alle 18 #\"$renderedCategoryName\" " +
                "!alta ogni settimana promemoria 1h prima"
        )
        assertNonTemporalPreview(fixture.copy(categoryName = renderedCategoryName))
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

    @Test
    fun italianSelectedWeekdays_surviveEditorRecreation() {
        assertSelectedWeekdaysSurviveRecreation(ITALIAN_SELECTED_WEEKDAYS_RECREATION)
    }

    @Test
    fun englishSelectedWeekdays_surviveEditorRecreation() {
        assertSelectedWeekdaysSurviveRecreation(ENGLISH_SELECTED_WEEKDAYS_RECREATION)
    }

    @Test
    fun setupFailure_afterMutation_restoresRowsSequencesAndLiveAlarm() {
        appStateRule.insertTaskWithRegisteredAlarm(SETUP_FAILURE_TASK)
        appStateRule.insertTaskWithoutRegisteredAlarm(SETUP_FAILURE_NO_ALARM_TASK)
        appStateRule.registerAlarmForTest(
            SETUP_FAILURE_ORPHAN_REQUEST_CODE,
            SETUP_FAILURE_ORPHAN_TRIGGER_AT
        )
        val before = appStateRule.captureState()
        val beforeAlarm = requireNotNull(before.registeredAlarms[SETUP_FAILURE_TASK.id])
        val beforeOrphan = requireNotNull(
            before.registeredAlarms[SETUP_FAILURE_ORPHAN_REQUEST_CODE]
        )
        assertEquals(SETUP_FAILURE_TRIGGER_AT, beforeAlarm.triggerAt)
        assertEquals(SETUP_FAILURE_ORPHAN_TRIGGER_AT, beforeOrphan.triggerAt)
        assertFalse(before.registeredAlarms.containsKey(SETUP_FAILURE_NO_ALARM_TASK.id))
        var journeyBodyRan = false
        val failingRule = AppStateRule(
            fixtureForTest = { ITALIAN_SAVE },
            afterFixtureMutation = {
                assertTrue(
                    "Fixture preparation left package reminder alarms registered",
                    appStateRule.registeredReminders().isEmpty()
                )
                appStateRule.registerAlarmForTest(
                    SETUP_FAILURE_NO_ALARM_TASK.id,
                    SETUP_FAILURE_UNEXPECTED_TRIGGER_AT
                )
                throw ExpectedFixtureSetupFailure()
            }
        )

        val failure = assertThrows(ExpectedFixtureSetupFailure::class.java) {
            failingRule.apply(
                base = object : Statement() {
                    override fun evaluate() {
                        journeyBodyRan = true
                    }
                },
                description = Description.createTestDescription(
                    NaturalLanguageEntryJourneyTest::class.java,
                    "nestedSetupFailure"
                )
            ).evaluate()
        }

        assertEquals(EXPECTED_SETUP_FAILURE_MESSAGE, failure.message)
        assertFalse("Journey body ran after fixture setup failed", journeyBodyRan)
        val after = appStateRule.captureState()
        assertEquals(before.categories, after.categories)
        assertEquals(before.tasks, after.tasks)
        assertEquals(before.subtasks, after.subtasks)
        assertEquals(before.sequences, after.sequences)
        assertEquals(before.registeredAlarms.keys, after.registeredAlarms.keys)
        val afterAlarm = requireNotNull(after.registeredAlarms[SETUP_FAILURE_TASK.id])
        val afterOrphan = requireNotNull(
            after.registeredAlarms[SETUP_FAILURE_ORPHAN_REQUEST_CODE]
        )
        assertFalse(after.registeredAlarms.containsKey(SETUP_FAILURE_NO_ALARM_TASK.id))
        assertEquals(beforeAlarm.type, afterAlarm.type)
        assertEquals(beforeAlarm.packageName, afterAlarm.packageName)
        assertEquals(beforeAlarm.receiverComponent, afterAlarm.receiverComponent)
        assertEquals(beforeAlarm.requestCode, afterAlarm.requestCode)
        assertTrue(
            "Restored alarm trigger ${afterAlarm.triggerAt} differed from " +
                "${beforeAlarm.triggerAt}",
            abs(afterAlarm.triggerAt - beforeAlarm.triggerAt) <= ALARM_TRIGGER_TOLERANCE_MILLIS
        )
        assertEquals(beforeOrphan.type, afterOrphan.type)
        assertEquals(beforeOrphan.packageName, afterOrphan.packageName)
        assertEquals(beforeOrphan.receiverComponent, afterOrphan.receiverComponent)
        assertEquals(beforeOrphan.requestCode, afterOrphan.requestCode)
        assertTrue(
            "Restored orphan trigger ${afterOrphan.triggerAt} differed from " +
                "${beforeOrphan.triggerAt}",
            abs(afterOrphan.triggerAt - beforeOrphan.triggerAt) <=
                ALARM_TRIGGER_TOLERANCE_MILLIS
        )
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
        scrollEditorTo("task-recurrence-kind")
        composeRule.onNodeWithTag("task-recurrence-kind")
            .assertTextEquals(text(R.string.task_recurrence_interval))
        scrollEditorTo("task-recurrence-basis-scheduled_date")
        composeRule.onNodeWithTag("task-recurrence-basis-scheduled_date").assertIsSelected()
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

    private fun assertSelectedWeekdaysSurviveRecreation(fixture: JourneyFixture) {
        assertActiveLocale(fixture)
        openNewTask()
        enterAndParse(fixture.phrase)
        assertSelectedWeekdayDraft(fixture)

        composeRule.activityRule.scenario.recreate()

        waitForTag("task-title")
        assertActiveLocale(fixture)
        assertSelectedWeekdayDraft(fixture)
        assertEquals(emptyList<TaskEntity>(), readTasks())
    }

    private fun assertSelectedWeekdayDraft(fixture: JourneyFixture) {
        scrollEditorTo("task-title")
        assertEditableText("task-title", fixture.expectedTitle)
        scrollEditorTo("task-recurrence-weekdays")
        composeRule.onNodeWithTag("task-recurrence-weekday-monday").assertIsSelected()
        composeRule.onNodeWithTag("task-recurrence-weekday-friday").assertIsSelected()
        scrollEditorTo("task-recurrence-basis-scheduled_date")
        composeRule.onNodeWithTag("task-recurrence-basis-scheduled_date").assertIsSelected()
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

    private fun saveThroughExactAlarmFallback() {
        composeRule.onNodeWithTag("task-editor-save").performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(
            "Save did not open exact-alarm access settings",
            device.wait(
                Until.hasObject(By.pkg(SETTINGS_PACKAGE)),
                PLATFORM_UI_TIMEOUT_MILLIS
            )
        )
        assertExactAlarmUnavailable()
        assertTrue("Could not return from exact-alarm settings", device.pressBack())
        instrumentation.waitForIdleSync()
    }

    private fun assertExactAlarmUnavailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(AlarmManager::class.java)
        assertFalse("Fixture unexpectedly had exact-alarm access", alarmManager.canScheduleExactAlarms())
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
        assertEquals("INTERVAL", task.recurrence)
        assertEquals("INTERVAL", task.recurrenceKind)
        assertEquals("WEEKS", task.recurrenceIntervalUnit)
        assertEquals(1, task.recurrenceIntervalCount)
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
        assertEquals(fixture.primaryLanguage, localeManager.applicationLocales[0].language)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
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
    val languageTags: String,
    val phrase: String,
    val changedWithoutParse: String = "",
    val expectedTitle: String,
    val description: String,
    val categoryId: Int,
    val categoryName: String,
    val categoryCustomName: String? = categoryName,
    val categoryDefaultKey: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime = LocalTime.of(18, 0)
) {
    val primaryLanguage: String
        get() = LocaleList.forLanguageTags(languageTags)[0].language

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
    languageTags = "it",
    phrase = "Compra latte 31/12/2037 alle 18 #Casa !alta ogni settimana promemoria 1h prima",
    expectedTitle = "Compra latte",
    description = "Scorta per la colazione",
    categoryId = 101,
    categoryName = "Casa",
    dueDate = LocalDate.of(2037, 12, 31)
)

private val ENGLISH_SAVE = JourneyFixture(
    languageTags = "en",
    phrase = "Buy milk 12/30/2037 at 6 pm #Home !high every week remind 1h before",
    expectedTitle = "Buy milk",
    description = "Breakfast supplies",
    categoryId = 102,
    categoryName = "Home",
    dueDate = LocalDate.of(2037, 12, 30)
)

private val ITALIAN_RECREATION = JourneyFixture(
    languageTags = "it",
    phrase = "Paga bollette domani alle 18 #Casa !alta ogni settimana promemoria 1h prima",
    changedWithoutParse = "Paga bollette oggi alle 07 #Casa !bassa ogni mese promemoria 30m prima",
    expectedTitle = "Paga bollette",
    description = "",
    categoryId = 103,
    categoryName = "Casa"
)

private val ITALIAN_SELECTED_WEEKDAYS_RECREATION = JourneyFixture(
    languageTags = "it",
    phrase = "Pianifica rilascio 31/12/2037 alle 18 ogni lunedi e venerdi",
    expectedTitle = "Pianifica rilascio",
    description = "",
    categoryId = 106,
    categoryName = "Casa",
    dueDate = LocalDate.of(2037, 12, 31)
)

private val ENGLISH_SELECTED_WEEKDAYS_RECREATION = JourneyFixture(
    languageTags = "en",
    phrase = "Plan release 12/31/2037 at 6 pm every Monday and Friday",
    expectedTitle = "Plan release",
    description = "",
    categoryId = 107,
    categoryName = "Home",
    dueDate = LocalDate.of(2037, 12, 31)
)

private val ENGLISH_SECONDARY_LOCALE = JourneyFixture(
    languageTags = "fr-CH,en-US",
    phrase = "Plan project tomorrow at 18 #Work !high every week remind 1h before",
    expectedTitle = "Plan project",
    description = "",
    categoryId = 104,
    categoryName = "Work",
    categoryCustomName = null,
    categoryDefaultKey = "WORK"
)

private val UNSUPPORTED_ONLY_LOCALES = JourneyFixture(
    languageTags = "fr-CH,de-DE",
    phrase = "Pianifica lavoro domani alle 18 #Lavoro !alta ogni settimana " +
        "promemoria 1h prima",
    expectedTitle = "Pianifica lavoro",
    description = "",
    categoryId = 105,
    categoryName = "Lavoro",
    categoryCustomName = null,
    categoryDefaultKey = "WORK"
)

private fun journeyFixtureFor(testMethod: String): JourneyFixture = when (testMethod) {
    "italianJourney_parseCorrectDescribeSave_persistsTaskAndReminder" -> ITALIAN_SAVE
    "englishJourney_parseCorrectDescribeSave_persistsTaskAndReminder" -> ENGLISH_SAVE
    "supportedEnglishSecondaryLocale_matchesRenderedResourcesAndParser" -> {
        ENGLISH_SECONDARY_LOCALE
    }
    "unsupportedOnlyLocales_useItalianResourcesCategoriesAndParser" -> {
        UNSUPPORTED_ONLY_LOCALES
    }
    "recreation_restoresRelativeDatePreview_withoutReparsingChangedInput" -> {
        ITALIAN_RECREATION
    }
    "italianSelectedWeekdays_surviveEditorRecreation" -> ITALIAN_SELECTED_WEEKDAYS_RECREATION
    "englishSelectedWeekdays_surviveEditorRecreation" -> ENGLISH_SELECTED_WEEKDAYS_RECREATION
    "setupFailure_afterMutation_restoresRowsSequencesAndLiveAlarm" -> ITALIAN_RECREATION
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
                localeManager.applicationLocales = LocaleList.forLanguageTags(fixture.languageTags)
                instrumentation.waitForIdleSync()
                base.evaluate()
            } finally {
                localeManager.applicationLocales = previousLocales
                instrumentation.waitForIdleSync()
            }
        }
    }
}

private class NotificationPermissionRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationPermissionTestState.create().withForced(granted = true) {
                    base.evaluate()
                }
            } else {
                base.evaluate()
            }
        }
    }
}

private class AppStateRule(
    private val fixtureForTest: (String) -> JourneyFixture,
    private val afterFixtureMutation: () -> Unit = {}
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
            try {
                prepareFixture(fixtureForTest(description.methodName))
                base.evaluate()
            } finally {
                restoreState(snapshot)
            }
        }
    }

    fun registeredReminders(): List<RegisteredAlarm> {
        val receiver = ComponentName(context, ReminderReceiver::class.java)
        val receiverNames = setOf(receiver.flattenToString(), receiver.flattenToShortString())
        return AlarmRegistryEvidence.parse(
            alarmDump = shell("dumpsys alarm"),
            pendingIntentDump = shell("dumpsys activity intents"),
            packageName = context.packageName
        ).filter { alarm ->
            alarm.packageName == context.packageName &&
                alarm.receiverComponent in receiverNames
        }
    }

    fun captureState(): AppStateSnapshot = snapshotState()

    fun insertTaskWithRegisteredAlarm(task: TaskEntity) {
        insertTask(task)
        val triggerAt = requireNotNull(task.reminderAt)
        registerAlarmForTest(task.id, triggerAt)
    }

    fun insertTaskWithoutRegisteredAlarm(task: TaskEntity) {
        insertTask(task)
        alarmGateway().cancel(task.id)
        check(registeredAlarmMap()[task.id] == null) {
            "Setup-failure absence sentinel unexpectedly had a registered alarm"
        }
    }

    fun registerAlarmForTest(taskId: Int, triggerAt: Long) {
        check(scheduleReminder(taskId, triggerAt)) {
            "Could not register setup-failure sentinel alarm for task $taskId"
        }
        check(registeredReminders().count { alarm -> alarm.requestCode == taskId } == 1) {
            "Setup-failure sentinel alarm was absent from the device registry"
        }
    }

    private fun snapshotState(): AppStateSnapshot {
        val (categories, tasks, subtasks) = runBlocking(Dispatchers.IO) {
            Triple(
                database.categoryDao().getAll(),
                database.taskDao().getAllTaskEntities(),
                database.taskDao().getAllSubtaskEntities()
            )
        }
        val registeredAlarms = registeredAlarmMap()
        return AppStateSnapshot(
            categories = categories,
            tasks = tasks,
            subtasks = subtasks,
            sequences = readSequences(),
            registeredAlarms = registeredAlarms
        )
    }

    private fun prepareFixture(fixture: JourneyFixture) {
        registeredReminders().forEach { alarm -> alarmGateway().cancel(alarm.requestCode) }
        runBlocking {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                replaceSequences(SEQUENCE_TABLES.associateWith { null })
                afterFixtureMutation()
                database.categoryDao().insert(
                    CategoryEntity(
                        id = fixture.categoryId,
                        customName = fixture.categoryCustomName,
                        defaultKey = fixture.categoryDefaultKey,
                        colorToken = "GREEN",
                        position = 0,
                        createdAt = FIXTURE_CREATED_AT
                    )
                )
            }
        }
    }

    private fun restoreState(snapshot: AppStateSnapshot) {
        registeredReminders().forEach { alarm -> alarmGateway().cancel(alarm.requestCode) }
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
        snapshot.registeredAlarms.values.forEach { alarm ->
            check(scheduleReminder(alarm.requestCode, alarm.triggerAt)) {
                "Could not restore registered alarm for request ${alarm.requestCode}"
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
        assertRestoredAlarms(snapshot)
    }

    private fun assertRestoredAlarms(snapshot: AppStateSnapshot) {
        val actual = registeredAlarmMap()
        assertEquals(snapshot.registeredAlarms.keys, actual.keys)
        snapshot.registeredAlarms.forEach { (taskId, expected) ->
            val restored = requireNotNull(actual[taskId])
            assertEquals(expected.type, restored.type)
            assertEquals(expected.packageName, restored.packageName)
            assertEquals(expected.receiverComponent, restored.receiverComponent)
            assertEquals(expected.requestCode, restored.requestCode)
            assertTrue(
                "Restored alarm trigger ${restored.triggerAt} differed from " +
                    "${expected.triggerAt} for task $taskId",
                abs(restored.triggerAt - expected.triggerAt) <=
                    ALARM_TRIGGER_TOLERANCE_MILLIS
            )
        }
    }

    private fun registeredAlarmMap(): Map<Int, RegisteredAlarm> {
        val grouped = registeredReminders().groupBy(RegisteredAlarm::requestCode)
        check(grouped.values.none { alarms -> alarms.size > 1 }) {
            "Multiple package reminder alarms found for one request code: $grouped"
        }
        return grouped.mapValues { (_, alarms) -> alarms.single() }
    }

    private fun scheduleReminder(taskId: Int, triggerAt: Long): Boolean {
        val gateway = alarmGateway()
        return gateway.setExact(taskId, triggerAt) || gateway.setInexact(taskId, triggerAt)
    }

    private fun insertTask(task: TaskEntity) {
        runBlocking(Dispatchers.IO) {
            database.taskDao().insertTask(task)
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

}

private data class AppStateSnapshot(
    val categories: List<CategoryEntity>,
    val tasks: List<TaskEntity>,
    val subtasks: List<SubtaskEntity>,
    val sequences: Map<String, Long?>,
    val registeredAlarms: Map<Int, RegisteredAlarm>
)

private class ExpectedFixtureSetupFailure : RuntimeException(EXPECTED_SETUP_FAILURE_MESSAGE)

private val SETUP_FAILURE_TASK = TaskEntity(
    id = 701,
    title = "Fixture restoration sentinel",
    description = "Must survive setup failure",
    priority = "LOW",
    categoryId = ITALIAN_RECREATION.categoryId,
    dueAt = SETUP_FAILURE_TRIGGER_AT + REMINDER_LEAD_MILLIS,
    reminderAt = SETUP_FAILURE_TRIGGER_AT,
    reminderStatus = "SCHEDULED",
    createdAt = FIXTURE_CREATED_AT,
    updatedAt = FIXTURE_CREATED_AT
)

private val SETUP_FAILURE_NO_ALARM_TASK = TaskEntity(
    id = 702,
    title = "Fixture alarm-absence sentinel",
    description = "Must remain without a registered alarm",
    priority = "LOW",
    categoryId = ITALIAN_RECREATION.categoryId,
    dueAt = SETUP_FAILURE_UNEXPECTED_TRIGGER_AT + REMINDER_LEAD_MILLIS,
    reminderAt = SETUP_FAILURE_UNEXPECTED_TRIGGER_AT,
    reminderStatus = "SCHEDULED",
    createdAt = FIXTURE_CREATED_AT,
    updatedAt = FIXTURE_CREATED_AT
)

private const val REMINDER_LEAD_MILLIS = 60 * 60 * 1_000L
private const val ALARM_TRIGGER_TOLERANCE_MILLIS = 1_000L
private const val WAIT_TIMEOUT_MILLIS = 10_000L
private const val PLATFORM_UI_TIMEOUT_MILLIS = 5_000L
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val FIXTURE_CREATED_AT = 1_788_044_400_000L
private const val SETUP_FAILURE_TRIGGER_AT = 2_145_990_600_000L
private const val SETUP_FAILURE_UNEXPECTED_TRIGGER_AT = 2_145_994_200_000L
private const val SETUP_FAILURE_ORPHAN_REQUEST_CODE = 703
private const val SETUP_FAILURE_ORPHAN_TRIGGER_AT = 2_145_997_800_000L
private const val EXPECTED_SETUP_FAILURE_MESSAGE = "Expected failure after fixture mutation"
private val SEQUENCE_TABLES = listOf("categories", "tasks", "subtasks")
