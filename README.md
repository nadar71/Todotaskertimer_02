# Now Do This

Now Do This is a local-first Android task manager built with Kotlin and Jetpack Compose.

## Features

- Pending tasks grouped into overdue, today, upcoming, and unscheduled sections
- Completion for tasks and subtasks, including recurring-task occurrence generation
- User-managed categories with localized sensible defaults, colors, ordering, and deletion
- Optional due date, one reminder, recurrence, and recurrence end date
- Monthly calendar with task counts and date-prefilled task creation
- Read-only completion history with search, category filters, inspection, and confirmed permanent deletion
- Priority sorting, saved preferences, search, delete-all confirmation, and swipe-delete undo
- Reminder notifications with exact-alarm fallback, startup reconciliation, and task-editor deep links
- Italian default resources, English alternative resources, and Android native per-app language selection

## Architecture

Now Do This deliberately uses a single `:app` Gradle module with feature-first Clean MVVM packages and local-first storage. ViewModels use domain repository contracts directly for simple reads, mutations, and screen coordination; multi-step workflows use use cases. See the [architecture documentation and decision records](docs/architecture/README.md) for system context, UDF data flow, platform boundaries, trade-offs, and objective modularization triggers.

## Build And Test

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The connected suite includes Room migration/DAO tests, feature Compose tests, navigation contracts, localization checks, and a production-activity journey covering category creation, recurring task completion, Calendar, and History.

## Verification Status

Automated JVM tests, Android-test compilation, lint, and debug assembly are required before integration. The following physical/emulator checks remain intentionally recorded as manual gates until those API levels are attached:

| Platform | Verification matrix | Status |
| --- | --- | --- |
| API 23 | Italian/English resources; create, edit, delete, undo; recurrence; Calendar; process recreation | Pending device run |
| API 36 (Android 16) | 55-test connected suite; native Italian selection; category creation; monthly recurrence; subtask; completion; Calendar; History; 200% font spot-check | Automated pass |
| API 33+ | Notification denial/grant; exact-alarm denial/fallback and grant; notification deep link; reboot reconciliation | Pending manual run |

Do not mark these rows passed from compile-only CI; record the tested API level and outcome after running on the corresponding device or emulator.
