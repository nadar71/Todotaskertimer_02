# Test Strategy

Tests are selected by risk and boundary rather than by a repository-wide coverage percentage.

| Test type | Primary risks |
| --- | --- |
| Pure JVM | Natural-language grammar and time-zone matrices, recurrence calculation, task classification, backup codec/validation, use cases, and ViewModel reducers |
| Room instrumentation | DAOs, relations, migrations, atomic Replace All/rollback behavior, and deterministic fixtures |
| Compose instrumentation | Semantics, layout contracts, and feature interaction |
| Journey instrumentation | Production activity navigation, natural-language parse/correct/save/recreate behavior, cross-feature behavior, and full backup/mutate/restore data flow |
| Direct Glance rendering instrumentation | Production `RemoteViews`, 3/5/8 capacities, states, locale/theme/large-font rendering, action targets, and non-overlap |
| Bound AppWidget host instrumentation | Compact/default-font Room refresh through multiple `AppWidgetHostView` instances |
| Benchmark process instrumentation | Update, recurring completion, add, and open while the optimized target process is absent |
| Macrobenchmark | Cold startup and 750-task list frame timing |
| Manual matrix | TalkBack, contrast, notification permission, exact alarms, reboot, and API coverage |

## Test Doubles

Pure domain and ViewModel tests use focused repository/platform fakes so reducers and invariants are deterministic. Room instrumentation uses a real Room database rather than mocking SQL behavior. Journey tests launch the production activity and navigation graph. Macrobenchmarks install the real optimized target app and seed Room through a debug/benchmark-only fixture provider.

Android framework behavior is not claimed from JVM mocks. The portability journey uses real in-memory Room plus the production codec, validator, repository, and use cases; its document gateway and reminder scheduler are deterministic boundary fakes. Alarm delivery, notification permission UX, reboot broadcasts, document-provider UX, TalkBack reading order, visual contrast, and vendor-specific behavior require an emulator or physical device and remain separate matrix rows.

Quick Capture responsive, state, localization, theme, and 200% text checks compose
the production Glance content and apply its `RemoteViews` directly to a measured
view container. Bound-host coverage is separate: two compact widgets at the default
font scale attach to `AppWidgetHostView` and refresh from Room. The benchmark stops
the optimized target process and exercises host update, recurring completion, and
real add/open actions against authoritative Room state. Pixel Launcher placement
and resize remain operator-driven evidence because launcher drag-and-drop is not
treated as deterministic automation.

Data Portability v1 has explicit regression proofs for full-field deterministic round
trip, graph validation, the 10 MiB bound, atomic rollback, repeated restore, invalid and
future-version no-mutation behavior, reminder ordering/warnings, UDF busy state, and
Italian/English Compose semantics. Its normative contract is [documented here](../data-portability/backup-format-v1.md).

Natural-Language Entry keeps grammar tests on the JVM with injected instants, zones,
and category candidates. ViewModel tests prove atomic selective application and
`SavedStateHandle` restoration. Compose tests cover localized semantics and 200% text,
while connected journeys launch `MainActivity` and use production Navigation 3,
TaskEditor, parser, Room repositories, `SaveTask`, and AlarmManager scheduling. The
journey fixture seeds only deterministic category rows and restores locale, Room, and
alarm state; it does not replace parser or persistence behavior.

## Local Gates

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease :app:bundleRelease
./gradlew :app:assembleRelease -Pandroid.enableR8.fullMode=true
```

Performance runs must follow [the measurement contract](../performance/README.md) and keep device, API, build variant, source revision, and iteration count visible.
