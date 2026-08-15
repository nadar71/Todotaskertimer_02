# Quick Capture Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a responsive Jetpack Glance home-screen widget that shows relevant pending tasks, opens the existing editor, and completes tasks through existing domain behavior even when the app process was absent.

**Architecture:** Add a `feature/quickcapture` vertical slice inside `:app`. A framework-free projection consumes existing task sections; Glance renders fresh Room data and delegates completion to `CompleteTask`; a process-lifetime coordinator observes Room only to invalidate widgets. Navigation remains in `MainActivity` and Navigation 3.

**Tech Stack:** Kotlin, coroutines/Flow, Room, Hilt entry points, Jetpack Glance AppWidget `1.1.1`, Navigation 3, JUnit, Android instrumentation, Macrobenchmark/UiAutomator.

## Global Constraints

- Follow `docs/superpowers/specs/2026-08-16-quick-capture-design.md` exactly.
- Keep the single `:app` production module and feature-first Clean MVVM boundaries.
- Use stable `androidx.glance:glance-appwidget:1.1.1`; do not adopt `1.2.0-rc01` or `1.3.0-alpha`.
- Room remains the only persisted source of truth; do not add tables, migrations, widget task caches, or preference snapshots.
- Order pending tasks overdue, today, upcoming; exclude completed and unscheduled tasks; capacities are exactly 3, 5, and 8.
- Add and Open Task must use the existing task editor. Do not add inline/title-only entry or change task validation.
- Completion must invoke `CompleteTask`; do not duplicate Room, recurrence, subtask, or reminder behavior.
- Italian is the base language and English lives under `values-en`; support system light/dark appearance and accessible labels.
- No app-authored WorkManager worker, foreground service, widget configuration activity, undo notification, new Gradle module, network, or account behavior.
- Glance may internally schedule its own worker; that framework implementation detail does not violate the WorkManager non-goal.
- Use TDD, commit each task, and run `git diff --check` before every production commit.

---

### Task 1: Framework-Free Widget Snapshot

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/domain/model/QuickCaptureSnapshot.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/domain/repository/QuickCaptureTaskSource.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/domain/usecase/LoadQuickCaptureTasks.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/data/TaskSectionsQuickCaptureSource.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/quickcapture/domain/usecase/LoadQuickCaptureTasksTest.kt`

**Interfaces:**

```kotlin
enum class QuickCaptureDueState { OVERDUE, TODAY, UPCOMING }

data class QuickCaptureTask(
    val id: Int,
    val title: String,
    val dueAt: Long,
    val dueState: QuickCaptureDueState
)

data class QuickCaptureSnapshot(val tasks: List<QuickCaptureTask>)

fun interface QuickCaptureTaskSource {
    fun observe(): Flow<TaskSections>
}

class LoadQuickCaptureTasks(
    private val source: QuickCaptureTaskSource
) {
    fun observe(capacity: Int): Flow<QuickCaptureSnapshot>
    suspend operator fun invoke(capacity: Int): QuickCaptureSnapshot
}
```

- [ ] Write failing tests with a fake `QuickCaptureTaskSource` flow proving overdue/today/upcoming concatenation, existing per-section order, completed/unscheduled exclusion, exact capacities 3/5/8, empty snapshots, refreshed `first()` reads, and rejection of capacities outside `1..8`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*LoadQuickCaptureTasksTest'` and confirm failure because the types do not exist.
- [ ] Implement `TaskSectionsQuickCaptureSource.observe()` as `observeTaskSections(TaskFilter())`; implement `LoadQuickCaptureTasks.observe(capacity)` as `source.observe().map { sections -> ... }`, defensively filter `!isCompleted`, require non-null `dueAt`, assign due state from the source section, and `take(capacity)`.
- [ ] Implement `invoke(capacity)` as a fresh `observe(capacity).first()` call so each widget render recalculates `DayBounds` through `ObserveTaskSections`.
- [ ] Run the focused test and complete `./gradlew :app:testDebugUnitTest`; run `git diff --check`.
- [ ] Commit `feat: add quick capture task snapshot`.

---

### Task 2: Widget Editor Navigation Contract

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/app/navigation/TaskEditorRequest.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetIntents.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/app/MainActivity.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/app/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/app/navigation/AppNavigator.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetIntentsTest.kt`
- Modify: matching `MainActivity`, `AppNavigation`, and `AppNavigator` tests.

**Interfaces:**

```kotlin
sealed interface TaskEditorRequest {
    data object Add : TaskEditorRequest
    data class Open(val taskId: Int) : TaskEditorRequest
}

object QuickCaptureWidgetIntents {
    const val ACTION_ADD = "com.indiewalkabout.nowdothis.action.QUICK_CAPTURE_ADD"
    const val ACTION_OPEN = "com.indiewalkabout.nowdothis.action.QUICK_CAPTURE_OPEN"
    const val EXTRA_TASK_ID = "quick_capture_task_id"
    fun add(context: Context): Intent
    fun open(context: Context, taskId: Int): Intent
    fun parse(intent: Intent?): TaskEditorRequest?
}
```

- [ ] Write failing parser tests for Add, positive Open ID, missing/non-positive ID rejection, unrelated action rejection, explicit `MainActivity` component, `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`, and unique data URIs `nowdothis://quick-capture/add` and `nowdothis://quick-capture/task/{id}`.
- [ ] Write failing navigation tests proving `TaskEditorRequest.Add` pushes `TaskEditorKey(null, null)`, Open pushes `TaskEditorKey(id, null)`, requests work on cold launch and `onNewIntent`, and consumed intents are cleared so recomposition/recreation cannot replay them.
- [ ] Run focused tests and confirm the new contract fails before implementation.
- [ ] Replace the `Flow<Int>` activity-to-navigation channel with buffered `Flow<TaskEditorRequest>` while preserving reminder notification parsing as `TaskEditorRequest.Open(id)`.
- [ ] Add `AppNavigator.openNewTask()` and reuse existing `openTask(id)`; consume both request variants in one lifecycle-aware Navigation 3 effect.
- [ ] Run focused tests, complete JVM tests, compile/Hilt, and `git diff --check`.
- [ ] Commit `feat: route quick capture widget intents`.

---

### Task 3: Responsive Glance Content And Resources

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetState.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetContent.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetTheme.kt`
- Create: `app/src/main/res/xml/quick_capture_widget_info.xml`
- Create: `app/src/main/res/layout/quick_capture_widget_loading.xml`
- Create: `app/src/main/res/layout/quick_capture_widget_error.xml`
- Create: widget drawables and light/dark color resources under `res/drawable`, `res/values`, and `res/values-night`.
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetContentTest.kt`

**Interfaces:**

```kotlin
sealed interface QuickCaptureWidgetState {
    data object Loading : QuickCaptureWidgetState
    data object Empty : QuickCaptureWidgetState
    data class Content(
        val snapshot: QuickCaptureSnapshot,
        val inFlightTaskIds: Set<Int> = emptySet()
    ) : QuickCaptureWidgetState
    data object Unavailable : QuickCaptureWidgetState
}

val CompactWidgetSize = DpSize(180.dp, 200.dp) // 3 rows
val MediumWidgetSize = DpSize(250.dp, 320.dp)  // 5 rows
val ExpandedWidgetSize = DpSize(250.dp, 464.dp) // 8 rows
fun capacityFor(size: DpSize): Int
```

- [ ] Add `implementation("androidx.glance:glance-appwidget:1.1.1")`, `testImplementation("androidx.glance:glance-testing:1.1.1")`, and matching AppWidget testing support only where the tests prove it is required.
- [ ] Write failing tests for exact size-to-capacity mapping, loading/empty/unavailable commands, 3/5/8 row caps, one-line titles, due-state content, overdue accent, disabled in-flight completion, Add/Open/Complete/Retry action parameters, and localized semantics labels.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*QuickCaptureWidgetContentTest'` and confirm failure.
- [ ] Implement `SizeMode.Responsive(setOf(CompactWidgetSize, MediumWidgetSize, ExpandedWidgetSize))`, stable header/action dimensions, 48 dp rows, ellipsized titles, localized due labels, and widget-specific resource-backed light/dark colors.
- [ ] Define provider metadata with `initialLayout`, `previewLayout`, resize support, `widgetCategory="home_screen"`, minimum compact dimensions, and `updatePeriodMillis="3600000"`; document that this is an inexact fallback, not a midnight guarantee.
- [ ] Add all Italian/English strings including Android-required plural quantities and accessibility descriptions. No visible instructional copy is added.
- [ ] Run focused and full JVM tests, Android resource processing, lint, and `git diff --check`.
- [ ] Commit `feat: render responsive quick capture widget`.

---

### Task 4: Completion Actions And Glance Provider

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/domain/usecase/CompleteQuickCaptureTask.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidget.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetReceiver.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetAction.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetUpdater.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/di/QuickCaptureModule.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/di/QuickCaptureWidgetEntryPoint.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create matching JVM and Android instrumentation tests.

**Interfaces:**

```kotlin
fun interface QuickCaptureWidgetUpdater { suspend fun updateAll() }

sealed interface CompleteQuickCaptureResult {
    data object Completed : CompleteQuickCaptureResult
    data object Ignored : CompleteQuickCaptureResult
    data object Failed : CompleteQuickCaptureResult
}

class CompleteQuickCaptureTask(
    private val completeTask: CompleteTask,
    private val updater: QuickCaptureWidgetUpdater
) {
    val inFlightTaskIds: StateFlow<Set<Int>>
    suspend operator fun invoke(taskId: Int): CompleteQuickCaptureResult
}
```

- [ ] Write failing tests proving one positive ID is completed through `CompleteTask`, recurrence results are accepted without duplicate logic, NotFound/AlreadyCompleted map to Ignored, exceptions map to Failed, duplicate concurrent taps invoke `CompleteTask` once under a `Mutex`, in-flight state is removed in `finally`, cancellation propagates, and every terminal path refreshes widgets.
- [ ] Write failing provider/action tests proving every render performs a fresh `LoadQuickCaptureTasks(capacity)`, read failure renders Unavailable, callbacks resolve dependencies through a Hilt `@EntryPoint @InstallIn(SingletonComponent::class)`, Add/Open PendingIntents are immutable and uniquely identified, and Retry requests an explicit widget update.
- [ ] Run focused tests and confirm failure.
- [ ] Implement the completion wrapper, Glance `ActionCallback`s, `GlanceAppWidget`, receiver, updater, Hilt bindings/entry point, and manifest receiver with `APPWIDGET_UPDATE` and `LOCALE_CHANGED` handling.
- [ ] Override composition failure with the raw `quick_capture_widget_error` `RemoteViews`; do not attempt to start a new Glance composition after session failure.
- [ ] Run focused/full JVM tests, provider instrumentation tests, compile/Hilt, lint, and `git diff --check`.
- [ ] Commit `feat: execute quick capture widget actions`.

---

### Task 5: Automatic Invalidation And Process Lifecycle

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetCoordinator.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/app/ToDoApplication.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/quickcapture/presentation/widget/QuickCaptureWidgetCoordinatorTest.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/quickcapture/QuickCaptureWidgetIntegrationTest.kt`

**Interfaces:**

```kotlin
@Singleton
class QuickCaptureWidgetCoordinator @Inject constructor(
    private val loadTasks: LoadQuickCaptureTasks,
    private val updater: QuickCaptureWidgetUpdater,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun onApplicationStart()
}
```

- [ ] Write failing coordinator tests proving start-once behavior, initial refresh, distinct snapshot refresh, save/delete/completion/recurrence/Replace All invalidation through the observed Room flow, retry after non-cancellation flow failure, and cancellation propagation without killing the application supervisor scope.
- [ ] Implement the coordinator as an invalidation observer only; never retain a task snapshot. Call it from `ToDoApplication.onCreate()` beside reminder startup.
- [ ] Write real in-memory Room integration tests that mutate through existing repositories/use cases and prove updates after save, delete, completion, next recurrence, and atomic restore; assert each subsequent provider read is fresh.
- [ ] Add a connected receiver test that starts with no app activity, sends widget update and completion actions, and verifies Room plus rendered widget state. Do not claim launcher-host behavior from an in-process test.
- [ ] Run focused JVM tests, focused connected tests on `emulator-5554`, complete JVM/connected suites, lint, and `git diff --check`.
- [ ] Commit `feat: refresh quick capture widgets from task changes`.

---

### Task 6: Host Journey, Accessibility, And Release Evidence

**Files:**
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/quickcapture/QuickCaptureNavigationJourneyTest.kt`
- Create or extend benchmark UiAutomator journey files under `benchmark/src/main/java/com/indiewalkabout/nowdothis/benchmark/` only if the installed launcher supports deterministic widget pinning; otherwise record the host journey as manual evidence without a misleading automated claim.
- Modify: `README.md`, `CHANGELOG.md`, architecture docs, quality strategy/matrix/accessibility checklist, release checklist, and migration/rollback docs.
- Add actual widget screenshots under `docs/images/` after verified emulator rendering.

- [ ] Write a production Navigation 3 journey proving cold-launch and `onNewIntent` Add/Open behavior, consume-once semantics, correct task editor destination, and no regression to reminder notification navigation.
- [ ] Add Glance/host checks for compact/medium/expanded capacities, empty/unavailable, Italian/English, light/dark, 200% font, localized labels, available 48 dp targets, resize, and all-widget refresh.
- [ ] Execute a process-absent emulator journey: seed a recurring task, render widget, stop the app process, request update, stop again, complete from widget, verify completed occurrence and next occurrence in Room, then use Add and Open Task.
- [ ] Capture screenshots only after verifying actual task text, theme, cropping, and no overlapping content. Do not use mockups as release evidence.
- [ ] Update documentation with actual commands/results, Glance internal-worker clarification, one-hour inexact date-boundary fallback, process-death evidence, privacy, architecture trade-offs, and rollback (removing receiver/dependency leaves Room data untouched).
- [ ] Run `ANDROID_SERIAL=emulator-5554 ./gradlew clean :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest :app:assembleRelease :app:bundleRelease`.
- [ ] Inspect the merged manifest for `QuickCaptureWidgetReceiver`, `APPWIDGET_UPDATE`, provider metadata, non-exported/unprivileged components as appropriate, and immutable PendingIntents; inspect the AAB for `baseline.prof`, DEX, resources, and absence of debug fixtures.
- [ ] Run `git diff --check`, ensure the worktree is clean after commit, and commit `docs: verify quick capture widget`.

---

## Final Review Gate

- Dispatch an independent whole-branch reviewer over the design-base-to-HEAD range.
- Review correctness, stale-data/date-boundary behavior, duplicate completion, recurrence/reminders, process death, PendingIntent security, Glance sizing, localization/accessibility, manifest exposure, architecture boundaries, tests, screenshots, and evidence accuracy.
- Fix all load-bearing findings, re-run affected and complete gates, and obtain explicit spec-compliance and code-quality approval before declaring Quick Capture complete.
