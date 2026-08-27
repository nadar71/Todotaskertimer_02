# Changelog

Notable changes follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and releases use semantic versioning.

## Unreleased

### Added

- Typed advanced recurrence rules for intervals, selected weekdays, anchored monthly dates, and monthly ordinal weekdays, with scheduled-date and completion-date bases.
- Room v3 recurrence migration, atomic recurring completion/reminder continuity, lossless backup v2 export with v1 import compatibility, and deterministic Italian/English advanced recurrence parsing.
- Executable connected critical journeys for recurrence completion, monthly anchor recovery, completion-date intervals, recurrence-end stopping, alarm continuity, backup restore, and bilingual editor recreation.

- Explicit offline Italian/English Quick entry with deterministic parsing for supported dates, times, priorities, current categories, recurrence, and reminders before normal editor confirmation and save.
- Real MainActivity/Navigation 3 bilingual parse-correct-describe-save journeys through Room and AlarmManager, plus relative-date recreation coverage without reparsing.
- Responsive home-screen Quick Capture widget with overdue/today/upcoming ordering, 3/5/8-task layouts, localized add/open/complete actions, and empty/error/retry states.
- Process-absent widget refresh and recurring-task completion coverage, plus production Navigation 3 add/open journey tests.
- Verified English/light and Italian/dark Pixel Launcher screenshots with explicit automated-versus-manual host evidence.
- Production-readiness evidence, CI gates, accessibility contracts, and performance benchmarks.
- Versioned JSON backup and confirmed Replace All restore for categories, tasks, completion history, subtasks, recurrence, and reminder metadata.
- Italian and English backup/restore UI through Android's native document picker.

### Performance

- Generated Baseline Profile for startup and critical task journeys.

### Accessibility

- Localized Quick entry labels and live parse feedback, a minimum 48 dp Parse target, and 200% text clipping/non-overlap regression coverage.
- Quick Capture action descriptions, minimum 48 dp targets, direct large-font production `RemoteViews` rendering, and localized English/Italian semantics.
- Explicit roles, states, labels, and minimum targets for primary workflows.

## 1.0.0

### Added

- Local-first task, category, calendar, history, recurrence, subtask, reminder, and localization workflows.
