# Advanced Recurrence Evidence - 2026-08-26

Task 8 evidence was gathered on 2026-08-27 from `feature/advanced_recurrence` based on `8194a1ce51abb9a02ac36cb86e8d2cb924077e03`.

## Observed automated evidence

- `./gradlew :app:testDebugUnitTest --rerun-tasks`: 436 passed, 0 failures, 0 errors, 0 skipped.
- Focused API 36 emulator journeys passed: four `AdvancedRecurrenceJourneyTest` cases, `CoreTaskJourneyTest`, and Italian and English selected-weekday recreation checks.
- `./gradlew :app:lintDebug`: 0 errors, 72 warnings.
- `./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:minifyReleaseWithR8`: passed.

The shared emulator was a Medium Phone AVD running Android 16/API 36. The critical journeys cover parsed selected weekdays, save, physical alarm registry correlation, completion, next-occurrence creation, recurrence end, backup v2 export/restore, monthly short-month clamping, and completion-date interval scheduling. They restore rows, SQLite sequences, app locales, notification state, and alarms in `finally`.

Two attempted full 147-test connected runs were interrupted by a concurrent checkout uninstalling the shared target package. They are not counted as passed evidence. No physical-device, TalkBack, light/dark manual contrast, or physical 200% font-scale check was run.

## Parser boundary

The deterministic parser supports the tested English and Italian recurrence forms. It does not support recurrence after an independent temporal clause: `every month, tomorrow, Mondays` is rejected as `AmbiguousRecurrence` and preserved for correction. This is a deliberate parser limit, not support for arbitrary mixed temporal clauses.

## Artifacts inspected

| Artifact | SHA-256 |
| --- | --- |
| `app-debug.apk` | `310fbddbb21eb15e74518392ab0f8185e36344937feda4b9b54f53fd9d9f7821` |
| `app-release-unsigned.apk` | `7cf3332cdef9928db58390408cd697ad158b4e5772dc18b622d3fc61d94d510f` |
| `app-release.aab` | `182e105a41ce7416f1276cb2132e25844d67ebfb6512e361308bad225486844d` |

The AAB contains `baseline.prof`, `baseline.profm`, `base/dex/classes.dex`, and `base/resources.pb`; R8 mapping output is present under `app/build/outputs/mapping/release`.
