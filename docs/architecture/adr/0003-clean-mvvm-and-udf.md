# ADR 0003: Clean MVVM And Unidirectional Data Flow

- Status: Accepted
- Date: 2026-08-12

## Context

Now Do This has multiple Compose screens that observe Room and DataStore data, apply
task rules, perform mutations, request Android actions, and navigate. Without an
explicit dependency and interaction direction, Composables or ViewModels could take
on persistence, domain, and platform responsibilities that are difficult to test and
change independently.

## Decision

Use Clean MVVM boundaries within each feature. Workflows such as `SaveTask`, whose
reusable rules and cross-boundary reminder side effects should not live in a
ViewModel, follow this use-case path:

```text
Compose UI -> ViewModel -> use case -> repository contract
                                  -> repository implementation -> Room/platform
```

Use unidirectional data flow for screen interaction:

```text
immutable StateFlow -> Compose rendering
user action -> UiEvent -> ViewModel
one-time result -> effect Flow -> route-level UI or navigation action
```

ViewModels may also depend directly on domain repository contracts for screen-specific
reads, mutations, and coordination:

```text
Compose UI -> ViewModel -> repository contract -> repository implementation
```

This direct contract path is used by `TaskListViewModel` for categories and sort
preferences, by `TaskEditorViewModel` for task and category reads, by
`HistoryViewModel` for permanent deletion, and by `CategoryViewModel` for category
mutations. `CategoryViewModel` may sequentially rename and recolor a category; this is
screen-specific edit coordination, and the number of repository calls alone does not
require a use case. These ViewModels do not depend on repository implementations or
framework persistence APIs.

Domain use cases own reusable business rules, operations requiring atomicity or
transaction semantics, cross-boundary side effects, and workflow invariants that
should not live in a ViewModel. They depend on application-owned contracts.
Repository and platform implementations own framework integration. Hilt composes
implementations at the application boundary. A feature may omit a layer when it has
no corresponding responsibility; the pattern does not require empty abstractions or
a use-case wrapper for every repository call.

## Consequences

- State, user intent, and one-time effects have distinct contracts and tests.
- Use-case workflows such as save, completion, and recurrence orchestration can be
  tested without Compose or Android framework execution.
- Persistence and platform implementations can change behind stable contracts.
- Screen-specific repository-contract coordination avoids pass-through use cases,
  while reusable rules, atomicity, cross-boundary side effects, and invariant
  ownership remain explicit extraction triggers.
- Mapping and contract types add code, and asynchronous state/effect behavior needs
  deliberate lifecycle and cancellation handling.
- The distinction requires review discipline so business rules and workflow
  invariants do not accumulate in ViewModels.
- Buffered effect flows represent transient work; durable outcomes must be persisted
  or represented in state rather than relying on replay after process death.

## Rejected Alternatives

- Direct DAO or Android service access from Composables was rejected because it mixes
  rendering with lifecycle, persistence, and platform work.
- ViewModels depending directly on Room, DataStore, or AlarmManager were rejected
  because framework details would displace domain contracts and reduce testability.
- Two-way mutable UI state was rejected because ownership and event ordering become
  ambiguous.
- A mandatory use-case wrapper for every repository call was rejected because
  pass-through wrappers add ceremony without isolating a workflow or reusable rule.
- Mandatory interfaces for every use case and empty layers were rejected because
  they add ceremony without creating a meaningful substitution boundary.

## Revisit When

Revisit this decision when measured complexity shows that the current event/state
contracts obstruct feature work, when a supported UI runtime requires different
state ownership, when direct repository-contract calls begin owning reusable rules,
atomicity requirements, cross-boundary side effects, or workflow invariants, or when
effect delivery needs durable processing across process death. Any replacement must
preserve explicit dependency ownership and testable domain rules.
