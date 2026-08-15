# Release Checklist

- [ ] Version code increased and semantic version name confirmed.
- [ ] `CHANGELOG.md` moved relevant entries from Unreleased into the release.
- [ ] Clean JVM tests, compilation, lint, debug assembly, release APK, and release AAB pass.
- [ ] Connected smoke tests pass on each recorded device/API.
- [ ] Room schema export and migration diff are reviewed; no destructive migration exists.
- [ ] Notification permission, reminder delivery, exact-alarm fallback, and reboot reconciliation are checked.
- [ ] Localization smoke tests pass in Italian and English.
- [ ] Create backup, preview, confirmation, Replace All restore, cancellation, and invalid/future rejection are checked against [format v1](../data-portability/backup-format-v1.md).
- [ ] A backup from the release candidate restores category/task/subtask IDs, completion state, recurrence, and reminder metadata on a clean test install.
- [ ] Backup privacy wording remains clear: JSON is local, user-directed, and unencrypted.
- [ ] Large-font, light/dark contrast, and TalkBack matrix is complete.
- [ ] AAB contains code, resources, and the generated Baseline Profile.
- [ ] R8 mapping files are retained with the release artifact.
- [ ] Signed artifact is verified when release credentials are supplied.
- [ ] Rollback and data compatibility implications are reviewed.
- [ ] Any planning-schema change has an explicit backup-format compatibility decision (additive v1 field or new format version).
