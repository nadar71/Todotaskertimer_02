# Architecture

This directory records the architecture that is implemented in Now Do This as of
2026-08-12. The app deliberately remains a single Android application module with
feature-first packages, Clean MVVM boundaries, and unidirectional data flow.

Now Do This is local-first. It has no account, backend, analytics SDK, or network
synchronization. Room is the source of truth for task data, DataStore holds local
preferences, and Android platform services deliver local reminders.

## Documents

- [System context](system-context.md) describes the people and local systems around
  the application.
- [Data flow](data-flow.md) traces UI events, state, effects, use cases, repository
  contracts, persistence, and platform adapters.
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
