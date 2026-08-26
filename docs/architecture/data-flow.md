# Clean MVVM And UDF Data Flow

The diagram below is the use-case path for workflows such as `SaveTask`, where
reusable rules and cross-boundary reminder side effects should not live in a
ViewModel. It is not a mandatory hop for every interaction. ViewModels may instead
consume domain repository contracts directly for screen-specific reads, mutations,
and coordination. Both paths keep data and platform implementations behind
domain-owned contracts.

```mermaid
flowchart LR
    UI["Compose screen"] -->|UiEvent| VM["ViewModel"]
    VM -->|invoke| UC["Use case"]
    UC -->|contract| Repo["Repository"]
    Repo --> DB["Room / DataStore"]
    Repo --> Platform["Alarm / notification adapter"]
    DB -->|Flow| Repo
    Repo -->|Flow| UC
    UC -->|domain result| VM
    VM -->|StateFlow UiState| UI
    VM -->|effect Flow| UI
```

The diagram groups domain-facing persistence and platform contracts at the
repository boundary. In the concrete save path, `SaveTask` depends separately on
`TaskRepository` and `ReminderScheduler`; their implementations still remain outside
the domain use case.

## Dependency Policy

- Use a domain use case when an operation enforces reusable business rules, requires
  atomicity or transaction semantics, coordinates cross-boundary side effects, or
  owns workflow invariants that should not live in a ViewModel.
- A ViewModel may sequence domain repository-contract calls for screen-specific
  reads, mutations, and coordination when that sequence does not own one of those
  use-case responsibilities. The number of calls alone is not an extraction trigger.
- A ViewModel does not depend on a repository implementation, Room, DataStore,
  AlarmManager, or notification framework APIs.

For example, `TaskListViewModel` invokes task lifecycle use cases while directly
observing `CategoryRepository` and `TaskPreferencesRepository` for screen data and
sort coordination. `TaskEditorViewModel` reads tasks and categories through their
repository contracts, then invokes `SaveTask` for validation, persistence, and
reminder scheduling. `CategoryViewModel` may sequentially rename and recolor through
`CategoryRepository`; that edit sequence is screen-specific coordination and does not
require a use case merely because it makes two calls.

## State

State is the durable description of what a screen should render. For example,
`TaskListViewModel.uiState` combines task sections, categories, the DataStore-backed
sort preference, the active filter, loading and error state, and confirmation state
into an immutable `StateFlow<TaskListUiState>`. `TaskListRoute` collects it with
lifecycle awareness and passes the value to the stateless `TaskListScreen`.

Observable data returns through `Flow`: `TaskRepository.observeSections` is
implemented by `OfflineTaskRepository`, which maps Room DAO emissions into domain
tasks and classified sections before the ViewModel builds UI state.

## Events

Events describe user intent and enter a screen through one dispatch point. The task
list sends `TaskListEvent` values to `TaskListViewModel.onEvent`. The ViewModel may
update local state, coordinate screen-specific repository-contract calls, or invoke a
use case for reusable rules, atomicity, cross-boundary side effects, or workflow
invariants; the Composable does not call Room or a repository implementation
directly.

For a write example, the editor invokes `SaveTask`. The use case validates the domain
model, calls the `TaskRepository` contract, and coordinates the domain-owned
`ReminderScheduler` contract. `OfflineTaskRepository` fulfills the persistence call
with Room transactions, while the reminder implementation delegates to the Android
alarm adapter and writes the resulting reminder status back through the repository.

## Effects

Effects are one-time outcomes that should not become durable render state, such as
opening a destination, requesting notification permission, or showing a snackbar.
`TaskListViewModel.effects` exposes a `Flow<TaskListEffect>` backed by a buffered
channel. `TaskListRoute` collects that flow and translates each effect into UI or
navigation work. This keeps Android and Compose actions out of the ViewModel while
leaving persistent screen content in `UiState`.

## Dependency Ownership

- Presentation owns Compose screens, contracts, ViewModels, screen coordination, and
  route-level effect handling. ViewModels may consume domain contracts under the
  dependency policy above.
- Domain owns models, use cases such as `SaveTask`, and contracts such as
  `TaskRepository` and `ReminderScheduler`.
- Data owns `OfflineTaskRepository`, DataStore-backed preferences, Room entities,
  DAOs, and mapping between persistence and domain models.
- App and core packages own composition, Navigation 3, database construction,
  notifications, alarms, time, and shared design primitives.

## Natural-Language Entry

```mermaid
flowchart LR
    Quick["Quick entry text + Parse event"] --> Editor["TaskEditorViewModel"]
    Editor --> Env["Locale / clock / zone / current categories snapshot"]
    Env --> Parser["Pure ParseNaturalLanguageTask"]
    Parser --> Draft["Typed parsed draft + recognized fields + issues"]
    Draft --> Editor
    Editor --> State["Atomic TaskEditorUiState + SavedStateHandle"]
    State --> Controls["Existing editor confirmation and correction controls"]
    Controls --> Save["Existing SaveTask"]
    Save --> Room["TaskRepository / Room"]
    Save --> Alarm["ReminderScheduler / AlarmManager"]
```

The parser feature is framework-free: it receives explicit language, instant, time
zone, and category candidates and returns typed values plus consumed ranges and
nonfatal issues. Android locale and localized default-category names are adapted only
at the presentation boundary. The ViewModel computes a complete parse result before
one state update and changes only explicitly recognized fields. Raw input, summary,
issues, and the existing editor draft are persisted in `SavedStateHandle`, so activity
or process restoration renders the same preview without time-dependent reparsing.

Parsing is an explicit command and never requests permissions or saves. The existing
description validation, correction controls, `SaveTask`, Room repository, and reminder
scheduler remain authoritative. The offline grammar is deliberately bounded to the
documented Italian/English date, time, priority, current category, recurrence, and
single-reminder forms; unsupported or ambiguous text remains recoverable and visible.

## Quick Capture Widget

```mermaid
flowchart LR
    Host["Android AppWidget host"] --> Receiver["Glance receiver / callback"]
    Receiver --> EntryPoint["Hilt application entry point"]
    EntryPoint --> Load["LoadQuickCaptureTasks"]
    EntryPoint --> Complete["CompleteTask"]
    Config["Application configuration change"] --> Updater["QuickCaptureWidgetUpdater port"]
    Updater --> Adapter["Glance updater + refresh signal"]
    Adapter --> Widget
    Load --> Room["Room task source of truth"]
    Complete --> Room
    Room -->|Flow| Widget["Responsive Glance composition (3 / 5 / 8)"]
    Widget --> Host
    Widget -->|explicit immutable intent| Activity["MainActivity Navigation 3"]
```

The host-selected `LocalSize` determines each responsive composition's task
capacity. Data is read directly from Room, so a receiver update or completion can
start a previously absent application process and rebuild current state without a
widget-only cache. Add and open actions use explicit immutable activity intents;
completion uses a Glance callback and the shared recurring-aware completion use
case. Glance may use its own internal worker for session execution, but the app does
not schedule widget work through an application-owned `WorkManager` pipeline. The
framework-free updater contract is owned by the feature domain; its Glance adapter
invalidates a presentation refresh generation before `updateAll()`. This lets an
already-bound widget recompose against current light/dark qualified resources when
the application receives a system configuration change.
