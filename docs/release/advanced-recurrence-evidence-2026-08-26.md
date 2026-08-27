# Advanced Recurrence Evidence - 2026-08-26

Task 8 evidence was gathered on 2026-08-27 from `feature/advanced_recurrence` based on `8194a1ce51abb9a02ac36cb86e8d2cb924077e03`.

## Observed automated evidence

- `./gradlew :app:testDebugUnitTest --rerun-tasks`: 436 passed, 0 failures, 0 errors, 0 skipped.
- Focused API 36 emulator journeys passed: 14 tests across
  `AdvancedRecurrenceJourneyTest`, `NaturalLanguageEntryJourneyTest`, and
  `CoreTaskJourneyTest`. The recreation, History command, and Core product-journey
  regressions passed three focused runs each after their fixture synchronization fixes;
  the exact-alarm Settings return regression also passed three focused runs.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`: 148 passed,
  0 failures, 0 errors, 0 skipped in an uninterrupted 4 minute 39 second Gradle run.
- `./gradlew :app:lintDebug`: 0 errors, 72 warnings.
- `./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:minifyReleaseWithR8 --rerun-tasks`:
  passed with all 105 tasks executed.

The shared emulator was a Medium Phone AVD running Android 16/API 36. The critical journeys cover parsed selected weekdays, save, physical alarm registry correlation, completion, next-occurrence creation, recurrence end, backup v2 export/restore, monthly short-month clamping, and completion-date interval scheduling. They restore rows, SQLite sequences, app locales, notification state, and alarms in `finally`.

Earlier interrupted and failing connected attempts remain documented in the Task 8
report and are not counted as pass evidence. Before the final run, process inspection
confirmed no competing connected-test, instrumentation, install, or uninstall process.
No physical-device, TalkBack, light/dark manual contrast, or physical 200% font-scale
check was run.

## Parser boundary

The deterministic parser supports the tested English and Italian recurrence forms. It does not support recurrence after an independent temporal clause: `every month, tomorrow, Mondays` is rejected as `AmbiguousRecurrence` and preserved for correction. This is a deliberate parser limit, not support for arbitrary mixed temporal clauses.

## Artifacts inspected

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `app-debug.apk` | 17,223,231 bytes | `6e5fd791689bc92699f752b063b4538e0a2332fdac90e52217451ccd18931af0` |
| `app-release-unsigned.apk` | 3,779,394 bytes | `8800eaff89c691175ac940058a261eeb4dceeabadb0a83694fffcb4bf3dfa3d6` |
| `app-release.aab` | 7,054,635 bytes | `4dd283cb803447fa03aa6c83246cd38f13132d8b6dcb5b60009c904c43000e89` |

The AAB contains `baseline.prof` (10,061 bytes), `baseline.profm` (621 bytes),
three DEX entries beginning with `base/dex/classes.dex`, and `base/resources.pb`
(826,849 bytes). R8 mapping output is present at
`app/build/outputs/mapping/release/mapping.txt` (47,333,226 bytes).
