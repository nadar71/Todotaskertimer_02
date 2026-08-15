# Quick Capture Widget Design

## Goal

Add a responsive Android home-screen widget that makes Now Do This useful without first opening the app. The widget shows the most relevant pending tasks, completes tasks through existing domain behavior, and opens the existing task editor for task creation and editing.

Quick Capture remains a focused Product Value subsystem inside the existing single `:app` module. It must strengthen the privacy-first, local-first product without introducing accounts, synchronization, duplicated task rules, or decorative architecture.

## Product Scope

The widget presents pending tasks in this order:

1. Overdue tasks.
2. Tasks due today.
3. Upcoming tasks.

The widget supports four commands:

- Complete a task immediately.
- Open an existing task in the full editor.
- Open the existing editor for a new task.
- Retry after a recoverable rendering failure.

The Add command deliberately reuses the full editor. Quick Capture v1 does not add title-only persistence, change the existing description requirement, or pre-empt the future deterministic natural-language entry subsystem.

## User Experience

The implementation uses Jetpack Glance and responsive layouts:

- Compact widgets show the header, Add command, and up to 3 tasks.
- Medium widgets show up to 5 tasks.
- Expanded widgets show up to 8 tasks.

Each task row contains a completion control, title, and concise localized due-state label. Overdue tasks use a restrained error-color accent; today and upcoming tasks remain visually neutral. Tapping the title opens that task in the existing editor. Tapping completion disables repeat interaction for that action, invokes the existing completion behavior, and refreshes all widget instances.

The widget has explicit stable states:

- Loading uses a dimensionally stable placeholder.
- Empty shows a localized no-pending-tasks message and Add command.
- Content shows the capped ordered task list.
- Unavailable shows a concise localized message and Retry command without technical details.

The widget follows Android-selected Italian or English resources and system light/dark appearance. Commands expose localized accessibility labels and state descriptions. Interactive targets are at least 48 dp where App Widget host constraints permit.

## Architecture

Quick Capture is a feature-first vertical slice:

```text
feature/quickcapture/
├── domain/
│   ├── model/QuickCaptureSnapshot
│   └── usecase/LoadQuickCaptureTasks
├── presentation/widget/
│   ├── QuickCaptureWidget
│   ├── QuickCaptureWidgetReceiver
│   ├── QuickCaptureWidgetAction
│   ├── QuickCaptureWidgetCoordinator
│   └── QuickCaptureWidgetUpdater
└── di/
    └── QuickCaptureModule
```

Names may be split into smaller focused files during planning, but the ownership boundaries are fixed:

- Domain selection and ordering are framework-free.
- Glance rendering, widget size handling, intents, receivers, and callbacks remain in the widget presentation/platform boundary.
- Android and Glance types do not enter task domain models or repositories.
- Hilt entry points provide dependencies to receivers and Glance callbacks instantiated by Android.
- Room remains the only persisted source of truth; the widget does not maintain a second task cache.

The feature remains in `:app`. A new Gradle module is not justified by team size, ownership, reuse, or measured build benefit.

## Domain Reuse

`LoadQuickCaptureTasks` consumes the existing Room-backed task observation and existing day-boundary, classification, filtering, and sorting concepts. It returns a presentation-neutral snapshot capped by the requested widget capacity. It does not reimplement SQL persistence or recurrence rules.

Completion delegates to the existing `CompleteTask` use case. Therefore atomic completion, completed subtasks, recurrence generation, alarm cancellation, and scheduling of a next occurrence behave exactly as they do inside the app.

Opening and adding tasks route through `MainActivity` and Navigation 3:

- Open Task targets `TaskEditorKey(taskId, null)`.
- Add targets `TaskEditorKey(null, null)`.

Widget intents are immutable, uniquely identified where required, and deliver correctly whether the activity is absent, running, or receives a new intent.

## Refresh And Process Death

A singleton `QuickCaptureWidgetCoordinator` observes the existing Room-backed task flow while the app process is alive. Relevant saves, completions, deletions, recurrence creation, and Replace All restore change that flow and trigger `updateAll()` without coupling task use cases to the widget feature.

When the app process is absent:

- Widget update callbacks open the existing Room database through injected singleton boundaries and render fresh data.
- Completion callbacks run short coroutine work, invoke `CompleteTask`, and explicitly update all widget instances.
- Add and Open Task use activity `PendingIntent`s and do not require an existing process.
- Platform periodic updates provide a fallback for date-boundary changes while the process remains absent.
- App startup, widget placement, resizing, locale changes, and package replacement request a fresh render.

No WorkManager is added in v1. Widget reads and completion are short, user-visible operations suited to receiver/callback execution. If measured reliability later demonstrates that hosts or devices routinely terminate this work, WorkManager becomes a documented escalation trigger.

## Failure Handling

- A read or rendering failure produces the localized Unavailable state and Retry command.
- A completion failure reloads authoritative Room state and must not display an optimistic completed state.
- Repeated taps for the same in-flight completion are suppressed.
- Navigation intent failure leaves task data unchanged.
- Widget failures do not crash the app process or expose exception, URI, task-description, or database content in logs.

## Localization And Appearance

Italian remains the base resource language and English remains under `values-en`. All widget text, due labels, action descriptions, empty/error states, and accessibility descriptions are localized. Quantity resources include every quantity required by Android lint for their locale.

Glance colors use widget-specific resource-backed light and dark palettes aligned with the application identity. The Compose `MaterialTheme` is not imported into the widget boundary because App Widgets render through `RemoteViews` constraints.

## Testing

### JVM

- Selection order: overdue, today, upcoming.
- Exclusion of completed and unscheduled tasks from the visible widget list.
- Compact, medium, and expanded capacity limits.
- Stable display models and localized due-state inputs.
- Completion delegation, recurring completion behavior, duplicate suppression, and failures.
- Widget intent parsing for Add and Open Task.

### Instrumentation

- Glance content for compact, medium, expanded, loading, empty, and unavailable states.
- Real Room refresh after save, delete, completion, recurrence creation, and Replace All restore.
- Add and Open Task navigation through the production activity and Navigation 3 graph.
- Completion actions use existing domain operations and refresh every widget instance.
- Process-absent update and action behavior on an emulator.
- Italian and English, light and dark appearance, resize behavior, large font, semantics labels, and available touch-target checks.

### Release Evidence

The complete unit and connected suites, lint, debug/release assembly, release bundle, and artifact-content checks remain required. README screenshots, architecture documentation, test strategy and matrix, changelog, and release/rollback notes are updated with actual evidence rather than intended results.

## Non-Goals

Quick Capture v1 does not include:

- Inline or title-only task creation.
- Natural-language parsing.
- Widget configuration, filters, category selection, reminders, recurrence editing, or subtasks.
- Undo notifications.
- Accounts, cloud synchronization, collaboration, or remote data.
- WorkManager, a foreground service, a second task cache, a Room migration, or a new Gradle module.

## Definition Of Done

Quick Capture v1 is complete when:

- A responsive Glance widget shows capped pending tasks ordered overdue, today, and upcoming.
- Add and task taps open the correct existing editor destination.
- Completion delegates to `CompleteTask`, including recurrence and reminder behavior.
- Relevant task mutations and Replace All restore refresh widgets without direct task-to-widget coupling.
- Update and completion work when the app process was absent.
- Italian/English, light/dark, resizing, accessibility, and failure states are verified.
- JVM, connected, lint, assembly, bundle, artifact, documentation, and independent review gates pass.
