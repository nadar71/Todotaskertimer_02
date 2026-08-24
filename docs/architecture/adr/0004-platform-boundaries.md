# ADR 0004: Android And Framework Boundaries

- Status: Accepted
- Date: 2026-08-12

## Context

The product relies on Android and Jetpack capabilities that have lifecycle,
permission, persistence, and framework-specific behavior. Allowing those types and
calls to spread through domain workflows would make rules harder to test and would
hide where operating-system behavior affects the product.

## Decision

Treat the following as explicit platform or framework boundaries:

| Boundary | Current owner | Application-facing boundary |
| --- | --- | --- |
| Room | `AppDatabase`, DAOs, and offline repository implementations | Domain repository contracts and mapped domain models |
| DataStore | `DataStoreTaskPreferencesRepository` | `TaskPreferencesRepository` and `Flow<TaskSort>` |
| AlarmManager | `AndroidAlarmGateway` and `AlarmManagerReminderScheduler` | `AlarmGateway` internally and domain-owned `ReminderScheduler` |
| Notifications | receivers, `NotificationPublisher`, permission checker, and Hilt module in `core.notifications` | Reminder contracts, task identifiers, and route-level permission effects |
| Locale selection | Android resource locale configuration and Compose locale lookup at the UI edge | Localized resources and locale-aware presentation values; no locale-specific domain persistence |
| Navigation 3 | `AppNavigation`, `AppNavigator`, and serializable feature navigation keys | Presentation effects and route callbacks expressed with domain identifiers |
| AppWidget / Glance | `feature.quickcapture.presentation.widget` receiver, responsive composition, callbacks, refresh signal, adapter, and explicit intents | Domain-owned `QuickCaptureWidgetUpdater`, existing task-domain load/completion use cases, and application entry point |

Keep Room entities, DataStore keys, AlarmManager and notification objects, locale
framework objects, and Navigation 3 back-stack operations in their listed owners.
Use cases and repository contracts expose application/domain concepts instead.

Create wrappers where they isolate policy or enable deterministic tests, as with
`ReminderScheduler` and `AlarmGateway`. Do not wrap a framework API merely to rename
it when it already belongs entirely to a platform-owned UI or composition boundary.

## Consequences

- Domain tests can replace persistence and reminder contracts without booting the
  Android framework.
- Room transactions, DataStore error handling, exact-alarm fallback, notification
  permission behavior, locale formatting, and Navigation 3 lifecycle behavior remain
  visible at specific integration points.
- Some app and presentation code intentionally uses Compose or Navigation types at
  the UI edge; complete framework independence is not a goal.
- AppWidget hosts own placement and size selection. The widget maps host size to
  fixed 3/5/8 capacities, reads current Room state after process reconstruction, and
  relies on Glance's internal session machinery rather than an app-owned widget
  worker or duplicate persistence layer.
- Application configuration callbacks request widget invalidation through the
  domain-owned updater contract. The Glance adapter advances presentation refresh
  state so live compositions re-resolve qualified resources without restricted APIs.
- Platform upgrades can require focused adapter and integration-test changes even
  when domain contracts remain stable.

## Rejected Alternatives

- Passing Room entities, DataStore preferences, Android intents, or navigation keys
  through domain workflows was rejected because framework representation would
  become part of business rules.
- Calling AlarmManager or notification APIs directly from use cases or ViewModels was
  rejected because permission and fallback policy would be difficult to isolate.
- Building custom navigation or locale frameworks was rejected because Navigation 3
  and Android resource selection already own those platform concerns.
- Wrapping every Android or Compose type was rejected because abstraction without a
  policy, substitution, or testing need adds indirection rather than a boundary.

## Revisit When

Revisit a boundary when a framework is replaced, an adapter can no longer express
required platform policy, a second runtime creates a real reuse consumer, or tests
demonstrate that framework behavior is leaking into domain rules. A change must name
the affected contract, migration path, and integration verification.
