# Data Portability v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add versioned JSON backup and atomic Replace All restore for all user-owned planning data through Android's Storage Access Framework.

**Architecture:** Add a `feature/portability` vertical slice inside `:app`. Pure format/validation code remains independent from Room; a Room data source owns snapshots and atomic replacement; use cases coordinate repository and reminders; Compose owns Activity Result launchers and a ViewModel owns UDF state.

**Tech Stack:** Kotlin, Kotlin Serialization, Room, Hilt, coroutines/Flow, Compose, Navigation 3, Storage Access Framework.

## Global Constraints

- Follow `docs/superpowers/specs/2026-08-13-data-portability-design.md` exactly; Replace All governs restore.
- Include categories, tasks, embedded subtasks, and every persisted planning field. Exclude preferences and platform alarm state.
- Use exact format `now-do-this-backup`, version `1`, and maximum input size `10 * 1024 * 1024` bytes.
- Validate fully before mutation; replace all three tables in one Room transaction, preserving positive IDs and relationships.
- Cancel pre-restore task alarms and reconcile only after commit.
- Keep Android `Uri`, Room entities, and serialization DTOs out of domain models/contracts.
- Italian is primary and English is under `values-en`. Do not add merge, CSV, encryption, cloud, automatic backup, Settings, a module, or Room migration.
- Use TDD, run JVM tests before production commits, and commit each task.

---

### Task 1: Versioned Format And Validation

**Files:** Create `feature/portability/domain/model/{PlanningBackup,PortabilityError}.kt`, `feature/portability/data/serialization/{BackupDocumentV1,BackupCodec,BackupValidator}.kt`; test under matching `app/src/test/...` packages.

**Interfaces:** Produce pure `PlanningBackup`, `PlanningCategory`, `PlanningTask`, `PlanningSubtask`, `BackupSummary`; errors `InvalidBackup`, `UnsupportedFutureVersion(version)`, `DocumentTooLarge`, `ReadFailed`, `WriteFailed`, `RestoreFailed`; `BackupCodec.encode/decode`; `BackupValidator.validate`.

- [ ] Write failing codec tests covering every persisted field, deterministic category/task/subtask ordering, exact format/version, round trip, and unknown-key tolerance.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*BackupCodecTest'` and confirm failure.
- [ ] Implement explicit v1 DTOs and `Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }` UTF-8 codec.
- [ ] Write validation tests for size boundary, wrong/future version, duplicate/non-positive IDs and positions, broken references, invalid stable enum names, category naming, blank titles, completion consistency, and recurrence end before due.
- [ ] Implement validation using existing enum names and exact 10 MiB limit; summary counts categories, tasks, completed tasks, and subtasks.
- [ ] Run focused tests and `./gradlew :app:testDebugUnitTest`; commit `feat: define versioned planning backup`.

---

### Task 2: Atomic Room Snapshot And Replacement

**Files:** Modify `TaskDao.kt`, `CategoryDao.kt`; create `feature/portability/data/local/PlanningDataSource.kt`; test `app/src/androidTest/.../PlanningDataSourceTest.kt`.

**Interfaces:** `snapshot(createdAtEpochMillis: Long): PlanningBackup`; `replaceAll(backup: PlanningBackup): Set<Int>` returning pre-restore task IDs after successful commit.

- [ ] Write failing real in-memory Room tests for all-field snapshot fidelity/order, Replace All deletion, preserved IDs/relations, repeat restore, and forced insertion rollback preserving the original graph.
- [ ] Run the focused connected test and confirm failure.
- [ ] Add ordered suspend bulk reads, category `deleteAll`, and `ABORT` bulk inserts without changing entities/version.
- [ ] Implement both operations with `withTransaction`; delete tasks then categories, insert categories then tasks then subtasks, and map every field explicitly.
- [ ] Run focused connected tests and JVM tests; commit `feat: restore planning data atomically`.

---

### Task 3: Repository, Documents, And Reminder Orchestration

**Files:** Create domain `BackupCandidate`, `PortabilityResult`, `PortabilityRepository`, use cases `CreateBackup`, `InspectBackup`, `RestoreBackup`; data `DocumentGateway`, `AndroidDocumentGateway`, `OfflinePortabilityRepository`; `PortabilityModule`; matching JVM tests.

**Interfaces:** Opaque `DocumentReference(value: String)`; repository `createBackup(ref): Exported`, `inspectBackup(ref): BackupCandidate`, `replaceAll(candidate): Set<Int>`; restore use case returns `Restored(summary)` or `RestoredWithReminderWarning(summary)`.

- [ ] Write failing repository tests for injected-clock snapshot, byte writes, bounded reads, decode/validation/error mapping, and validated-candidate-only restore.
- [ ] Write restore tests proving no alarm work before commit; post-commit cancellation then reconcile; replacement failure performs none; reminder failure yields warning without reverting data.
- [ ] Implement contracts, use cases, ContentResolver gateway, repository, IO dispatching, exception mapping, and singleton Hilt bindings without logging content/URIs.
- [ ] Run focused and complete JVM tests; commit `feat: orchestrate backup and restore`.

---

### Task 4: UDF And SAF Route

**Files:** Create `presentation/{PortabilityContract,PortabilityViewModel,PortabilityRoute}.kt` and `PortabilityViewModelTest.kt`.

**Interfaces:** State `isBusy, candidate, showRestoreConfirmation, result, error`; events `CreateBackup`, `BackupDestinationSelected`, `RestoreBackup`, `BackupSourceSelected`, `ConfirmRestore`, `DismissRestore`, `ClearResult`; effects `LaunchCreateDocument(suggestedName)`, `LaunchOpenDocument`, `ShowMessage`.

- [ ] Write failing ViewModel tests for picker effects, cancellation, export, preview, invalid source, confirmation, one-shot busy suppression, restore, and result behavior.
- [ ] Implement Hilt ViewModel with StateFlow, buffered Channel, one operation job, injected clock/zone filename `now-do-this-backup-YYYY-MM-DD.json`.
- [ ] Implement route with `CreateDocument("application/json")`, `OpenDocument`, MIME list `application/json,text/json,text/plain`, immediate URI-string wrapping, and lifecycle-aware effects.
- [ ] Run focused/complete JVM tests and compile (allow only missing Task 5 screen); commit `feat: add portability workflow state`.

---

### Task 5: Navigation And Accessible Screen

**Files:** Modify `AppNavigation`, `AppNavigator`, Task List contract/route/screen/ViewModel, bilingual strings; create `DataPortabilityKey`, `PortabilityScreen`; add screen/navigation/task-list Android tests.

**Interfaces:** `data object DataPortabilityKey : NavKey`; `openDataPortability()`; Task List event/effect/callback `OpenDataPortability`.

- [ ] Write failing tests for overflow dispatch, navigator destination, localized commands, preview counts/date, destructive replacement warning, confirmation events, busy state, back action, 48 dp targets, and semantics.
- [ ] Run focused connected tests and confirm failure.
- [ ] Integrate the non-root Navigation 3 destination and place Backup and restore after History in the overflow menu.
- [ ] Implement a quiet scaffold with back top bar, two full-width icon buttons, unframed summary, progress, and AlertDialog; use stable tags `portability-back/create/restore/summary/confirm/cancel`.
- [ ] Add all Italian/English strings; run focused connected tests, JVM tests, and lint; commit `feat: add backup and restore screen`.

---

### Task 6: Journey, Documentation, And Final Evidence

**Files:** Create `DataPortabilityJourneyTest.kt`, `docs/data-portability/backup-format-v1.md`; modify README, CHANGELOG, architecture index, test strategy/matrix, release checklist, and migration/rollback docs.

- [ ] Write a connected journey that seeds category, pending reminder/recurrence task with subtasks, and completed task; exports; mutates; restores; verifies full graph/IDs; and proves invalid/future documents do not mutate data.
- [ ] Run the journey and complete connected suite on `emulator-5554`.
- [ ] Document every root/category/task/subtask field, JSON type/nullability, stable values, ordering, size, unknown-key policy, Replace All, future rejection, reminders, privacy, and a redacted example.
- [ ] Update product, architecture, quality, changelog, release, and rollback evidence with actual results.
- [ ] Run `ANDROID_SERIAL=emulator-5554 ./gradlew clean :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest :app:assembleRelease :app:bundleRelease`.
- [ ] Verify AAB contains `baseline.prof`, `classes.dex`, and `resources.pb`; run `git diff --check`; commit `docs: verify data portability v1`.
