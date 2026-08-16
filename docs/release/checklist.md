# Release Checklist

- [ ] Version code increased and semantic version name confirmed.
- [ ] `CHANGELOG.md` moved relevant entries from Unreleased into the release.
- [ ] Clean JVM tests, compilation, lint, debug assembly, release APK, and release AAB pass.
- [ ] Connected smoke tests pass on each recorded device/API.
- [ ] Room schema export and migration diff are reviewed; no destructive migration exists.
- [ ] Notification permission, reminder delivery, exact-alarm fallback, and reboot reconciliation are checked.
- [ ] Localization smoke tests pass in Italian and English.
- [ ] Quick Capture direct production Glance rendering covers 3/5/8 responsive
  capacities, empty/unavailable states, locale, theme, 200% text, and 48 dp actions.
- [ ] Separate compact/default-font `AppWidgetHostView` coverage proves multiple
  bound instances refresh from Room.
- [ ] Quick Capture update, recurring completion, add, and open pass with the
  optimized target process absent; launcher placement/resize evidence is identified
  as manual rather than automated.
- [ ] Release manifest keeps the widget receiver non-exported with provider metadata,
  and release artifacts exclude benchmark fixture classes and methods.
- [ ] Create backup, preview, confirmation, Replace All restore, cancellation, and invalid/future rejection are checked against [format v1](../data-portability/backup-format-v1.md).
- [ ] A backup from the release candidate restores category/task/subtask IDs, completion state, recurrence, and reminder metadata on a clean test install.
- [ ] Backup privacy wording remains clear: JSON is local, user-directed, and unencrypted.
- [ ] Large-font, light/dark contrast, and TalkBack matrix is complete.
- [ ] AAB contains code, resources, and the generated Baseline Profile.
- [ ] R8 mapping files are retained with the release artifact.
- [ ] Signed artifact is verified when release credentials are supplied.
- [ ] Rollback and data compatibility implications are reviewed.
- [ ] Any planning-schema change has an explicit backup-format compatibility decision (additive v1 field or new format version).
