package com.indiewalkabout.nowdothis.feature.recurrence

import android.app.AlarmManager
import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.core.notifications.AlarmManagerReminderScheduler
import com.indiewalkabout.nowdothis.core.notifications.AndroidAlarmGateway
import com.indiewalkabout.nowdothis.core.notifications.NotificationPermissionTestState
import com.indiewalkabout.nowdothis.core.notifications.ReminderReceiver
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.naturallanguage.AlarmRegistryEvidence
import com.indiewalkabout.nowdothis.feature.naturallanguage.RegisteredAlarm
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.NaturalLanguageInput
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.AttributeParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.RecurrenceParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.ReminderParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.TemporalParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.usecase.ParseNaturalLanguageTask
import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataSource
import com.indiewalkabout.nowdothis.feature.portability.data.repository.DocumentGateway
import com.indiewalkabout.nowdothis.feature.portability.data.repository.OfflinePortabilityRepository
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupCodec
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupValidator
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.CreateBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.InspectBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.usecase.RestoreBackup
import com.indiewalkabout.nowdothis.feature.task.data.local.SubtaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.data.repository.OfflineTaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CalculateNextOccurrence
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.CompleteTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTaskResult
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ValidateTask
import dagger.hilt.android.EntryPointAccessors
import java.io.FileInputStream
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class AdvancedRecurrenceJourneyTest {
    private val stateRule = AdvancedRecurrenceStateRule()

    @get:Rule
    val state: TestRule = stateRule

    @Test
    fun captureParseCompleteRecurExportRestore_preservesRuleAndAlarm() = runBlocking {
        val zone = ZoneId.of("Europe/Rome")
        val clock = MutableClock(at("2037-12-30T09:00", zone))
        val repository = OfflineTaskRepository(stateRule.database, stateRule.database.taskDao())
        val scheduler = AlarmManagerReminderScheduler(
            gateway = stateRule.alarmGateway(),
            taskRepository = repository,
            clock = clock
        )
        val parser = ParseNaturalLanguageTask(
            temporalParser = TemporalParser(),
            attributeParser = AttributeParser(),
            reminderParser = ReminderParser(),
            recurrenceParser = RecurrenceParser()
        )
        val parseResult = parser(
            NaturalLanguageInput(
                rawText = "Prepare report 12/31/2037 at 6 pm every Monday and Friday remind 1h before",
                language = ParserLanguage.ENGLISH,
                nowEpochMillis = clock.nowMillis(),
                zoneId = zone,
                categories = emptyList()
            )
        )

        assertEquals("Prepare report", parseResult.draft.title)
        assertEquals(
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                RecurrenceBasis.SCHEDULED_DATE
            ),
            parseResult.draft.recurrenceRule
        )

        val saved = SaveTask(
            repository = repository,
            scheduler = scheduler,
            validateTask = ValidateTask(),
            clock = clock,
            seriesIdFactory = { "advanced-recurrence-series" }
        )(
            Task(
                title = requireNotNull(parseResult.draft.title),
                description = "Release evidence fixture",
                priority = TaskPriority.MEDIUM,
                dueAt = requireNotNull(parseResult.draft.dueAt),
                reminderAt = requireNotNull(parseResult.draft.reminderAt),
                recurrenceRule = requireNotNull(parseResult.draft.recurrenceRule),
                createdAt = 0,
                updatedAt = 0
            )
        )
        val initialTaskId = (saved as SaveTaskResult.Saved).taskId
        val initial = requireNotNull(repository.getTask(initialTaskId))
        assertEquals(ReminderStatus.SCHEDULED, initial.reminderStatus)
        assertExactRegisteredAlarms(initial.id to requireNotNull(initial.reminderAt))

        clock.now = at("2038-01-01T19:00", zone)
        val completion = CompleteTask(
            repository = repository,
            scheduler = scheduler,
            calculateNextOccurrence = CalculateNextOccurrence(ZoneIdProvider { zone }),
            clock = clock
        )(initialTaskId)
        val completed = completion as CompleteTaskResult.Completed
        val next = requireNotNull(completed.nextOccurrence)

        assertTrue(completed.completed.isCompleted)
        assertNotEquals(initialTaskId, next.id)
        assertEquals(initial.recurrenceRule, next.recurrenceRule)
        assertEquals("2038-01-04T18:00", localDateTime(next.dueAt, zone).toString())
        assertEquals("2038-01-04T17:00", localDateTime(next.reminderAt, zone).toString())
        assertEquals(ReminderStatus.SCHEDULED, next.reminderStatus)
        assertExactRegisteredAlarms(next.id to requireNotNull(next.reminderAt))

        val documents = MemoryDocumentGateway()
        val portability = OfflinePortabilityRepository(
            planningDataStore = PlanningDataSource(stateRule.database),
            documentGateway = documents,
            backupCodec = BackupCodec(),
            backupValidator = BackupValidator(),
            clock = clock,
            dispatcher = Dispatchers.IO
        )
        val reference = DocumentReference("memory://advanced-recurrence")
        val createBackup = CreateBackup(portability)
        val inspectBackup = InspectBackup(portability)
        val restoreBackup = RestoreBackup(portability, scheduler)

        val export = createBackup(reference)
        assertTrue(export is PortabilityResult.Exported)
        assertTrue(documents.requireBytes(reference).decodeToString().contains("\"version\":2"))
        val backup = (inspectBackup(reference) as PortabilityResult.Inspected).candidate

        withContext(Dispatchers.IO) {
            stateRule.database.taskDao().insertTask(
                TaskEntity(
                    title = "Mutated after export",
                    description = "Must be replaced",
                    priority = "LOW",
                    dueAt = at("2038-02-01T09:00", zone),
                    createdAt = clock.nowMillis(),
                    updatedAt = clock.nowMillis()
                )
            )
        }
        val restored = restoreBackup(backup)
        assertTrue(restored is PortabilityResult.Restored)

        val restoredTasks = withContext(Dispatchers.IO) {
            stateRule.database.taskDao().getAllTaskEntities()
        }
        assertEquals(2, restoredTasks.size)
        assertTrue(restoredTasks.none { it.title == "Mutated after export" })
        val restoredNext = restoredTasks.single { !it.isCompleted }
        assertEquals("SELECTED_WEEKDAYS", restoredNext.recurrenceKind)
        assertEquals(0b001_0001, restoredNext.recurrenceWeekdayMask)
        assertEquals(next.id, restoredNext.id)
        assertExactRegisteredAlarms(restoredNext.id to requireNotNull(restoredNext.reminderAt))
    }

    @Test
    fun monthlyAnchor_recoversAfterShortMonthClamp() = runBlocking {
        val zone = ZoneId.of("Europe/Rome")
        val clock = MutableClock(at("2038-01-30T09:00", zone))
        val repository = repository()
        val scheduler = scheduler(repository, clock)
        val initialId = save(
            repository = repository,
            scheduler = scheduler,
            clock = clock,
            task = recurringTask(
                title = "Month end reporting",
                dueAt = at("2038-01-31T18:00", zone),
                recurrenceRule = RecurrenceRule.MonthlyDay(
                    anchorDay = 31,
                    everyMonths = 1,
                    basis = RecurrenceBasis.SCHEDULED_DATE
                )
            )
        )
        val complete = completeTask(repository, scheduler, clock, zone)

        clock.now = at("2038-01-31T19:00", zone)
        val february = requireNotNull((complete(initialId) as CompleteTaskResult.Completed).nextOccurrence)
        assertEquals("2038-02-28T18:00", localDateTime(february.dueAt, zone).toString())

        clock.now = at("2038-02-28T19:00", zone)
        val march = requireNotNull((complete(february.id) as CompleteTaskResult.Completed).nextOccurrence)
        assertEquals("2038-03-31T18:00", localDateTime(march.dueAt, zone).toString())
        assertEquals(february.recurrenceRule, march.recurrenceRule)
    }

    @Test
    fun completionDateInterval_usesActualCompletionDateAndRetainsDueTime() = runBlocking {
        val zone = ZoneId.of("Europe/Rome")
        val clock = MutableClock(at("2038-04-01T09:00", zone))
        val repository = repository()
        val scheduler = scheduler(repository, clock)
        val initialId = save(
            repository = repository,
            scheduler = scheduler,
            clock = clock,
            task = recurringTask(
                title = "Biweekly follow-up",
                dueAt = at("2038-04-01T18:00", zone),
                recurrenceRule = RecurrenceRule.Interval(
                    unit = com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit.WEEKS,
                    every = 2,
                    basis = RecurrenceBasis.COMPLETION_DATE
                )
            )
        )

        clock.now = at("2038-04-10T19:00", zone)
        val next = requireNotNull(
            (completeTask(repository, scheduler, clock, zone)(initialId) as CompleteTaskResult.Completed)
                .nextOccurrence
        )

        assertEquals("2038-04-24T18:00", localDateTime(next.dueAt, zone).toString())
        assertEquals(RecurrenceBasis.COMPLETION_DATE, (next.recurrenceRule as RecurrenceRule.Interval).basis)
    }

    @Test
    fun recurrenceEnd_stopsSeriesWithoutCreatingAnotherOccurrence() = runBlocking {
        val zone = ZoneId.of("Europe/Rome")
        val clock = MutableClock(at("2038-05-01T09:00", zone))
        val repository = repository()
        val scheduler = scheduler(repository, clock)
        val dueAt = at("2038-05-01T18:00", zone)
        val initialId = save(
            repository = repository,
            scheduler = scheduler,
            clock = clock,
            task = recurringTask(
                title = "Final daily occurrence",
                dueAt = dueAt,
                recurrenceRule = RecurrenceRule.Interval(
                    unit = com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit.DAYS,
                    every = 1,
                    basis = RecurrenceBasis.SCHEDULED_DATE
                ),
                recurrenceEndAt = dueAt
            )
        )

        clock.now = at("2038-05-01T19:00", zone)
        val result = completeTask(repository, scheduler, clock, zone)(initialId)

        assertEquals(null, (result as CompleteTaskResult.Completed).nextOccurrence)
        assertEquals(
            1,
            withContext(Dispatchers.IO) { stateRule.database.taskDao().getAllTaskEntities().size }
        )
    }

    private fun assertExactRegisteredAlarms(vararg expected: Pair<Int, Long>) {
        val actual = stateRule.registeredReminders().groupBy(RegisteredAlarm::requestCode)
            .also { grouped -> assertTrue(grouped.values.all { it.size == 1 }) }
            .mapValues { (_, alarms) -> alarms.single() }
        assertEquals(expected.map { it.first }.toSet(), actual.keys)
        expected.forEach { (taskId, expectedTriggerAt) ->
            val alarm = requireNotNull(actual[taskId])
            assertTrue(abs(alarm.triggerAt - expectedTriggerAt) <= ALARM_TRIGGER_TOLERANCE_MILLIS)
        }
    }

    private fun repository() = OfflineTaskRepository(stateRule.database, stateRule.database.taskDao())

    private fun scheduler(
        repository: OfflineTaskRepository,
        clock: AppClock
    ) = AlarmManagerReminderScheduler(stateRule.alarmGateway(), repository, clock)

    private suspend fun save(
        repository: OfflineTaskRepository,
        scheduler: AlarmManagerReminderScheduler,
        clock: AppClock,
        task: Task
    ): Int = (SaveTask(
        repository = repository,
        scheduler = scheduler,
        validateTask = ValidateTask(),
        clock = clock,
        seriesIdFactory = { "advanced-recurrence-series" }
    )(task) as SaveTaskResult.Saved).taskId

    private fun completeTask(
        repository: OfflineTaskRepository,
        scheduler: AlarmManagerReminderScheduler,
        clock: AppClock,
        zone: ZoneId
    ): suspend (Int) -> CompleteTaskResult = CompleteTask(
        repository = repository,
        scheduler = scheduler,
        calculateNextOccurrence = CalculateNextOccurrence(ZoneIdProvider { zone }),
        clock = clock
    )::invoke

    private fun recurringTask(
        title: String,
        dueAt: Long,
        recurrenceRule: RecurrenceRule,
        recurrenceEndAt: Long? = null
    ) = Task(
        title = title,
        description = "Release evidence fixture",
        priority = TaskPriority.MEDIUM,
        dueAt = dueAt,
        recurrenceRule = recurrenceRule,
        recurrenceEndAt = recurrenceEndAt,
        createdAt = 0,
        updatedAt = 0
    )
}

private class MutableClock(var now: Long) : AppClock {
    override fun nowMillis(): Long = now
}

private fun at(value: String, zone: ZoneId): Long = LocalDateTime.parse(value)
    .atZone(zone)
    .toInstant()
    .toEpochMilli()

private fun localDateTime(value: Long?, zone: ZoneId): LocalDateTime =
    java.time.Instant.ofEpochMilli(requireNotNull(value)).atZone(zone).toLocalDateTime()

private class MemoryDocumentGateway : DocumentGateway {
    private val documents = mutableMapOf<String, ByteArray>()

    override suspend fun write(reference: DocumentReference, bytes: ByteArray) {
        documents[reference.value] = bytes.copyOf()
    }

    override suspend fun read(reference: DocumentReference, maxBytes: Long): ByteArray =
        requireNotNull(documents[reference.value]).copyOf()

    fun requireBytes(reference: DocumentReference): ByteArray =
        requireNotNull(documents[reference.value]).copyOf()
}

private class AdvancedRecurrenceStateRule : TestRule {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    val database: AppDatabase by lazy {
        EntryPointAccessors.fromApplication(context, DebugDatabaseEntryPoint::class.java).database()
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locale = localeManager.applicationLocales
            val notification = NotificationPermissionTestState.create()
            val permissionGranted = notification.isGranted
            val snapshot = snapshot()
            try {
                notification.force(true)
                localeManager.applicationLocales = LocaleList.forLanguageTags("en")
                clearFixtureState()
                base.evaluate()
            } finally {
                try {
                    restore(snapshot)
                } finally {
                    localeManager.applicationLocales = locale
                    notification.force(permissionGranted)
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                }
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
            alarm.packageName == context.packageName && alarm.receiverComponent in receiverNames
        }
    }

    fun alarmGateway(): AndroidAlarmGateway = AndroidAlarmGateway(
        context = context,
        alarmManager = context.getSystemService(AlarmManager::class.java)
    )

    private fun snapshot(): AdvancedRecurrenceSnapshot = runBlocking(Dispatchers.IO) {
        AdvancedRecurrenceSnapshot(
            categories = database.categoryDao().getAll(),
            tasks = database.taskDao().getAllTaskEntities(),
            subtasks = database.taskDao().getAllSubtaskEntities(),
            sequences = readSequences(),
            alarms = reminderMap()
        )
    }

    private fun clearFixtureState() {
        registeredReminders().forEach { alarmGateway().cancel(it.requestCode) }
        runBlocking(Dispatchers.IO) {
            database.clearAllTables()
            replaceSequences(SEQUENCE_TABLES.associateWith { null })
        }
    }

    private fun restore(snapshot: AdvancedRecurrenceSnapshot) {
        clearFixtureState()
        runBlocking(Dispatchers.IO) {
            if (snapshot.categories.isNotEmpty()) database.categoryDao().insertAll(snapshot.categories)
            if (snapshot.tasks.isNotEmpty()) database.taskDao().insertTasks(snapshot.tasks)
            if (snapshot.subtasks.isNotEmpty()) {
                database.taskDao().insertRestoredSubtasks(snapshot.subtasks)
            }
            replaceSequences(snapshot.sequences)
        }
        snapshot.alarms.values.forEach { alarm ->
            check(alarmGateway().setExact(alarm.requestCode, alarm.triggerAt) ||
                alarmGateway().setInexact(alarm.requestCode, alarm.triggerAt))
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
        val actualAlarms = reminderMap()
        assertEquals(snapshot.alarms.keys, actualAlarms.keys)
        snapshot.alarms.forEach { (requestCode, expected) ->
            val actual = requireNotNull(actualAlarms[requestCode])
            assertEquals(expected.type, actual.type)
            assertEquals(expected.packageName, actual.packageName)
            assertEquals(expected.receiverComponent, actual.receiverComponent)
            assertTrue(abs(expected.triggerAt - actual.triggerAt) <= ALARM_TRIGGER_TOLERANCE_MILLIS)
        }
    }

    private fun reminderMap(): Map<Int, RegisteredAlarm> = registeredReminders()
        .groupBy(RegisteredAlarm::requestCode)
        .also { grouped -> check(grouped.values.all { it.size == 1 }) }
        .mapValues { (_, alarms) -> alarms.single() }

    private fun readSequences(): Map<String, Long?> {
        val values = mutableMapOf<String, Long>()
        database.openHelper.writableDatabase.query(
            "SELECT name, seq FROM sqlite_sequence WHERE name IN ('categories', 'tasks', 'subtasks')"
        ).use { cursor ->
            while (cursor.moveToNext()) values[cursor.getString(0)] = cursor.getLong(1)
        }
        return SEQUENCE_TABLES.associateWith(values::get)
    }

    private fun replaceSequences(sequences: Map<String, Long?>) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.beginTransaction()
        try {
            SEQUENCE_TABLES.forEach { table ->
                sqlite.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(table))
                sequences[table]?.let { sequence ->
                    sqlite.execSQL(
                        "INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)",
                        arrayOf<Any>(table, sequence)
                    )
                }
            }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation()
        .uiAutomation
        .executeShellCommand(command)
        .use { descriptor -> FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() } }
}

private data class AdvancedRecurrenceSnapshot(
    val categories: List<CategoryEntity>,
    val tasks: List<TaskEntity>,
    val subtasks: List<SubtaskEntity>,
    val sequences: Map<String, Long?>,
    val alarms: Map<Int, RegisteredAlarm>
)

private val SEQUENCE_TABLES = listOf("categories", "tasks", "subtasks")
private const val ALARM_TRIGGER_TOLERANCE_MILLIS = 1_000L
