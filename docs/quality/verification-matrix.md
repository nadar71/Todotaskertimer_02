# Verification Matrix

Only `Passed`, `Failed`, and `Pending` are valid statuses. Emulator and physical-device evidence are recorded separately.

| Area | Status | Command or procedure | Environment | Date | Evidence |
| --- | --- | --- | --- | --- | --- |
| JVM tests | Passed | `./gradlew :app:testDebugUnitTest` | Local macOS, JDK 17 | 2026-08-24 | 220 passed; 0 failed, 0 errors, 0 skipped |
| Android lint | Passed | `./gradlew :app:lintDebug` | Local macOS | 2026-08-24 | 0 errors, 68 warnings; `app/build/reports/lint-results-debug.html` |
| Debug assembly | Passed | Final clean Quick Capture hardening gate | Local macOS, API 36 SDK | 2026-08-24 | `app/build/outputs/apk/debug/app-debug.apk` |
| Optimized release APK/AAB | Passed | Final clean Quick Capture hardening gate | Local macOS | 2026-08-24 | Unsigned APK/AAB, R8 mapping, manifest and package inspection |
| Feature Compose tests | Passed | Feature-package `connectedDebugAndroidTest` | Medium Phone AVD, API 36 | 2026-08-13 | 39 tests passed; accessibility checklist |
| Full connected suite | Passed | `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` | Medium Phone AVD, API 36 | 2026-08-24 | 90 passed; 0 failed, 0 errors, 0 skipped |
| Room migration and DAO behavior | Passed | Full connected suite | Medium Phone AVD, API 36 | 2026-08-24 | DAO, migration, Replace All, rollback, Quick Capture refresh, and 8 deterministic completion/save/reminder interleavings passed within 90 tests |
| Data portability JVM contracts | Passed | `./gradlew :app:testDebugUnitTest` | Local macOS, JDK 17 | 2026-08-13 | 42 codec, validator, repository, use-case, and ViewModel tests passed |
| Data portability connected journey | Passed | `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` | Medium Phone AVD, API 36 | 2026-08-13 | 11 portability tests passed, including provider truncation and 3 export/mutate/restore and no-mutation journey proofs |
| Localization | Passed | Full connected suite plus Italian/English screenshot smoke | Medium Phone AVD, API 36 | 2026-08-24 | Automated app/widget localization, 3 production host/theme tests, and existing operator-captured launcher screenshots |
| Baseline Profile generation | Passed | `./gradlew :app:generateBaselineProfile` | Medium Phone AVD, API 36 | 2026-08-13 | Generated profiles under `app/src/release` |
| Baseline Profile packaging | Passed | Inspect release AAB | Local release build | 2026-08-24 | `baseline.prof`, `baseline.profm`, 3 DEX entries, and `resources.pb` present |
| Startup/list benchmark | Passed | Macrobenchmark classes, 10 iterations each | Medium Phone AVD, API 36 | 2026-08-13 | [Results](../performance/results-2026-08.md) |
| Accessibility semantics | Passed | Feature Compose, direct production Glance rendering, bound AppWidget host tests, and lint | Medium Phone AVD, API 36 | 2026-08-24 | Localized descriptions and 48 dp Quick Capture targets; [checklist](accessibility-checklist.md) |
| Large font at 200% | Passed | Feature tests plus direct production Quick Capture Glance `RemoteViews` rendering | Medium Phone AVD, API 36 | 2026-08-24 | Direct-render target-size/non-overlap assertions plus existing editor coverage; not a bound-host claim |
| Quick Capture production navigation | Passed | Full connected suite | Medium Phone AVD, API 36 | 2026-08-24 | 3 cold/running/recreation add/open/reminder tests through `MainActivity` Navigation 3 |
| Quick Capture rendering/AppWidget host | Passed | Full connected suite | Medium Phone AVD, API 36 | 2026-08-24 | 3 tests: direct responsive states/locales/themes/200% targets, two-instance bound-host Room refresh, and live bound-host day/night qualified-color refresh |
| Quick Capture process absent | Passed | Separate `QuickCaptureProcessAbsentTest` benchmark invocation | Optimized target, Medium Phone AVD, API 36 | 2026-08-24 | 1 passed; PID absent before update, recurring completion, add, and open; PID restored by the production open action before fixture query |
| Quick Capture Pixel Launcher placement/resize | Passed | Operator pin confirmation and resize; Android screen capture | Pixel Launcher, API 36 emulator | 2026-08-16 | Manual evidence only; 5-row English/light and 8-row Italian/dark screenshots |
| Light/dark contrast | Pending | Manual visual review | Physical release candidate | - | Manual review pending |
| TalkBack workflows | Pending | Create, complete, delete/undo, categories, Calendar, History | Physical release candidate | - | Manual review pending |
| Notification permission | Pending | Deny/grant and reminder delivery | API 33+ physical device | - | Manual platform check pending |
| Exact-alarm fallback/grant | Pending | Deny capability, verify fallback, grant and retest | API 33+ physical device | - | Manual platform check pending |
| Reboot reconciliation | Pending | Schedule reminder, reboot, verify restoration | API 33+ physical device | - | Manual platform check pending |
| Minimum API smoke | Pending | Core create/edit/delete/recurrence journey | API 23 device/emulator | - | Device run pending |

The screenshot set under `docs/images/` is product evidence, not a substitute for the pending interaction and platform rows. Full commands, automation boundaries, and recorded-build artifact hash snapshots are in the [Quick Capture evidence record](../release/quick-capture-evidence-2026-08-16.md).
