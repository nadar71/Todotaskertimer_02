# Accessibility Validation

## Automated Contracts

- Primary add, back, save, delete, retry, navigation, and inspection controls expose click actions and localized labels.
- Task completion exposes checkbox role, toggle state, and a title-specific localized description.
- Task and History rows expose button roles in addition to their visible labels.
- Priority and category color choices expose radio-button role and selected state.
- Reminder availability exposes a localized state description.
- Calendar days expose full localized dates, today and selected state, and task count.
- Previous and next month controls are at least 48 dp in both dimensions.
- Category move, edit, delete, and palette targets are at least 48 dp.
- Quick Capture add, retry, task-open, and completion actions expose localized
  descriptions; direct production Glance `RemoteViews` rendering verifies that
  available actions retain at least 48 dp targets at 200% text.
- The same direct render verifies English/Italian strings, light/dark palettes,
  3/5/8 capacities, and non-overlapping sibling text. Separate compact/default-font
  `AppWidgetHostView` coverage verifies two bound instances refresh from Room.
- Natural-Language Entry exposes localized field and Parse labels, a minimum 48 dp
  Parse target, and textual recognized/issue feedback with polite live-region
  semantics in visual reading order. Its 200% test compares unclipped layout bounds,
  parent containment, and adjacent vertical order; a constrained negative control
  proves the clipping oracle fails when content is actually clipped.

## Non-Color Cues

| State | Additional cue |
| --- | --- |
| High priority | Warning icon and localized priority label |
| Other priorities | Localized priority label |
| Selected category color | Check icon, border, radio-button role, selected state, and color name |
| Validation error | Inline localized error text and field error semantics |
| Disabled action | Disabled semantic state |
| Parse result and issue | Localized text summary/issue with polite live-region semantics |
| Overdue task | Dedicated localized section heading and due date text |
| Completed task | Checkbox state or completed icon and localized description |

## Validation Matrix

| Check | Environment | Date | Result |
| --- | --- | --- | --- |
| Feature Compose semantics | Medium Phone AVD, API 36 | 2026-08-13 | Passed after accessibility fixes |
| Large font, `font_scale=2.0` | Medium Phone AVD, API 36 | 2026-08-13 | Passed: 39 feature tests |
| Quick Capture direct production `RemoteViews` render at 200% text | Medium Phone AVD, API 36 | 2026-08-16 | Passed: automated 48 dp action-target and non-overlap assertions; not a bound-host claim |
| Quick Capture English/light and Italian/dark visual smoke | Pixel Launcher, API 36 emulator | 2026-08-16 | Passed: operator-placed medium/expanded widget; screenshots retained |
| Natural-Language Entry Italian/English production Compose | Medium Phone AVD, API 36 | 2026-08-26 | Passed: 14 focused tests cover localized rendering, semantics, 48 dp target, and 200% geometry/negative control |
| Natural-Language Entry connected journey | Medium Phone AVD, API 36 | 2026-08-26 | Passed: 3 real MainActivity/Navigation 3/Room/reminder journeys |
| Natural-Language Entry TalkBack parse/correct/save order | Pending physical-device review | - | Pending; automated labels/live regions are not a spoken-order claim |
| Quick Capture TalkBack reading order | Pending physical-device review | - | Pending; content descriptions are automated, spoken order is not claimed |
| Light and dark contrast review | Pending physical-device review | - | Pending |
| TalkBack: create task | Pending physical-device review | - | Pending |
| TalkBack: complete task | Pending physical-device review | - | Pending |
| TalkBack: delete and undo | Pending physical-device review | - | Pending |
| TalkBack: category management | Pending physical-device review | - | Pending |
| TalkBack: Calendar navigation | Pending physical-device review | - | Pending |
| TalkBack: History inspection | Pending physical-device review | - | Pending |

## Verification Commands

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.indiewalkabout.nowdothis.feature

adb -s emulator-5554 shell settings put system font_scale 2.0
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.indiewalkabout.nowdothis.feature
adb -s emulator-5554 shell settings put system font_scale 1.0

./gradlew :app:lintDebug
```

The emulator checks are repeatable development evidence. Complete the pending contrast,
TalkBack, switch-access, notification-permission, and exact-alarm rows on a physical
release candidate before claiming full manual accessibility validation.
