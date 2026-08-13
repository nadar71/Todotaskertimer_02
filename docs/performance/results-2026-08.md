# Performance Results: 2026-08

## Scope And Limitations

These measurements are development evidence collected on an Android emulator. They prove that the benchmark journeys, deterministic fixture, Baseline Profile generation, and release packaging are repeatable. They are not authoritative production-device performance results and must not be compared with measurements from a physical device.

- Date: 2026-08-13
- Device: Android Studio `Medium_Phone` AVD (`sdk_gphone64_arm64`)
- API level: 36
- Serial: `emulator-5554`
- Source revision: `4b8c3b7` (`feature/production-readiness`)
- App variant: `nonMinifiedRelease`
- Benchmark library: Macrobenchmark 1.4.1
- Iterations: 10 per benchmark and compilation mode
- Thermal throttle sleep: 0 seconds reported by Macrobenchmark
- Emulator warning suppression: `EMULATOR`

## Startup

Cold startup used `StartupTimingMetric` and `StartupMode.COLD`.

| Compilation mode | Median time to initial display | Minimum | Maximum |
| --- | ---: | ---: | ---: |
| None | 1062.4002504999999 ms | 691.967376 ms | 1780.212043 ms |
| Partial, Baseline Profile required | 951.897917 ms | 777.244333 ms | 2715.14821 ms |

The profiled median was lower in this run, but the coefficients of variation (`0.3248257136212406` and `0.4933481599058441`) make this emulator result unsuitable for a performance claim.

## Task List

The task-list journey used `FrameTimingMetric`, a deterministic 750-task fixture, and `CompilationMode.Partial(BaselineProfileMode.Require)`.

| Metric | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| Frame CPU duration | 54.5372495 ms | 94.32473320000001 ms | 116.83379189999998 ms | 187.38062811999998 ms |
| Frame overrun | 58.679449 ms | 117.1492548 ms | 142.71264859999985 ms | 249.9934124299998 ms |

Frame count across the 10 runs had a median of `39`, minimum `30`, maximum `45`, and coefficient of variation `0.13017661116753135`.

## Follow-Up

Repeat the same commands on one named API 33+ physical device before using these numbers in release notes, store material, or an interview performance comparison. Keep the source revision, build variant, fixture, and iteration count fixed.
