# Migration And Rollback

Room schema versions move forward only. Destructive migration is prohibited because the application is local-first and the database is the user's source of truth. Every schema change requires an exported schema, an explicit migration, and migration tests.

Installing a binary that expects an older Room schema is unsupported. Recovery after a faulty schema release requires a compatible forward app update. Data Portability v2 is an additional user-directed recovery path for planning data, but it does not make an older binary compatible with a newer Room database.

Before a risky release, create and retain a JSON backup using the release candidate.
Restore is Replace All: after complete decoding, graph validation, and explicit user
confirmation, it atomically replaces categories, tasks, completion history, and
subtasks while preserving IDs. It does not restore UI preferences, app language,
notification channels, or platform alarm registrations. Reminder alarms are
reconciled from restored task data only after the database commit.

Room v3 adds flattened recurrence columns and migrates legacy `NONE`, `DAILY`,
`WEEKLY`, and `MONTHLY` values into the typed-rule representation. For legacy monthly
rows, the anchor is derived in the device zone at migration time; a user changing zones
at exactly that upgrade boundary may therefore observe a different historical anchor,
because the earlier schema stored no zone. The full `1→2→3` path is migration-tested
and destructive migration remains prohibited.

Format versions `1` and `2` are accepted; future versions are rejected before
mutation. Version 2 is the export format and encodes structured recurrence losslessly;
version 1 remains decode-only compatibility for legacy rules. The exact contracts and
privacy limits are in the [v1](../data-portability/backup-format-v1.md) and
[v2](../data-portability/backup-format-v2.md) references.

For code-only regressions that do not change the schema, issue a corrected patch release. Preserve the affected AAB, mapping files, changelog, and verification record so the failure can be reproduced and symbolicated.

Quick Capture is a code-and-manifest addition and does not change the Room schema,
backup format, or user task data. A corrected patch may disable or remove the
non-exported widget receiver, its provider metadata/resources, and the Glance
dependency without migrating or deleting Room data. Existing launcher instances
then become unavailable and can be removed by the host; reinstalling a corrected
receiver rebuilds content from the same Room source of truth. Retain the affected
APK/AAB and mapping artifacts, and repeat process-absent and launcher-host checks
after re-enabling the surface.
