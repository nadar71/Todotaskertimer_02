# Advanced Recurrence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic, timezone-aware interval, selected-weekday, monthly-date, and monthly-ordinal recurrence with selectable scheduling basis, lossless backup v2, bilingual editor/parser support, and end-to-end verification.

**Architecture:** Replace the behavioral recurrence enum with validated typed domain rules while flattening rule parameters into explicit Room v3 columns. Keep recurrence calculation pure, completion orchestration in the existing use case/repository transaction, editor state unidirectional, and natural-language parsing deterministic and correctable.

**Tech Stack:** Kotlin, Java Time, coroutines/Flow, Room 2→3 migration, Hilt, Jetpack Compose Material 3, Navigation 3, kotlinx.serialization JSON, JUnit, Turbine, Compose UI tests, Android connected tests.

**Spec:** `docs/superpowers/specs/2026-08-26-advanced-recurrence-design.md`

## Global Constraints

- Stay in the single `:app` module and existing feature-first folders.
- Italian is the primary language; English is fully supported through Android native per-app language selection.
- Calendar rules default to `SCHEDULED_DATE`; interval rules default to `COMPLETION_DATE`; users may override either.
- Completing an overdue scheduled rule creates exactly one strictly future occurrence and no missed-occurrence backlog.
- Monthly date rules retain their original day anchor and clamp each short month independently.
- Support ordinals first, second, third, fourth, and last; do not support fifth.
- Interval and month counts are bounded to `1..999`.
- Backup exports v2 and imports both v1 and v2 using Replace All after full validation.
- Destructive Room migration, network parsing, RRULE, multiple rules, and future-series materialization are prohibited.
- Use TDD for every behavioral change and commit after every independently reviewable task.

---

### Task 1: Typed Recurrence Domain Contracts

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/RecurrenceRule.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/Task.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/ValidateTask.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/TaskSnapshotVersion.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/mapper/TaskEntityMapper.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModel.kt`
- Test: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain/RecurrenceRuleTest.kt`
- Test: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain/TaskLifecycleUseCasesTest.kt`

**Interfaces:**
- Consumes: `Task.dueAt`, `Task.recurrenceEndAt`, existing save validation and snapshot concurrency checks.
- Produces: `RecurrenceRule`, `IntervalUnit`, `RecurrenceBasis`, `MonthlyOrdinalValue`, and `Task.recurrenceRule: RecurrenceRule` for every later task.

- [ ] **Step 1: Write failing contract and validation tests**

Cover immutable weekday snapshots, `1..999` counts, anchor days `1..31`, supported ordinals, recurrence requiring a due date, end-before-due rejection, and recurrence fields participating in `snapshotVersion()`.

```kotlin
@Test fun selectedWeekdays_snapshotsInput() {
    val source = mutableSetOf(DayOfWeek.MONDAY)
    val rule = RecurrenceRule.SelectedWeekdays(source, RecurrenceBasis.SCHEDULED_DATE)
    source += DayOfWeek.FRIDAY
    assertEquals(setOf(DayOfWeek.MONDAY), rule.weekdays)
}

@Test fun activeRule_withoutDueDate_isRejected() {
    val errors = ValidateTask()(task(
        dueAt = null,
        recurrenceRule = RecurrenceRule.Interval(
            IntervalUnit.DAYS, 2, RecurrenceBasis.COMPLETION_DATE
        )
    ), now = 0L)
    assertTrue(TaskValidationError.RECURRENCE_WITHOUT_DUE_TIME in errors)
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecurrenceRuleTest' --tests '*TaskLifecycleUseCasesTest'`

Expected: compilation failures because typed recurrence contracts and `Task.recurrenceRule` do not exist.

- [ ] **Step 3: Implement the minimal typed contracts**

```kotlin
enum class RecurrenceBasis { SCHEDULED_DATE, COMPLETION_DATE }
enum class IntervalUnit { DAYS, WEEKS }
enum class MonthlyOrdinalValue { FIRST, SECOND, THIRD, FOURTH, LAST }

sealed interface RecurrenceRule {
    data object None : RecurrenceRule
    data class Interval(
        val unit: IntervalUnit,
        val every: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule
    data class SelectedWeekdays(
        private val weekdaySnapshot: Set<DayOfWeek>,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule {
        val weekdays: Set<DayOfWeek> get() = weekdaySnapshot
    }
    data class MonthlyDay(
        val anchorDay: Int,
        val everyMonths: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule
    data class MonthlyOrdinal(
        val ordinal: MonthlyOrdinalValue,
        val weekday: DayOfWeek,
        val everyMonths: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule
}
```

Use checked factory functions or `require` guards so invalid rules cannot enter normal domain flow. Change `Task.recurrence` to `Task.recurrenceRule` and update validation/snapshot call sites. Add narrowly scoped legacy adapters in `TaskEntityMapper` and the editor save/load mapping so the build remains green before Tasks 3 and 6; adapters support only `NONE`, one-day, one-week, and one-month rules and reject advanced persistence until Room v3 lands. `RecurrenceType` is temporary boundary compatibility, never the domain source of truth, and must be deleted after Tasks 6 and 7 migrate the final presentation/parser references. Run `rg -n "RecurrenceType|\.recurrence\b" app/src` at the end of Task 7 and require zero behavioral references.

- [ ] **Step 4: Run focused and full JVM tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecurrenceRuleTest' --tests '*TaskLifecycleUseCasesTest'`

Then run: `./gradlew :app:testDebugUnitTest`

Expected: PASS with legacy recurrence fixtures converted to equivalent typed rules.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: model typed recurrence rules"
```

---

### Task 2: Pure Next-occurrence Calculator

**Files:**
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/CalculateNextOccurrence.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/NextOccurrenceResult.kt`
- Replace tests: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain/CalculateNextOccurrenceTest.kt`

**Interfaces:**
- Consumes: typed rules from Task 1 and `ZoneIdProvider`.
- Produces: `CalculateNextOccurrence.invoke(task, completedAt, referenceAt): NextOccurrenceResult`, consumed by completion orchestration.

- [ ] **Step 1: Write the calculation matrix as failing tests**

Include day/week intervals for both bases; Monday/Wednesday/Friday selection; 28–31 monthly anchors; first through fourth and last weekday; leap years; Europe/Rome DST gap/overlap; overdue skip; inclusive recurrence-end boundary; overflow failure; and exactly one future result.

```kotlin
@Test fun overdueSelectedWeekdays_skipsMissedDates() {
    val task = task(
        dueAt = instant(2026, 8, 3, 9),
        recurrenceRule = RecurrenceRule.SelectedWeekdays(
            setOf(MONDAY, FRIDAY), SCHEDULED_DATE
        )
    )
    assertEquals(
        NextOccurrenceResult.Next(instant(2026, 8, 14, 9)),
        calculator(task, completedAt = instant(2026, 8, 12, 18), referenceAt = instant(2026, 8, 12, 18))
    )
}

@Test fun monthlyAnchor_recoversAfterFebruaryClamp() {
    val rule = RecurrenceRule.MonthlyDay(31, 1, SCHEDULED_DATE)
    assertEquals(instant(2025, 3, 31, 9), nextAfter(instant(2025, 2, 28, 9), rule))
}
```

- [ ] **Step 2: Run calculator tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*CalculateNextOccurrenceTest'`

Expected: failures for every advanced rule and new invocation contract.

- [ ] **Step 3: Implement calendar arithmetic**

```kotlin
sealed interface NextOccurrenceResult {
    data class Next(val dueAt: Long) : NextOccurrenceResult
    data object Ended : NextOccurrenceResult
    data class Invalid(val reason: Reason) : NextOccurrenceResult
    enum class Reason { MISSING_DUE_DATE, OVERFLOW, INVALID_RULE }
}

operator fun invoke(
    task: Task,
    completedAt: Long,
    referenceAt: Long,
): NextOccurrenceResult
```

Calculate in `ZonedDateTime`, advance calendar units rather than milliseconds, resolve selected weekdays in ISO terms, derive ordinal dates with `TemporalAdjusters`, and clamp `MonthlyDay` using `min(anchorDay, month.lengthOfMonth())`. Use bounded arithmetic or direct cycle jumps so an ancient overdue task cannot loop once per missed day.

- [ ] **Step 4: Run focused and full JVM tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*CalculateNextOccurrenceTest'`

Then run: `./gradlew :app:testDebugUnitTest`

Expected: PASS across all timezone and boundary matrices.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain
git commit -m "feat: calculate advanced recurrence"
```

---

### Task 3: Room v3 Migration And Mapping

**Files:**
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/core/database/DatabaseMigrations.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/core/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/TaskEntity.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/mapper/TaskEntityMapper.kt`
- Modify: `app/src/androidTest/java/com/indiewalkabout/nowdothis/MigrationTest.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/data/TaskEntityMapperTest.kt`
- Generate: `app/schemas/com.indiewalkabout.nowdothis.core.database.AppDatabase/3.json`

**Interfaces:**
- Consumes: Task 1 domain rules.
- Produces: Room v3 flattened recurrence columns and strict bidirectional mapping used by repositories and portability.

- [ ] **Step 1: Write failing mapper and migration tests**

Verify every rule round trips, malformed column combinations throw a controlled `InvalidRecurrenceRecord`, v2 legacy values convert correctly, `NONE` leaves parameters null, `MONTHLY` derives the anchor in `Europe/Rome`, and the full `1→2→3` path retains rows/subtasks/categories.

```kotlin
@Test fun migration2To3_convertsLegacyMonthlyUsingDueDay() {
    insertV2Task(recurrence = "MONTHLY", dueAt = romeInstant(2026, 1, 31, 9))
    migrateTo(3)
    assertColumn("recurrence_kind", "MONTHLY_DAY")
    assertColumn("recurrence_anchor_day", 31L)
    assertColumn("recurrence_basis", "SCHEDULED_DATE")
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run JVM mapper: `./gradlew :app:testDebugUnitTest --tests '*TaskEntityMapperTest'`

Run connected migration: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.MigrationTest`

Expected: missing v3 schema/columns and mapper failures.

- [ ] **Step 3: Implement v3 schema and mapper**

Add columns `recurrence_kind`, `recurrence_interval_unit`, `recurrence_interval_count`, `recurrence_basis`, `recurrence_weekday_mask`, `recurrence_anchor_day`, `recurrence_ordinal`, and `recurrence_ordinal_weekday`. Keep `recurrence_end_at`. Use nullable columns with Room defaults and one authoritative `recurrence_kind` discriminant. Register `MIGRATION_2_3` without changing `MIGRATION_1_2`.

Map weekday sets to a stable seven-bit ISO mask and validate unused parameters are null for each kind. For legacy `MONTHLY`, iterate affected rows inside `MIGRATION_2_3`, derive the anchor with `Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).dayOfMonth`, and update the new columns. This matches the legacy calculator's device-zone interpretation at upgrade time; pin the test process timezone in migration fixtures and restore it in `finally`.

- [ ] **Step 4: Generate schema and run persistence gates**

Run: `./gradlew :app:kspDebugKotlin :app:testDebugUnitTest --tests '*TaskEntityMapperTest'`

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.MigrationTest`

Expected: schema `3.json` generated and all mapper/migration paths pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest app/schemas
git commit -m "feat: persist advanced recurrence rules"
```

---

### Task 4: Atomic Completion And Reminder Continuity

**Files:**
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/CompleteTask.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/repository/TaskRepository.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/repository/OfflineTaskRepository.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain/TaskLifecycleUseCasesTest.kt`
- Modify: `app/src/androidTest/java/com/indiewalkabout/nowdothis/CoreTaskJourneyTest.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/quickcapture/domain/usecase/CompleteQuickCaptureTaskTest.kt`

**Interfaces:**
- Consumes: `NextOccurrenceResult` and Room v3 mapping.
- Produces: one atomic completed/current pair and zero-or-one next occurrence with reminder offset preserved.

- [ ] **Step 1: Write failing orchestration tests**

Cover scheduled versus completion basis, overdue skip, series ID stability, new task/subtask IDs, copied rule/end/category/priority, completed-state reset, reminder offset, end reached, invalid calculation rollback, concurrent snapshot mutation, scheduler failure, and Quick Capture delegation.

```kotlin
@Test fun completionDateInterval_anchorsToActualCompletionAndCopiesSeries() = runTest {
    val result = completeTask.invoke(taskId)
    val completed = assertIs<CompleteTaskResult.Completed>(result)
    assertEquals(completedAtPlusTwoDaysAtDueTime, completed.nextOccurrence?.dueAt)
    assertEquals(original.seriesId, completed.nextOccurrence?.seriesId)
}
```

- [ ] **Step 2: Run lifecycle tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskLifecycleUseCasesTest' --tests '*CompleteQuickCaptureTaskTest'`

Expected: advanced calculation inputs/results are not orchestrated.

- [ ] **Step 3: Implement atomic workflow changes**

Pass the transaction's completion timestamp as both `completedAt` and `referenceAt`. Treat `Ended` as a normal completion with no next task; treat `Invalid` as a domain failure that causes no repository mutation. Preserve the existing post-commit reminder reconciliation and optimistic snapshot checks. Derive the next reminder using the original reminder-to-due offset rather than adding elapsed milliseconds across DST.

- [ ] **Step 4: Run unit and connected lifecycle gates**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskLifecycleUseCasesTest' --tests '*CompleteQuickCaptureTaskTest'`

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.CoreTaskJourneyTest`

Expected: one committed next occurrence or none, with no orphan alarms.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: complete advanced recurring tasks"
```

---

### Task 5: Data Portability Backup v2

**Files:**
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/portability/domain/model/PlanningBackup.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/portability/data/serialization/BackupDocumentV2.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/portability/data/serialization/BackupCodec.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/portability/data/serialization/BackupValidator.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/portability/data/local/PlanningDataSource.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/portability/data/serialization/BackupCodecTest.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/portability/data/serialization/BackupValidatorTest.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/portability/data/repository/OfflinePortabilityRepositoryTest.kt`
- Modify: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/portability/DataPortabilityJourneyTest.kt`
- Modify: `docs/data-portability/backup-format-v1.md`
- Create: `docs/data-portability/backup-format-v2.md`

**Interfaces:**
- Consumes: typed domain rules and Room v3 fields.
- Produces: deterministic v2 export, version-dispatched v1/v2 import, and validated Replace All planning data.

- [ ] **Step 1: Write failing codec, validator, repository, and journey tests**

Test every rule shape, deterministic ordering, explicit null/default encoding, v1 legacy conversion, v2 round trip, version 3 rejection, invalid discriminants/parameters, truncated input, Replace All rollback, restored alarms, and restored SQLite sequences.

```kotlin
@Test fun decodeV1_weekly_convertsToTypedScheduledRule() {
    val backup = codec.decode(v1Fixture(recurrence = "WEEKLY"))
    assertEquals(
        RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, SCHEDULED_DATE),
        backup.tasks.single().recurrenceRule
    )
}
```

- [ ] **Step 2: Run portability tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupCodecTest' --tests '*BackupValidatorTest' --tests '*OfflinePortabilityRepositoryTest'`

Expected: export remains v1 and advanced rule shape is unavailable.

- [ ] **Step 3: Implement version-dispatched serialization**

Keep `BackupDocumentV1` decode-only and add `BackupDocumentV2` as the sole export shape. Read `BackupEnvelope` first, dispatch exactly on version 1 or 2, and set `SUPPORTED_VERSION = 2`. Represent recurrence with a serializable kind plus only the fields valid for that kind. Convert both document versions into one typed `PlanningBackup` before validation.

- [ ] **Step 4: Run portability unit and connected gates**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupCodecTest' --tests '*BackupValidatorTest' --tests '*OfflinePortabilityRepositoryTest' --tests '*RestoreBackupTest'`

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.portability.DataPortabilityJourneyTest`

Expected: v2 exports round trip, v1 imports convert, and invalid restores leave data/alarms untouched.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest docs/data-portability
git commit -m "feat: export recurrence in backup v2"
```

---

### Task 6: Recurrence Editor UDF And Localized Compose UI

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/RecurrenceEditorState.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/RecurrenceEditor.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorContract.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModel.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorScreenTest.kt`

**Interfaces:**
- Consumes: typed rules, validation errors, and repository save flow.
- Produces: `RecurrenceEditorState`, recurrence events, saved-state serialization, and a correctable bilingual editor.

- [ ] **Step 1: Write failing reducer, recreation, and Compose tests**

Cover each rule selector, correct default basis, explicit basis override, hidden-field clearing, weekday toggle, bounds `1..999`, monthly anchor, ordinal menus, due-required/end errors, invalid draft restoration, Italian/English resources, 48 dp targets, semantics, and 200 percent font geometry.

```kotlin
@Test fun selectingWeekdays_appliesScheduledDefaultAndClearsIntervalFields() {
    viewModel.onEvent(TaskEditorEvent.SelectRecurrenceKind(SELECTED_WEEKDAYS))
    val state = viewModel.uiState.value.recurrence
    assertEquals(SCHEDULED_DATE, state.basis)
    assertNull(state.intervalEvery)
}
```

- [ ] **Step 2: Run focused UI tests and verify RED**

Run JVM: `./gradlew :app:testDebugUnitTest --tests '*TaskEditorViewModelTest'`

Run Compose: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.task.presentation.editor.TaskEditorScreenTest`

Expected: advanced recurrence state, events, and controls are absent.

- [ ] **Step 3: Implement state, reducer, saved state, and UI**

Use a serializable editor-only draft with a rule-kind enum and nullable editing fields; map it to a validated domain rule only at the save boundary. Put the recurrence composable in `RecurrenceEditor.kt` to keep `TaskEditorScreen.kt` focused. Use weekday toggle chips, numeric input/steppers, menus, and a basis segmented control; render only relevant controls and stable test tags.

- [ ] **Step 4: Run ViewModel, Compose, and full JVM gates**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskEditorViewModelTest'`

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.task.presentation.editor.TaskEditorScreenTest`

Then run: `./gradlew :app:testDebugUnitTest`

Expected: UDF state, recreation, both locales, and large-font tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: edit advanced recurrence rules"
```

---

### Task 7: Deterministic Bilingual Recurrence Parsing

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/RecurrenceParser.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/AttributeParser.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/model/NaturalLanguageParseResult.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/usecase/ParseNaturalLanguageTask.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModel.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/RecurrenceParserTest.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/usecase/ParseNaturalLanguageTaskTest.kt`
- Modify: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1 rules and Task 6 correctable recurrence draft.
- Produces: one optional typed recurrence rule, owned source ranges, and explicit ambiguity issues.

- [ ] **Step 1: Write failing direct and end-to-end parser matrices**

Cover `every 2 weeks`, `ogni 3 giorni`, `every Monday and Friday`, `ogni lunedi e venerdi`, every supported monthly ordinal, `last Friday of every month`, `ultimo venerdi del mese`, explicit basis phrases, accents/case, punctuation boundaries, duplicates, contradictions, unsupported fifth ordinals, malformed continuations, and exact title preservation.

```kotlin
@Test fun italianSelectedWeekdays_returnsScheduledRuleAndExactRange() {
    val result = parser.parse(input("Palestra ogni lunedi e venerdi"))
    assertEquals(
        RecurrenceRule.SelectedWeekdays(setOf(MONDAY, FRIDAY), SCHEDULED_DATE),
        result.rule
    )
    assertEquals("Palestra", removeConsumed(result))
}

@Test fun malformedOrdinal_preservesWholeTitleAndAppliesNoRule() {
    val result = useCase(input("Report quinto lunedi del mese"))
    assertNull(result.draft.recurrenceRule)
    assertEquals("Report quinto lunedi del mese", result.draft.title)
}
```

- [ ] **Step 2: Run parser tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecurrenceParserTest' --tests '*ParseNaturalLanguageTaskTest'`

Expected: new grammar is not recognized and old enum result cannot represent it.

- [ ] **Step 3: Implement focused grammar and ownership**

Parse recurrence in a dedicated pure component returning rule candidates, source ranges, and issues. Require hard lexical boundaries, claim malformed recurrence-like phrases without consuming them, reject intersecting temporal/category ownership, and apply a rule only when exactly one internally valid candidate remains. The editor merges recognized recurrence as one correctable field.

- [ ] **Step 4: Run parser, editor, and full JVM gates**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecurrenceParserTest' --tests '*ParseNaturalLanguageTaskTest' --tests '*TaskEditorViewModelTest'`

Then run: `./gradlew :app:testDebugUnitTest`

Expected: all old grammar remains green and advanced grammar is deterministic in both languages.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: parse advanced recurrence phrases"
```

---

### Task 8: Critical Product Journey And Release Evidence

**Files:**
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/recurrence/AdvancedRecurrenceJourneyTest.kt`
- Modify: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/naturallanguage/NaturalLanguageEntryJourneyTest.kt`
- Modify: `docs/architecture/data-flow.md`
- Modify: `docs/quality/test-strategy.md`
- Modify: `docs/quality/accessibility-checklist.md`
- Modify: `docs/quality/verification-matrix.md`
- Modify: `docs/release/checklist.md`
- Modify: `docs/release/migration-and-rollback.md`
- Create: `docs/release/advanced-recurrence-evidence-2026-08-26.md`
- Modify: `docs/release/README.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: executable acceptance evidence for capture → parse → schedule → remind → complete → recur → export → restore.

- [ ] **Step 1: Write failing connected critical journeys**

Add independent fixtures for selected weekdays, monthly anchor recovery, completion-date intervals, recurrence-end stop, reminder rescheduling, v2 export/restore, Italian/English locale recreation, and the complete product journey. Snapshot and restore database rows, SQLite sequences, alarms, locale, and notification permission in `finally`.

```kotlin
@Test fun captureParseCompleteRecurExportRestore_preservesRuleAndAlarm() {
    // Launch new-task route, parse a bilingual fixture, save, complete,
    // assert one next task/alarm, export v2, mutate, restore, and assert exact state.
}
```

- [ ] **Step 2: Run journeys and verify RED before final wiring/evidence**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.recurrence.AdvancedRecurrenceJourneyTest`

Expected: the new journey class initially fails until all platform wiring and fixtures are complete.

- [ ] **Step 3: Complete platform wiring and documentation**

Fix only load-bearing integration gaps found by the journeys. Document the typed rule boundary, Room v3 migration, backup v2 compatibility, deterministic parser limits, DST/month-end trade-offs, test environments, exact commands, artifact hashes, and any pending physical-device accessibility checks. Do not claim evidence that was not executed.

- [ ] **Step 4: Run complete verification gates**

Run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease
./gradlew :app:minifyReleaseWithR8
git diff --check
```

Inspect the generated AAB for `classes.dex`, `resources.pb`, and baseline profile entries. Record exact JVM/connected counts, emulator API, locale/theme/font coverage, lint warning count, artifact SHA-256 values, and known manual limits.

- [ ] **Step 5: Request independent specification and code-quality review**

Review the complete branch against `docs/superpowers/specs/2026-08-26-advanced-recurrence-design.md`. Fix all P0/P1/P2 findings and any P3 that invalidates acceptance evidence, rerun affected and full gates, and obtain explicit approval.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest docs README.md CHANGELOG.md
git commit -m "docs: verify advanced recurrence"
```

---

## Final Acceptance Checklist

- [ ] Four advanced rule families and both recurrence bases are represented by typed domain rules.
- [ ] Overdue scheduled rules create one future occurrence and no backlog.
- [ ] Monthly day anchors recover after short-month clamping.
- [ ] DST and timezone tests prove local wall-clock preservation.
- [ ] Room v3 migrates legacy rows and the full `1→2→3` path without data loss.
- [ ] Completion, next occurrence, and reminder continuity are atomic and race-tested.
- [ ] Backup v2 round trips every rule and backup v1 remains importable.
- [ ] Editor UDF survives recreation and is localized/accessibility-tested at 200 percent font scale.
- [ ] Italian and English parser grammar is deterministic, bounded, and title-preserving on ambiguity.
- [ ] The critical product journey passes on the recorded emulator/device configuration.
- [ ] JVM, connected, lint, debug/release, AAB, R8, schema, and `git diff --check` gates pass.
- [ ] Independent review explicitly approves the final branch.
