# Natural-Language Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit, deterministic, offline Italian/English Quick entry parser that previews inferred task fields in the existing new-task editor before the normal save flow.

**Architecture:** A feature-first `naturallanguage` slice owns framework-free parser models, small ordered parser stages, and orchestration. Android locale/default-category adaptation stays at the presentation/DI edge; `TaskEditorViewModel` atomically maps recognized fields onto its existing draft, while existing validation, repositories, Room, reminder scheduling, and Navigation 3 remain authoritative.

**Tech Stack:** Kotlin 2.2, Java Time with core-library desugaring, coroutines/Flow, Hilt, Jetpack Compose Material 3, Navigation 3, SavedStateHandle, JUnit 4, Robolectric, AndroidX Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-08-26-natural-language-entry-design.md`

## Global Constraints

- Parsing is deterministic, offline, and supports Italian as the primary language plus English through Android native per-app language selection.
- Quick entry is visible only for new tasks and runs only after an explicit Parse command.
- Parsing never saves, creates categories, requests reminder permissions, or bypasses the required description.
- Date-only input resolves to 09:00 in the injected local time zone.
- Invalid or unsupported syntax remains in the title; ambiguous input is visible and recoverable.
- Re-parsing changes only fields explicitly recognized in the latest input.
- No Room migration, network/AI/NLP dependency, automatic typing parser, edit-task parser, optional-description change, subtask parsing, or advanced recurrence is allowed.
- All production and test edits follow the existing feature-first MVVM/Clean Architecture conventions and preserve cancellation.

---

## File Structure

Create focused parser files rather than one monolithic regex class:

- `feature/naturallanguage/domain/model/NaturalLanguageInput.kt`: immutable parser input and category candidate.
- `feature/naturallanguage/domain/model/NaturalLanguageParseResult.kt`: parsed draft, recognized fields, issues, language, and source range model.
- `feature/naturallanguage/domain/parser/TextNormalizer.kt`: whitespace, diacritic/case normalization, quoted markers, and consumed-range title reconstruction.
- `feature/naturallanguage/domain/parser/TemporalParser.kt`: bilingual date/time parsing and DST-safe local resolution.
- `feature/naturallanguage/domain/parser/AttributeParser.kt`: priority, category, and recurrence parsing.
- `feature/naturallanguage/domain/parser/ReminderParser.kt`: absolute and due-relative reminder parsing.
- `feature/naturallanguage/domain/usecase/ParseNaturalLanguageTask.kt`: ordered orchestration, precedence, dependencies, and final result.
- `feature/naturallanguage/presentation/NaturalLanguageEnvironment.kt`: active parser language and localized default-category display names at the Android boundary.
- `feature/naturallanguage/di/NaturalLanguageModule.kt`: Hilt construction of the parser use case and Android environment.
- Existing task editor contract/ViewModel/screen/route: Quick entry state, events, restoration, rendering, and result mapping only.

### Task 1: Parser Contracts, Normalization, And Title Preservation

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/model/NaturalLanguageInput.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/model/NaturalLanguageParseResult.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/TextNormalizer.kt`
- Test: `app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/TextNormalizerTest.kt`

**Interfaces:**
- Produces: `ParserLanguage`, `NaturalLanguageInput`, `CategoryCandidate`, `ParsedTaskDraft`, `RecognizedField`, `ParseIssue`, `NaturalLanguageParseResult`, `SourceMatch`, and `TextNormalizer.remainingTitle(raw, consumed)`.
- Consumes: existing `TaskPriority` and `RecurrenceType` domain enums only.

- [ ] **Step 1: Write failing contract and normalizer tests**

Cover whitespace normalization, overlapping/adjacent consumed ranges, quoted `#"Progetti Casa"`, punctuation preservation, diacritic-insensitive matching keys, and the rule that unsuccessful markers stay in the title. Use assertions shaped like:

```kotlin
@Test fun remainingTitle_removesOnlySuccessfulRanges() {
    val raw = "  Compra latte, domani alle 18  #Sconosciuta "
    val consumed = listOf(SourceMatch(16, 23, RecognizedField.DUE_DATE))
    assertEquals(
        "Compra latte, alle 18 #Sconosciuta",
        TextNormalizer.remainingTitle(raw, consumed)
    )
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*TextNormalizerTest'`

Expected: compilation fails because the parser contracts do not exist.

- [ ] **Step 3: Implement immutable contracts and range-safe normalization**

Use typed, framework-free values. `ParsedTaskDraft` must distinguish “not recognized” from recognized nullable data by pairing values with `Set<RecognizedField>`:

```kotlin
data class ParsedTaskDraft(
    val title: String?,
    val dueAt: Long?,
    val reminderAt: Long?,
    val priority: TaskPriority?,
    val categoryId: Int?,
    val recurrence: RecurrenceType?
)

data class NaturalLanguageParseResult(
    val draft: ParsedTaskDraft,
    val recognized: Set<RecognizedField>,
    val issues: List<ParseIssue>,
    val consumed: List<SourceMatch>
)
```

Reject invalid or overlapping consumed ranges in an internal canonicalization function; never remove text for an unrecognized token.

- [ ] **Step 4: Run focused tests and static checks**

Run: `./gradlew :app:testDebugUnitTest --tests '*TextNormalizerTest' && ./gradlew :app:compileDebugKotlin`

Expected: all focused tests pass.

- [ ] **Step 5: Commit Task 1**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage
git commit -m "feat: add natural language parser contracts"
```

### Task 2: Deterministic Bilingual Date And Time Parsing

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/TemporalParser.kt`
- Test: `app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/TemporalParserTest.kt`

**Interfaces:**
- Consumes: `NaturalLanguageInput`, `ParserLanguage`, `SourceMatch`, and `ParseIssue` from Task 1.
- Produces: `TemporalParse(dueAt: Long?, matches: List<SourceMatch>, issues: List<ParseIssue>)` and `TemporalParser.parse(input)`.

- [ ] **Step 1: Write a parameterized RED matrix**

Test `oggi/ today`, `domani/tomorrow`, Italian `dd/MM[/yyyy]`, English `MM/dd[/yyyy]`, `alle 18`, `at 6 pm`, date-only 09:00, explicit-time-without-date using the current local date, month/year rollover, invalid dates, DST gap resolution, and DST overlap determinism. Inject fixed `nowEpochMillis` and `ZoneId` in every case.

```kotlin
@Test fun tomorrowWithoutTime_defaultsToNineInRome() {
    val result = parser.parse(input("Compra latte domani", ITALIAN, ROME, NOW))
    assertEquals(epoch("2026-08-27T09:00:00+02:00"), result.dueAt)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*TemporalParserTest'`

Expected: compilation fails for missing `TemporalParser`.

- [ ] **Step 3: Implement date/time lexicons and DST policy**

Resolve a local date/time through `ZoneRules.getValidOffsets(localDateTime)`: use the sole valid offset normally, shift forward by the transition duration for gaps, and use the earlier offset for overlaps. Use strict numeric-date parsing and explicit language-specific month/day order. Return source matches only for valid recognized expressions.

- [ ] **Step 4: Run temporal and contract tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*TemporalParserTest' --tests '*TextNormalizerTest'`

Expected: all tests pass without reading the system clock or zone.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/TemporalParser.kt app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/TemporalParserTest.kt
git commit -m "feat: parse bilingual task dates and times"
```

### Task 3: Attributes, Reminder Dependencies, And Parser Orchestration

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/AttributeParser.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/parser/ReminderParser.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/usecase/ParseNaturalLanguageTask.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/di/NaturalLanguageModule.kt`
- Test: `app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage/domain/usecase/ParseNaturalLanguageTaskTest.kt`

**Interfaces:**
- Consumes: contracts and `TemporalParser` from Tasks 1-2.
- Produces: `AttributeParser.parse(input)`, `ReminderParser.parse(input, dueAt)`, and `ParseNaturalLanguageTask.invoke(input): NaturalLanguageParseResult` injectable through Hilt.

- [ ] **Step 1: Write failing end-to-end parser tests**

Use table-driven Italian/English examples covering `!alta/!high`, all priorities, custom and localized default category candidates, quoted category names, accent/case normalization, ambiguous/unknown categories, daily/weekly/monthly recurrence, absolute reminders, `1h/30m before`, duplicate last-valid-wins precedence, relative reminder without due date, and complete title reconstruction.

```kotlin
@Test fun italianHybridInput_returnsCorrectedTypedDraft() {
    val result = parse(input("Compra latte domani alle 18 #Casa !alta ogni settimana promemoria 1h prima"))
    assertEquals(TaskPriority.HIGH, result.draft.priority)
    assertEquals(7, result.draft.categoryId)
    assertEquals(RecurrenceType.WEEKLY, result.draft.recurrence)
    assertEquals(result.draft.dueAt!! - 3_600_000, result.draft.reminderAt)
    assertEquals("Compra latte", result.draft.title)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ParseNaturalLanguageTaskTest'`

Expected: compilation fails for missing stages/use case.

- [ ] **Step 3: Implement ordered orchestration**

Parse reminder syntax before general temporal syntax, combine nonoverlapping valid matches, resolve duplicate fields by last explicit source position, and append a typed duplicate issue. Unknown or ambiguous category markers and dependency-invalid reminders must not be consumed. Build the title after all successful stages. Keep all parsers stateless.

- [ ] **Step 4: Provide parser graph through Hilt**

`NaturalLanguageModule` provides the stateless stages and use case without singleton mutable state:

```kotlin
@Provides fun provideParser() = ParseNaturalLanguageTask(
    temporalParser = TemporalParser(),
    attributeParser = AttributeParser(),
    reminderParser = ReminderParser()
)
```

- [ ] **Step 5: Run parser suite and compile**

Run: `./gradlew :app:testDebugUnitTest --tests '*naturallanguage*' && ./gradlew :app:hiltJavaCompileDebug`

Expected: all parser tests and Hilt compilation pass.

- [ ] **Step 6: Commit Task 3**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage app/src/test/java/com/indiewalkabout/nowdothis/feature/naturallanguage
git commit -m "feat: orchestrate bilingual natural language parsing"
```

### Task 4: Android Environment And Process-Restored Editor Integration

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/presentation/NaturalLanguageEnvironment.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage/di/NaturalLanguageModule.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorContract.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModel.kt`
- Test: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `ParseNaturalLanguageTask`, `AppClock`, `ZoneIdProvider`, current categories, and existing `DefaultCategoryNameResolver`.
- Produces: `NaturalLanguageEnvironment.snapshot(categories): ParserEnvironment`, `TaskEditorEvent.UpdateQuickEntry`, `TaskEditorEvent.ParseQuickEntry`, and serializable presentation-only `QuickEntryIssue`/`QuickEntrySummaryField` state.

- [ ] **Step 1: Write failing ViewModel tests**

Add tests for create-only availability; explicit parse; no parse while typing; atomic selective replacement; unchanged description/subtasks/unrecognized fields; no permission effects; empty input; contained parser failure; Italian/English environment snapshots; and process recreation restoring raw input/result/issues without re-parsing. Include a test proving edit mode rejects/ignores Quick entry events.

- [ ] **Step 2: Verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskEditorViewModelTest'`

Expected: new Quick entry state/event assertions fail.

- [ ] **Step 3: Implement Android environment adaptation**

`AndroidNaturalLanguageEnvironment` reads the active application locale at call time, maps language tags beginning with `it` to `ITALIAN` and all supported English tags to `ENGLISH`, obtains `clock.nowMillis()` and `zoneIdProvider.zoneId()`, and resolves default category names with `DefaultCategoryNameResolver`. Custom names pass through unchanged.

- [ ] **Step 4: Extend editor UDF state and persistence**

Add raw input, recognized summary fields, and typed issue keys to `TaskEditorUiState`; persist them with `SavedStateHandle` using stable enum names/serialization. Parsing remains synchronous pure work and does not add a progress flag. `ParseQuickEntry` computes a complete result first and applies it in one `_uiState.update`. Apply title only when nonblank and other values only when their `RecognizedField` is present. Direct parsed reminder assignment must not call `updateReminder()` or emit permission effects.

- [ ] **Step 5: Run ViewModel and full JVM tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskEditorViewModelTest' && ./gradlew :app:testDebugUnitTest`

Expected: all tests pass; existing optimistic draft-version restoration remains intact.

- [ ] **Step 6: Commit Task 4**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/naturallanguage app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor app/src/test/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModelTest.kt
git commit -m "feat: integrate natural language editor state"
```

### Task 5: Localized Accessible Quick Entry UI

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/QuickEntrySection.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorScreenTest.kt`

**Interfaces:**
- Consumes: Quick entry state and events from Task 4.
- Produces: create-only `QuickEntrySection`, stable test tags `quick-entry-input`, `quick-entry-parse`, `quick-entry-summary`, and `quick-entry-issues`.

- [ ] **Step 1: Write failing Compose tests**

Cover create-mode visibility, edit-mode absence, input dispatch, explicit Parse dispatch, disabled Parse for blank input, localized Italian/default and English resources, summary/issue semantics, 48 dp action target, and a 200% font-scale screenshot/layout assertion without overlap or clipped text.

- [ ] **Step 2: Verify RED on API 36**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.task.presentation.editor.TaskEditorScreenTest`

Expected: tests fail because the section/tags do not exist.

- [ ] **Step 3: Implement the compact editor section**

Place `QuickEntrySection` as the first `LazyColumn` item only when `state.taskId == null`. Use a multiline `OutlinedTextField`, a Material icon plus localized Parse label, restrained body text for summaries/issues, no nested card, minimum 48 dp command target, and stable dimensions that tolerate 200% font scale. Do not show instructional feature prose beyond the field hint and actual parse feedback.

- [ ] **Step 4: Add complete Italian/English resources**

Add labels, hint, Parse command, empty/failure messages, issue text for duplicate/unknown/ambiguous/dependency-invalid input, and recognized-field labels. Verify resource keys exist in both locales and Italian remains in default `values`.

- [ ] **Step 5: Run focused connected and lint checks**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.task.presentation.editor.TaskEditorScreenTest
./gradlew :app:lintDebug
```

Expected: focused UI tests pass and lint has zero errors.

- [ ] **Step 6: Commit Task 5**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor app/src/main/res app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorScreenTest.kt
git commit -m "feat: add localized quick entry editor UI"
```

### Task 6: Connected Product Journey, Release Evidence, And Final Gates

**Files:**
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/naturallanguage/NaturalLanguageEntryJourneyTest.kt`
- Create: `docs/release/natural-language-entry-evidence-2026-08-26.md`
- Modify: `docs/quality/test-strategy.md`
- Modify: `docs/quality/verification-matrix.md`
- Modify: `docs/quality/accessibility-checklist.md`
- Modify: `docs/architecture/data-flow.md`
- Modify: `docs/architecture/README.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: completed parser/editor feature from Tasks 1-5 and existing real Room/reminder infrastructure.
- Produces: retained bilingual journey coverage and auditable release evidence.

- [ ] **Step 1: Write the failing connected journeys**

Using the production activity/navigation and real Room repositories, cover Italian and English:

```text
open new task -> enter hybrid phrase -> Parse -> verify inferred controls
-> correct one inferred value -> add required description -> Save
-> verify persisted title/due/priority/category/recurrence/reminder
```

Also recreate the activity after parsing and prove the same preview is restored without a changed relative date.

- [ ] **Step 2: Run the new journey and inspect any contract gap**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.naturallanguage.NaturalLanguageEntryJourneyTest`

Expected: the journey either passes against the completed production flow or fails at a specific production contract assertion; no test-only parser or persistence path is acceptable.

- [ ] **Step 3: Close any production contract gap and make the journey GREEN**

Use existing test fixtures and repository APIs; do not add a test-only parser or persistence path. Ensure locale switching uses Android's per-app locale mechanism and resets after each test.

- [ ] **Step 4: Run complete verification**

Run:

```bash
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease
./gradlew :app:assembleRelease -Pandroid.enableR8.fullMode=true
git diff --check
```

Inspect test XML totals, release APK/AAB presence, R8 mapping, generated locale config, merged manifest, and both locale resource sets. Record exact commands, counts, device/API, artifact sizes/hashes, lint warnings, and any explicitly pending physical-device accessibility checks.

- [ ] **Step 5: Update architecture, quality, release, and showcase docs**

Document the pure parser boundary, editor UDF flow, deterministic/offline trade-off, grammar limits, test matrices, and the critical Product Value journey. Do not claim physical-device TalkBack validation unless it was actually performed.

- [ ] **Step 6: Commit Task 6**

```bash
git add app/src/androidTest docs README.md CHANGELOG.md
git commit -m "docs: verify natural language entry"
```

- [ ] **Step 7: Request whole-branch review and close findings**

Generate a review package from the `develop` fork point to `HEAD`. Dispatch an independent architecture/code-quality reviewer, fix every load-bearing finding with focused regressions, rerun affected plus complete gates, and obtain explicit approval before declaring the phase complete.
