# Natural-Language Entry Design

## Context

Now Do This is a privacy-first, local-first Android planner whose task editor already owns validation, reminder permissions, persistence, and process-restored draft state. Natural-Language Entry adds a deterministic bilingual capture accelerator without introducing a network parser, AI service, automatic save path, Room migration, or duplicate task rules.

This subsystem is implemented on `feature/natural_language_entry` from the verified `develop` branch. Italian remains the primary language and English remains fully supported through Android's native per-app language selection.

## Product Scope

Natural-Language Entry v1 appears only when creating a task. A dedicated Quick entry field and explicit Parse action convert supported Italian or English expressions into the existing task editor fields. The normal editor is the confirmation and correction surface, and the existing Save action remains the only persistence command.

The parser supports:

- relative dates: today/tomorrow and oggi/domani;
- localized numeric dates;
- 24-hour times and English 12-hour times;
- date-only input resolved to 09:00 in the injected local time zone;
- priority markers in Italian and English;
- case-insensitive category markers matched against current user-managed categories and localized default category names;
- existing recurrence values: daily, weekly, and monthly;
- one absolute reminder or one reminder expressed relative to the due time.

The task description remains blank after parsing and remains required by the existing domain validation. Natural-Language Entry does not create categories, tasks, reminders, or recurrence rules by itself.

## Input Grammar

The grammar combines natural date, time, recurrence, and reminder phrases with explicit category and priority markers. Representative inputs are:

```text
Compra latte domani alle 18 #Casa !alta ogni settimana promemoria 1h prima
Buy milk tomorrow at 6 pm #Home !high every week remind 1h before
```

Supported priority markers are `!alta`, `!media`, and `!bassa` for Italian and `!high`, `!medium`, and `!low` for English. Category markers use `#<name>` and may contain spaces only when quoted. Matching ignores case and respects locale-aware normalization. If multiple categories normalize to the same marker, the parser does not select one and reports an ambiguity.

Parser stages run in a documented order so overlapping expressions are deterministic:

1. normalize whitespace while retaining source ranges;
2. identify quoted and explicit markers;
3. parse reminder expressions before general date/time expressions;
4. parse recurrence;
5. parse due date and time;
6. parse priority and category;
7. remove only successfully consumed ranges and normalize the remaining text into the proposed title.

When an input repeats the same field, the last explicit valid occurrence wins and a nonfatal issue reports the duplicate. Invalid or unsupported syntax is not consumed and therefore remains in the proposed title. A relative reminder without a due date is not applied and reports a nonfatal issue. An inferred title is applied only when its normalized value is nonblank.

## Architecture

Natural-Language Entry is a feature-first vertical slice:

```text
feature/naturallanguage/
  domain/model/
  domain/parser/
  domain/usecase/
```

The domain layer contains no Android, Compose, Room, repository implementation, or localized resource dependency.

Primary contracts:

- `NaturalLanguageInput` contains raw text, parser locale, current instant, time zone, and available category candidates.
- `ParsedTaskDraft` contains a proposed title and optional due time, reminder, recurrence, priority, and category ID.
- `ParseIssue` represents deterministic nonfatal conditions such as ambiguity, unsupported dependencies, and duplicate values.
- `NaturalLanguageParseResult` contains the typed draft, consumed-field metadata, and issues.
- `ParseNaturalLanguageTask` orchestrates small parser stages that return typed values and consumed source ranges.

Parser stages are independently testable. They communicate through typed results rather than shared mutable state. An internal orchestration context resolves precedence, validates cross-field dependencies, and constructs the remaining title.

The existing `TaskEditorViewModel` owns all mutable editor state. It calls `ParseNaturalLanguageTask` with the current categories, injected clock, and injected time zone, then maps explicitly recognized values onto the existing `TaskEditorUiState`. No parser type crosses into the data layer. `SaveTask`, repositories, Room, reminder scheduling, Navigation 3, and task validation remain authoritative and unchanged except for dependency wiring required to expose the use case.

## Category Resolution

The editor already observes categories through `CategoryRepository`; these current values are passed into the parser as immutable candidates. Category matching uses normalized display names, including localized names for sensible default categories.

Matching outcomes are:

- exactly one normalized match: apply its category ID;
- no match: retain the marker in the title and report an unknown-category issue;
- multiple matches: retain the marker in the title and report an ambiguous-category issue.

The parser never creates, renames, or chooses between ambiguous categories.

## Editor Data Flow

The new-task editor displays a compact Quick entry section above the title field. It contains a multiline input, a Parse action, localized supporting content, and accessible semantics. The section is absent when `TaskEditorKey` identifies an existing task.

The explicit flow is:

1. The user enters a phrase and selects Parse.
2. The ViewModel snapshots locale, clock, time zone, and current categories.
3. The pure use case returns a parsed draft, recognized-field metadata, and issues.
4. The ViewModel atomically applies only fields explicitly recognized by this parse.
5. The UI shows a concise localized summary of recognized fields and any nonfatal issues.
6. The user corrects values through existing editor controls, supplies the required description, and selects the normal Save action.
7. Existing validation, reminder permissions, scheduling, optimistic concurrency, and persistence execute unchanged.

Re-parsing does not reset editor fields absent from the latest parse. It replaces a field only when the latest result explicitly recognizes that field. Parsing reminder data updates draft state but does not itself request notification or exact-alarm permissions; the existing reminder/save flows remain responsible for platform interaction.

## State Restoration And Errors

`SavedStateHandle` persists the raw Quick entry text, recognized-field summary, and parse issues together with the existing editor draft. Process recreation restores the same preview without parsing again against a changed clock or category set.

Empty or whitespace-only input mutates no task fields and shows a localized validation issue. Unexpected parser failures are contained by the ViewModel, preserve the current editor draft, and produce a localized failure effect or issue. Coroutine cancellation always propagates.

All issues are nonfatal until existing task validation runs. Parsing never partially mutates editor state: the result is computed first and then applied in one state update.

## Localization And Accessibility

Italian strings live in the default `values` resources and English strings in `values-en`. Parser vocabulary is selected from the active app locale supplied as a domain value; it does not read Android resources directly.

The Quick entry field and Parse action expose localized labels and content descriptions. Interactive targets remain at least 48 dp. Result and issue semantics follow visual reading order, remain understandable without color, and support 200% font scale without clipping or overlap.

## Testing Strategy

Pure JVM coverage includes:

- Italian and English vocabulary matrices;
- relative and numeric dates across midnight, month/year boundaries, DST gaps/overlaps, and multiple time zones;
- date-only 09:00 behavior;
- 24-hour and English 12-hour time parsing;
- source-range consumption and title preservation;
- priority and category matching, including case, accents, quoted names, unknown names, and ambiguous normalized names;
- recurrence and reminder parsing;
- duplicate precedence and cross-field dependency issues;
- cancellation and unexpected-failure behavior at integration boundaries.

ViewModel tests cover new-task-only availability, explicit Parse behavior, atomic selective field replacement, required-description preservation, empty input, parser failure, category snapshots, permission non-interaction, and `SavedStateHandle` process restoration.

Compose and connected Android tests cover Italian and English rendering, accessibility semantics, result and issue presentation, 200% font scale, absence on edit screens, and this primary journey:

```text
quick entry -> parse -> correct -> save -> persisted task/reminder
```

Final gates include the full JVM suite, connected API 36 suite, lint, debug and optimized release artifacts, localization checks, `git diff --check`, task-level reviews, and a whole-branch independent review.

## Out Of Scope

Natural-Language Entry v1 does not include:

- automatic parsing while typing;
- automatic save or a second persistence path;
- parsing on existing-task edit screens;
- optional task descriptions or changed task validation;
- category creation;
- subtasks;
- selected-weekday, interval, ordinal, or completion-based advanced recurrence;
- voice input;
- network, AI, machine-learning, or third-party NLP services;
- open-ended conversational interpretation.

## Completion Criteria

Natural-Language Entry v1 is complete when:

- the dedicated new-task Quick entry flow parses the documented Italian and English grammar deterministically and offline;
- recognized values populate existing editor controls without saving or silently changing unrecognized fields;
- ambiguous and unsupported input remains recoverable and visible;
- editor corrections and the existing required description flow remain authoritative;
- state survives process recreation without time-dependent re-parsing;
- parser, ViewModel, UI, and connected journey coverage passes;
- release, localization, accessibility, and quality evidence is documented;
- independent review reports no load-bearing findings.
