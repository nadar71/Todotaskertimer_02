# Release Checklist

- [ ] Version code increased and semantic version name confirmed.
- [ ] `CHANGELOG.md` moved relevant entries from Unreleased into the release.
- [ ] Clean JVM tests, compilation, lint, debug assembly, release APK, and release AAB pass.
- [ ] Connected smoke tests pass on each recorded device/API.
- [ ] Room schema export and migration diff are reviewed; no destructive migration exists.
- [ ] Advanced recurrence Room v3 migration and `1→2→3` path preserve legacy rows.
- [ ] Notification permission, reminder delivery, exact-alarm fallback, and reboot reconciliation are checked.
- [ ] Localization smoke tests pass in Italian and English.
- [ ] Google Play app icon, feature graphic, and both six-image locale sets pass
  deterministic media validation; upload order and alt text match the
  [media handoff](../../store-assets/google-play/README.md).
- [ ] Installed adaptive icon, supported launcher masks, themed icon, splash, and
  both locale contact sheets match the recorded
  [Google Play media evidence](google-play-media-evidence-2026-08-28.md).
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
- [ ] Backup v2 losslessly round trips every typed recurrence rule; backup v1 remains importable.
- [ ] Critical recurrence journey records capture/parse/save/remind/complete/recur/export/restore evidence and registered-alarm continuity.
- [ ] Backup privacy wording remains clear: JSON is local, user-directed, and unencrypted.
- [ ] AdMob sample IDs are replaced, the GDPR message is published, privacy options
  are reachable when required, and consent denial prevents Mobile Ads initialization.
- [ ] Privacy policy, Google Play Data Safety and Ads declarations, and `app-ads.txt`
  match the final advertising behavior documented in [ads privacy](ads-privacy.md).
- [ ] Large-font, light/dark contrast, and TalkBack matrix is complete.
- [ ] AAB contains code, resources, and the generated Baseline Profile.
- [ ] R8 mapping files are retained with the release artifact.
- [ ] Signed artifact is verified when release credentials are supplied.
- [ ] Rollback and data compatibility implications are reviewed.
- [ ] Any planning-schema change has an explicit backup-format compatibility decision (additive v1 field or new format version).
