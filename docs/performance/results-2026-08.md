# Performance Results: 2026-08

## Scope And Limitations

These measurements are development evidence collected on an Android emulator. They prove that the benchmark journeys, deterministic fixture, Baseline Profile generation, and release packaging are repeatable. They are not authoritative production-device performance results and must not be compared with measurements from a physical device.

- Date: 2026-08-13
- Device: Android Studio `Medium_Phone` AVD (`sdk_gphone64_arm64`)
- API level: 36
- Serial: `emulator-5554`
- Source base: `7982f74275dccd2fb0dfb0a5bfd3c6eeffed247e` plus the Task 3 benchmark working tree
- App variant: `nonMinifiedRelease`
- Benchmark library: Macrobenchmark 1.4.1
- Iterations: 10 per benchmark and compilation mode
- Thermal throttle sleep: 0 seconds reported by Macrobenchmark
- Emulator warning suppression: `EMULATOR`

## Startup

Cold startup used `StartupTimingMetric` and `StartupMode.COLD`.

| Compilation mode | Median time to initial display | Minimum | Maximum |
| --- | ---: | ---: | ---: |
| None | 1122.776063 ms | 822.6755 ms | 2636.928168 ms |
| Partial, Baseline Profile required | 1028.6894585 ms | 839.474542 ms | 2796.53921 ms |

The profiled median was lower in this run, but the high coefficients of variation (`0.45190022009563574` and `0.4950722813220284`) make this emulator result unsuitable for a performance claim.

## Task List

The task-list journey used `FrameTimingMetric`, a deterministic 750-task fixture, and `CompilationMode.Partial(BaselineProfileMode.Require)`.

| Metric | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| Frame CPU duration | 53.977291 ms | 98.31263320000006 ms | 128.3229792999999 ms | 205.3283940000001 ms |
| Frame overrun | 57.525386 ms | 117.041241 ms | 157.9542595999996 ms | 281.24893020000025 ms |

Frame count across the 10 runs had a median of `39.5`, minimum `31`, maximum `45`, and coefficient of variation `0.0964021912134672`.

## Follow-Up

Repeat the same commands on one named API 33+ physical device before using these numbers in release notes, store material, or an interview performance comparison. Keep the source revision, build variant, fixture, and iteration count fixed.
