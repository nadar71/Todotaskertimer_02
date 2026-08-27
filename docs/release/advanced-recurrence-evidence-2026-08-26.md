# Advanced Recurrence Evidence - 2026-08-26

Task 8 evidence was gathered on 2026-08-27 from `feature/advanced_recurrence` based on `8194a1ce51abb9a02ac36cb86e8d2cb924077e03`.

## Observed automated evidence

- `./gradlew :app:testDebugUnitTest --rerun-tasks`: 436 passed across 35 suites, 0 failures, 0 errors, 0 skipped in 1 minute 34 seconds.
- Focused API 36 emulator journeys passed: 14 tests across
  `AdvancedRecurrenceJourneyTest`, `NaturalLanguageEntryJourneyTest`, and
  `CoreTaskJourneyTest` in 1 minute 36 seconds. The production headline passed three
  completed focused runs after extension through reminder scheduling and portability.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`: 148 passed,
  0 failures, 0 errors, 0 skipped in an uninterrupted 6 minute 39 second Gradle run;
  generated XML records 376.344 seconds of test time.
- `./gradlew :app:lintDebug`: 0 errors, 72 warnings.
- `./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:minifyReleaseWithR8 --rerun-tasks`:
  passed with all 105 tasks executed in 3 minutes 41 seconds.

The shared emulator was a Medium Phone AVD running Android 16/API 36. The headline
journey now drives `MainActivity` through Navigation 3 capture, parse, editor save, and
task-list completion with a real reminder. The exact production-created source and
successor IDs, series, selected-weekday rule, due/reminder ownership, Room rows, and
package alarm set are then carried through production backup v2 export, destructive
mutation of that successor, and Replace All restore. The restored task entities match
their pre-export snapshots exactly, and the successor owns the sole restored alarm.
The surrounding critical journeys retain recurrence-end, monthly short-month clamp,
and completion-date scheduling coverage. Their fixtures restore rows, SQLite sequences,
app locales, notification state, and alarms in `finally`.

Earlier interrupted and failing connected attempts remain documented in the Task 8
report and are not counted as pass evidence. One rapid focused relaunch failed in the
Android runner before the test body because its UiAutomation service was still
registered; after a release interval, the third completed focused pass succeeded.
Before the final run, process inspection confirmed no competing connected test,
instrumentation, install, uninstall, or app process.
No physical-device, TalkBack, light/dark manual contrast, or physical 200% font-scale
check was run.

## Parser boundary

The deterministic parser supports the tested English and Italian recurrence forms. It does not support recurrence after an independent temporal clause: `every month, tomorrow, Mondays` is rejected as `AmbiguousRecurrence` and preserved for correction. This is a deliberate parser limit, not support for arbitrary mixed temporal clauses.

## Artifacts inspected

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `app-debug.apk` | 17,223,231 bytes | `6e5fd791689bc92699f752b063b4538e0a2332fdac90e52217451ccd18931af0` |
| `app-release-unsigned.apk` | 3,779,394 bytes | `09f27338e5187fae12ea8db79f88fbe363311520d036a0a5e95926a0b17301ba` |
| `app-release.aab` | 7,054,635 bytes | `d33a7c24a835824b8631cc6521e2d1e0a2756cbaa51b0ab36c2d0b97d24f556c` |

The AAB contains `baseline.prof` (10,061 bytes), `baseline.profm` (621 bytes),
three DEX entries beginning with `base/dex/classes.dex`, and `base/resources.pb`
(826,849 bytes). R8 mapping output is present at
`app/build/outputs/mapping/release/mapping.txt` (47,333,226 bytes).
