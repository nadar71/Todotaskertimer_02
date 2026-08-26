# Task 7 Report: Deterministic Bilingual Recurrence Parsing

## Status

Task 7 is implemented on `feature/advanced_recurrence`. The parser now returns typed
recurrence rules, valid candidates, consumable source matches, ownership-only ranges,
and an explicit ambiguity issue. Italian and English grammar is deterministic and
offline, and parsed rules merge into the Task 6 correctable editor draft.

## Implementation

- Added a pure `RecurrenceParser` with immutable result collections and hard Unicode
  lexical boundaries.
- Added closed Italian and English grammar for explicit day/week/month intervals,
  selected weekday lists, and first/second/third/fourth/last weekday monthly rules.
- Preserved legacy daily, weekly, and monthly scheduled-date behavior. Explicit-N
  day/week intervals use the approved completion-date default; calendar rules use the
  scheduled-date default.
- Added explicit scheduled/completion basis suffixes in both languages. Partial or
  contradictory suffixes are ambiguous rather than partially applied.
- Duplicate, contradictory, partial, malformed, repeated-weekday, out-of-range, and
  fifth-ordinal attempts apply no rule and expose no consumed recurrence range. Their
  ownership ranges can shield lower-priority grammar while exact title text remains.
- Moved recurrence out of `AttributeParser`, wired ownership through temporal,
  reminder, category, and attribute orchestration, and renamed the parsed draft field
  to `recurrenceRule`.
- Merged typed rules through `RecurrenceEditorState.fromRule`, preserving recurrence
  end state. Monthly rules are re-anchored from the effective editor due instant in
  the captured parser `ZoneId`.
- Added a localized ambiguity issue for the Quick entry correction surface.
- Deleted `RecurrenceType.kt`, removed the legacy editor-name adapter, migrated the
  remaining Android-test fixtures to typed rules, and removed obsolete generated
  baseline-profile entries for the deleted class.

## TDD Evidence

Initial focused RED:

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

compileDebugUnitTestKotlin FAILED: RecurrenceParser, recurrenceRule,
AmbiguousRecurrence, and the recurrenceParser dependency were absent.
```

Ownership RED after the first implementation slice:

```text
43 tests completed, 1 failed
malformedOrdinalPreservesWholeTitleAndShieldsInnerTime
```

The fifth-ordinal ownership range stopped before an inner time phrase. Extending every
malformed parsed attempt through its bounded attempt range made the inner temporal
candidate unavailable without consuming recurrence text.

Self-review punctuation RED:

```text
9 tests completed, 1 failed
selectedWeekdayLists_areAccentInsensitiveAndScheduled
```

The closed list grammar did not accept an English Oxford comma. The separator rule was
extended narrowly for optional conjunctions after commas in both languages.

Enum-removal Android-test RED:

```text
./gradlew :app:compileDebugAndroidTestKotlin
FAILED: remaining Quick Capture fixtures referenced RecurrenceType
```

Those fixtures now pass typed `RecurrenceRule` values directly.

## Verification

Focused parser/editor JVM gate:

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest' \
  --tests '*TaskEditorViewModelTest'

98 tests, 0 failures; BUILD SUCCESSFUL
```

The focused count is 9 direct parser tests, 34 end-to-end use-case tests, and 55
ViewModel tests.

Full JVM gate:

```text
./gradlew :app:testDebugUnitTest

35 suites, 413 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL
```

Android-test compile gate:

```text
./gradlew :app:compileDebugAndroidTestKotlin

BUILD SUCCESSFUL
```

## Self-Review

- Confirmed malformed and ambiguous recurrence attempts return no consumable match and
  cannot set `RecognizedField.RECURRENCE`.
- Confirmed category ownership remains authoritative and recurrence ownership barriers
  are applied before reminder/temporal/lower attribute parsing.
- Confirmed monthly anchoring uses the parsed due when present and the merged editor due
  in the captured parser zone at the presentation handoff.
- Confirmed legacy daily/weekly/monthly rules remain scheduled-date based while
  explicit-N interval defaults follow the approved product decision.
- Confirmed `rg -n 'RecurrenceType' app/src` returns no matches.
- Confirmed no plan, spec, ledger, or unrelated application behavior was modified.

## Concerns

The checked-in release baseline profile contained obsolete signatures for the deleted
enum and its old consumers; those inert entries were removed. The complete baseline
profile was not regenerated because profile collection requires a separate device
benchmark run and is outside the Task 7 JVM/compile gates.

## Fix Round 1

All four P2 review findings are closed:

- Unsupported `or`/`o` continuations and punctuation-led duplicate or conflicting
  basis suffixes now invalidate and own the complete recurrence attempt. Supported
  comma weekday lists and ordinary trailing punctuation remain valid controls.
- Completed monthly-ordinal shells accept a bounded candidate token for validation,
  so unsupported ordinals and malformed weekdays own the outer phrase before a nested
  `every month`/`ogni mese` match can backtrack. Only first through fourth and last
  produce rules.
- Italian weekday candidates now admit one bounded final Unicode letter plus canonical
  combining marks before `TextNormalizer.matchingKey()` validation. Grave, acute,
  circumflex, diaeresis, and decomposed equivalents retain exact source indices.
- Malformed ownership now stops and trims before independent priority/category markers
  and punctuation. The malformed recurrence issue remains visible while `!priority`
  and `#category` continue through their authoritative parsers.

### Fix-Round TDD Evidence

The new direct and end-to-end review matrix failed before the production fix:

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

53 tests completed, 8 failed
```

The failures covered both continuation probes, nested outer ordinals, normalized
Italian accents, and marker-bounded malformed ownership at both parser layers. The
valid comma controls passed during RED. After implementation, one older category
barrier assertion failed because it expected category exclusion to erase the malformed
recurrence issue; it was updated to the reviewed ownership contract.

### Fix-Round Verification

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest' \
  --tests '*TaskEditorViewModelTest'

3 suites, 109 tests, 0 failures; BUILD SUCCESSFUL
```

```text
./gradlew -Dkotlinx.coroutines.test.default_timeout=10s \
  :app:testDebugUnitTest

35 suites, 424 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL
```

The default-timeout full command first completed 424 tests with one unrelated failure:
`QuickCaptureWidgetContentTest.unavailable_rendersLocalizedMessageAndRetryActionWithoutTechnicalDetails`
exceeded the Glance/coroutines two-second test harness timeout. The exact widget test
failed the same way in isolation, then passed unchanged with the ten-second coroutine
test timeout. No widget production or test files were modified.

Final self-review confirmed exact source/consumed ranges, exact malformed-title
preservation, bilingual negative controls, pairwise-disjoint consumed fields, Task 6
editor correctability, and zero behavioral `RecurrenceType` references. No plan, spec,
or ledger files were modified.

## Fix Round 2

All four residual P2 findings from `task-7-re-review-1.md` are closed:

- A punctuation separator followed by a locale-valid normalized weekday now marks the
  complete recurrence attempt malformed. Punctuation followed by ordinary prose remains
  outside recurrence, preserving the valid `every Monday / notes` and comma controls.
- Completed English and Italian monthly-ordinal shells accept unsuffixed numeric ordinal
  candidates, so `11 Monday of every month` and its Italian mirror own the outer attempt
  before the nested legacy month candidate can apply. Bare numbers remain excluded from
  the context-free partial-attempt detector so times, dates, and reminder quantities are
  not recurrence-owned.
- A broad completed ordinal shell becomes an attempt only when its first token is
  ordinal-like or its second token is weekday-like. `Best photo of the month` and
  `Foto preferita del mese` therefore remain ordinary, issue-free titles.
- Weekday list candidates are bounded Unicode word tokens validated through
  `TextNormalizer.matchingKey()` across the complete token. Precomposed and decomposed
  accents such as `sábato` and `sábato` retain their original source offsets.

### Round 2 TDD Evidence

The direct and end-to-end review tests were added before production changes:

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

62 tests completed, 8 failed
```

Exactly one direct and one end-to-end test failed for each residual. After the first
implementation pass, those eight were green but 15 temporal/reminder regressions failed.
Root-cause inspection showed that bare numbers had been added to the context-free
`attemptStartPattern`, causing ordinary times and dates to become recurrence ownership
ranges. Restricting bare numeric ordinals to the completed monthly shell restored those
tests without weakening the new numeric-shell behavior.

### Round 2 Verification

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

2 suites, 62 tests, 0 failures; BUILD SUCCESSFUL
```

```text
./gradlew :app:testDebugUnitTest

35 suites, 432 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL
```

The standard full JVM command passed, so the documented coroutine timeout override was
not used. Final checks found no `RecurrenceType` references, no whitespace errors, and no
plan, spec, ledger, editor, persistence, or unrelated production changes.

## Fix Round 3

The single P2 finding from `task-7-re-review-2.md` is closed. A broad weekday-list
candidate now declines an invalid first token when that token is an exact locale legacy
unit (`day/week/month` or `giorno/settimana/mese`). This occurs before containment
deduplication, so the valid legacy attempt survives and owns only its own source range.

The bypass is deliberately limited to exact legacy units. The surviving legacy attempt
still runs normalized continuation validation, so `every month / Friday` and
`ogni mese / venerdì` remain malformed whole attempts while comma followed by ordinary
prose remains outside recurrence.

### Round 3 TDD Evidence

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

64 tests completed, 2 failed
```

Only the new direct and end-to-end bilingual regression methods failed during RED. Each
method covers all legacy day/week/month forms, exact source or consumed ranges, preserved
comma prose, typed scheduled-date rules, and no ambiguity issue.

### Round 3 Verification

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

2 suites, 64 tests, 0 failures; BUILD SUCCESSFUL
```

```text
./gradlew :app:testDebugUnitTest

35 suites, 434 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL
```

The standard full JVM command passed without the coroutine timeout override. No plan,
spec, ledger, editor, persistence, or unrelated production files were changed.

## Fix Round 4

The legacy-unit weekday-list bypass now declines only ordinary prose. When a later token
is weekday-like but invalid, it produces a malformed attempt that owns the matched
continuation, so the valid legacy candidate is removed by containment. This rejects
`every month, Mondays` and `ogni mese, lunedìs` with no rule or consumed recurrence,
the exact original title, and `AmbiguousRecurrence`; `every month, notes` and
`ogni mese, note` retain their legacy scheduled-date rules and exact consumed ranges.

### Round 4 TDD Evidence

The bilingual malformed plural-weekday controls were added to the existing direct parser
and end-to-end use-case matrices before the production change:

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

64 tests completed, 2 failed
```

The failures were the new direct ownership assertion and end-to-end title-preservation
assertion. The first implementation restored the expected new behavior but widened
comma ownership for generic malformed prose; the pre-existing punctuation-boundary
control failed, so preservation was restricted to the legacy-unit plus weekday-like
continuation path.

### Round 4 Verification

```text
./gradlew :app:testDebugUnitTest \
  --tests '*RecurrenceParserTest' \
  --tests '*ParseNaturalLanguageTaskTest'

2 suites, 64 tests, 0 failures; BUILD SUCCESSFUL
```

```text
./gradlew :app:testDebugUnitTest

37 suites, 0 failures; BUILD SUCCESSFUL
```

Final self-review confirmed the valid English and Italian comma-prose controls, exact
and plural weekday malformed continuations, whole-title preservation, no consumed
malformed range, scoped ownership, and a clean whitespace diff. No plan, spec, or
ledger files were modified.
