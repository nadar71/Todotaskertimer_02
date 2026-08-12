# Improve for Senior Design

## Objective

Prepare Now Do This for senior Android engineer and mobile architect interviews beginning in September 2026. The work must make engineering judgment, production readiness, and Android platform depth visible without adding decorative architecture or destabilizing the existing local-first product.

## Strategy

Work proceeds in two independently valuable programs:

1. **Production Readiness** makes the existing implementation measurable, repeatable, accessible, releasable, and easy to evaluate in a short repository review.
2. **Product Value** adds a focused private quick-capture experience through backup and restore, widgets, deterministic natural-language entry, and richer recurrence.

Production Readiness comes first. It creates an interview-ready stopping point before product expansion begins. Kotlin Multiplatform, iOS, synchronization, and broad modularization are explicitly outside this scope.

Codex usage percentages in this document allocate estimated AI effort within this roadmap. They do not represent account quota, which is not observable from the project.

## Architectural Position

The project remains a single application module with feature-first packages and Clean MVVM boundaries. This is a deliberate choice for the current product and team size.

The existing dependency direction remains:

```text
Compose UI -> ViewModel -> use case -> repository contract
                                      -> repository implementation
                                      -> Room or Android platform service
```

Screen interaction remains unidirectional:

```text
immutable StateFlow -> Compose rendering
user action -> UI event -> ViewModel
one-time result -> effect flow -> platform/navigation action
```

New abstractions or modules require at least one concrete justification: independent ownership, enforceable dependency isolation, reusable infrastructure, isolated build or testing, or measured build-time benefit. Clean Architecture layer names alone do not justify module extraction.

## Program 1: Production Readiness

### Scope And Budget

| Phase | Deliverable | Focused time | Roadmap usage |
| --- | --- | ---: | ---: |
| Architecture evidence | Diagrams, ADRs, module trigger criteria | 0.5 day | 4% |
| CI quality gates | One GitHub Actions workflow and reports | 0.5-1 day | 7% |
| Performance evidence | Benchmarks, baseline profile, results | 1 day | 8% |
| Accessibility | Automated checks and manual validation | 1-1.5 days | 10% |
| Release discipline | Release configuration and checklist | 0.5-1 day | 6% |
| Portfolio presentation | README, screenshots, final verification | 0.5 day | 5% |

Estimated total: 4-5.5 focused days and 40% of this roadmap's Codex usage.

### Architecture Evidence

The repository will document:

- System context and internal dependency diagrams.
- UDF state and event flow.
- The local-first decision and its consequences.
- The single-module feature-first decision and objective modularization triggers.
- Repository, use-case, database, reminder, and platform boundaries.
- Accepted limitations and deferred alternatives.

Documentation must describe trade-offs and rejected alternatives, not merely restate folder names.

### CI Quality Gates

A single pull-request workflow will run:

```text
compileDebugKotlin
testDebugUnitTest
lintDebug
assembleDebug
```

The workflow will cache Gradle safely and upload useful test and lint reports on failure. Dependabot will cover Gradle and GitHub Actions dependencies. A concise pull-request checklist will cover behavior, tests, accessibility, migration safety, and documentation.

SonarQube, remote build infrastructure, mandatory percentage coverage, and automated store deployment are excluded. They would add maintenance without improving the near-term architectural evidence proportionally.

### Performance Evidence

Performance work will measure:

- Cold startup.
- Initial task-list rendering.
- Scrolling with a deterministic 500-1,000 task fixture.
- Opening the task editor.
- The Room query and section-classification path.

A benchmark module and Baseline Profile are permitted because they produce measurable evidence. Results will be recorded as reproducible before-and-after measurements, including device/API/build type. Optimization work is limited to bottlenecks demonstrated by those measurements.

### Accessibility

The primary create, inspect, complete, delete, Calendar, and History journeys will be checked for:

- TalkBack labels, roles, state descriptions, and traversal order.
- Minimum 48dp interactive targets.
- Light and dark contrast.
- Text scaling through 200% without clipping or overlap.
- Error and selection communication that does not depend on color.
- Logical focus and keyboard behavior where applicable.
- Motion that does not prevent task completion.

Stable semantic contracts will receive focused Compose tests. TalkBack, large text, and visual contrast results will also be recorded in a manual verification matrix.

### Release Discipline

The release path will include:

- Explicit debug and release behavior.
- R8 and resource shrinking for release after verification.
- Environment-provided signing with no repository secrets.
- Semantic versioning and a concise changelog policy.
- Android App Bundle generation.
- Release, Room migration, backup compatibility, and rollback checklists.

Full Play Console automation is deferred until recurring releases justify it.

### Portfolio Presentation

The README will lead with the product, architecture, and evidence. It will include screenshots or a short demo reference, architecture and data-flow diagrams, build commands, CI status, performance results, test strategy, trade-offs, limitations, and future decision triggers.

The repository should be understandable during a five-minute interview review and still support a deeper architecture discussion.

## Program 2: Product Value

### Product Position

Now Do This will be presented as a privacy-first, offline planner with strong capture, recurrence, reminders, and user-owned data. Features that imply accounts, collaboration, or network synchronization remain outside scope.

### Scope And Budget

| Phase | Deliverable | Focused time | Roadmap usage |
| --- | --- | ---: | ---: |
| Data portability | Versioned backup, restore, and validation | 1-1.5 days | 10% |
| Quick capture | Home-screen widget and quick-add action | 1.5-2 days | 14% |
| Natural-language entry | Deterministic Italian and English parser | 2-3 days | 20% |
| Advanced recurrence | Rich local recurrence rules | 1.5-2.5 days | 12% |
| Product verification | Journey tests and presentation polish | 1 day | 4% |

Estimated total: 7-10 focused days and 60% of this roadmap's Codex usage.

### Data Portability

Backup and restore will use a documented, versioned JSON format and Android's Storage Access Framework. Import must validate before mutation, run atomically, preserve relationships, define duplicate handling, and reject unsupported future schemas without damaging current data. CSV interoperability is deferred so the first release can prioritize complete-fidelity recovery.

### Quick Capture

The widget will expose a small high-value surface: today/upcoming tasks, completion, and quick add. It must remain useful when the process is absent, respect locale and theme, and update after relevant repository mutations. Widget behavior will delegate to existing domain operations rather than duplicate task rules.

### Natural-Language Entry

Natural-language entry will be deterministic and offline. Italian and English parsing will initially cover date, time, priority, category, recurrence, and one reminder. The editor must show parsed fields before save, preserve the original title text when confidence is insufficient, and allow every inferred value to be corrected.

An AI service, network parser, and open-ended conversational assistant are excluded.

### Advanced Recurrence

Supported additions will include every-N-unit intervals, selected weekdays, monthly ordinal rules, and recurrence based on scheduled date or completion date. Recurrence calculation remains pure domain logic with clock and time-zone dependencies injected. Migration and backward compatibility must preserve existing recurring tasks.

### Product Verification

The critical journey is:

```text
capture -> parse -> schedule -> remind -> complete -> recur -> export -> restore
```

Domain tests cover parsing and recurrence matrices. Persistence tests cover atomic backup restoration and schema compatibility. Android tests cover widget entry, editor confirmation, reminder state, and the primary restored-data journey.

Tags, attachments, Pomodoro, habits, collaboration, synchronization, and broad analytics are deferred because each introduces a separate product domain.

## Delivery Order And Stopping Points

### August 12-18

Complete Production Readiness. At this point the repository is interview-ready even if no product expansion lands.

### August 19-25

Complete backup/restore and widget quick capture. These provide visible product value while reinforcing Android platform and local-first expertise.

### August 26-31

Implement deterministic natural-language capture. Advanced recurrence follows only if the earlier work is complete, verified, and documented with enough time remaining for rehearsal and polish.

No new feature starts during the final interview-preparation days. Those days are reserved for regression testing, screenshots, a short demo, repository cleanup, and rehearsal of architectural decisions.

## Definition Of Done

Production Readiness is complete when:

- CI enforces the documented build, test, lint, and assembly gates.
- Performance claims have reproducible measurements.
- Primary journeys pass automated semantics tests and the manual accessibility matrix.
- A release bundle can be produced through documented, secret-safe steps.
- Architectural decisions and trade-offs can be found from the README.

Product Value is complete when:

- Backup and restore preserve all supported local data atomically.
- Quick capture works from a widget without duplicating domain rules.
- Italian and English parsing is deterministic, previewable, and correctable.
- Added recurrence rules are migration-safe and time-zone aware.
- The critical product journey has automated coverage and a demonstrable release build.

## Deferred Work

- Kotlin Multiplatform and iOS.
- Network synchronization and accounts.
- Large-scale Gradle modularization.
- Collaboration and shared lists.
- Automated Play Store deployment.
- CSV import and export.
- Features unrelated to the private offline planning position.

These items remain valid future options, but none is required for the September 2026 interview objective.
