# Now Do This Backup Format v1

This document defines the JSON contract written and accepted by Now Do This backup
format version 1. A backup is a UTF-8 JSON document containing user-owned planning
data only.

## Root Object

All fields are required. Nullable fields must be present and may contain JSON `null`.

| Field | JSON type | Nullable | Contract |
| --- | --- | --- | --- |
| `format` | string | No | Exactly `now-do-this-backup` |
| `version` | number (integer) | No | Exactly `1` |
| `createdAtEpochMillis` | number (integer) | No | Backup creation instant in Unix epoch milliseconds |
| `categories` | array | No | Category objects |
| `tasks` | array | No | Task objects with embedded subtasks |

## Category Object

| Field | JSON type | Nullable | Contract |
| --- | --- | --- | --- |
| `id` | number (integer) | No | Positive stable Room identifier |
| `customName` | string | Yes | Non-blank custom name, or `null` for a default category |
| `defaultKey` | string | Yes | Stable default-category value, or `null` for a custom category |
| `colorToken` | string | No | Stable color value |
| `position` | number (integer) | No | Zero-based, unique category position |
| `createdAt` | number (integer) | No | Creation instant in Unix epoch milliseconds |

Exactly one of `customName` and `defaultKey` identifies a category. Stable
`defaultKey` values are `WORK`, `PERSONAL`, and `WISHLIST`. Stable `colorToken`
values are `BLUE`, `GREEN`, and `PINK`.

## Task Object

| Field | JSON type | Nullable | Contract |
| --- | --- | --- | --- |
| `id` | number (integer) | No | Positive stable Room identifier |
| `title` | string | No | Non-blank task title |
| `description` | string | No | Task description; may be empty |
| `priority` | string | No | Stable priority value |
| `categoryId` | number (integer) | Yes | Existing category ID, or `null` |
| `isCompleted` | boolean | No | Completion state |
| `completedAt` | number (integer) | Yes | Completion instant in epoch milliseconds; required when completed |
| `dueAt` | number (integer) | Yes | Due instant in epoch milliseconds |
| `reminderAt` | number (integer) | Yes | Reminder instant in epoch milliseconds |
| `reminderStatus` | string | No | Stable reminder state |
| `recurrence` | string | No | Stable recurrence rule |
| `recurrenceEndAt` | number (integer) | Yes | Recurrence end in epoch milliseconds; not before `dueAt` |
| `seriesId` | string | Yes | Stable recurring-series identifier |
| `createdAt` | number (integer) | No | Creation instant in epoch milliseconds |
| `updatedAt` | number (integer) | No | Last-update instant in epoch milliseconds |
| `subtasks` | array | No | Embedded subtask objects |

Stable `priority` values are `HIGH`, `MEDIUM`, and `LOW`. Stable
`reminderStatus` values are `NONE`, `REQUESTED`, `SCHEDULED`, and `UNAVAILABLE`.
Stable `recurrence` values are `NONE`, `DAILY`, `WEEKLY`, and `MONTHLY`.

For a completed task, `isCompleted` is `true` and `completedAt` is non-null. For an
incomplete task, `isCompleted` is `false` and `completedAt` is `null`.

## Subtask Object

| Field | JSON type | Nullable | Contract |
| --- | --- | --- | --- |
| `id` | number (integer) | No | Positive stable Room identifier |
| `taskId` | number (integer) | No | ID of the containing task |
| `title` | string | No | Non-blank subtask title |
| `isCompleted` | boolean | No | Completion state |
| `completedAt` | number (integer) | Yes | Completion instant in epoch milliseconds; required when completed |
| `position` | number (integer) | No | Zero-based position unique within the task |

Subtask completion fields follow the same consistency rule as task completion fields.

## Encoding And Compatibility

- Export ordering is deterministic: categories by `position` then `id`, tasks by
  `id`, and each task's subtasks by `position` then `id`.
- Version 1 ignores unknown JSON keys so additive metadata from a compatible writer
  does not prevent restore.
- UTF-8 decoding is strict. Malformed byte sequences are invalid and are never replaced
  with substitute characters before restore.
- Missing required fields, invalid JSON types, unknown stable values, duplicate IDs
  or positions, and broken category/subtask references are invalid.
- Only version `1` is supported. The required `format` and `version` envelope is read
  before the v1 payload. A version greater than `1` with the correct format is rejected
  as a future format even when its payload has a different schema. Missing headers,
  a wrong format, and zero, negative, or malformed versions are invalid.
- The maximum generated or accepted document size is 10 MiB (10,485,760 bytes).
  Larger exports are rejected before writing, and larger imports are rejected before
  an unbounded in-memory read.
- Timestamps are signed epoch-millisecond integers. Past dates are retained; alarm
  eligibility is decided after restore.

## Restore Semantics

Restore uses **Replace All**, never merge. The complete document is read, decoded,
and validated before confirmation. After confirmation, Room deletes the current task
and category graph and inserts the backup graph, preserving all IDs and relationships,
inside one transaction. A database failure rolls back the complete replacement.

Scheduled alarms are device state and are not serialized. Only after the Room
transaction commits, the app cancels alarms for the replaced task IDs and reconciles
eligible reminders from restored data. Reminder reconciliation failure does not undo
restored planning data and is reported as a warning.

## Privacy And Scope

The file can contain task titles, descriptions, category names, dates, and planning
history. Treat it as private user data and store or share it accordingly. The format
is not encrypted and the app does not upload it.

The backup excludes DataStore UI preferences, theme, app language, selected filters
and sorting, notification channels, AlarmManager entries, device settings, analytics,
and cache data.

## Redacted Example

This structurally complete example uses generic values rather than real user data.

```json
{
  "format": "now-do-this-backup",
  "version": 1,
  "createdAtEpochMillis": 1786640000000,
  "categories": [
    {
      "id": 10,
      "customName": "Example category",
      "defaultKey": null,
      "colorToken": "GREEN",
      "position": 0,
      "createdAt": 1786500000000
    }
  ],
  "tasks": [
    {
      "id": 20,
      "title": "Example task",
      "description": "Redacted example description",
      "priority": "HIGH",
      "categoryId": 10,
      "isCompleted": false,
      "completedAt": null,
      "dueAt": 1786900000000,
      "reminderAt": 1786896400000,
      "reminderStatus": "SCHEDULED",
      "recurrence": "WEEKLY",
      "recurrenceEndAt": 1789500000000,
      "seriesId": "example-series",
      "createdAt": 1786500001000,
      "updatedAt": 1786600000000,
      "subtasks": [
        {
          "id": 30,
          "taskId": 20,
          "title": "Example step",
          "isCompleted": false,
          "completedAt": null,
          "position": 0
        }
      ]
    }
  ]
}
```
