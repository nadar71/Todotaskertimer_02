# Test Strategy

Tests are selected by risk and boundary rather than by a repository-wide coverage percentage.

| Test type | Primary risks |
| --- | --- |
| Pure JVM | Recurrence calculation, task classification, validation, use cases, and ViewModel reducers |
| Room instrumentation | DAOs, relations, migrations, transaction behavior, and deterministic fixtures |
| Compose instrumentation | Semantics, layout contracts, and feature interaction |
| Journey instrumentation | Production activity navigation and cross-feature behavior |
| Macrobenchmark | Cold startup and 750-task list frame timing |
| Manual matrix | TalkBack, contrast, notification permission, exact alarms, reboot, and API coverage |

## Test Doubles

Pure domain and ViewModel tests use focused repository/platform fakes so reducers and invariants are deterministic. Room instrumentation uses a real Room database rather than mocking SQL behavior. Journey tests launch the production activity and navigation graph. Macrobenchmarks install the real optimized target app and seed Room through a debug/benchmark-only fixture provider.

Android framework behavior is not claimed from JVM mocks. Alarm delivery, notification permission UX, reboot broadcasts, TalkBack reading order, visual contrast, and vendor-specific behavior require an emulator or physical device and remain separate matrix rows.

## Local Gates

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease :app:bundleRelease
```

Performance runs must follow [the measurement contract](../performance/README.md) and keep device, API, build variant, source revision, and iteration count visible.
