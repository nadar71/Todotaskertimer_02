# Performance Evidence

This directory records reproducible Baseline Profile and Macrobenchmark evidence for Now Do This. The `:benchmark` module is test infrastructure only; application features and local-first data flow remain in `:app`.

## Measurement Contract

- Prefer one API 33 or newer physical device for release evidence. An emulator may be used for development evidence when the report labels it non-authoritative and does not present the numbers as production-device performance.
- Record the device model, API level, date, source commit, build variant, compilation mode, and iteration count.
- Do not compare measurements from different devices or invent a target threshold.
- Keep the device unplugged from thermal stress before a run, close unrelated apps, disable battery saver, and leave the screen unlocked.
- Run only one connected Android device. Set `ANDROID_SERIAL` to make accidental device switching visible.

The benchmark setup replaces the Room data for `com.indiewalkabout.nowdothis` through a benchmark-only provider. Do not run it against an installation whose local tasks must be retained.

## Deterministic Fixture

`BenchmarkTaskFixture` is compiled from `app/src/debug/` into debug and generated benchmark targets only. The public AGP Variant API adds its provider manifest solely to `benchmarkRelease` and `nonMinifiedRelease`; the production release contains neither the provider nor fixture classes.

Every preparation replaces local app data with:

- exactly 750 tasks with IDs `10001..10750`;
- fixed clock `2026-08-12T09:00:00Z` (`1786525200000` milliseconds);
- fixed task titles, descriptions, creation times, and update times;
- four categories with IDs `1..4`;
- a repeating `HIGH`, `MEDIUM`, `LOW` priority sequence, yielding 250 tasks per priority;
- every fifth task unscheduled and all other due dates derived from the fixed clock.

Preparation is idempotent. `TaskListBenchmark` prepares the fixture before every iteration, outside the measured frame-timing block.

## Device Check

```bash
export ANDROID_HOME=/Users/simone/Library/Android/sdk
export ANDROID_SERIAL=<device-serial>

adb devices -l
adb -s "$ANDROID_SERIAL" shell getprop ro.product.manufacturer
adb -s "$ANDROID_SERIAL" shell getprop ro.product.model
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk
```

Stop if the selected device reports API lower than 33. If it is an emulator, label every result as emulator-only and schedule a physical-device rerun before making release performance claims.

## Build And Fixture Verification

```bash
./gradlew :benchmark:compileNonMinifiedReleaseKotlin
./gradlew :benchmark:assemble :app:assembleRelease

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.benchmark.BenchmarkTaskFixtureTest
```

The fixture test must report one passing test before profile collection.

## Profile Generation

```bash
./gradlew :app:generateBaselineProfile
```

The generator clears app data before startup collection, marks startup rules with `includeInStartupProfile = true`, then independently prepares the fixed fixture before collecting editor-opening and list-scrolling journeys.

## Measurements

Run both commands without changing the selected device or source checkout:

```bash
./gradlew :benchmark:connectedNonMinifiedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.benchmark.StartupBenchmark

./gradlew :benchmark:connectedNonMinifiedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.benchmark.TaskListBenchmark
```

`StartupBenchmark` performs 10 cold-start iterations for `CompilationMode.None()` and 10 for `CompilationMode.Partial(BaselineProfileMode.Require)`. `TaskListBenchmark` performs 10 iterations with `FrameTimingMetric`, using three downward and three upward flings per measured iteration.

Benchmark JSON and traces are copied below `benchmark/build/outputs/connected_android_test_additional_output/`. Transcribe the reported medians and percentiles into the dated results document without recalculating or rounding away the source values.

## Packaging Check

```bash
./gradlew :app:bundleRelease
unzip -l app/build/outputs/bundle/release/app-release.aab \
  | rg 'BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof'
```

The command must list `baseline.prof`. Also inspect the release artifact to confirm test-only benchmark classes are absent.
