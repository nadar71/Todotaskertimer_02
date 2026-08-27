# Release Discipline

Now Do This uses semantic `MAJOR.MINOR.PATCH` version names and monotonically increasing integer version codes. Every release updates `CHANGELOG.md`, passes the release checklist, retains R8 mapping files, and records device-dependent checks in the verification matrix.

Release builds enable R8, resource shrinking, and the generated Baseline Profile. Local builds remain unsigned unless every signing variable is present:

```text
NOWDOTHIS_STORE_FILE
NOWDOTHIS_STORE_PASSWORD
NOWDOTHIS_KEY_ALIAS
NOWDOTHIS_KEY_PASSWORD
```

Secret values are never committed or logged. A missing or partial set produces an unsigned release artifact for local inspection.

```bash
./gradlew clean :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease
```

See [checklist.md](checklist.md), [migration-and-rollback.md](migration-and-rollback.md),
the [Quick Capture verification evidence](quick-capture-evidence-2026-08-16.md), and
the [Data Portability v1 contract](../data-portability/backup-format-v1.md),
[backup v2 contract](../data-portability/backup-format-v2.md), and
[Advanced Recurrence verification evidence](advanced-recurrence-evidence-2026-08-26.md).
