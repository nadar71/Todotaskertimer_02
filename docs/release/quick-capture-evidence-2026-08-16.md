# Quick Capture Final Hardening Evidence

## Scope And Environment

- Date: 2026-08-24
- Device: `emulator-5554`, Medium Phone AVD, Android 16 / API 36
- Launcher: Pixel Launcher
- Build host: macOS, JDK 17, Android SDK 36
- Production source of truth: Room; no widget-only cache
- Release signing: no `NOWDOTHIS_*` signing variables supplied, so the inspected
  local APK and AAB are intentionally unsigned

The emulator used English and `font_scale=1.0`. The live-theme host test changed the
system between day and night while an already-bound widget was present, then restored
the original mode.

## Automated Evidence

| Boundary | Result | Evidence |
| --- | --- | --- |
| JVM contracts | Passed: 221/221, 0 failed/errors/skipped | `app/build/test-results/testDebugUnitTest/` |
| Lint | Passed: 0 errors, 68 warnings | `app/build/reports/lint-results-debug.html` |
| Full connected suite | Passed: 90/90, 0 failed/errors/skipped | `app/build/outputs/androidTest-results/connected/debug/` |
| Atomic Room completion and persistence | Passed: 8/8 | Two completers create one successor; completion derives from canonical Save/Replace All state; stale editor Save cannot resurrect completion; successor reminder work converges per task ID across repeated finite owner-generation changes so restored reminders survive while deleted/no-reminder rows leave no orphan alarm |
| Editor conflict behavior | Passed: 16/16 ViewModel tests | Optimistic conflict preserves the draft, clears saving state, shows the retryable save message, and does not navigate; process recreation retains the draft's original snapshot version so a widget-completed row cannot be overwritten; an unavailable-reminder retry advances from the canonical saved version |
| Completion cancellation | Passed: 12/12 use-case tests | Terminal refresh remains cancellable and cancellation removes the in-flight task ID promptly |
| Coordinator recovery | Passed: 8/8 coordinator tests | Source and updater failures use 1 s to 60 s capped exponential backoff; a successful update resets the delay; cancellation/restart remains covered |
| Production navigation | Passed: 3/3 | Cold and running add/open, reminder compatibility, recreation, back-stack return, and consumed-intent no-replay checks |
| Production Glance rendering and AppWidget host | Passed: 3/3 | Direct production `RemoteViews` rendering covers 3/5/8 sizes, content/empty/unavailable, English/Italian, light/dark, 200% text, 48 dp actions, and non-overlap; bound-host tests cover two-instance Room refresh and live qualified-color changes across a day/night configuration update |
| Optimized process-absent journey | Passed: 1/1 | `benchmark/build/outputs/androidTest-results/connected/benchmarkRelease/`; PID absent before update, recurring completion/successor, add, and open, with PID restored by the production open action before fixture query |
| Release packaging | Passed | Unsigned optimized APK/AAB, R8 mapping, manifest, Baseline Profile, DEX, resources, and fixture-exclusion inspection |

The responsive/state/localization/theme/200% assertions compose production Glance
content and apply its `RemoteViews` directly to a measured view container; they are
not bound-host evidence. Separately, compact/default-font production widgets are
bound to a custom `AppWidgetHostView`: one test refreshes two instances from Room,
and another proves an already-bound widget adopts live values-night text colors after
the system configuration changes. Neither test claims to automate Pixel Launcher.
The process test uses `am stop-app` before update,
completion, and add, then stops the process again after returning from Add and before
Open, so existing host `PendingIntent` tokens remain valid. It asserts PID absence
before each action and PID restoration after update, completion, and the production
Open action. The Open PID assertion occurs before querying the debug/benchmark-only
fixture boundary. That query proves authoritative Room state: the original recurring
task is completed, exactly one successor exists, its due time advances by one day,
and one pending task remains.

## Commands

The recorded clean host gate produced the retained debug APK, optimized release APK/AAB,
and R8 mapping. The final corrective JVM and lint reruns passed with 221/221 tests and
0 errors (68 warnings):

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The final connected suite passed 90/90 on `emulator-5554`:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

The final process-absent journey was run separately and passed 1/1:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.benchmark.QuickCaptureProcessAbsentTest
```

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
- Live day/night refresh is initiated by `Application.onConfigurationChanged` and
  calls the inward widget-updater contract; it requires no exported configuration
  receiver and no restricted Glance color API.
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

The AAB contains `baseline.prof` (9,921 bytes), `baseline.profm` (599 bytes), three
DEX entries, `base/resources.pb`, the binary manifest, widget layouts/drawables, and
both provider XML variants. String inspection across every release DEX entry found
no `BenchmarkFixtureProvider`, `prepare_quick_capture`,
`request_quick_capture_pin`, or benchmark-fixture authority symbols.

## Recorded Build Artifact Snapshots

These sizes and SHA-256 values identify the exact outputs inspected during the
recorded final hardening build. They are integrity snapshots, not guarantees that a
later build from the same source will be byte-for-byte reproducible. Later Gradle
invocations may replace files at these `build/` paths; the table records their
capture-time contents rather than asserting future contents at those paths.

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 17,259,895 bytes | `f54ae0e10e5c0fd3987ab309a391b19e83342b74e167c8e85bfe93cf861b7f32` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,668,710 bytes | `259df0c02e25408986c979471190fd1b1366b28a3ca2ad44781f589f934ebdbe` |
| `app/build/outputs/bundle/release/app-release.aab` | 6,835,251 bytes | `a360519c5f1cf8f80402efe7ee45e353a38b8e05aba729f4d12e4b459755cb5b` |
| `app/build/outputs/mapping/release/mapping.txt` | 44,983,968 bytes | `f2edcdaa5572bedd1e9496c44120af2737d78df268a8b1f40b470bedd5de194b` |

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
Separate compact/default-font bound-host coverage proves two instances refresh and
an already-bound instance adopts live day/night qualified colors.
Physical-device TalkBack reading order and contrast remain pending in the
accessibility matrix and are not claimed here.

Quick Capture changes no Room schema or backup format. Rollback can remove/disable
the non-exported receiver, metadata/resources, and Glance dependency without data
migration; existing hosts lose the surface, while a corrected version reconstructs
it from unchanged Room data.
