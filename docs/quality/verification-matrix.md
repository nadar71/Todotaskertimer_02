# Verification Matrix

Only `Passed`, `Failed`, and `Pending` are valid statuses. Emulator and physical-device evidence are recorded separately.

| Area | Status | Command or procedure | Environment | Date | Evidence |
| --- | --- | --- | --- | --- | --- |
| JVM tests | Passed | `./gradlew :app:testDebugUnitTest` | Local macOS, JDK 17 | 2026-08-13 | Gradle test report |
| Android lint | Passed | `./gradlew :app:lintDebug` | Local macOS | 2026-08-13 | `app/build/reports/lint-results-debug.html` |
| Debug assembly | Passed | `./gradlew :app:assembleDebug` | Local macOS, API 36 SDK | 2026-08-13 | Debug APK |
| Optimized release APK/AAB | Passed | `./gradlew clean :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease` | Local macOS | 2026-08-13 | AAB, R8 mapping files, release docs |
| Feature Compose tests | Passed | Feature-package `connectedDebugAndroidTest` | Medium Phone AVD, API 36 | 2026-08-13 | 39 tests passed; accessibility checklist |
| Full connected suite | Passed | `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` | Medium Phone AVD, API 36 | 2026-08-13 | 57 tests passed |
| Room migration and DAO behavior | Passed | Full connected suite | Medium Phone AVD, API 36 | 2026-08-13 | DAO and migration tests passed within the 57-test suite |
| Localization | Passed | Full connected suite plus Italian/English screenshot smoke | Medium Phone AVD, API 36 | 2026-08-13 | Automated localization tests and product screenshots |
| Baseline Profile generation | Passed | `./gradlew :app:generateBaselineProfile` | Medium Phone AVD, API 36 | 2026-08-13 | Generated profiles under `app/src/release` |
| Baseline Profile packaging | Passed | Inspect release AAB | Local release build | 2026-08-13 | `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` |
| Startup/list benchmark | Passed | Macrobenchmark classes, 10 iterations each | Medium Phone AVD, API 36 | 2026-08-13 | [Results](../performance/results-2026-08.md) |
| Accessibility semantics | Passed | Feature Compose tests and lint | Medium Phone AVD, API 36 | 2026-08-13 | [Checklist](accessibility-checklist.md) |
| Large font at 200% | Passed | Feature tests at `font_scale=2.0`, including editor scroll reachability | Medium Phone AVD, API 36 | 2026-08-13 | 39 feature tests passed; accessibility checklist |
| Light/dark contrast | Pending | Manual visual review | Physical release candidate | - | Manual review pending |
| TalkBack workflows | Pending | Create, complete, delete/undo, categories, Calendar, History | Physical release candidate | - | Manual review pending |
| Notification permission | Pending | Deny/grant and reminder delivery | API 33+ physical device | - | Manual platform check pending |
| Exact-alarm fallback/grant | Pending | Deny capability, verify fallback, grant and retest | API 33+ physical device | - | Manual platform check pending |
| Reboot reconciliation | Pending | Schedule reminder, reboot, verify restoration | API 33+ physical device | - | Manual platform check pending |
| Minimum API smoke | Pending | Core create/edit/delete/recurrence journey | API 23 device/emulator | - | Device run pending |

The screenshot set under `docs/images/` is product evidence, not a substitute for the pending interaction and platform rows.
