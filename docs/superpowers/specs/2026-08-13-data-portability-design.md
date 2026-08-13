# Data Portability v1 Design

- Status: Approved
- Date: 2026-08-13
- Branch: `feature/data_portability`

## Goal

Add complete-fidelity, user-owned backup and Replace All restore for Now Do This planning data. The feature remains local and offline, uses Android's Storage Access Framework, preserves Room relationships and stable identifiers, and never mutates current data until the selected backup is fully decoded and validated.

## Scope

The backup includes user-owned planning data:

- categories, including custom names, stable default keys, color tokens, positions, and creation times;
- tasks, including title, description, priority, category relationship, completion state, due date, reminder fields, recurrence fields, series identifier, and timestamps;
- subtasks, including stable identifiers, task relationship, title, completion state, completion time, and position.

Completion History is represented by completed tasks and is therefore preserved without a separate collection.

The backup excludes DataStore UI preferences, selected filters and sort order, notification channels, scheduled AlarmManager entries, app language, theme, device settings, analytics, and cache data.

CSV, merge import, encryption, cloud storage, automatic scheduling, and backward export to older formats are outside v1.

## User Experience

`Backup and restore` appears in the existing Task List overflow menu and opens a dedicated Navigation 3 destination.

The screen contains two primary commands:

- `Create backup` launches `CreateDocument("application/json")` with a localized dated filename.
- `Restore backup` launches `OpenDocument` restricted to JSON-compatible documents.

After a file is selected, restore reads and validates it without changing Room. A preview displays the backup creation date and category, task, completed-task, and subtask counts. The user must then confirm a destructive warning stating that current planning data will be replaced.

The screen exposes loading state and localized results for successful export, successful restore, invalid file, unsupported version, read/write failure, and restore failure. Commands are disabled while an operation is active. The screen and confirmation dialog provide stable test tags and accessible labels.

The Activity Result contracts and `ContentResolver` streams remain at the Compose route/platform boundary. The ViewModel receives selected `Uri` values through typed events and delegates stream access to a platform gateway; it does not own an Activity or launcher.

## Architecture

The implementation follows the current single-module, feature-first Clean MVVM structure:

```text
feature/portability/
├── data/
│   ├── local/          Room snapshot and atomic replacement
│   ├── repository/     Repository implementation
│   └── serialization/  Versioned JSON DTOs, codec, and validator
├── di/                 Hilt bindings
├── domain/
│   ├── model/          Backup summary and typed results/errors
│   ├── repository/     PortabilityRepository contract
│   └── usecase/        CreateBackup, InspectBackup, RestoreBackup
├── navigation/         DataPortabilityKey
└── presentation/       Contract, route, screen, and ViewModel
```

The dependencies point inward:

```text
Compose -> ViewModel -> use cases -> repository contract
repository implementation -> JSON codec + Room data source + document gateway
RestoreBackup -> repository + ReminderScheduler
```

Room entities and Android `Uri` do not enter the domain model. The document gateway accepts an opaque document reference owned by the data/platform boundary. Serialization DTOs are format contracts and remain separate from Room entities so database and backup schemas can evolve independently.

## Backup Format

The UTF-8 JSON root is a Kotlin Serialization document with these required fields:

```json
{
  "format": "now-do-this-backup",
  "version": 1,
  "createdAtEpochMillis": 1786640000000,
  "categories": [],
  "tasks": []
}
```

Each task embeds its subtasks. Every persisted planning field has a corresponding explicit DTO field. Enum-like values use stable identifiers already used by the domain/database, such as `HIGH`, `WORK`, `BLUE`, `SCHEDULED`, and `WEEKLY`; localized labels are never serialized.

The encoder emits deterministic collection ordering: categories by position then ID, tasks by ID, and subtasks by position then ID. Unknown JSON keys are ignored for additive compatibility within a supported format version. Missing required fields, invalid field types, and unknown enum identifiers are rejected.

Version `1` is the only accepted version. A version greater than `1` returns `UnsupportedFutureVersion` before mutation. A non-positive version, wrong format identifier, or malformed document returns `InvalidBackup`.

The reader rejects documents larger than 10 MiB before unbounded allocation. This is comfortably above the expected local dataset while protecting the app from accidental oversized selections.

## Validation

Inspection decodes and validates the entire graph before presenting confirmation. Validation rejects:

- duplicate category, task, or subtask IDs;
- non-positive persisted IDs;
- a task category ID not present in the backup;
- a subtask whose embedded `taskId` differs from its parent task;
- blank task or subtask titles;
- a category with neither exactly one supported default key nor a non-blank custom name;
- a category with both default key and custom name;
- invalid priority, category color, reminder status, or recurrence identifiers;
- completed tasks without `completedAt`, incomplete tasks with `completedAt`, or equivalent inconsistent subtask completion fields;
- recurrence end before due date when both values exist;
- duplicate category positions or duplicate subtask positions within one task.

Timestamps are stored as epoch milliseconds and are not rejected merely for being in the past. Reminder timestamps may be past; post-restore reconciliation decides which future alarms are schedulable.

Validation returns typed errors suitable for localized user messages and test assertions. Raw parser, SQL, and filesystem exceptions are not exposed to the UI.

## Export Flow

1. The route launches Android `CreateDocument` and receives a writable document reference.
2. `CreateBackup` asks the repository for a consistent Room snapshot.
3. The Room data source reads categories and task/subtask relations inside one `withTransaction` block.
4. The repository maps entities to v1 DTOs and encodes deterministic UTF-8 JSON.
5. The document gateway writes the complete payload to the selected destination and closes the stream.
6. Success includes the exported counts; cancellation performs no work and is not shown as an error.

A read or encoding failure does not create a successful result. A destination write failure returns `WriteFailed`; partial external files are owned by the selected document provider and do not affect app data.

## Replace All Restore Flow

1. The route launches Android `OpenDocument` and receives a readable document reference.
2. `InspectBackup` reads at most 10 MiB, decodes, and validates the complete document.
3. The ViewModel retains the validated in-memory restore candidate and displays its summary.
4. The user confirms replacing current planning data.
5. `RestoreBackup` captures the current task IDs that may own alarms.
6. In one Room `withTransaction` block, the data source deletes tasks (cascading subtasks), deletes categories, inserts categories with original IDs, inserts tasks with original IDs, and inserts subtasks with original IDs.
7. Foreign keys and `ABORT` conflict behavior remain enabled. Any deletion or insertion failure rolls the entire transaction back, preserving the original database.
8. Only after commit, `RestoreBackup` cancels alarms for the pre-restore task IDs and invokes `ReminderScheduler.reconcile()` to schedule eligible restored future reminders.
9. Alarm cancellation or reconciliation failure does not roll back committed user data. It returns a successful restore with a reminder warning, because the database remains the source of truth and startup reconciliation can retry.

The Restore command cannot be executed from an unvalidated document reference; it consumes the validated candidate created by inspection. Confirming twice is idempotently blocked by ViewModel operation state.

## Duplicate And Identity Policy

Restore is Replace All, not merge. The final Room graph exactly represents the backup document. Existing tasks created after the backup are removed. Backup IDs are retained, preserving category relationships, subtask relationships, recurring series identifiers, notification deep-link task IDs, and future referential compatibility.

There is no conflict-resolution UI because current and backup records are never combined. A future merge/import feature requires a separate identity and conflict policy.

## Error Handling

Domain-facing failures are a closed set:

- `InvalidBackup`
- `UnsupportedFutureVersion(version)`
- `DocumentTooLarge`
- `ReadFailed`
- `WriteFailed`
- `RestoreFailed`
- `RestoredWithReminderWarning`

Cancellation from either system picker returns the screen to idle without an error. The preview remains available after a failed confirmation so the user may retry. Selecting another file replaces the previous preview only after the new file validates successfully.

No logs contain backup JSON, titles, descriptions, URI strings, or signing/private user data.

## Testing

Pure JVM tests cover:

- deterministic v1 encoding and full-field round trip;
- unknown-key tolerance;
- malformed JSON, wrong format, non-positive and future versions;
- the 10 MiB boundary;
- duplicate IDs and positions, broken category/subtask references, invalid enum identifiers, and inconsistent completion/recurrence state;
- use-case result mapping and reminder-warning behavior;
- ViewModel picker requests, cancellation, preview, confirmation, busy-state suppression, and localized-error state.

Room instrumentation tests use a real in-memory `AppDatabase` and prove:

- consistent snapshot ordering and all-field fidelity;
- Replace All removes records absent from the backup;
- IDs and relationships survive restoration;
- a forced insertion failure rolls back deletions and preserves the original graph;
- repeated restore of the same valid document produces the same final graph.

Compose tests cover screen commands, preview counts, destructive warning, progress state, accessible labels, and localized Italian/English resources. A connected journey creates planning data, exports it, changes the database, restores the exported document, and verifies the original task/category/subtask and completion state.

The standard JVM, lint, debug assembly, optimized release, and connected gates must pass. Release inspection confirms no debug fixture or backup payload is packaged unintentionally.

## Documentation And Release Impact

The README feature list, architecture index, verification matrix, changelog, release checklist, and migration/rollback documentation will link the backup format and tested restore behavior. A format reference documents every v1 field and compatibility rule.

This feature does not change the Room schema version because it adds queries and feature code without altering entities. Future Room fields require an explicit decision: add an optional/defaulted v1 JSON field when backward compatible, or introduce backup format version `2` with a migration path.

## Definition Of Done

Data Portability v1 is complete when:

- users can create a versioned JSON backup through Android's document picker;
- users can inspect and confirm a Replace All restore through the document picker;
- all supported planning fields, IDs, and relationships round-trip;
- invalid or future backups cannot mutate current data;
- valid restoration is atomic and tested against rollback;
- restored reminders are reconciled after commit;
- Italian and English UI/resources and accessibility contracts pass;
- format, limitations, verification evidence, and release implications are documented;
- the optimized release AAB builds and the working tree is clean.
