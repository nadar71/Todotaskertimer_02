# Migration And Rollback

Room schema versions move forward only. Destructive migration is prohibited because the application is local-first and the database is the user's source of truth. Every schema change requires an exported schema, an explicit migration, and migration tests.

Installing a binary that expects an older Room schema is unsupported. Recovery after a faulty schema release requires a compatible forward app update. A user-owned backup may become an additional recovery path when the separate Product Value program delivers backup and restore; it is not available today.

For code-only regressions that do not change the schema, issue a corrected patch release. Preserve the affected AAB, mapping files, changelog, and verification record so the failure can be reproduced and symbolicated.
