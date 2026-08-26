package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.category.presentation.AndroidDefaultCategoryNameResolver
import com.indiewalkabout.nowdothis.feature.category.presentation.DefaultCategoryNameResolver
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.CategoryCandidate
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.model.ParserLanguage
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.AttributeParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.ReminderParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.parser.TemporalParser
import com.indiewalkabout.nowdothis.feature.naturallanguage.domain.usecase.ParseNaturalLanguageTask
import com.indiewalkabout.nowdothis.feature.naturallanguage.presentation.AndroidNaturalLanguageEnvironment
import com.indiewalkabout.nowdothis.feature.naturallanguage.presentation.NaturalLanguageEnvironment
import com.indiewalkabout.nowdothis.feature.naturallanguage.presentation.ParserEnvironment
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionDecision
import com.indiewalkabout.nowdothis.feature.task.domain.model.AtomicCompletionResult
import com.indiewalkabout.nowdothis.feature.task.domain.model.DeletedTaskSnapshot
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceType
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import com.indiewalkabout.nowdothis.feature.task.domain.model.ReminderStatus
import com.indiewalkabout.nowdothis.feature.task.domain.model.Subtask
import com.indiewalkabout.nowdothis.feature.task.domain.model.Task
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskFilter
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskPriority
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSnapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.model.snapshotVersion
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderPermissionChecker
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduleResult
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskRepository
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.SaveTask
import com.indiewalkabout.nowdothis.feature.task.domain.usecase.ValidateTask
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TaskEditorViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val clock = AppClock { 50_000L }
    private lateinit var repository: EditorTaskRepository
    private lateinit var categories: EditorCategoryRepository
    private lateinit var scheduler: EditorReminderScheduler
    private lateinit var permissions: EditorPermissionChecker
    private lateinit var naturalLanguageEnvironment: EditorNaturalLanguageEnvironment
    private val naturalLanguageParser = ParseNaturalLanguageTask(
        temporalParser = TemporalParser(),
        attributeParser = AttributeParser(),
        reminderParser = ReminderParser()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = EditorTaskRepository()
        categories = EditorCategoryRepository()
        scheduler = EditorReminderScheduler()
        permissions = EditorPermissionChecker()
        naturalLanguageEnvironment = EditorNaturalLanguageEnvironment()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newTask_usesInitialDateAndRestoredTitle() = runTest(dispatcher) {
        val viewModel = createViewModel(
            key = TaskEditorKey(taskId = null, initialDueAt = 90_000L),
            handle = SavedStateHandle(mapOf("title" to "Bozza"))
        )
        advanceUntilIdle()

        assertEquals("Bozza", viewModel.uiState.value.title)
        assertEquals(90_000L, viewModel.uiState.value.dueAt)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun quickEntryTyping_updatesRawInputWithoutParsing() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()

        viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("Buy milk tomorrow"))

        assertEquals("Buy milk tomorrow", viewModel.uiState.value.quickEntryInput)
        assertEquals("", viewModel.uiState.value.title)
        assertEquals(0, naturalLanguageEnvironment.snapshotCalls)
    }

    @Test
    fun quickEntryParse_waitsForFirstSuccessfulCategorySnapshot() = runTest(dispatcher) {
        val delayedCategories = MutableSharedFlow<List<Category>>()
        val home = category(8, customName = "Home")
        categories.observations += delayedCategories
        naturalLanguageEnvironment.parserEnvironment = ParserEnvironment(
            language = ParserLanguage.ENGLISH,
            nowEpochMillis = NOW,
            zoneId = ROME,
            categories = listOf(CategoryCandidate(8, "Home"))
        )
        val viewModel = createViewModel(TaskEditorKey(null, null))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(CategoryReadiness.LOADING, viewModel.uiState.value.categoryReadiness)
        viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("Task #Home"))
        viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)

        assertEquals(0, naturalLanguageEnvironment.snapshotCalls)
        assertEquals("", viewModel.uiState.value.title)
        assertNull(viewModel.uiState.value.categoryId)

        delayedCategories.emit(listOf(home))
        runCurrent()

        assertEquals(CategoryReadiness.READY, viewModel.uiState.value.categoryReadiness)
        viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)

        assertEquals(1, naturalLanguageEnvironment.snapshotCalls)
        assertEquals("Task", viewModel.uiState.value.title)
        assertEquals(8, viewModel.uiState.value.categoryId)
    }

    @Test
    fun categoryObservationFailure_exposesRetryAndRecoversBeforeParsing() =
        runTest(dispatcher) {
            val work = category(9, customName = "Work")
            categories.observations += flow { throw IllegalStateException("categories failed") }
            categories.observations += flowOf(listOf(work))
            naturalLanguageEnvironment.parserEnvironment = ParserEnvironment(
                language = ParserLanguage.ENGLISH,
                nowEpochMillis = NOW,
                zoneId = ROME,
                categories = listOf(CategoryCandidate(9, "Work"))
            )
            val viewModel = createViewModel(TaskEditorKey(null, null))
            val effect = async { viewModel.effects.first() }
            advanceUntilIdle()

            assertEquals(CategoryReadiness.ERROR, viewModel.uiState.value.categoryReadiness)
            assertEquals(
                TaskEditorEffect.ShowMessage(R.string.task_editor_load_failed),
                effect.await()
            )
            viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("Task #Work"))
            viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)
            assertEquals(0, naturalLanguageEnvironment.snapshotCalls)

            viewModel.onEvent(TaskEditorEvent.RetryCategoryLoad)
            advanceUntilIdle()

            assertEquals(2, categories.observeCalls)
            assertEquals(CategoryReadiness.READY, viewModel.uiState.value.categoryReadiness)
            assertEquals(listOf(work), viewModel.uiState.value.categories)
            viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)
            assertEquals("Task", viewModel.uiState.value.title)
            assertEquals(9, viewModel.uiState.value.categoryId)
        }

    @Test
    fun explicitQuickEntryParse_appliesAllFieldsInOneStateMutation() = runTest(dispatcher) {
        val home = category(8, customName = "Home")
        categories.categories.value = listOf(home)
        naturalLanguageEnvironment.parserEnvironment = ParserEnvironment(
            language = ParserLanguage.ENGLISH,
            nowEpochMillis = NOW,
            zoneId = ROME,
            categories = listOf(CategoryCandidate(8, "Home"))
        )
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Keep this description"))
        viewModel.onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(epoch("2026-09-30T18:00:00+02:00")))
        viewModel.onEvent(TaskEditorEvent.AddSubtask)
        val subtaskId = viewModel.uiState.value.subtasks.single().draftId
        viewModel.onEvent(TaskEditorEvent.RenameSubtask(subtaskId, "Keep this subtask"))
        viewModel.onEvent(
            TaskEditorEvent.UpdateQuickEntry(
                "Buy milk tomorrow at 6 pm #Home !high every week remind 30m before"
            )
        )
        val emissions = mutableListOf<TaskEditorUiState>()
        val effects = mutableListOf<TaskEditorEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { emissions += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        emissions.clear()

        viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)

        val state = viewModel.uiState.value
        assertEquals(1, emissions.size)
        assertEquals("Buy milk", state.title)
        assertEquals(epoch("2026-08-27T18:00:00+02:00"), state.dueAt)
        assertEquals(epoch("2026-08-27T17:30:00+02:00"), state.reminderAt)
        assertEquals(ReminderStatus.REQUESTED, state.reminderStatus)
        assertEquals(TaskPriority.HIGH, state.priority)
        assertEquals(8, state.categoryId)
        assertEquals(RecurrenceType.WEEKLY, state.recurrence)
        assertEquals("Keep this description", state.description)
        assertEquals(listOf("Keep this subtask"), state.subtasks.map { it.title })
        assertEquals(epoch("2026-09-30T18:00:00+02:00"), state.recurrenceEndAt)
        assertEquals(QuickEntrySummaryField.entries, state.quickEntrySummary)
        assertTrue(state.quickEntryIssues.isEmpty())
        assertEquals(listOf(home), naturalLanguageEnvironment.categorySnapshots.single())
        assertEquals(0, permissions.notificationChecks)
        assertEquals(0, permissions.exactAlarmChecks)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun quickEntryReparse_replacesOnlyRecognizedFields() = runTest(dispatcher) {
        naturalLanguageEnvironment.parserEnvironment = ParserEnvironment(
            language = ParserLanguage.ENGLISH,
            nowEpochMillis = NOW,
            zoneId = ROME,
            categories = listOf(CategoryCandidate(8, "Home"))
        )
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(
            TaskEditorEvent.UpdateQuickEntry(
                "Buy milk tomorrow at 6 pm #Home !high every week remind 30m before"
            )
        )
        viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("User description"))
        viewModel.onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(2_000_000_000_000L))
        viewModel.onEvent(TaskEditorEvent.AddSubtask)
        val subtaskId = viewModel.uiState.value.subtasks.single().draftId
        viewModel.onEvent(TaskEditorEvent.RenameSubtask(subtaskId, "User subtask"))
        val before = viewModel.uiState.value

        viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("!medium"))
        viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)

        val reparsed = viewModel.uiState.value
        assertEquals(before.title, reparsed.title)
        assertEquals(TaskPriority.MEDIUM, reparsed.priority)
        assertEquals(before.dueAt, reparsed.dueAt)
        assertEquals(before.reminderAt, reparsed.reminderAt)
        assertEquals(before.reminderStatus, reparsed.reminderStatus)
        assertEquals(before.categoryId, reparsed.categoryId)
        assertEquals(before.recurrence, reparsed.recurrence)
        assertEquals("User description", reparsed.description)
        assertEquals(2_000_000_000_000L, reparsed.recurrenceEndAt)
        assertEquals(listOf("User subtask"), reparsed.subtasks.map { it.title })
        assertEquals(listOf(QuickEntrySummaryField.PRIORITY), reparsed.quickEntrySummary)
    }

    @Test
    fun emptyQuickEntry_preservesDraftAndShowsTypedIssue() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Existing title"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Existing description"))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(90_000L))
        viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("  \n\t "))

        viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)

        val state = viewModel.uiState.value
        assertEquals("Existing title", state.title)
        assertEquals("Existing description", state.description)
        assertEquals(90_000L, state.dueAt)
        assertTrue(state.quickEntrySummary.isEmpty())
        assertEquals(listOf(QuickEntryIssue.EMPTY_INPUT), state.quickEntryIssues)
    }

    @Test
    fun quickEntryFailure_preservesDraftAndContainsFailureAsTypedIssue() =
        runTest(dispatcher) {
            naturalLanguageEnvironment.failure = IllegalStateException("parser boundary failed")
            val viewModel = createViewModel(TaskEditorKey(null, null))
            advanceUntilIdle()
            viewModel.onEvent(TaskEditorEvent.UpdateTitle("Existing title"))
            viewModel.onEvent(TaskEditorEvent.UpdateDescription("Existing description"))
            viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("Buy milk tomorrow"))

            viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)

            val state = viewModel.uiState.value
            assertEquals("Existing title", state.title)
            assertEquals("Existing description", state.description)
            assertEquals("Buy milk tomorrow", state.quickEntryInput)
            assertTrue(state.quickEntrySummary.isEmpty())
            assertEquals(listOf(QuickEntryIssue.PARSE_FAILED), state.quickEntryIssues)
        }

    @Test
    fun quickEntryCancellation_propagatesWithoutDraftOrSavedStateMutation() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val viewModel = createViewModel(TaskEditorKey(null, null), handle)
            advanceUntilIdle()
            viewModel.onEvent(TaskEditorEvent.UpdateTitle("Existing title"))
            viewModel.onEvent(TaskEditorEvent.UpdateDescription("Existing description"))
            viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("Buy milk tomorrow"))
            naturalLanguageEnvironment.failure = CancellationException("cancel parse")
            val beforeState = viewModel.uiState.value
            val beforeSavedState = handle.snapshotValues()

            assertThrows(CancellationException::class.java) {
                viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)
            }

            assertEquals(beforeState, viewModel.uiState.value)
            assertEquals(beforeSavedState, handle.snapshotValues())
            assertFalse(QuickEntryIssue.PARSE_FAILED in viewModel.uiState.value.quickEntryIssues)
            assertNull(repository.lastUpsert)
        }

    @Test
    fun existingTask_ignoresQuickEntryEvents() = runTest(dispatcher) {
        repository.emit(existingTask(title = "Canonical"))
        naturalLanguageEnvironment.failure = AssertionError("must not parse in edit mode")
        val viewModel = createViewModel(TaskEditorKey(7, null))
        advanceUntilIdle()
        val before = viewModel.uiState.value

        viewModel.onEvent(TaskEditorEvent.UpdateQuickEntry("Replace everything"))
        viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)

        assertEquals(before, viewModel.uiState.value)
        assertEquals(0, naturalLanguageEnvironment.snapshotCalls)
    }

    @Test
    fun quickEntryPresentation_restoresWithoutReparsingAndUsesStableValues() =
        runTest(dispatcher) {
            naturalLanguageEnvironment.parserEnvironment = ParserEnvironment(
                language = ParserLanguage.ENGLISH,
                nowEpochMillis = NOW,
                zoneId = ROME,
                categories = emptyList()
            )
            val handle = SavedStateHandle()
            val first = createViewModel(TaskEditorKey(null, null), handle)
            advanceUntilIdle()
            first.onEvent(TaskEditorEvent.UpdateQuickEntry("Buy milk tomorrow #Missing"))
            first.onEvent(TaskEditorEvent.ParseQuickEntry)

            assertEquals(
                "[\"TITLE\",\"DUE_DATE\"]",
                handle.get<String>("quick_entry_summary")
            )
            assertEquals(
                "[\"UNKNOWN_CATEGORY\"]",
                handle.get<String>("quick_entry_issues")
            )
            val restorationEnvironment = EditorNaturalLanguageEnvironment().apply {
                failure = AssertionError("restoration must not parse")
            }

            val restored = createViewModel(
                key = TaskEditorKey(null, null),
                handle = handle,
                environment = restorationEnvironment
            )
            advanceUntilIdle()

            val state = restored.uiState.value
            assertEquals("Buy milk tomorrow #Missing", state.quickEntryInput)
            assertEquals(
                listOf(QuickEntrySummaryField.TITLE, QuickEntrySummaryField.DUE_DATE),
                state.quickEntrySummary
            )
            assertEquals(listOf(QuickEntryIssue.UNKNOWN_CATEGORY), state.quickEntryIssues)
            assertEquals("Buy milk #Missing", state.title)
            assertEquals(epoch("2026-08-27T09:00:00+02:00"), state.dueAt)
            assertEquals(0, restorationEnvironment.snapshotCalls)
        }

    @Test
    fun androidEnvironment_snapshotsActiveLocaleTimeZoneAndResolvedCategoryNames() {
        val application = RuntimeEnvironment.getApplication()
        val context = EditorLocaleContext(application, "it-IT")
        var now = 10L
        var zone = ZoneId.of("Europe/Rome")
        var resolvedDefaultName = "Lavoro"
        val resolvedKeys = mutableListOf<DefaultCategoryKey>()
        val environment = AndroidNaturalLanguageEnvironment(
            context = context,
            clock = AppClock { now },
            zoneIdProvider = ZoneIdProvider { zone },
            defaultCategoryNameResolver = DefaultCategoryNameResolver { key ->
                resolvedKeys += key
                resolvedDefaultName
            }
        )
        val sourceCategories = listOf(
            category(1, customName = null, defaultKey = DefaultCategoryKey.WORK),
            category(2, customName = "Studio")
        )

        val italian = environment.snapshot(sourceCategories)

        assertEquals(ParserLanguage.ITALIAN, italian.language)
        assertEquals(10L, italian.nowEpochMillis)
        assertEquals(ZoneId.of("Europe/Rome"), italian.zoneId)
        assertEquals(
            listOf(CategoryCandidate(1, "Lavoro"), CategoryCandidate(2, "Studio")),
            italian.categories
        )

        now = 20L
        zone = ZoneId.of("America/New_York")
        resolvedDefaultName = "Work"
        context.languageTags = "en-US"
        val english = environment.snapshot(sourceCategories)

        assertEquals(ParserLanguage.ENGLISH, english.language)
        assertEquals(20L, english.nowEpochMillis)
        assertEquals(ZoneId.of("America/New_York"), english.zoneId)
        assertEquals(
            listOf(CategoryCandidate(1, "Work"), CategoryCandidate(2, "Studio")),
            english.categories
        )
        context.languageTags = "fr-FR"
        resolvedDefaultName = "Lavoro"
        val unsupported = environment.snapshot(sourceCategories)

        assertEquals(ParserLanguage.ITALIAN, unsupported.language)
        assertEquals(
            listOf(CategoryCandidate(1, "Lavoro"), CategoryCandidate(2, "Studio")),
            unsupported.categories
        )
        assertEquals(
            listOf(DefaultCategoryKey.WORK, DefaultCategoryKey.WORK, DefaultCategoryKey.WORK),
            resolvedKeys
        )
    }

    @Test
    fun androidEnvironment_classifiesExactNormalizedPrimaryLanguageSubtag() {
        val context = EditorLocaleContext(RuntimeEnvironment.getApplication(), "enx-PY")
        var defaultCategoryName = "Lavoro"
        val environment = AndroidNaturalLanguageEnvironment(
            context = context,
            clock = AppClock { 10L },
            zoneIdProvider = ZoneIdProvider { ROME },
            defaultCategoryNameResolver = DefaultCategoryNameResolver { defaultCategoryName }
        )
        val defaultCategory = category(
            id = 1,
            customName = null,
            defaultKey = DefaultCategoryKey.WORK
        )
        val cases = listOf(
            Triple("enx-PY", ParserLanguage.ITALIAN, "Lavoro"),
            Triple("EN-lATN-us", ParserLanguage.ENGLISH, "Work"),
            Triple("IT-lATN-it", ParserLanguage.ITALIAN, "Lavoro")
        )

        cases.forEach { (languageTags, expectedLanguage, expectedCategoryName) ->
            context.languageTags = languageTags
            defaultCategoryName = expectedCategoryName

            val snapshot = environment.snapshot(listOf(defaultCategory))

            assertEquals(languageTags, expectedLanguage, snapshot.language)
            assertEquals(
                languageTags,
                listOf(CategoryCandidate(1, expectedCategoryName)),
                snapshot.categories
            )
        }
    }

    @Test
    fun androidEnvironment_emptyLocaleTagsUseItalianParserAndCategoryNames() {
        val context = EditorEmptyLocaleContext(RuntimeEnvironment.getApplication())
        val environment = AndroidNaturalLanguageEnvironment(
            context = context,
            clock = AppClock { 10L },
            zoneIdProvider = ZoneIdProvider { ROME },
            defaultCategoryNameResolver = DefaultCategoryNameResolver { "Lavoro" }
        )

        val snapshot = environment.snapshot(
            listOf(category(1, customName = null, defaultKey = DefaultCategoryKey.WORK))
        )

        assertEquals(ParserLanguage.ITALIAN, snapshot.language)
        assertEquals(listOf(CategoryCandidate(1, "Lavoro")), snapshot.categories)
    }

    @Test
    fun androidEnvironment_usesFirstSupportedLocaleAndRenderedDefaultCategoryFallback() {
        val context = EditorLocaleContext(
            RuntimeEnvironment.getApplication(),
            "fr-CH,en-US"
        )
        val environment = AndroidNaturalLanguageEnvironment(
            context = context,
            clock = AppClock { 10L },
            zoneIdProvider = ZoneIdProvider { ROME },
            defaultCategoryNameResolver = AndroidDefaultCategoryNameResolver(context)
        )
        val defaultCategory = category(
            id = 1,
            customName = null,
            defaultKey = DefaultCategoryKey.WORK
        )

        val englishFallback = environment.snapshot(listOf(defaultCategory))

        assertEquals(ParserLanguage.ENGLISH, englishFallback.language)
        assertEquals(
            listOf(CategoryCandidate(1, context.getString(R.string.category_work))),
            englishFallback.categories
        )

        context.languageTags = "fr-CH,de-DE"
        val unsupportedOnly = environment.snapshot(listOf(defaultCategory))

        assertEquals(ParserLanguage.ITALIAN, unsupportedOnly.language)
        assertEquals(
            listOf(CategoryCandidate(1, context.getString(R.string.category_work))),
            unsupportedOnly.categories
        )
    }

    @Test
    fun existingTask_loadsOnceWithoutOverwritingEditedDraft() = runTest(dispatcher) {
        repository.emit(existingTask(title = "Original"))
        val viewModel = createViewModel(TaskEditorKey(taskId = 7, initialDueAt = null))
        advanceUntilIdle()
        assertEquals("Original", viewModel.uiState.value.title)

        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Edited"))
        repository.emit(existingTask(title = "Remote replacement"))
        advanceUntilIdle()

        assertEquals("Edited", viewModel.uiState.value.title)
    }

    @Test
    fun missingExistingTask_emitsMessageThenNavigateBack() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(taskId = 99, initialDueAt = null))
        val effects = async { List(2) { viewModel.effects.first() } }
        advanceUntilIdle()

        assertEquals(
            listOf(
                TaskEditorEffect.ShowMessage(R.string.task_editor_missing),
                TaskEditorEffect.NavigateBack
            ),
            effects.await()
        )
    }

    @Test
    fun draftAndSubtasks_restoreInEditedOrder() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = createViewModel(TaskEditorKey(null, 80_000L), handle)
        advanceUntilIdle()
        first.onEvent(TaskEditorEvent.UpdateTitle("Draft"))
        first.onEvent(TaskEditorEvent.UpdateDescription("Details"))
        first.onEvent(TaskEditorEvent.SelectPriority(TaskPriority.HIGH))
        first.onEvent(TaskEditorEvent.AddSubtask)
        first.onEvent(TaskEditorEvent.AddSubtask)
        val initial = first.uiState.value.subtasks
        first.onEvent(TaskEditorEvent.RenameSubtask(initial[0].draftId, "First"))
        first.onEvent(TaskEditorEvent.RenameSubtask(initial[1].draftId, "Second"))
        first.onEvent(TaskEditorEvent.ToggleSubtask(initial[1].draftId))
        first.onEvent(TaskEditorEvent.MoveSubtask(initial[1].draftId, -1))

        val restored = createViewModel(TaskEditorKey(null, null), handle)
        advanceUntilIdle()

        assertEquals("Draft", restored.uiState.value.title)
        assertEquals("Details", restored.uiState.value.description)
        assertEquals(TaskPriority.HIGH, restored.uiState.value.priority)
        assertEquals(listOf("Second", "First"), restored.uiState.value.subtasks.map { it.title })
        assertTrue(restored.uiState.value.subtasks.first().isCompleted)
    }

    @Test
    fun manualReminder_defersAccessChecksAndEffectsUntilSave() = runTest(dispatcher) {
        permissions.notificationRequired = true
        permissions.exactAlarmRequired = true
        val viewModel = createViewModel(TaskEditorKey(null, null))
        val effects = mutableListOf<TaskEditorEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        advanceUntilIdle()

        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(70_000L))
        advanceUntilIdle()

        assertEquals(0, permissions.notificationChecks)
        assertEquals(0, permissions.exactAlarmChecks)
        assertTrue(effects.isEmpty())

        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Valid"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Description"))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(80_000L))
        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        assertEquals(
            listOf(TaskEditorEffect.RequestNotificationPermission),
            effects
        )
        assertEquals(1, permissions.notificationChecks)
        assertEquals(0, permissions.exactAlarmChecks)
        assertTrue(viewModel.uiState.value.isSaving)
        assertNull(repository.lastUpsert)
    }

    @Test
    fun parsedReminder_notificationDenialKeepsEditorOpenWithoutPersistingOrNavigating() =
        runTest(dispatcher) {
            permissions.notificationRequired = true
            naturalLanguageEnvironment.parserEnvironment = ParserEnvironment(
                language = ParserLanguage.ENGLISH,
                nowEpochMillis = NOW,
                zoneId = ROME,
                categories = emptyList()
            )
            val viewModel = createViewModel(TaskEditorKey(null, null))
            val effects = mutableListOf<TaskEditorEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect { effects += it }
            }
            advanceUntilIdle()
            viewModel.onEvent(
                TaskEditorEvent.UpdateQuickEntry(
                    "Buy milk tomorrow at 18 remind 1h before"
                )
            )
            viewModel.onEvent(TaskEditorEvent.ParseQuickEntry)
            viewModel.onEvent(TaskEditorEvent.UpdateDescription("Description"))

            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals(listOf(TaskEditorEffect.RequestNotificationPermission), effects)
            assertTrue(viewModel.uiState.value.isSaving)
            assertNull(repository.lastUpsert)

            viewModel.onEvent(TaskEditorEvent.NotificationPermissionResult(granted = false))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.notificationPermissionDenied)
            assertEquals(ReminderStatus.REQUESTED, viewModel.uiState.value.reminderStatus)
            assertNull(repository.lastUpsert)
            assertFalse(TaskEditorEffect.NavigateBack in effects)
        }

    @Test
    fun manualReminder_notificationGrantContinuesPendingSaveAndNavigates() =
        runTest(dispatcher) {
            permissions.notificationRequired = true
            val viewModel = validViewModel(reminderAt = 70_000L, dueAt = 80_000L)
            val effects = mutableListOf<TaskEditorEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect { effects += it }
            }

            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals(listOf(TaskEditorEffect.RequestNotificationPermission), effects)
            assertNull(repository.lastUpsert)
            permissions.notificationRequired = false

            viewModel.onEvent(TaskEditorEvent.NotificationPermissionResult(granted = true))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    TaskEditorEffect.RequestNotificationPermission,
                    TaskEditorEffect.NavigateBack
                ),
                effects
            )
            assertFalse(viewModel.uiState.value.notificationPermissionDenied)
            assertEquals(70_000L, repository.lastUpsert?.reminderAt)
            assertEquals(ReminderStatus.SCHEDULED, viewModel.uiState.value.reminderStatus)
        }

    @Test
    fun exactAlarmUnavailable_afterAccessResultUsesInexactFallbackAndKeepsVisibleState() =
        runTest(dispatcher) {
            permissions.exactAlarmRequired = true
            scheduler.result = ReminderScheduleResult.INEXACT
            val viewModel = validViewModel(reminderAt = 70_000L, dueAt = 80_000L)
            val effects = mutableListOf<TaskEditorEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect { effects += it }
            }

            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals(listOf(TaskEditorEffect.RequestExactAlarmAccess), effects)
            assertTrue(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.exactTimingUnavailable)
            assertNull(repository.lastUpsert)

            viewModel.onEvent(TaskEditorEvent.RefreshExactAlarmAccess)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    TaskEditorEffect.RequestExactAlarmAccess,
                    TaskEditorEffect.NavigateBack
                ),
                effects
            )
            assertTrue(viewModel.uiState.value.exactTimingUnavailable)
            assertEquals(ReminderStatus.SCHEDULED, viewModel.uiState.value.reminderStatus)
            assertEquals(listOf(70_000L), scheduler.scheduledTriggers)
        }

    @Test
    fun permissionResultsWithoutPendingSave_updateVisibleAccessStateOnly() = runTest(dispatcher) {
        permissions.notificationRequired = true
        permissions.exactAlarmRequired = true
        val handle = SavedStateHandle()
        val viewModel = createViewModel(TaskEditorKey(null, null), handle)
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(70_000L))

        viewModel.onEvent(TaskEditorEvent.NotificationPermissionResult(granted = false))
        viewModel.onEvent(TaskEditorEvent.RefreshExactAlarmAccess)
        assertEquals(70_000L, viewModel.uiState.value.reminderAt)
        assertTrue(viewModel.uiState.value.notificationPermissionDenied)
        assertTrue(viewModel.uiState.value.exactTimingUnavailable)

        permissions.exactAlarmRequired = false
        viewModel.onEvent(TaskEditorEvent.RefreshExactAlarmAccess)
        assertFalse(viewModel.uiState.value.exactTimingUnavailable)

        val restored = createViewModel(TaskEditorKey(null, null), handle)
        advanceUntilIdle()
        assertTrue(restored.uiState.value.notificationPermissionDenied)
        assertFalse(restored.uiState.value.exactTimingUnavailable)
    }

    @Test
    fun draftChangingEvents_areRejectedWhileSaveIsSuspended() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val saveGate = CompletableDeferred<Unit>()
        repository.upsertGate = saveGate
        val viewModel = createViewModel(TaskEditorKey(null, null), handle)
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Draft A"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Description A"))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(90_000L))
        viewModel.onEvent(TaskEditorEvent.AddSubtask)
        viewModel.onEvent(TaskEditorEvent.AddSubtask)
        val subtaskIds = viewModel.uiState.value.subtasks.map(TaskEditorSubtask::draftId)
        viewModel.onEvent(TaskEditorEvent.RenameSubtask(subtaskIds[0], "First"))
        viewModel.onEvent(TaskEditorEvent.RenameSubtask(subtaskIds[1], "Second"))

        viewModel.onEvent(TaskEditorEvent.Save)
        runCurrent()
        val savingState = viewModel.uiState.value
        val savedStateDuringSave = handle.snapshotValues()
        assertTrue(savingState.isSaving)
        assertEquals(1, repository.upsertCalls)

        listOf(
            TaskEditorEvent.UpdateQuickEntry("Draft B tomorrow"),
            TaskEditorEvent.ParseQuickEntry,
            TaskEditorEvent.UpdateTitle("Draft B"),
            TaskEditorEvent.UpdateDescription("Description B"),
            TaskEditorEvent.SelectPriority(TaskPriority.HIGH),
            TaskEditorEvent.SelectCategory(99),
            TaskEditorEvent.UpdateDueAt(100_000L),
            TaskEditorEvent.UpdateReminderAt(80_000L),
            TaskEditorEvent.SelectRecurrence(RecurrenceType.WEEKLY),
            TaskEditorEvent.UpdateRecurrenceEndAt(200_000L),
            TaskEditorEvent.AddSubtask,
            TaskEditorEvent.RenameSubtask(subtaskIds[0], "Changed"),
            TaskEditorEvent.ToggleSubtask(subtaskIds[0]),
            TaskEditorEvent.MoveSubtask(subtaskIds[0], 1),
            TaskEditorEvent.DeleteSubtask(subtaskIds[1])
        ).forEach(viewModel::onEvent)

        assertEquals(savingState, viewModel.uiState.value)
        assertEquals(savedStateDuringSave, handle.snapshotValues())

        saveGate.complete(Unit)
        advanceUntilIdle()

        val saved = requireNotNull(repository.lastUpsert)
        assertEquals("Draft A", saved.title)
        assertEquals("Description A", saved.description)
        assertEquals(TaskPriority.LOW, saved.priority)
        assertNull(saved.categoryId)
        assertEquals(90_000L, saved.dueAt)
        assertNull(saved.reminderAt)
        assertEquals(RecurrenceRule.None, saved.recurrenceRule)
        assertEquals(listOf("First", "Second"), saved.subtasks.map(Subtask::title))
    }

    @Test
    fun successfulSave_mapsEntireDraftAndNavigatesBack() = runTest(dispatcher) {
        categories.categories.value = listOf(category(3))
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Plan"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Quarter"))
        viewModel.onEvent(TaskEditorEvent.SelectPriority(TaskPriority.HIGH))
        viewModel.onEvent(TaskEditorEvent.SelectCategory(3))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(90_000L))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(80_000L))
        viewModel.onEvent(TaskEditorEvent.SelectRecurrence(RecurrenceType.WEEKLY))
        viewModel.onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(190_000L))
        viewModel.onEvent(TaskEditorEvent.AddSubtask)
        val subtaskId = viewModel.uiState.value.subtasks.single().draftId
        viewModel.onEvent(TaskEditorEvent.RenameSubtask(subtaskId, "Outline"))
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        val saved = requireNotNull(repository.lastUpsert)
        assertEquals("Plan", saved.title)
        assertEquals("Quarter", saved.description)
        assertEquals(TaskPriority.HIGH, saved.priority)
        assertEquals(3, saved.categoryId)
        assertEquals(90_000L, saved.dueAt)
        assertEquals(80_000L, saved.reminderAt)
        assertEquals(
            RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE),
            saved.recurrenceRule
        )
        assertEquals(190_000L, saved.recurrenceEndAt)
        assertEquals(listOf("Outline"), saved.subtasks.map { it.title })
        assertEquals(listOf(0), saved.subtasks.map { it.position })
        assertEquals(TaskEditorEffect.NavigateBack, effect.await())
    }

    @Test
    fun monthlyRuleWithDifferentAnchor_isRejectedAtTheEditorLoadBoundary() = runTest(dispatcher) {
        val dueDay = java.time.Instant.ofEpochMilli(90_000L)
            .atZone(ZoneId.systemDefault())
            .dayOfMonth
        repository.emit(
            existingTask(title = "Advanced monthly").copy(
                recurrenceRule = RecurrenceRule.MonthlyDay(
                    anchorDay = if (dueDay == 31) 30 else 31,
                    everyMonths = 1,
                    basis = RecurrenceBasis.SCHEDULED_DATE
                )
            )
        )
        val effect = async { createViewModel(TaskEditorKey(7, null)).effects.first() }

        assertEquals(TaskEditorEffect.ShowMessage(R.string.task_editor_load_failed), effect.await())
    }

    @Test
    fun recurrenceEndWithoutRule_mapsToAnInlineEditorError() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Plan"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Quarter"))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(70_000L))
        viewModel.onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(80_000L))

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        assertEquals(
            TaskEditorFieldError.END_WITHOUT_RECURRENCE,
            viewModel.uiState.value.errors.recurrenceEnd
        )
        assertNull(repository.lastUpsert)
    }

    @Test
    fun invalidSave_mapsEveryValidationErrorInline() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(70_000L))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(80_000L))
        viewModel.onEvent(TaskEditorEvent.SelectRecurrence(RecurrenceType.DAILY))
        viewModel.onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(60_000L))

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        val errors = viewModel.uiState.value.errors
        assertEquals(TaskEditorFieldError.REQUIRED, errors.title)
        assertEquals(TaskEditorFieldError.REQUIRED, errors.description)
        assertEquals(TaskEditorFieldError.REMINDER_AFTER_DUE, errors.reminder)
        assertEquals(TaskEditorFieldError.END_BEFORE_DUE, errors.recurrenceEnd)

        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(null))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(40_000L))
        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()
        assertEquals(TaskEditorFieldError.DUE_REQUIRED, viewModel.uiState.value.errors.recurrence)
        assertEquals(TaskEditorFieldError.REMINDER_IN_PAST, viewModel.uiState.value.errors.reminder)
    }

    @Test
    fun subtaskDeleteAndMove_keepStableDraftIds() = runTest(dispatcher) {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        advanceUntilIdle()
        repeat(3) { viewModel.onEvent(TaskEditorEvent.AddSubtask) }
        val ids = viewModel.uiState.value.subtasks.map { it.draftId }

        viewModel.onEvent(TaskEditorEvent.MoveSubtask(ids[2], -1))
        viewModel.onEvent(TaskEditorEvent.DeleteSubtask(ids[0]))

        assertEquals(listOf(ids[2], ids[1]), viewModel.uiState.value.subtasks.map { it.draftId })
    }

    @Test
    fun clearingReminder_clearsStaleDeliveryStatus() = runTest(dispatcher) {
        repository.emit(
            existingTask(title = "Reminder").copy(reminderStatus = ReminderStatus.UNAVAILABLE)
        )
        val viewModel = createViewModel(TaskEditorKey(7, null))
        advanceUntilIdle()

        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(null))

        assertNull(viewModel.uiState.value.reminderAt)
        assertEquals(ReminderStatus.NONE, viewModel.uiState.value.reminderStatus)

        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(85_000L))

        assertEquals(ReminderStatus.REQUESTED, viewModel.uiState.value.reminderStatus)
    }

    @Test
    fun selectingNoRecurrence_clearsHiddenEndDateAndRestoresClearedState() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            repository.emit(existingTask(title = "Recurring"))
            val viewModel = createViewModel(TaskEditorKey(7, null), handle)
            advanceUntilIdle()

            viewModel.onEvent(TaskEditorEvent.SelectRecurrence(RecurrenceType.NONE))
            val restored = createViewModel(TaskEditorKey(7, null), handle)
            advanceUntilIdle()

            assertEquals(RecurrenceType.NONE, restored.uiState.value.recurrence)
            assertNull(restored.uiState.value.recurrenceEndAt)
        }

    @Test
    fun persistenceFailure_preservesDraftAndEmitsRetryableMessage() = runTest(dispatcher) {
        repository.upsertFailure = IllegalStateException("database unavailable")
        val viewModel = validViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        assertEquals("Valid", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(TaskEditorEffect.ShowMessage(R.string.task_editor_save_failed), effect.await())
    }

    @Test
    fun optimisticConflict_preservesDraftAndEmitsRetryableMessageWithoutNavigation() =
        runTest(dispatcher) {
            repository.emit(existingTask(title = "Canonical"))
            repository.rejectConditionalUpdate = true
            val viewModel = createViewModel(TaskEditorKey(7, null))
            advanceUntilIdle()
            viewModel.onEvent(TaskEditorEvent.UpdateTitle("Local draft"))
            val effect = async { viewModel.effects.first() }

            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals("Local draft", viewModel.uiState.value.title)
            assertFalse(viewModel.uiState.value.isSaving)
            assertNull(repository.lastUpsert)
            assertEquals(
                TaskEditorEffect.ShowMessage(R.string.task_editor_save_failed),
                effect.await()
            )
        }

    @Test
    fun recreatedStaleDraft_keepsOriginalVersionAndConflictsAfterWidgetCompletion() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val original = existingTask(title = "Canonical")
            repository.emit(original)
            val first = createViewModel(TaskEditorKey(7, null), handle)
            advanceUntilIdle()
            first.onEvent(TaskEditorEvent.UpdateTitle("Stale draft"))

            repository.emit(
                original.copy(
                    isCompleted = true,
                    completedAt = 50_000L,
                    updatedAt = 50_000L
                )
            )
            val recreated = createViewModel(TaskEditorKey(7, null), handle)
            advanceUntilIdle()
            val effect = async { recreated.effects.first() }

            recreated.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals("Stale draft", recreated.uiState.value.title)
            assertFalse(recreated.uiState.value.isSaving)
            assertNull(repository.lastUpsert)
            assertEquals(
                TaskEditorEffect.ShowMessage(R.string.task_editor_save_failed),
                effect.await()
            )
            val canonical = requireNotNull(repository.getTask(7))
            assertEquals("Canonical", canonical.title)
            assertTrue(canonical.isCompleted)
        }

    @Test
    fun unavailableReminder_keepsEditorOpenForRetry() = runTest(dispatcher) {
        scheduler.result = ReminderScheduleResult.FAILED
        val viewModel = validViewModel(reminderAt = 70_000L, dueAt = 80_000L)
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(TaskEditorEvent.Save)
        advanceUntilIdle()

        assertEquals(ReminderStatus.UNAVAILABLE, viewModel.uiState.value.reminderStatus)
        assertEquals(
            TaskEditorEffect.ShowMessage(R.string.task_editor_reminder_unavailable),
            effect.await()
        )
        assertEquals("Valid", viewModel.uiState.value.title)
    }

    @Test
    fun unavailableReminder_retryUsesSavedVersionAndCanNavigateAfterSuccess() =
        runTest(dispatcher) {
            repository.emit(existingTask(title = "Reminder retry"))
            scheduler.result = ReminderScheduleResult.FAILED
            val viewModel = createViewModel(TaskEditorKey(7, null))
            advanceUntilIdle()
            val unavailable = async { viewModel.effects.first() }

            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()
            assertEquals(
                TaskEditorEffect.ShowMessage(R.string.task_editor_reminder_unavailable),
                unavailable.await()
            )

            scheduler.result = ReminderScheduleResult.EXACT
            val success = async { viewModel.effects.first() }
            viewModel.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals(TaskEditorEffect.NavigateBack, success.await())
        }

    @Test
    fun savedCreateWithUnavailableReminder_recreatesAndRetriesExistingTask() =
        runTest(dispatcher) {
            scheduler.result = ReminderScheduleResult.FAILED
            val originalHandle = SavedStateHandle()
            val first = createViewModel(TaskEditorKey(null, null), originalHandle)
            advanceUntilIdle()
            first.onEvent(TaskEditorEvent.UpdateTitle("Created task"))
            first.onEvent(TaskEditorEvent.UpdateDescription("Created description"))
            first.onEvent(TaskEditorEvent.UpdateDueAt(80_000L))
            first.onEvent(TaskEditorEvent.UpdateReminderAt(70_000L))
            val unavailable = async { first.effects.first() }

            first.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals(
                TaskEditorEffect.ShowMessage(R.string.task_editor_reminder_unavailable),
                unavailable.await()
            )
            assertEquals(40, first.uiState.value.taskId)
            val immediatelySavedState = first.uiState.value
            naturalLanguageEnvironment.failure = AssertionError("saved create must not parse")
            first.onEvent(TaskEditorEvent.UpdateQuickEntry("must be ignored"))
            first.onEvent(TaskEditorEvent.ParseQuickEntry)
            assertEquals(immediatelySavedState, first.uiState.value)
            assertEquals(0, naturalLanguageEnvironment.snapshotCalls)

            val recreatedEnvironment = EditorNaturalLanguageEnvironment().apply {
                failure = AssertionError("recreated saved task must not parse")
            }
            val recreated = createViewModel(
                key = TaskEditorKey(null, null),
                handle = originalHandle.freshProcessSnapshot(),
                environment = recreatedEnvironment
            )
            advanceUntilIdle()

            assertEquals(40, recreated.uiState.value.taskId)
            val restoredState = recreated.uiState.value
            recreated.onEvent(TaskEditorEvent.UpdateQuickEntry("still ignored"))
            recreated.onEvent(TaskEditorEvent.ParseQuickEntry)
            assertEquals(restoredState, recreated.uiState.value)
            assertEquals(0, recreatedEnvironment.snapshotCalls)

            scheduler.result = ReminderScheduleResult.EXACT
            recreated.onEvent(TaskEditorEvent.UpdateTitle("Retried existing task"))
            val success = async { recreated.effects.first() }
            recreated.onEvent(TaskEditorEvent.Save)
            advanceUntilIdle()

            assertEquals(TaskEditorEffect.NavigateBack, success.await())
            assertEquals(1, repository.upsertCalls)
            assertEquals(1, repository.conditionalUpdateCalls)
            assertEquals(40, repository.lastExpectedVersion?.id)
            assertEquals(40, repository.lastUpsert?.id)
            assertEquals("Retried existing task", repository.lastUpsert?.title)
        }

    private suspend fun validViewModel(
        reminderAt: Long? = null,
        dueAt: Long? = null
    ): TaskEditorViewModel {
        val viewModel = createViewModel(TaskEditorKey(null, null))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(TaskEditorEvent.UpdateTitle("Valid"))
        viewModel.onEvent(TaskEditorEvent.UpdateDescription("Description"))
        viewModel.onEvent(TaskEditorEvent.UpdateDueAt(dueAt))
        viewModel.onEvent(TaskEditorEvent.UpdateReminderAt(reminderAt))
        return viewModel
    }

    private fun createViewModel(
        key: TaskEditorKey,
        handle: SavedStateHandle = SavedStateHandle(),
        environment: NaturalLanguageEnvironment = naturalLanguageEnvironment
    ) = TaskEditorViewModel(
        key = key,
        savedStateHandle = handle,
        taskRepository = repository,
        categoryRepository = categories,
        saveTask = SaveTask(repository, scheduler, ValidateTask(), clock) { "series" },
        permissionChecker = permissions,
        clock = clock,
        parseNaturalLanguageTask = naturalLanguageParser,
        naturalLanguageEnvironment = environment
    )

    private fun existingTask(title: String) = Task(
        id = 7,
        title = title,
        description = "Existing description",
        priority = TaskPriority.MEDIUM,
        categoryId = 3,
        dueAt = 90_000L,
        reminderAt = 80_000L,
        recurrenceRule = RecurrenceRule.MonthlyDay(
            java.time.Instant.ofEpochMilli(90_000L)
                .atZone(ZoneId.systemDefault())
                .dayOfMonth,
            1,
            RecurrenceBasis.SCHEDULED_DATE
        ),
        recurrenceEndAt = 190_000L,
        createdAt = 10L,
        updatedAt = 20L,
        subtasks = listOf(Subtask(4, 7, "Existing subtask", false, null, 0))
    )

    private fun epoch(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    private companion object {
        val ROME: ZoneId = ZoneId.of("Europe/Rome")
        val NOW: Long = ZonedDateTime.parse("2026-08-26T10:15:00+02:00")
            .toInstant()
            .toEpochMilli()
    }
}

private fun SavedStateHandle.freshProcessSnapshot(): SavedStateHandle = SavedStateHandle(
    keys().associateWith { key -> get<Any?>(key) }
)

private fun SavedStateHandle.snapshotValues(): Map<String, Any?> =
    keys().associateWith { key -> get<Any?>(key) }

private class EditorNaturalLanguageEnvironment : NaturalLanguageEnvironment {
    var parserEnvironment = ParserEnvironment(
        language = ParserLanguage.ENGLISH,
        nowEpochMillis = 0L,
        zoneId = ZoneId.of("UTC"),
        categories = emptyList()
    )
    var failure: Throwable? = null
    var snapshotCalls = 0
    val categorySnapshots = mutableListOf<List<Category>>()

    override fun snapshot(categories: List<Category>): ParserEnvironment {
        snapshotCalls += 1
        categorySnapshots += categories.toList()
        failure?.let { throw it }
        return parserEnvironment
    }
}

private class EditorLocaleContext(
    base: Context,
    var languageTags: String
) : ContextWrapper(base) {
    override fun getResources(): Resources {
        val configuration = Configuration(baseContext.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(languageTags))
        }
        return baseContext.createConfigurationContext(configuration).resources
    }
}

private class EditorEmptyLocaleContext(base: Context) : ContextWrapper(base) {
    private val emptyConfiguration = Configuration(base.resources.configuration).apply {
        setLocales(LocaleList.getEmptyLocaleList())
    }

    @Suppress("DEPRECATION")
    private val emptyLocaleResources = object : Resources(
        base.assets,
        base.resources.displayMetrics,
        emptyConfiguration
    ) {
        override fun getConfiguration(): Configuration = emptyConfiguration
    }

    override fun getResources(): Resources = emptyLocaleResources
}

private class EditorPermissionChecker : ReminderPermissionChecker {
    var notificationRequired = false
    var exactAlarmRequired = false
    var notificationChecks = 0
    var exactAlarmChecks = 0

    override fun needsNotificationPermission(): Boolean {
        notificationChecks += 1
        return notificationRequired
    }

    override fun needsExactAlarmAccess(): Boolean {
        exactAlarmChecks += 1
        return exactAlarmRequired
    }
}

private class EditorReminderScheduler : ReminderScheduler {
    var result = ReminderScheduleResult.EXACT
    val scheduledTriggers = mutableListOf<Long>()
    override suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult {
        scheduledTriggers += triggerAt
        return result
    }
    override suspend fun cancel(taskId: Int) = Unit
    override suspend fun reconcile() = Unit
}

private class EditorCategoryRepository : CategoryRepository {
    val categories = MutableStateFlow(emptyList<Category>())
    val observations = ArrayDeque<Flow<List<Category>>>()
    var observeCalls = 0

    override fun observeAll(): Flow<List<Category>> {
        observeCalls += 1
        return if (observations.isEmpty()) categories else observations.removeFirst()
    }
    override suspend fun create(name: String, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun rename(id: Int, name: String) = CategoryMutationResult.Success
    override suspend fun recolor(id: Int, color: CategoryColor) = CategoryMutationResult.Success
    override suspend fun reorder(orderedIds: List<Int>) = CategoryMutationResult.Success
    override suspend fun delete(id: Int) = CategoryMutationResult.Success
}

private class EditorTaskRepository : TaskRepository {
    private val observed = mutableMapOf<Int, MutableStateFlow<Task?>>()
    var lastUpsert: Task? = null
    var upsertFailure: Throwable? = null
    var rejectConditionalUpdate = false
    var upsertCalls = 0
    var conditionalUpdateCalls = 0
    var lastExpectedVersion: TaskSnapshotVersion? = null
    var upsertGate: CompletableDeferred<Unit>? = null
    private var nextId = 40

    fun emit(task: Task) {
        observed.getOrPut(task.id) { MutableStateFlow(null) }.value = task
    }

    override fun observeTask(taskId: Int): Flow<Task?> =
        observed.getOrPut(taskId) { MutableStateFlow(null) }

    override fun observeSections(filter: TaskFilter, bounds: com.indiewalkabout.nowdothis.core.time.DayBounds): Flow<TaskSections> =
        MutableStateFlow(TaskSections())

    override suspend fun getTask(taskId: Int): Task? = observed[taskId]?.value

    override suspend fun upsert(task: Task): Int {
        upsertCalls += 1
        upsertGate?.await()
        upsertFailure?.let { throw it }
        val id = task.id.takeIf { it != 0 } ?: nextId++
        val persisted = task.copy(id = id)
        lastUpsert = persisted
        emit(persisted)
        return id
    }

    override suspend fun updateIfUnchanged(
        task: Task,
        expectedVersion: TaskSnapshotVersion
    ): Boolean {
        conditionalUpdateCalls += 1
        lastExpectedVersion = expectedVersion
        if (rejectConditionalUpdate) return false
        val current = observed[task.id]?.value ?: return false
        if (current.snapshotVersion() != expectedVersion) return false
        lastUpsert = task
        emit(task)
        return true
    }

    override suspend fun completeAtomically(
        taskId: Int,
        completedAt: Long,
        completionDecision: (Task, Long) -> AtomicCompletionDecision
    ): AtomicCompletionResult = AtomicCompletionResult.NotFound

    override suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot = error("unused")
    override suspend fun deleteAll(): List<Int> = emptyList()
    override suspend fun restore(snapshot: DeletedTaskSnapshot): Int = snapshot.task.id
    override suspend fun deleteCompleted(taskId: Int) = Unit
    override suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus) {
        observed[taskId]?.value?.let { emit(it.copy(reminderStatus = status)) }
    }
    override suspend fun updateReminderStatusIfCurrent(
        expectedVersion: TaskSnapshotVersion,
        status: ReminderStatus
    ): Boolean = false
    override suspend fun futureReminders(after: Long): List<Task> = emptyList()
}

private fun category(
    id: Int,
    customName: String? = "Category $id",
    defaultKey: DefaultCategoryKey? = null
) = Category(
    id = id,
    customName = customName,
    defaultKey = defaultKey,
    color = CategoryColor.BLUE,
    position = 0,
    createdAt = 1
)
