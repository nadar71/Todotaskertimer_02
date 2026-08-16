# Quick Capture Task 6 Evidence

## Scope And Environment

- Date: 2026-08-16
- Device: `emulator-5554`, Medium Phone AVD, Android 16 / API 36
- Launcher: Pixel Launcher
- Build host: macOS, JDK 17, Android SDK 36
- Production source of truth: Room; no widget-only cache
- Release signing: no `NOWDOTHIS_*` signing variables supplied, so the inspected
  local APK and AAB are intentionally unsigned

The emulator was reset to English, light theme, and `font_scale=1.0`, and prior app
packages were uninstalled before the successful clean matrix.

## Automated Evidence

| Boundary | Result | Evidence |
| --- | --- | --- |
| JVM contracts | Passed: 211/211, 0 failed/errors/skipped | `app/build/test-results/testDebugUnitTest/` |
| Lint | Passed: 0 errors, 68 warnings | `app/build/reports/lint-results-debug.html` |
| Full connected suite | Passed: 82/82, 0 failed/errors/skipped | `app/build/outputs/androidTest-results/connected/debug/` |
| Production navigation | Passed: 3/3 | Cold and running add/open, reminder compatibility, recreation, back-stack return, and consumed-intent no-replay checks |
| Production Glance rendering and AppWidget host | Passed: 2/2 | Direct production `RemoteViews` rendering covers 3/5/8 sizes, content/empty/unavailable, English/Italian, light/dark, 200% text, 48 dp actions, and non-overlap; a separate compact/default-font test binds two `AppWidgetHostView` instances and refreshes both from Room |
| Optimized process-absent journey | Passed: 1/1 | `benchmark/build/outputs/androidTest-results/connected/benchmarkRelease/`; PID absent before update, recurring completion/successor, add, and open, with PID restored by the production open action before fixture query |
| Release packaging | Passed | Unsigned optimized APK/AAB, R8 mapping, manifest, Baseline Profile, DEX, resources, and fixture-exclusion inspection |

The responsive/state/localization/theme/200% assertions compose production Glance
content and apply its `RemoteViews` directly to a measured view container; they are
not bound-host evidence. Separately, two compact/default-font production widgets are
bound to a custom `AppWidgetHostView` and refreshed from Room. Neither test claims to
automate Pixel Launcher. The process test uses `am stop-app` before update,
completion, and add, then stops the process again after returning from Add and before
Open, so existing host `PendingIntent` tokens remain valid. It asserts PID absence
before each action and PID restoration after update, completion, and the production
Open action. The Open PID assertion occurs before querying the debug/benchmark-only
fixture boundary. That query proves authoritative Room state: the original recurring
task is completed, exactly one successor exists, its due time advances by one day,
and one pending task remains.

## Commands

The final required clean matrix passed in 5 minutes 54 seconds:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew clean :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest :app:assembleRelease :app:bundleRelease
```

The final process-absent journey was run separately and passed 1/1 in 2 minutes 35 seconds:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.benchmark.QuickCaptureProcessAbsentTest
```

The Task 6 evidence corrections were verified separately on the same emulator. The
focused direct-render/bound-host class passed 2/2 in 30 seconds, the strengthened
process-absent benchmark passed 1/1 in 38 seconds, and the complete connected suite
was rerun afterward so its retained report again records 82/82 in 2 minutes 55
seconds. Counts did not change:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.feature.quickcapture.QuickCaptureWidgetHostJourneyTest
ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.benchmark.QuickCaptureProcessAbsentTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

The JVM/lint correction gate completed successfully in 24 seconds. No JVM inputs
changed, so Gradle retained `:app:testDebugUnitTest` as up to date; its XML reports
remain 211/211. Android-test lint analysis reran for the edited instrumentation code,
and lint remains at 0 errors and 68 warnings:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The first clean attempt completed all 82 connected tests but lint found two API-26
calls in the debug-only pin helper without a minimum-SDK guard. Task 6 added an
`SDK_INT >= O` guard and reran the entire clean matrix successfully. No baseline or
suppression was added.

## Launcher And Screenshots

Pixel Launcher evidence is operator-driven, not launcher automation. The optimized
benchmark target app requested Android's official pin-confirmation UI while its
activity was foregrounded; the operator confirmed placement and later dragged the
launcher's resize handle. The app seeded deterministic, fictional local task titles.
Android screen capture then recorded the actual launcher rendering:

| File | Verified state | Size | SHA-256 |
| --- | --- | ---: | --- |
| `docs/images/quick-capture-en-light-medium.png` | English, light, Pixel Launcher medium host, 5 ordered rows | 1,306,804 bytes | `d6801554b54c4847beb8124c33f74dcea61cc97c38671456bd52f7d944db7f21` |
| `docs/images/quick-capture-it-dark-expanded.png` | Italian, dark, manually expanded Pixel Launcher host, 8 ordered rows | 1,208,084 bytes | `1719f1511315bee8eadf8451c550eec05af72c6b8f2375555f7942dbc6f15526` |

Visual inspection confirmed localized header/due labels, all expected rows, clear
completion controls, and no incoherent overlap. The medium screenshot intentionally
shows one single-line ellipsis. UI hierarchy inspection found localized add/open/
complete descriptions and 48 dp-equivalent action bounds. This is emulator visual
evidence, not a physical-device TalkBack or contrast certification.

## Release Inspection

The merged and packaged release manifests show:

- `MainActivity` is exported for its launcher entry; `QuickCaptureWidgetReceiver` is
  non-exported.
- The widget receiver handles `APPWIDGET_UPDATE` and `LOCALE_CHANGED` and references
  `@xml/quick_capture_widget_info` through `android.appwidget.provider` metadata.
- Glance action trampolines and callback receivers are non-exported; its
  `RemoteViewsService` is protected by `android.permission.BIND_REMOTEVIEWS`.
- Provider XML declares home-screen category, horizontal/vertical resize, 180 x 200
  dp minimums, an API-31 preview layout, and `updatePeriodMillis=3600000`.

The hourly platform update is an inexact fallback. It does not guarantee a midnight
date-boundary refresh. Glance contributes internal WorkManager/session components to
the merged manifest; Now Do This adds no app-authored widget worker or widget work
schedule.

Rendered-action tests inspect production `RemoteViews` and prove add/open intents
are explicit, distinct, and immutable. Retry uses an explicit receiver broadcast
with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT` and a per-widget URI/request code.

The AAB contains `baseline.prof` (9,870 bytes), `baseline.profm` (599 bytes), three
DEX entries, `base/resources.pb`, the binary manifest, widget layouts/drawables, and
both provider XML variants. String inspection across every release DEX entry found
no `BenchmarkFixtureProvider`, `prepare_quick_capture`,
`request_quick_capture_pin`, or benchmark-fixture authority symbols.

## Recorded Build Artifact Snapshots

These sizes and SHA-256 values identify the exact outputs inspected during the
recorded Task 6 build. They are integrity snapshots, not guarantees that a later
build from the same source will be byte-for-byte reproducible. Later Gradle
invocations may replace files at these `build/` paths; the table records their
capture-time contents rather than asserting the current contents of those paths.
The evidence-correction run did not rebuild or reinspect release artifacts.

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 16,965,384 bytes | `34b8d9a83bb153562055fd82bb6dcca3d6b8444681476871635f955a12182a86` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,668,710 bytes | `97a204ce88514d7a5643dccd0dc8df7b3b20db803aa97e2f0f1f1eeefb4547b2` |
| `app/build/outputs/bundle/release/app-release.aab` | 6,829,308 bytes | `09c085baaf4946a143e610525748620c62b71e4d4f7bd3e3935e9aaf36283541` |
| `app/build/outputs/mapping/release/mapping.txt` | 44,930,358 bytes | `09f02cb963fca1ba13d4afdeb4b07a78c98eaff0022b793f085a5ac85fcc1b03` |

`apksigner verify` reported that the APK does not verify, and `jarsigner -verify`
reported the AAB unsigned, matching the documented no-credentials build behavior.
Supply all four signing variables and repeat signature verification for a publishable
release candidate.

## Privacy, Accessibility, And Rollback

Quick Capture exposes task titles and due-state labels on the user's home screen; it
does not expose descriptions, categories, reminder details, account data, or network
data. The widget reads the same local Room database as the app and adds no network,
analytics, or duplicate persistence. Screenshot fixtures contain fictional titles.

Automated direct production `RemoteViews` rendering proves localized descriptions,
minimum available action targets, large-font layout, and non-color due labels.
Separate compact/default-font bound-host coverage proves two instances refresh.
Physical-device TalkBack reading order and contrast remain pending in the
accessibility matrix and are not claimed here.

Quick Capture changes no Room schema or backup format. Rollback can remove/disable
the non-exported receiver, metadata/resources, and Glance dependency without data
migration; existing hosts lose the surface, while a corrected version reconstructs
it from unchanged Room data.
