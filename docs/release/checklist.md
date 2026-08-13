# Release Checklist

- [ ] Version code increased and semantic version name confirmed.
- [ ] `CHANGELOG.md` moved relevant entries from Unreleased into the release.
- [ ] Clean JVM tests, compilation, lint, debug assembly, release APK, and release AAB pass.
- [ ] Connected smoke tests pass on each recorded device/API.
- [ ] Room schema export and migration diff are reviewed; no destructive migration exists.
- [ ] Notification permission, reminder delivery, exact-alarm fallback, and reboot reconciliation are checked.
- [ ] Localization smoke tests pass in Italian and English.
- [ ] Large-font, light/dark contrast, and TalkBack matrix is complete.
- [ ] AAB contains code, resources, and the generated Baseline Profile.
- [ ] R8 mapping files are retained with the release artifact.
- [ ] Signed artifact is verified when release credentials are supplied.
- [ ] Rollback and data compatibility implications are reviewed.
