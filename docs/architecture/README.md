# Architecture

This directory records the architecture that is implemented in Now Do This as of
2026-08-24. The app deliberately remains a single Android application module with
feature-first packages, Clean MVVM boundaries, and unidirectional data flow.
ViewModels may consume domain repository contracts directly for screen-specific
reads, mutations, and coordination. Use cases own reusable business rules, operations
with atomicity or transaction requirements, cross-boundary side effects, and workflow
invariants that should not live in a ViewModel.

Now Do This is local-first. It has no account, backend, analytics SDK, or network
synchronization. Room is the source of truth for task data, DataStore holds local
preferences, Android platform services deliver local reminders, and the Storage
Access Framework provides user-directed planning-data backup documents.

The `feature/portability` boundary follows the same Clean MVVM dependency direction.
Compose owns document-picker launchers, the ViewModel drives typed UDF state/effects,
use cases coordinate backup/inspection/restore, and the repository combines a pure
versioned codec with a transactional Room data source and platform document gateway.
Restore validates before mutation, replaces the complete graph atomically, then
reconciles reminders after commit.

The `feature/quickcapture` boundary is a Glance/AppWidget presentation surface over
the existing task domain and Room source of truth. Each responsive composition
observes the capacity selected from its host-provided size, while add/open intents
enter the production `MainActivity` Navigation 3 host and completion invokes the
shared task-completion use case. The receiver and Glance callback can reconstruct
dependencies from the application entry point when no app activity or process is
running. A domain-owned invalidation port is implemented by the Glance adapter, and
application configuration changes advance a presentation refresh signal before
updating bound widgets so qualified resources are resolved live. No widget cache or
app-authored `WorkManager` pipeline is introduced.

## Documents

- [System context](system-context.md) describes the people and local systems around
  the application.
- [Data flow](data-flow.md) traces UI events, state, effects, use cases, repository
  contracts, persistence, and platform adapters.
- [Backup format v1](../data-portability/backup-format-v1.md) defines the serialized
  planning graph, compatibility policy, and atomic Replace All behavior.
- [ADR 0001: Local-first](adr/0001-local-first.md) records why data and execution
  remain on the device.
- [ADR 0002: Single-module feature-first](adr/0002-single-module-feature-first.md)
  records the current module boundary and objective modularization triggers.
- [ADR 0003: Clean MVVM and UDF](adr/0003-clean-mvvm-and-udf.md) records presentation
  and dependency direction.
- [ADR 0004: Platform boundaries](adr/0004-platform-boundaries.md) records where
  Android and Jetpack frameworks meet application-owned contracts.

ADR statuses use the vocabulary `Accepted | Superseded`. A superseded record remains
in the repository and links to the decision that replaced it.
