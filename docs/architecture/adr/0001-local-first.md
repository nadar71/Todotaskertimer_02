# ADR 0001: Local-First Storage And Execution

- Status: Accepted
- Date: 2026-08-12

## Context

Now Do This is a private task manager whose primary create, inspect, schedule,
complete, calendar, and history journeys must work without connectivity. The current
implementation stores task data in Room, stores preferences in DataStore, and uses
Android alarms and notifications for reminders. It contains no account, backend,
analytics SDK, or network synchronization. Consent-gated advertising is an isolated
network boundary and does not change where planning data is stored or processed.

## Decision

Keep application data and execution local-first. Room is the durable source of truth
for task and category data, DataStore is the source of truth for local preferences,
and Android system services deliver reminders. Domain code reaches those mechanisms
through application-owned repository and platform contracts.

Network availability is not a precondition for a supported product journey. Account,
backend, analytics, and synchronization capabilities are outside the current system
boundary.

Google UMP determines whether ads may be requested. Mobile Ads initializes at most
once after that permission, and the banner remains absent otherwise. No task,
category, reminder, history, or backup data is supplied to either SDK.

## Consequences

- Core workflows remain available offline and do not require service operations or
  account management.
- Personal task data is not sent to an application backend or analytics provider.
- Persistence migrations, reminder reconciliation, backup compatibility, and device
  behavior remain explicit Android engineering responsibilities.
- Data does not automatically follow a user across devices, and remote recovery and
  collaboration are unavailable.
- Future data portability must preserve relationships and validate local data before
  mutation; it does not silently turn Room into a cache for a remote source.

## Rejected Alternatives

- A backend-first model with mandatory accounts was rejected because it would make
  connectivity and service operation dependencies of a private local planner.
- Transparent cloud synchronization was rejected because conflict resolution,
  identity, privacy, and operational complexity are not justified by the current
  product scope.
- Analytics instrumentation was rejected because there is no defined measurement
  need that outweighs the additional data boundary and disclosure obligations.

## Revisit When

Revisit this decision when validated product requirements demand cross-device access,
collaboration, or managed remote recovery; when a regulatory or security requirement
cannot be met on-device; or when a concrete service-dependent workflow is approved
with ownership, privacy, offline, conflict, and migration policies.
