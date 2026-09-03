# Now Do This

Now Do This is a local-first Android task manager built with Kotlin and Jetpack Compose. It supports explicit offline Italian/English natural-language task entry, task and subtask completion, categories, priorities, due dates, one reminder, recurrence, Calendar planning, completion History, home-screen Quick Capture, user-owned JSON backup/restore, and Android-native per-app language selection. Italian is the primary language and English is included.

## Product

| Task list | Task editor | Calendar |
| --- | --- | --- |
| ![Task list in English](docs/images/task-list-en-light.png) | ![Task editor in English](docs/images/task-editor-en-light.png) | ![Calendar in English](docs/images/calendar-en-light.png) |

| Categories | History |
| --- | --- |
| ![Categories in Italian](docs/images/categories-it-light.png) | ![History in English, dark theme](docs/images/history-en-dark.png) |

| Quick Capture, English light | Quick Capture, Italian dark |
| --- | --- |
| ![Quick Capture widget in English light theme at medium size](docs/images/quick-capture-en-light-medium.png) | ![Quick Capture widget in Italian dark theme at expanded size](docs/images/quick-capture-it-dark-expanded.png) |

Core workflows include parse-preview-correct-save Quick entry, grouped pending tasks, completion and recurring occurrence generation, user-managed categories, task search and sorting, swipe-delete undo, a monthly Calendar, searchable read-only completion History, home-screen add/open/complete actions, and Storage Access Framework backup with confirmed Replace All restore.

The [backup format v1 reference](docs/data-portability/backup-format-v1.md) documents the complete planning-data contract, compatibility policy, privacy scope, and restore semantics.

## Architectural Highlights

- Single `:app` production module with feature-first packages for `task`, `category`, `calendar`, `history`, `naturallanguage`, `quickcapture`, `portability`, and `ads`.
- Clean MVVM boundaries: Compose UI, ViewModel, reusable use cases, domain repository contracts, and offline implementations.
- Unidirectional data flow with immutable UI state, UI events, `StateFlow`, and one-off effect flows.
- Local-first persistence with Room and DataStore plus user-directed JSON documents; no account, backend, analytics SDK, or network synchronization. Google UMP and Mobile Ads are an isolated, consent-gated network boundary and never receive task data.
- Hilt dependency injection and coroutines/Flow across asynchronous boundaries.
- Navigation 3 with serializable navigation keys and lifecycle-aware ViewModel stores.
- Pure deterministic natural-language parsing with injected language/time/category input; the existing editor and SaveTask flow remain authoritative.

The [architecture index](docs/architecture/README.md) links system context, data flow, platform boundaries, ADRs, and objective triggers for future modularization.

## Data And UDF Flow

Compose screens emit typed events to ViewModels. ViewModels reduce repository and use-case flows into immutable screen state; repositories coordinate Room, DataStore, and Android platform adapters. Reusable business invariants and cross-boundary workflows live in use cases, while screen-specific orchestration remains in ViewModels.

See the [documented UDF flow](docs/architecture/data-flow.md) and [Clean MVVM decision](docs/architecture/adr/0003-clean-mvvm-and-udf.md).

## Android Platform Depth

- Room schema exports, explicit migrations, DAO/transaction instrumentation tests, and forward-only migration policy.
- Atomic full-fidelity planning backup/restore with versioned serialization, graph validation, stable IDs, and post-commit reminder reconciliation.
- AlarmManager reminders with exact-alarm capability fallback, notification permission handling, boot/startup reconciliation, and editor deep links.
- A responsive Glance Quick Capture widget backed directly by Room, with 3/5/8-task host sizes and process-independent add, open, complete, and refresh paths.
- Android-native per-app locale selection with generated locale configuration.
- Accessibility semantics for roles, selected/toggle states, localized spoken descriptions, and minimum interactive targets.
- R8 optimization, resource shrinking, optional environment-based release signing, mapping artifacts, and packaged Baseline Profile.

## Quality And Performance Evidence

Pull requests and pushes to `develop` run compilation, JVM tests, lint, and debug assembly through [Android quality CI](.github/workflows/android-quality.yml). The repository also contains:

- [Test strategy](docs/quality/test-strategy.md)
- [Verification matrix](docs/quality/verification-matrix.md)
- [Accessibility checklist](docs/quality/accessibility-checklist.md)
- [Macrobenchmark and Baseline Profile procedure](docs/performance/README.md)
- [Emulator performance results](docs/performance/results-2026-08.md)
- [Release checklist](docs/release/checklist.md)
- [Natural-Language Entry release evidence](docs/release/natural-language-entry-evidence-2026-08-26.md)
- [Advanced Recurrence release evidence](docs/release/advanced-recurrence-evidence-2026-08-26.md)

Recorded emulator measurements validate repeatable benchmark infrastructure but are not presented as physical-device performance claims.

## Build And Run

Use JDK 17 and an Android SDK with API 36 installed.

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease :app:bundleRelease
```

The release build remains unsigned unless all four `NOWDOTHIS_*` signing variables documented in [release discipline](docs/release/README.md) are supplied.

## Trade-offs And Revisit Triggers

The single production module is deliberate for the current team size and domain. A new Gradle module requires separate ownership, enforceable dependency isolation, reusable infrastructure, isolated build/testing, or measured build-time benefit. Local-first storage and deterministic offline parsing avoid account and model-service complexity, while Natural-Language Entry deliberately supports a bounded grammar rather than open-ended interpretation. The only remote runtime dependency is consent-gated advertising; core planning remains fully usable offline. Advanced Recurrence uses typed interval, selected-weekday, monthly-date, and monthly-ordinal rules with scheduled-date or completion-date bases; overdue schedules produce one future occurrence, monthly anchors recover after short months, and backup v2 preserves every rule field. The parser does not support recurrence after an independent temporal clause: `every month, tomorrow, Mondays` is rejected for correction as one ambiguous recurrence attempt. User-owned backup provides manual portability, but there is still no account-based synchronization, automatic cloud backup, merge import, or cross-device conflict resolution.

The ADRs record rejected alternatives and concrete revisit triggers rather than treating current choices as permanent.

## Roadmap

Candidate product investments include richer recurrence, configurable widget views, and improved planning workflows. KMP/iOS remains deferred until product behavior and shared-domain value justify the additional platform and architecture cost.
