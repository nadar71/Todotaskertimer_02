# Advanced Recurrence Design

## Context

Advanced Recurrence is the fourth Product Value subsystem for Now Do This. It extends the existing local-first task workflow after Data Portability, Quick Capture, and Natural-Language Entry. Italian remains the primary language and English remains fully supported through Android's native per-app language selection.

The feature stays in the single `:app` module and follows the existing feature-first Clean Architecture, MVVM, UDF, repository, coroutine, and Flow boundaries. It must not introduce accounts, network services, a generic calendar engine, or a second task persistence path.

## Goals

- Support every-N-unit interval recurrence.
- Support recurrence on selected weekdays.
- Support monthly recurrence by anchored date.
- Support monthly recurrence by ordinal weekday.
- Let users calculate recurrence from the scheduled date or completion date.
- Preserve calendar intent across overdue completion, short months, leap years, DST, and timezone changes.
- Extend deterministic Italian and English natural-language entry for the new rule types.
- Migrate existing tasks without changing their established recurrence behavior.
- Introduce a lossless backup v2 while continuing to import backup v1.
- Keep completion, next-occurrence creation, and reminder scheduling atomic and failure-safe.

## Non-goals

- RRULE import or export.
- Fifth-weekday monthly rules.
- Multiple recurrence rules on one task.
- Multiple reminders per occurrence.
- Generating a backlog of missed occurrences.
- Editing an independently materialized future series; only one next occurrence exists at a time.
- Exceptions such as holidays, skipped dates, or per-occurrence overrides.
- Open-ended natural-language understanding.
- Kotlin Multiplatform, synchronization, accounts, or collaboration.

## Product Decisions

### Default recurrence basis

- Interval rules default to `COMPLETION_DATE`.
- Selected-weekday and monthly calendar rules default to `SCHEDULED_DATE`.
- The editor lets the user override either default explicitly.

### Missed occurrences

Completing an overdue scheduled-date rule creates exactly one next occurrence strictly in the future. Intermediate missed occurrences are skipped. The application never floods the task list with a generated backlog.

### Short months

A monthly date rule retains its original anchor day. If a target month lacks that date, the occurrence uses the month's last valid day without changing the anchor. For example, an anchor of January 31 produces February 28 or 29 and then March 31.

### Backup evolution

Advanced recurrence introduces backup format v2. Version 2 exports every recurrence field losslessly and imports both v2 and legacy v1. Unsupported future versions are rejected before mutation.

## Domain Model

The behavioral recurrence model is typed rather than represented by a widening enum:

```kotlin
sealed interface RecurrenceRule {
    data object None : RecurrenceRule

    data class Interval(
        val unit: IntervalUnit,
        val every: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule

    data class SelectedWeekdays(
        val weekdays: Set<DayOfWeek>,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule

    data class MonthlyDay(
        val anchorDay: Int,
        val everyMonths: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule

    data class MonthlyOrdinal(
        val ordinal: MonthlyOrdinalValue,
        val weekday: DayOfWeek,
        val everyMonths: Int,
        val basis: RecurrenceBasis,
    ) : RecurrenceRule
}
```

`IntervalUnit` supports days and weeks. Month intervals use `MonthlyDay`, whose `everyMonths` and `anchorDay` preserve calendar intent across short months. `RecurrenceBasis` supports `SCHEDULED_DATE` and `COMPLETION_DATE`. `MonthlyOrdinalValue` supports first, second, third, fourth, and last.

Construction and validation enforce these invariants:

- `every` and `everyMonths` are within a documented finite range of 1 through 999.
- Selected weekdays are non-empty and contain only ISO weekdays.
- Monthly anchor days are 1 through 31.
- Ordinals are one of the five supported values.
- Every active recurrence requires a due timestamp.
- A recurrence end timestamp cannot precede the first due timestamp.
- `None` carries no recurrence parameters or recurrence end.

Legacy daily and weekly choices map to interval rules with `every = 1`. Legacy monthly maps to `MonthlyDay` with `everyMonths = 1` and an anchor derived from the task's original due date. The domain exposes no Room column representation.

## Next-occurrence Calculation

`CalculateNextOccurrence` is a pure domain component. Its inputs are the validated recurrence rule, scheduled due instant, actual completion instant, optional recurrence end, `ZoneId`, and reference time. Its output is either one next due instant or no occurrence.

### Shared guarantees

- Results are strictly later than the reference time.
- At most one occurrence is returned.
- Local wall-clock due time is preserved across timezone offset and DST transitions.
- A result after the recurrence end is not created.
- Arithmetic overflow and unsupported values return an explicit domain failure.
- Calculation never reads Android APIs, Room, locale resources, or the system clock directly.

### Scheduled-date basis

Calculation advances from the existing scheduled local date and time. When completion is overdue, it repeatedly advances the rule mathematically until it finds the first occurrence strictly after the reference time. It does not materialize skipped dates.

### Completion-date basis

Calculation anchors the next interval to the actual local completion date and time. The task's local due-time component is retained for date-based interval rules so completing at an unusual hour does not silently change the preferred reminder schedule.

### Rule behavior

- Day and week intervals advance by local calendar units rather than fixed milliseconds.
- Month intervals retain an anchor day and clamp each target month independently.
- Selected weekdays select the earliest configured weekday strictly in the future, preserving local due time.
- Monthly ordinal rules select the configured weekday occurrence in each eligible month.
- `last` means the final matching weekday in the month.

## Completion Workflow

`CompleteTask` remains the application orchestration boundary. In one repository transaction it:

1. Revalidates the current task snapshot.
2. Marks the current occurrence complete.
3. Calculates zero or one next occurrence.
4. Creates the next occurrence with a new task identifier while retaining the series identifier.
5. Copies title, description, category, priority, subtasks, recurrence rule, recurrence end, and reminder offset semantics.
6. Resets occurrence-specific completion and reminder-delivery state.
7. Commits both completion and next-task creation atomically.

Reminder cancellation for the completed occurrence and scheduling for the committed next occurrence remain coordinated through the existing reminder boundary. Failures must not leave an uncommitted task with a live alarm or a committed task without the retryable scheduling state already used by the app.

Quick Capture completion delegates to this same domain workflow and does not calculate recurrence independently.

## Persistence

Room stores recurrence in explicit flattened task columns. The migration adds a discriminant plus nullable parameters for interval unit/count, basis, weekday mask, monthly anchor day, ordinal, and ordinal weekday. Exact names and the next database version are selected in the implementation plan after inspecting the current schema export.

The mapper is the only boundary that translates flattened columns into a domain rule. It validates legal parameter combinations and rejects corrupt records explicitly. Production code does not silently coerce malformed combinations into a different rule.

### Legacy row migration

- `NONE` becomes `RecurrenceRule.None`.
- `DAILY` becomes every one day with scheduled-date basis.
- `WEEKLY` becomes every one week with scheduled-date basis.
- `MONTHLY` becomes `MonthlyDay` every one month, anchored to the original due date's local day, with scheduled-date basis.

The legacy recurrence column remains only if required for a safe staged Room migration; the final mapped schema has one authoritative representation. Forward migration is mandatory and destructive migration is prohibited.

## Data Portability v2

Backup v2 replaces the legacy recurrence string in each task with a structured recurrence object containing its rule kind, basis, and rule-specific parameters. `None` is represented explicitly and carries no active parameters.

The importer supports:

- v2 lossless recurrence decoding and validation;
- v1 conversion from `NONE`, `DAILY`, `WEEKLY`, and `MONTHLY`;
- rejection of unsupported future versions;
- rejection of unknown rule kinds and invalid parameter combinations.

Replace All behavior is unchanged: decode and validate the entire backup before opening the mutation transaction, then replace all supported app data atomically. Any recurrence error leaves existing app data and alarms unchanged. Deterministic serialization and full-field round trips remain required.

## Editor UDF

The existing task editor owns one immutable recurrence draft. The recurrence section first selects one of:

- Does not repeat
- Interval
- Selected weekdays
- Monthly by date
- Monthly by weekday position

Only controls relevant to the selected rule are rendered:

- bounded numeric input or stepper for interval counts;
- unit menu for day or week;
- weekday toggles for selected weekdays;
- anchor day and month interval for monthly date rules;
- ordinal, weekday, and month interval menus for monthly ordinal rules;
- a two-option segmented control for scheduled-date versus completion-date basis;
- the existing optional recurrence end date.

Events update the draft through the ViewModel's UDF reducer. Validation errors are represented in state and rendered as localized field errors. Invalid drafts survive `SavedStateHandle` recreation but cannot be saved. Switching rule type clears parameters that do not belong to the new type, applies the approved default basis, and never leaves hidden active values.

The layout uses stable dimensions, minimum 48 dp interactive targets, and remains usable at 200 percent font scale in Italian and English.

## Natural-language Entry

The deterministic offline parser adds focused Italian and English grammar for:

- every N days, weeks, or months;
- selected weekday lists;
- first, second, third, fourth, or last weekday of each month;
- an explicit recurrence basis only where the phrase is unambiguous.

Representative accepted forms include `every 2 weeks`, `ogni 3 giorni`, `every Monday and Friday`, `ogni lunedi e venerdi`, `last Friday of every month`, and `ultimo venerdi del mese`. Accent-insensitive matching follows the existing normalizer while title slicing preserves original user text outside consumed ranges.

Existing daily, weekly, and monthly grammar remains backward compatible. Duplicate, contradictory, malformed, unsupported, or partial recurrence phrases produce a parse issue, consume no recurrence text, and preserve that text in the title. Parsed rules populate the same correctable editor draft and are never persisted directly by the parser.

## Localization And Accessibility

All new labels, options, validation messages, and semantics are provided in primary Italian and English resource files. Locale selection continues to use Android native per-app language APIs. Tests obtain configuration-correct resources after locale transitions.

Weekday presentation follows locale ordering and localized names while persistence uses ISO `DayOfWeek`. Controls expose selected state and meaningful content descriptions. Screen-reader order follows visual order. No meaning depends only on color.

## Error Handling

- Invalid editor state blocks save and identifies the affected field.
- Invalid persisted combinations surface a controlled data failure.
- Unsupported backup schemas and recurrence rules fail before mutation.
- Calculation overflow or impossible inputs produce explicit domain failures.
- A recurrence end reached normally produces no next task and is not an error.
- Reminder operations retain rollback or retry semantics established by the existing task workflow.
- Parser ambiguity preserves the user's original text and applies no rule.

## Testing Strategy

### JVM domain tests

- Every-N day, week, and month matrices for both bases.
- Selected weekdays across week and year boundaries.
- Monthly anchor behavior for days 28 through 31, leap years, and repeated clamping.
- Monthly ordinal behavior for every supported ordinal and weekday.
- DST gaps, DST overlaps, timezone offsets, and wall-clock preservation.
- Overdue scheduled tasks skip missed occurrences and create exactly one future result.
- Recurrence end boundaries, overflow, and invalid rule rejection.
- Completion orchestration, series identity, copied fields, and reminder state.

### Persistence and portability tests

- Room migration from every supported prior schema path.
- Legacy row conversion and mapper corruption rejection.
- Atomic completion and next-occurrence insertion.
- Backup v2 deterministic round trip.
- Backup v1 import conversion.
- Unsupported or invalid backup rejection with no data or alarm mutation.

### Presentation and parser tests

- UDF event/state transitions and rule-type cleanup.
- Saved-state restoration of valid and invalid drafts.
- Italian and English labels, semantics, and error messages.
- Focused grammar, duplicate ownership, ambiguity, and exact title preservation.
- Compose geometry and interaction at 200 percent font scale.

### Connected journeys

- Edit and save each recurrence family.
- Complete, create one next occurrence, and schedule its reminder.
- Complete overdue calendar recurrence and prove missed dates are not materialized.
- Exercise app-locale changes without inheriting stale resources.
- Run the critical journey: capture, parse, schedule, remind, complete, recur, export, and restore.

## Documentation And Release Evidence

Update architecture data flow, test strategy, accessibility checklist, verification matrix, release checklist, backup format documentation, migration and rollback guidance, README, and changelog. Claims distinguish JVM, emulator, and physical-device evidence. Release verification includes debug and release builds, lint, AAB inspection, R8, schema exports, and deterministic backup fixtures.

## Definition Of Done

Advanced Recurrence is complete when:

- All four advanced rule families calculate deterministically for both recurrence bases.
- Existing recurring tasks migrate without behavioral regression.
- Overdue calendar tasks create one future occurrence and no backlog.
- Monthly anchors survive short months without drifting.
- Completion and next-occurrence persistence are atomic and reminder-safe.
- Backup v2 is lossless and backup v1 remains importable.
- Italian and English editor and focused parser flows are correctable and accessible.
- JVM, migration, Compose, connected journey, lint, build, R8, and release-bundle gates pass.
- Independent review finds no load-bearing specification or code-quality issue.
