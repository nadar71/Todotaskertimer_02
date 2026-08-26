# Now Do This Backup Format v2

This document defines the JSON contract written and accepted by Now Do This backup
format version 2. Version 2 is the sole export format. Imports continue to accept the
legacy version 1 contract described in [backup-format-v1.md](backup-format-v1.md).

## Root Object

All root fields are required. Nullable fields must be present and may contain JSON
`null`.

| Field | JSON type | Nullable | Contract |
| --- | --- | --- | --- |
| `format` | string | No | Exactly `now-do-this-backup` |
| `version` | number (integer) | No | Exactly `2` |
| `createdAtEpochMillis` | number (integer) | No | Backup creation instant in Unix epoch milliseconds |
| `categories` | array | No | Category objects |
| `tasks` | array | No | Task objects with embedded subtasks |

Category and subtask objects are unchanged from version 1. Task fields are also
unchanged except that the legacy `recurrence` string is replaced by the structured
recurrence object below. Stable category, color, priority, reminder, completion,
identifier, relationship, and timestamp rules remain the same as version 1.

## Task Object

| Field | JSON type | Nullable | Contract |
| --- | --- | --- | --- |
| `id` | number (integer) | No | Positive stable Room identifier |
| `title` | string | No | Non-blank task title |
| `description` | string | No | Task description; may be empty |
| `priority` | string | No | `HIGH`, `MEDIUM`, or `LOW` |
| `categoryId` | number (integer) | Yes | Existing category ID, or `null` |
| `isCompleted` | boolean | No | Completion state |
| `completedAt` | number (integer) | Yes | Required when completed; otherwise `null` |
| `dueAt` | number (integer) | Yes | Required for active recurrence |
| `reminderAt` | number (integer) | Yes | Reminder instant in epoch milliseconds |
| `reminderStatus` | string | No | `NONE`, `REQUESTED`, `SCHEDULED`, or `UNAVAILABLE` |
| `recurrence` | object | No | Structured recurrence rule |
| `recurrenceEndAt` | number (integer) | Yes | Active-rule end, not before `dueAt`; `null` for `NONE` |
| `seriesId` | string | Yes | Stable recurring-series identifier |
| `createdAt` | number (integer) | No | Creation instant in epoch milliseconds |
| `updatedAt` | number (integer) | No | Last-update instant in epoch milliseconds |
| `subtasks` | array | No | Embedded subtask objects |

## Recurrence Object

The `kind` discriminant is required. Each kind must contain exactly the fields in its
row: missing, unknown, or fields belonging to another kind are invalid even when their
value is `null`.

| `kind` | Required fields | Contract |
| --- | --- | --- |
| `NONE` | `kind` | No active parameters |
| `INTERVAL` | `kind`, `unit`, `every`, `basis` | `unit` is `DAYS` or `WEEKS`; `every` is 1 through 999 |
| `SELECTED_WEEKDAYS` | `kind`, `basis`, `weekdays` | Non-empty, duplicate-free ISO weekday names |
| `MONTHLY_DAY` | `kind`, `basis`, `anchorDay`, `everyMonths` | `anchorDay` is 1 through 31; `everyMonths` is 1 through 999 |
| `MONTHLY_ORDINAL` | `kind`, `basis`, `ordinal`, `weekday`, `everyMonths` | Ordinal weekday rule repeated every 1 through 999 months |

Stable `basis` values are `SCHEDULED_DATE` and `COMPLETION_DATE`. Stable weekday
values are `MONDAY` through `SUNDAY`. Stable `ordinal` values are `FIRST`, `SECOND`,
`THIRD`, `FOURTH`, and `LAST`.

Exported weekday arrays use ISO order from Monday through Sunday. Unknown kinds,
tokens, duplicate weekdays, empty weekday sets, out-of-range numbers, missing active
`dueAt`, and recurrence ends before `dueAt` are invalid. Invalid recurrence is never
coerced to another rule.

## Encoding And Compatibility

- UTF-8 decoding is strict. Malformed byte sequences are invalid.
- Export ordering is deterministic: categories by `position` then `id`, tasks by `id`,
  subtasks by `position` then `id`, and selected weekdays by ISO weekday number.
- All root, category, task, and subtask fields are encoded. Nullable fields are encoded
  explicitly as JSON `null`. A recurrence object encodes only the fields valid for its
  discriminated kind; it has no unrelated nullable placeholders.
- Unknown root, category, task, and subtask keys are ignored for additive compatibility.
  Recurrence-object keys are strict because accepting an unused parameter could hide a
  changed rule meaning.
- The envelope is decoded first. Import dispatches exactly to version `1` or `2`.
  Version `3` and later are rejected as future formats before full payload decoding;
  zero, negative, missing, or malformed versions are invalid.
- The maximum generated or accepted document size is 10 MiB (10,485,760 bytes).

## Restore Semantics

The complete document is read, version-dispatched, decoded into typed planning data,
and validated before confirmation or mutation. Restore uses Replace All inside one
Room transaction, preserving IDs and relationships. Any mapping, deletion, insertion,
constraint, or transaction error rolls back rows and SQLite sequences.

Alarms are not serialized. They remain untouched when decoding, validation, or the
Room replacement fails. Only after a successful commit does the app cancel alarms for
replaced task IDs and reconcile eligible reminders from restored rows. Reminder work
that fails after commit produces a warning and does not roll back restored planning
data.

## Redacted Example

```json
{
  "format": "now-do-this-backup",
  "version": 2,
  "createdAtEpochMillis": 1786640000000,
  "categories": [],
  "tasks": [
    {
      "id": 20,
      "title": "Example task",
      "description": "Redacted example description",
      "priority": "HIGH",
      "categoryId": null,
      "isCompleted": false,
      "completedAt": null,
      "dueAt": 1786900000000,
      "reminderAt": 1786896400000,
      "reminderStatus": "SCHEDULED",
      "recurrence": {
        "kind": "MONTHLY_ORDINAL",
        "basis": "COMPLETION_DATE",
        "everyMonths": 2,
        "ordinal": "LAST",
        "weekday": "FRIDAY"
      },
      "recurrenceEndAt": null,
      "seriesId": "example-series",
      "createdAt": 1786500001000,
      "updatedAt": 1786600000000,
      "subtasks": []
    }
  ]
}
```

## Privacy And Scope

The privacy and exclusion scope is unchanged from version 1. The file is local,
unencrypted user data and can contain task text, category names, dates, and history.
