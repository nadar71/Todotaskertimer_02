# Changelog

Notable changes follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and releases use semantic versioning.

## Unreleased

### Added

- Responsive home-screen Quick Capture widget with overdue/today/upcoming ordering, 3/5/8-task layouts, localized add/open/complete actions, and empty/error/retry states.
- Process-absent widget refresh and recurring-task completion coverage, plus production Navigation 3 add/open journey tests.
- Verified English/light and Italian/dark Pixel Launcher screenshots with explicit automated-versus-manual host evidence.
- Production-readiness evidence, CI gates, accessibility contracts, and performance benchmarks.
- Versioned JSON backup and confirmed Replace All restore for categories, tasks, completion history, subtasks, recurrence, and reminder metadata.
- Italian and English backup/restore UI through Android's native document picker.

### Performance

- Generated Baseline Profile for startup and critical task journeys.

### Accessibility

- Quick Capture action descriptions, minimum 48 dp targets, direct large-font production `RemoteViews` rendering, and localized English/Italian semantics.
- Explicit roles, states, labels, and minimum targets for primary workflows.

## 1.0.0

### Added

- Local-first task, category, calendar, history, recurrence, subtask, reminder, and localization workflows.
