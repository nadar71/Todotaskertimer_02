# Google Play Media Evidence - 2026-08-28

## Scope

This record covers the NowDoThis launcher identity and localized Google Play
media produced by the Google Play release-media plan. No Play Console upload,
Git push, or branch merge was performed as part of this verification.

Verification was executed on 2026-08-31 in the `feature/store_media` worktree.

## Automated Verification

Results are recorded after running the plan-declared commands from the repository
root.

| Check | Command | Result |
|---|---|---|
| Media unit tests | `.venv-store-media/bin/python -m unittest tools.store_media.test_media -v` | PASS: 38 tests in 6.048 s, zero failures or errors; includes native-capture geometry and mixed-alpha wordmark regressions |
| Capture host safety | `bash tools/store_media/test_capture.sh` | PASS: rejects physical/unsupported display targets before destructive commands and restores normalized emulator state after failure |
| Localized capture journeys | `bash tools/store_media/capture.sh --locale <it-IT\|en-US> --serial emulator-5554` | PASS: one connected journey per locale on API 36; six 1080 x 2400 captures each; in-app environment assertions passed |
| Emulator fixture tests | `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.storemedia.StoreMediaFixtureTest --console=plain` | PASS: 3 tests on the API 36 emulator, including Default sort reset |
| Asset validation | `.venv-store-media/bin/python -m tools.store_media.media validate --root .` | PASS: exit 0 and zero validation errors |
| Android JVM tests, lint, debug APK, release AAB, special variants | `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease :app:compileBenchmarkReleaseKotlin :app:compileNonMinifiedReleaseKotlin --console=plain` | PASS: `BUILD SUCCESSFUL` in 1m13s; 153 tasks (18 executed, 135 up-to-date); lint XML contains zero Error issues |
| Fixture provider exposure | Inspect merged `release`, `benchmarkRelease`, and `nonMinifiedRelease` manifests | PASS: fixture provider absent from all three; present only in debug |

Gradle emitted the existing local SDK warning that `ndk-bundle` has no
`source.properties`, followed by a non-fatal notice that two native libraries
were packaged without stripping. The requested tasks still completed with exit
code 0. Outputs were written to `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/bundle/release/app-release.aab`.

## Installed Identity

Device and launcher details are recorded before any installation or device-state
change. Unsupported launcher mask controls are reported as not available.

| Item | Evidence | Result |
|---|---|---|
| Emulator identity | Serial `emulator-5554`; model `sdk_gphone64_arm64`; `ro.kernel.qemu=1` rechecked before each state-changing sequence | PASS: confirmed emulator; no physical device used |
| Android API | Android 16, API 36 | PASS: API 33+ requirement met |
| Launcher | Pixel Launcher, `com.google.android.apps.nexuslauncher/.NexusLauncherActivity` | PASS: installed debug app resolved and rendered |
| Circle mask | Active framework mask lookup returned a circular path (`M50 0C77.6...`); installed color icon inspected on the home screen | PASS: white check and mint forward stroke remain distinct and unclipped |
| Squircle mask | Pixel Launcher exposes no icon-shape selector and the active framework configuration contains only the circle mask | NOT AVAILABLE on this launcher; not claimed as passed |
| Rounded-square mask | Pixel Launcher exposes no icon-shape selector and the active framework configuration contains only the circle mask | NOT AVAILABLE on this launcher; not claimed as passed |
| Themed monochrome icon | Enabled `Icone a tema`; system UI reported `themed_icon_toggle checked="true"`; pinned home icon used one foreground tone over the system theme background | PASS: monochrome forward-check identity is legible and unclipped |
| Android splash | Cold launch after `am force-stop`; 250 ms frame inspected at 1080 x 2400 | PASS: evergreen background and centered white/mint forward-check match the launcher identity with no clipping |

Inspection captures were kept locally under
`/tmp/nowdothis-store-media-review/emulator/` and were not added to Git. The most
relevant frames are `color-home-pinned.png`, `themed-home-pinned.png`, and
`splash-0250ms.png`. The themed-icon toggle was restored to its original off
state and the temporary home-screen shortcut was removed after inspection.

## Contact Sheet Review

Review criteria for every image: headline readable at full and reduced carousel
size; no clipped text; no personal or debug content; no cloud-sync or paid claim;
clean status bar; narrative order is correct; and genuine app UI occupies 75% of
the 1080 x 1920 composition, exceeding the 70% requirement.

### Italian (`it-IT`)

| Order | Screenshot | Full size | Reduced size | Content and truthfulness | Result |
|---:|---|---|---|---|---|
| 1 | `01-focus.png` | PASS: headline and task rows readable; no clipping | PASS: benefit and populated list remain identifiable | Fictional tasks, clean 10:00 status bar, no unsupported claim | PASS |
| 2 | `02-quick-capture.png` | PASS: editor, quick-entry field, and action fit | PASS: quick-capture state remains clear | Empty genuine editor state; no personal or debug content | PASS |
| 3 | `03-natural-language.png` | PASS: sentence, parsed title, and headline fit | PASS: natural-entry benefit remains clear | Fictional sentence demonstrates supported parsing only | PASS |
| 4 | `04-recurrence.png` | PASS: visible no-reminder state, weekday recurrence, calculation basis, and complete end selector fit | PASS: repeat controls remain identifiable | Alt text truthfully states that the reminder is not set | PASS |
| 5 | `05-organize.png` | PASS: history title, category filters, completed task, and Work category fit | PASS: categorized completion context remains clear | Real history context from fictional fixture data; no personal content | PASS |
| 6 | `06-portability.png` | PASS: local backup and restore actions fit | PASS: local ownership benefit remains clear | Copy and UI do not imply cloud sync | PASS |

### English (`en-US`)

| Order | Screenshot | Full size | Reduced size | Content and truthfulness | Result |
|---:|---|---|---|---|---|
| 1 | `01-focus.png` | PASS: headline and task rows readable; no clipping | PASS: benefit and populated list remain identifiable | Fictional tasks, clean 10:00 status bar, no unsupported claim | PASS |
| 2 | `02-quick-capture.png` | PASS: editor, quick-entry field, and action fit | PASS: quick-capture state remains clear | Empty genuine editor state; no personal or debug content | PASS |
| 3 | `03-natural-language.png` | PASS: sentence, parsed title, and headline fit | PASS: natural-entry benefit remains clear | Fictional sentence demonstrates supported parsing only | PASS |
| 4 | `04-recurrence.png` | PASS: visible no-reminder state, weekday recurrence, calculation basis, and complete end selector fit | PASS: repeat controls remain identifiable | Alt text truthfully states that the reminder is not set | PASS |
| 5 | `05-organize.png` | PASS: history title, category filters, completed task, and Work category fit | PASS: categorized completion context remains clear | Real history context from fictional fixture data; no personal content | PASS |
| 6 | `06-portability.png` | PASS: local backup and restore actions fit | PASS: local ownership benefit remains clear | Copy and UI do not imply cloud sync | PASS |

Both 906 x 1032 contact sheets were inspected at native size and as 397 x 453
reduced carousel previews. The renderer reserves 1440 of 1920 vertical pixels
for real UI (75%) in every screenshot, and the automated composition test also
enforces the minimum-prominence contract. Narrative order and locale inventories
match for all twelve images.

The review contact sheets are:

- `store-assets/google-play/it-IT/contact-sheet.png`
- `store-assets/google-play/en-US/contact-sheet.png`

They are review artifacts, not Play Console uploads.

## Upload Handoff

Follow `store-assets/google-play/README.md`. Upload the common app icon and feature
graphic, then the six Italian and six English phone screenshots in numeric order.
Keep the locale-specific `alt-text.txt` filename mapping with the corresponding
listing. Do not upload wordmark, contact sheets, source files, or transient raw
captures.
