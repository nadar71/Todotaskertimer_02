# Natural-Language Entry Release Evidence

## Scope And Environment

- Date: 2026-08-26
- Base revision: `9930301f46a1ba9ffb75b4b0233a39a0afa2d258`
- Device: `emulator-5554`, Medium Phone AVD (`sdk_gphone64_arm64`)
- OS: Android 16 / API 36, build fingerprint
  `google/sdk_gphone64_arm64/emu64a:16/BP22.250325.006/13344233:user/release-keys`
- Host: macOS, JDK 17, Android SDK 36; Android CLI `1.0.15498356`
- Device font scale after testing: `1.0`
- Release signing: no `NOWDOTHIS_*` signing variables were supplied, so the
  recorded local release APK and AAB are intentionally unsigned

Natural-Language Entry is deterministic and offline. The production
`ParseNaturalLanguageTask` use case receives raw text, active app language, a clock
snapshot, time zone, and current Room-backed category candidates. It returns a typed
draft and nonfatal issues; it has no Android, Compose, Room, network, AI, or automatic
save dependency. `TaskEditorViewModel` applies recognized values atomically to the
existing editor state. The user still corrects fields, supplies the required
description, and invokes the existing `SaveTask`/Room/AlarmManager path.

## Product Journeys

`NaturalLanguageEntryJourneyTest` launches production `MainActivity`, follows the
Navigation 3 add path, renders the production `TaskEditorRoute`, parses through the
injected production parser, and saves through the production ViewModel and use case.
It does not use a test parser, in-memory task repository, direct task insert, or
test-only persistence path.

The retained Italian and English journeys independently prove:

1. a hybrid phrase populates title, due time, reminder, high priority, current custom
   category, and weekly recurrence in visible editor controls;
2. the user corrects high priority to medium;
3. the parsed description remains blank, then the user supplies the required
   description;
4. the normal Save action persists the corrected task through Room;
5. the persisted row contains the expected title, description, due time, reminder
   time, category ID, `MEDIUM` priority, `WEEKLY` recurrence, and `SCHEDULED` reminder
   status;
6. Save returns to the task list, proven by the list-only `task-search` and `task-add`
   tags, absence of the editor and Quick entry tags, and then the persisted title; and
7. the device AlarmManager registry contains exactly one `RTC_WAKEUP` alarm whose
   package, receiver, request code, and trigger correspond to the persisted reminder.

The alarm assertion reads `dumpsys alarm` and `dumpsys activity intents` through
`UiAutomation`, correlates their `PendingIntentRecord` token, and compares the
registry's numeric `origWhen` to `reminderAt` within 1,000 ms representation
tolerance. A standalone parser contract rejects a PendingIntent identity without a
registered alarm and rejects an alarm whose operation token cannot be correlated.

The recreation journey parses an Italian relative `domani` date and immediately
captures the exact production due and reminder control text. It then changes only the
raw Quick entry text to an unparsed `oggi` phrase with different time, priority,
recurrence, and reminder values. After `ActivityScenario.recreate()`, the changed raw
text is restored while the exact captured schedule display, high priority, weekly
recurrence, title, category, and recognized preview remain unchanged. This avoids a
second wall-clock snapshot at midnight; the injected-clock JVM matrix separately
proves the tomorrow calculation.

Each test snapshots the pre-test Room categories/tasks/subtasks, the exact nullable
`sqlite_sequence` rows for all three tables, and reminder `PendingIntent` identities.
It clears and seeds deterministic category data before the activity starts, then
cancels test alarms and restores all rows, each sequence's prior absence or value,
and prior alarm identities after the activity closes. Teardown asserts the restored
table rows, sequence map, and alarm identities against the snapshot. The locale rule
snapshots the exact per-app `LocaleList`, applies `it` or `en` before activity launch,
and restores it in `finally`.

## TDD Evidence

The first focused connected run compiled and executed 3 tests with 2 failures,
0 errors, and 0 skipped:

- the Italian save assertion observed Room's intentional intermediate `REQUESTED`
  reminder state before the same `SaveTask` coroutine completed scheduling; the
  journey now condition-waits for the final `SCHEDULED` row and alarm identity;
- MainActivity recreated, but `ActivityScenario` ignored it because the saved-state
  branch cleared the unrelated launcher `ACTION_MAIN`, so the recreated intent no
  longer matched the launch intent.

A focused JVM regression was then added before the MainActivity correction. Its RED
run failed compilation on the missing `clearConsumedNavigationIntent` contract. The
minimal production change clears only recognized widget/reminder navigation intents,
preserving unrelated launcher intent identity. The focused JVM test and all three
connected journeys then passed.

The first full connected gate exposed one additional retained-journey gap: Quick
entry shifted lazy editor content, so `CoreTaskJourneyTest` attempted to click an
uncomposed category control. That RED run had 101 tests, 1 failure, 0 errors, and
0 skipped. The test now scrolls the production LazyColumn by stable tags; its focused
rerun passed 1/1 and the complete connected gate passed 101/101.

### Fix Round 1

The AlarmManager registry parser test was added before its helper. Its RED run failed
Android-test compilation on the missing `AlarmRegistryEvidence` and `RegisteredAlarm`
symbols, so 0 tests executed. After the test-only dump parser was implemented, its
focused run passed 3/3: one positive correlation and two required rejection cases.

The sequence before/after assertion was then added while teardown still restored only
table rows. Its functional RED run executed all 3 journeys and failed all 3 in
teardown, with nullable sequence maps proving the leak: Italian save expected
`{categories=3, tasks=null, subtasks=null}` but observed
`{categories=101, tasks=1, subtasks=null}`; English save expected
`{categories=101, tasks=1, subtasks=null}` but observed
`{categories=102, tasks=2, subtasks=null}`; recreation expected
`{categories=102, tasks=2, subtasks=null}` but observed
`{categories=103, tasks=2, subtasks=null}`. Exact sequence replacement made the same
focused journey class pass 3/3. The hardened core journey also passed 1/1.

## Automated Gates

| Gate | Exact command | Result |
| --- | --- | --- |
| Alarm registry evidence parser | `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.naturallanguage.AlarmRegistryEvidenceTest` | 3 tests; 0 failures, 0 errors, 0 skipped |
| Focused journey | `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.naturallanguage.NaturalLanguageEntryJourneyTest` | 3 tests; 0 failures, 0 errors, 0 skipped |
| Focused core journey | `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.CoreTaskJourneyTest` | 1 test; 0 failures, 0 errors, 0 skipped |
| Full JVM | `./gradlew --no-daemon :app:testDebugUnitTest` | 301 tests across 33 XML suites; 0 failures, 0 errors, 0 skipped |
| Full connected | `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest` | 104 tests; 0 failures, 0 errors, 0 skipped; XML time 141.224 s |
| Lint and artifacts | `./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease` | Passed |
| Explicit R8 full mode | `./gradlew --no-daemon :app:assembleRelease -Pandroid.enableR8.fullMode=true` | Passed; R8 and release packaging were up to date from the preceding optimized build |
| Diff whitespace | `git diff --check` | Passed with no output |

The authoritative test XML locations were
`app/build/test-results/testDebugUnitTest/` and
`app/build/outputs/androidTest-results/connected/debug/`. Counts were parsed from
their `<testsuite>` attributes, not inferred from Gradle progress output.

Lint produced 0 errors and 68 warnings:

| Lint ID | Count |
| --- | ---: |
| `AndroidGradlePluginVersion` | 1 |
| `GradleDependency` | 20 |
| `MissingQuantity` | 2 |
| `NewerVersionAvailable` | 6 |
| `OldTargetApi` | 1 |
| `UnusedResources` | 30 |
| `UseKtx` | 6 |
| `VectorRaster` | 2 |

No warning was introduced as an error or suppressed for this feature. The complete
machine-readable report is `app/build/reports/lint-results-debug.xml`.

## Manifest, Locale, And Resource Inspection

- The merged release manifest declares min SDK 23, target SDK 34, and
  `android:localeConfig="@xml/_generated_res_locale_config"`.
- The generated release locale config has default locale `it` and exactly two locale
  entries: `it` and `en`.
- `values/strings.xml` and `values-en/strings.xml` each contain 215 unique `<string>`
  resources, including 16 `quick_entry_*` resources in each set.
- Resource-name parity is exact: 0 default-only names and 0 English-only names.
- The release AAB contains three DEX files, `base/resources.pb`, the binary manifest,
  generated locale config, and packaged `baseline.prof`/`baseline.profm`.
- R8 outputs are present and nonempty: `mapping.txt` has 367,356 lines, `seeds.txt`
  1,735, `usage.txt` 70,912, and `resources.txt` 14,442.

## Recorded Artifact Snapshots

These hashes identify the exact gate outputs inspected on 2026-08-26. They are
capture-time integrity records; a later build may replace the files.

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 18,084,634 bytes | `90fd749802088195fe7875d7bac7df6765c668d0a0f1de3130e61d9913245720` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,692,734 bytes | `ca507afb273d83088f0ab8c0b0c3b6efc25d1d0aa6537692622f22347c40f271` |
| `app/build/outputs/bundle/release/app-release.aab` | 6,884,139 bytes | `1848b3023303036fe2b64836996d9ccfada565ca56d14ad52debf045018f0256` |
| `app/build/outputs/mapping/release/mapping.txt` | 45,529,101 bytes | `611f03a3940fb5da7ee5e1491a35664e8b95db71da986ed3d4a017984956017c` |

`apksigner verify --verbose` confirmed the debug APK uses v1 and v2 signatures.
The unsigned release APK did not verify, and `jarsigner -verify` reported the AAB
unsigned, matching the no-credentials build configuration. A publishable candidate
still requires all four documented `NOWDOTHIS_*` signing variables and a repeated
signature check.

## Limits And Manual Follow-Up

The connected suite includes production Compose semantics, localized Italian/English
rendering, minimum Parse target size, and a programmatic 200% font-scale clipping and
overlap oracle. This Task 6 run did not perform physical-device TalkBack reading-order,
switch-access, contrast, notification permission, exact-alarm grant/fallback, reboot
reconciliation, or API 23 smoke testing. Those rows remain pending in the quality and
accessibility matrices. No physical-device accessibility validation is claimed.
The registry assertion proves the alarm scheduled during each save journey; it does
not prove that exact-alarm capability was granted, that the alarm was delivered, or
that a notification was posted.

Natural-Language Entry v1 intentionally accepts only the documented bilingual dates,
times, priorities, current category markers, daily/weekly/monthly recurrence, and one
absolute or due-relative reminder. Unsupported or ambiguous syntax remains visible;
the feature does not create categories, parse continuously, save automatically, use a
network/AI service, or make descriptions optional.
