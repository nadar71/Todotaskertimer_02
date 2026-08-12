# Clean MVVM And UDF Data Flow

The application uses feature-first packages while preserving one dependency
direction through each feature: Compose UI to ViewModel to use case to a
domain-owned repository or platform contract. Data and platform implementations sit
behind those contracts.

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
list sends `TaskListEvent` values to `TaskListViewModel.onEvent`. The ViewModel updates
local state or invokes a use case; the Composable does not call Room or a repository
implementation directly.

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

- Presentation owns Compose screens, contracts, ViewModels, and route-level effect
  handling.
- Domain owns models, use cases such as `SaveTask`, and contracts such as
  `TaskRepository` and `ReminderScheduler`.
- Data owns `OfflineTaskRepository`, DataStore-backed preferences, Room entities,
  DAOs, and mapping between persistence and domain models.
- App and core packages own composition, Navigation 3, database construction,
  notifications, alarms, time, and shared design primitives.
