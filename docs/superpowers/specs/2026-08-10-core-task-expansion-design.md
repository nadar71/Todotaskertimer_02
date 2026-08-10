# Core Task Expansion Design

## Purpose

Expand the current local to-do application with the core capabilities visible in the reference screenshots while preserving the existing feature-first direction. The release adds user-managed categories, task completion, optional scheduling, one reminder, recurrence, subtasks, a calendar, and completion history. The UI takes layout and workflow cues from the screenshots but follows polished Android Material 3 conventions.

## Scope

### Included

- User-managed categories with localized seeded defaults: Work, Personal, and Wishlist.
- Pending and completed task states with completion timestamps.
- Optional task due date and time.
- One optional reminder date and time per task.
- Recurrence modes: None, Daily, Weekly, and Monthly, with an optional end date.
- Ordered subtasks with individual completion state.
- Time-grouped task list: Overdue, Today, Upcoming, Unscheduled, and Completed Today.
- Category filtering and existing search behavior.
- Month calendar with task indicators and selected-day task list.
- Completion history for tasks completed before today.
- Italian as the default application language and English as an alternative through Android's native per-app language settings.
- Explicit Room migration from the current version 1 schema.

### Deferred

- Onboarding.
- Account and calendar synchronization.
- Widgets.
- Custom themes and theme selection.
- Task templates.
- Profile analytics, charts, and PRO functionality.
- Feedback and FAQ screens.
- Multiple reminders per task.
- Custom recurrence intervals beyond Daily, Weekly, and Monthly.
- Reopening completed tasks.
- A custom in-app language selector.

## Architectural Direction

The app remains a single Gradle application module but uses sibling feature packages with one-way dependencies. This is the middle ground between a single large task package and separate Gradle modules. The boundaries should make later module extraction possible without imposing multi-module complexity now.

```text
app/
  navigation/

core/
  database/
  designsystem/
  notifications/
  util/

feature/
  task/
    data/
    domain/
    presentation/list/
    presentation/editor/
    navigation/

  category/
    data/
    domain/
    presentation/
    navigation/

  calendar/
    domain/
    presentation/
    navigation/

  history/
    domain/
    presentation/
    navigation/
```

The feature responsibilities are:

- `feature/task`: task creation, editing, list presentation, completion, subtasks, recurrence, and reminder policy.
- `feature/category`: category persistence, defaults, management, ordering, and colors.
- `feature/calendar`: date and month selection plus read-only scheduled-task presentation.
- `feature/history`: read-only completed-task browsing, search, and permanent deletion.
- `core/database`: Room database aggregation and migrations. Feature DAOs and entities remain feature-owned.
- `core/notifications`: Android alarm, notification, and reminder-reconciliation infrastructure.
- `app/navigation`: root Navigation 3 back stack and destination composition.

Dependency direction stays acyclic. Calendar and history consume read-only task-domain contracts. Task records reference category identifiers, while category does not depend on task. Android infrastructure implements interfaces declared by the consuming domain layer.

## Clean MVVM Rules

Each feature follows this dependency direction:

```text
Compose UI -> ViewModel -> Use case -> Domain repository interface
                                         ^
                                         |
                              Data repository implementation -> DAO or platform API
```

- Domain models are plain Kotlin types without Room, Compose, Hilt, or Android framework annotations.
- Data packages own Room entities, DAOs, mappers, and repository implementations.
- Domain packages own repository interfaces and business use cases.
- Presentation packages own immutable UI state, UI events, one-time effects, feature-scoped ViewModels, and Compose screens.
- DAO observation uses `Flow`; repositories map entities to domain models; ViewModels expose `StateFlow<UiState>`.
- Mutations use suspend functions from `viewModelScope`. IO dispatchers, clocks, and time-zone providers are injected for testability.
- Compose collects state with lifecycle awareness.
- Navigation, snackbars, and permission requests are emitted as one-time effects rather than persistent screen state.
- The activity no longer owns a global `SharedViewModel`. Each destination obtains its own Hilt ViewModel and communicates through stable navigation arguments and repository flows.

## Data Model

### Task

The task record preserves the existing integer primary key and fields, then adds:

- Nullable category ID.
- Completion flag and nullable completion timestamp.
- Nullable due timestamp.
- Nullable reminder timestamp.
- Reminder scheduling status sufficient to distinguish requested, scheduled, and unavailable delivery.
- Recurrence type: None, Daily, Weekly, or Monthly.
- Nullable recurrence end timestamp.
- Nullable recurrence series ID shared by generated occurrences.
- Creation and update timestamps.

Persistent timestamps use epoch milliseconds. Domain and presentation code convert through injected time-zone rules rather than performing ad hoc date arithmetic.

### Subtask

A subtask contains an ID, parent task ID, title, completion flag, nullable completion timestamp, and display position. Deleting a parent task cascades to its subtasks.

Completing a parent task completes every unfinished subtask in the same Room transaction. Reopening a parent is outside this release. Completing all subtasks shows full progress but does not automatically complete the parent.

### Category

A category contains an ID, optional custom name, optional seeded-default key, color token, display position, and creation timestamp.

Work, Personal, and Wishlist are inserted as seeded-default keys rather than localized database strings. Their labels resolve through Android resources so they change when the application locale changes. Renaming a default category converts it to a custom category name. Users may create, rename, recolor, reorder, and delete categories.

The task-category foreign key is nullable with `ON DELETE SET NULL`. Deleting a category therefore preserves its tasks and presents them as Uncategorized.

## Task Lifecycle Rules

### Creation And Editing

- Title and description remain required.
- Category, due date/time, reminder, recurrence, recurrence end date, and subtasks are optional.
- A reminder must not be later than its task's due time when a due time exists.
- Recurrence requires a due date/time because the next occurrence is calculated from it.
- An optional recurrence end must not precede the first due date.
- Editing or deleting a task replaces or cancels its scheduled reminder.
- Editor draft state survives configuration and process recreation through `SavedStateHandle`-backed ViewModel state.

### Completion

Completing a task runs one transaction that:

1. Marks the current task completed and records its completion timestamp.
2. Completes every unfinished subtask.
3. Cancels the current reminder.
4. For a recurring task, creates a new pending occurrence when its next due time does not exceed the optional recurrence end.

The generated occurrence retains the title, description, category, priority, recurrence rule, recurrence end, and recurrence series ID. Its due and reminder timestamps advance by the same recurrence interval. It receives fresh subtasks in their original order with pending state. The completed occurrence remains unchanged for calendar and history accuracy.

### Deletion And Undo

Task deletion removes the task and cascading subtasks and cancels its reminder. Existing swipe-to-delete and snackbar undo behavior remain available. Undo restores the complete task snapshot, including subtasks and reminder request, and reconciles its alarm.

Deleting a completed history entry is permanent after confirmation. Reopening completed entries is deferred to prevent duplicate or conflicting recurrence occurrences.

## Queries And Read Contracts

Task list queries return only the rows needed by each time section. Time boundaries come from injected clock and time-zone providers:

- Overdue: pending tasks with due time before the start of today.
- Today: pending tasks due during the current local day.
- Upcoming: pending tasks due after the current local day.
- Unscheduled: pending tasks without a due timestamp.
- Completed Today: tasks completed during the current local day.

Category selection and text search apply consistently to every section. Search matches title and description. The All category filter includes categorized and uncategorized tasks.

Calendar exposes scheduled task summaries for the visible month and full task summaries for the selected local day. History exposes tasks completed before the start of today, ordered by completion time descending, with text and category filters. Calendar and history consume read-only task-domain interfaces rather than DAOs.

Database indexes cover category ID, due timestamp, completion timestamp, completion flag, and recurrence series ID.

## Screens And Navigation

Navigation 3 uses these serializable keys:

- `TaskList`
- `TaskEditor(taskId: Int?, initialDueAt: Long?)`
- `CategoryManagement`
- `Calendar`
- `CompletionHistory`

The root scaffold has Material 3 Tasks and Calendar destinations plus an add-task floating action button. The task FAB opens an empty editor; the calendar FAB opens an editor with the selected date prefilled.

### Task List

The task list presents search, horizontally scrolling category chips, and the five approved time sections in this order: Overdue, Today, Upcoming, Unscheduled, and Completed Today. Empty sections may be omitted, but a fully empty result has a dedicated localized empty state.

Task rows show completion state, title, category color and label, priority, due time, and compact indicators for reminder, recurrence, and subtask progress. Category and priority are never communicated by color alone. Swipe-to-delete and snackbar undo are retained. A See all action in Completed Today opens completion history.

### Task Editor

One editor handles create and update. It includes title, description, priority, category, optional due date/time, one optional reminder, recurrence mode and end date, and an ordered subtask checklist. It validates fields inline and preserves draft input if persistence or scheduling fails.

### Category Management

The category screen supports create, rename, recolor, reorder, and delete. Delete requires confirmation and explains that associated tasks become Uncategorized.

### Calendar

The calendar screen uses a Material 3 month layout with task indicators. Selecting a date displays that day's tasks below the grid. Undated tasks do not appear. Selecting a task opens its editor.

### Completion History

History groups tasks by completion date and supports search, category filtering, inspection, and confirmed permanent deletion. It excludes tasks completed during the current day because those remain visible on the task list.

## Reminder And Notification Design

The task domain declares a `ReminderScheduler` contract. `core/notifications` implements it with Android alarm and notification APIs.

- The scheduler attempts exact delivery when Android grants Alarms and reminders access.
- If exact access is unavailable, it schedules an inexact alarm and exposes that delivery timing can vary.
- Notification permission and exact-alarm access are requested contextually when the user enables a reminder.
- Permission denial never blocks task persistence.
- Stable task IDs create stable pending-intent identities, so updates replace existing alarms instead of duplicating them.
- Notifications use a dedicated localized channel and open the associated task editor.
- Completion and deletion cancel alarms.
- App startup, device reboot, app upgrade, and exact-alarm permission changes trigger reconciliation of future active reminders from the database.
- If notification permission is later granted, reconciliation schedules reminders that are still in the future.

Scheduling occurs after a successful database write. A scheduling failure leaves the task saved, records an unavailable reminder state, and presents retryable feedback. Reconciliation is the recovery mechanism for interruption between persistence and alarm scheduling.

## Localization

- `res/values/strings.xml` contains Italian, the unqualified default language.
- `res/values-en/strings.xml` contains complete English translations.
- `res/resources.properties` declares `unqualifiedResLocale=it`.
- AGP automatic locale configuration exposes Italian and English in Android's native per-app language settings.
- Kotlin and Compose contain no user-facing hardcoded strings.
- String plurals cover task and subtask counts.
- Dates and times use locale-aware Android formatting.
- Default category names, validation errors, snackbars, dialogs, notification channel text, notifications, and accessibility descriptions are localized.

## Error Handling

- Field validation appears inline and prevents only invalid save operations.
- Database errors preserve editor input and produce a retryable localized snackbar.
- Reminder scheduling errors do not roll back the saved task; the editor clearly reports that the reminder is not active.
- Missing or deleted task IDs safely navigate back to the list with localized feedback.
- Permission denial becomes explicit UI state and never produces a crash.
- Flow collection exposes loading, content, empty, and recoverable error states through each feature's `UiState`.

## Migration

Room schema export is enabled. Database version 1 migrates explicitly to the expanded schema without destructive fallback.

- Existing task IDs, titles, descriptions, and priorities are preserved.
- Existing tasks begin pending, uncategorized, unscheduled, non-recurring, and without reminders.
- Existing tasks therefore appear under Unscheduled and the All or Uncategorized category filters.
- The migration creates category and subtask tables, inserts the three seeded category keys, recreates the task table as required for foreign keys, copies existing rows, and adds indexes.
- Migration tests start from an actual version 1 schema, run the migration, validate the resulting schema, and assert preservation of existing rows.

## Verification Strategy

### Unit Tests

- Time-section grouping at day and time-zone boundaries.
- Daily, weekly, and monthly recurrence, including month-end behavior and optional end dates.
- Parent and subtask completion rules.
- Recurring occurrence generation and subtask copying.
- Reminder timestamp advancement and validation.
- Category default-label resolution and deletion behavior.
- Editor validation and typed error mapping.
- ViewModel loading, filtering, draft restoration, successful mutations, errors, and one-time effects.

### Database And Integration Tests

- Every DAO query and index-sensitive filter.
- Completion and recurrence transactions.
- Foreign-key cascade and set-null behavior.
- Seeded categories.
- Version 1 migration and row preservation.
- Reminder schedule, replace, cancel, denial, and reconciliation behavior.

### Compose And Navigation Tests

- Create and edit a task with all optional fields.
- Complete and delete tasks, including undo.
- Filter by category and search text.
- Select a calendar date and create a predated task.
- Open completion history and delete an entry.
- Resolve representative Italian and English strings and plurals.
- Navigate every key and handle a missing task ID.
- Restore an editor after recreation.

### Quality Gates

- Unit tests pass.
- Instrumentation and migration tests pass.
- Android lint passes without missing-translation errors.
- `assembleDebug` succeeds.
- Manual checks cover at least API 23 and API 33 or newer for notification permissions, exact-alarm fallback, locale selection, device reboot reconciliation, and process recreation.
- Interactive elements have appropriate semantic labels and Material-size touch targets.
- Font scaling does not clip or overlap controls.

## Success Criteria

The release is complete when a user can organize tasks into custom categories; create dated or undated tasks with subtasks, recurrence, and one reminder; complete tasks while preserving recurring history; browse tasks by time, category, calendar date, and completion date; and use the entire experience in Italian or English. Existing version 1 task data must survive upgrade, and all behavior must follow the clean MVVM boundaries and verification gates defined above.
