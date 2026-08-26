# Natural-Language Entry Release Evidence

## Scope And Environment

- Date: 2026-08-26
- Acceptance-fix base revision: `2b969af5586578450dac15731d89320eeeab0143`
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

The five product journey methods in `NaturalLanguageEntryJourneyTest` launch
production `MainActivity`, follow the Navigation 3 add path, render the production
`TaskEditorRoute`, and parse through the injected production parser. The two save
journeys continue through the production ViewModel, use case, Room, and AlarmManager.
They do not use a test parser, in-memory task repository, direct task insert, or
test-only persistence path. A separate fixture failure regression seeds test-only
sentinel rows and alarms solely to verify cleanup; it is not a product journey or
persistence substitute.

The retained Italian and English save journeys independently prove:

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
7. Save opens the real exact-alarm access screen when capability is unavailable,
   returns to the editor callback, and preserves the visible inexact-timing state; and
8. the device AlarmManager registry contains exactly one `RTC_WAKEUP` alarm whose
   package, receiver, request code, and trigger correspond to the persisted reminder.

Two additional production-UI journeys set `fr-CH,en-US` and `fr-CH,de-DE` application
locale lists before activity launch. They prove that parser grammar selects the first
supported `en`/`it` primary subtag in list order, defaults to Italian when no supported
entry exists, and matches default category candidates to the resource name Android
actually renders. The English-secondary journey also exercises strict `at 18`
24-hour parsing.

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
`sqlite_sequence` rows for all three tables, and every live AlarmManager registry
record owned by this package and `ReminderReceiver`, including request codes with no
Room task. Fixture preparation and the journey body
both run inside restoration `try/finally` ownership. Cleanup restores all table rows
and each sequence's prior absence or value. For each previously live alarm, it requires
either exact or fallback inexact scheduling to succeed, then queries dumpsys again and
asserts type, package, receiver component, request code, and trigger within 1,000 ms.
The complete prior alarm key set is asserted after restoration, so fixture alarms and
other extras cannot survive. The setup-failure case includes an orphan request code
and proves its restoration. The locale rule snapshots the exact per-app `LocaleList`,
applies single- or multi-locale fixtures before activity launch, and restores it in
`finally`.

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

### Fix Round 2

A deterministic setup-failure regression was added before the lifecycle and snapshot
APIs. Its first RED run failed Android-test compilation on the missing mutation hook,
state capture, and registered-alarm sentinel helpers, so 0 tests executed. The fixture
then moved preparation under restoration `try/finally`, replaced the PendingIntent-only
snapshot with structured live registry records, required successful exact-or-inexact
restoration, and asserted restored registry identity and trigger. The focused class
passed 4/4 in 47 seconds.

The same regression was then strengthened with a preexisting task that intentionally
had no live alarm and a stray alarm created after setup mutation. Its second RED run
failed Android-test compilation on the two missing negative-sentinel helpers, so 0
tests executed. After those test-only helpers were added, the focused class passed
4/4 in 40 seconds. The regression proves that a forced setup exception restores exact
rows and nullable sequence values, restores the prior live alarm, removes the stray
alarm, does not run the journey body, and rethrows the expected setup exception.

### Final Acceptance Fix Round

The final whole-branch review produced nine additional RED contracts before their
minimal production changes. The parser RED runs exposed four Unicode-normalization
failures, three temporal failures, five end-to-end parse failures, and the overlapping
`alle 1/2` exception. The editor RED run executed 33 tests with five reminder/save-race
failures; category readiness initially failed compilation on the missing state/retry
contracts. Connected RED runs failed on the missing category-loading resource and on
the orphan alarm being absent from the fixture snapshot. The cancellation test began
green because production already rethrew `CancellationException`; deleting that
rethrow made the focused mutation run fail 1/1, proving the new regression is
sensitive.

The resulting coverage includes strict English 24-hour forms, recoverable
cross-grammar overlap, script-safe category keys and Unicode separators, ordered
multi-locale selection, category loading/error/retry states, a frozen saving draft,
common Save-boundary notification/exact access for parser and manual reminders,
fresh API 36 notification deny/grant checks, real exact-settings return with inexact
fallback, and exact restoration of package/component alarms including orphans.

## Automated Gates

| Gate | Exact command | Result |
| --- | --- | --- |
| Focused parser and editor JVM | `./gradlew --no-daemon :app:testDebugUnitTest --tests '*TextNormalizerTest' --tests '*TemporalParserTest' --tests '*ParseNaturalLanguageTaskTest' --tests '*TaskEditorViewModelTest' --rerun-tasks` | 111 tests across 4 XML suites; 0 failures, 0 errors, 0 skipped |
| Focused editor Compose UI | `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.task.presentation.editor.TaskEditorScreenTest` | 16 tests; 0 failures, 0 errors, 0 skipped |
| Focused journey, fallback, locales, and fixture cleanup | `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.naturallanguage.NaturalLanguageEntryJourneyTest` | 6 tests; 0 failures, 0 errors, 0 skipped |
| API 33+ notification access | `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.core.notifications.Api33ReminderPermissionConnectedTest` | 1 test; fresh denial and platform grant passed on API 36 |
| Active locale-list adapter | `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.naturallanguage.AndroidNaturalLanguageEnvironmentConnectedTest` | 1 test; `fr-CH,en-US` and unsupported-only passed on API 36 |
| Full JVM | `./gradlew --no-daemon :app:testDebugUnitTest --rerun-tasks` | 320 tests across 33 XML suites; 0 failures, 0 errors, 0 skipped |
| Full connected | `./gradlew --no-daemon :app:connectedDebugAndroidTest` | 111 tests; 0 failures, 0 errors, 0 skipped; XML time 161.987 s |
| Lint | `./gradlew --no-daemon :app:lintDebug` | Passed; 0 errors and 68 warnings |
| APK and AAB packaging | `./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:bundleRelease` | Passed; debug APK, optimized unsigned release APK, and unsigned release AAB produced |
| Explicit R8 full mode | `./gradlew --no-daemon :app:assembleRelease -Pandroid.enableR8.fullMode=true --rerun-tasks` | Passed; all 55 tasks executed, including `minifyReleaseWithR8` |
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
- `values/strings.xml` and `values-en/strings.xml` each contain 216 unique `<string>`
  resources, including 17 `quick_entry_*` resources in each set.
- Resource-name parity is exact: 0 default-only names and 0 English-only names.
- The release AAB contains three DEX files, `base/resources.pb`, the binary manifest,
  generated locale config, and packaged `baseline.prof`/`baseline.profm`.
- R8 outputs are present and nonempty: `mapping.txt` has 368,258 lines, `seeds.txt`
  1,737, `usage.txt` 70,918, and `resources.txt` 14,448.

## Recorded Artifact Snapshots

These hashes identify the exact gate outputs inspected on 2026-08-26. They are
capture-time integrity records; a later build may replace the files.

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 17,993,646 bytes | `11e941c1ae4077ceeb988fbfd4d8dac915afee0311477439c5918696a1ed77c4` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,709,574 bytes | `1f95a4781aa92c4d9c10c405dca558ab1c734b0ee800a8c6fe95d96e7012036a` |
| `app/build/outputs/bundle/release/app-release.aab` | 6,898,905 bytes | `9bdcb032315b525bc6c460c5f40947a086cf323ff9fd15f196bdb498b0adac09` |
| `app/build/outputs/mapping/release/mapping.txt` | 45,662,545 bytes | `76799520af6bdec4028e3828c4231c9271ebe56bf722c5f09278f35d1f4f4dfa` |

`apksigner verify --verbose` confirmed the debug APK uses v1 and v2 signatures.
The unsigned release APK did not verify, and `jarsigner -verify` reported the AAB
unsigned, matching the no-credentials build configuration. A publishable candidate
still requires all four documented `NOWDOTHIS_*` signing variables and a repeated
signature check.

## Limits And Manual Follow-Up

The connected suite includes production Compose semantics, localized Italian/English
rendering, minimum Parse target size, a programmatic 200% font-scale clipping and
overlap oracle, fresh API 36 notification denial/grant state, and real
exact-alarm-settings return with inexact fallback. It does not click through the
platform notification permission dialog, deliver an alarm, post a notification,
grant exact-alarm capability, reboot the device, or cover API 23. Physical-device
TalkBack reading order, switch access, contrast, reminder delivery, exact-alarm grant,
and reboot reconciliation remain pending. No physical-device accessibility or
notification-delivery validation is claimed.

Natural-Language Entry v1 intentionally accepts only the documented bilingual dates,
times, priorities, current category markers, daily/weekly/monthly recurrence, and one
absolute or due-relative reminder. Unsupported or ambiguous syntax remains visible;
the feature does not create categories, parse continuously, save automatically, use a
network/AI service, or make descriptions optional.
