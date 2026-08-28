# Google Play Release Media Design

## Objective

Replace the default Android launcher identity and produce a polished, truthful,
localized Google Play media set for NowDoThis. The result should present the app
as a focused premium productivity tool while accurately reflecting its current
features.

This work changes branding assets and store media only. It does not alter task
behavior, application architecture, navigation, persistence, or domain logic.

## Scope

The first release targets Android phones and includes:

- A refined NowDoThis forward-check logo and wordmark.
- Color, adaptive, round, legacy, and monochrome launcher icons.
- A 512 x 512 Google Play app icon.
- A 1024 x 500 Google Play feature graphic.
- Six 1080 x 1920 portrait phone screenshots in Italian.
- The same six screenshots localized in English.
- Italian and English screenshot alt text.
- Reusable local sources and regeneration instructions.

Tablet screenshots, preview video, UI redesign, onboarding, paid-feature claims,
and cloud-sync claims are outside this scope.

## Brand Direction

The identity is crisp, calm, and purposeful. A check mark flows into a forward
stroke to communicate both task completion and immediate next action. It is the
single visual anchor across launcher and store assets.

The palette uses:

- Deep evergreen as the primary color for focus and trust.
- Fresh mint as the action and progress accent.
- White and cool light gray as supporting neutrals.
- A limited coral accent where reminder context needs differentiation.

The wordmark is `NowDoThis`, with `This` subtly accented. Artwork uses flat,
clean geometry without gradients, glass effects, decorative clutter, obsolete
device frames, or embedded store badges.

## Launcher Icon

The launcher icon uses separate foreground and background layers in a 108 x 108
dp adaptive-icon canvas. Essential foreground geometry remains inside the 66 x
66 dp safe zone so OEM circle, squircle, rounded-square, and other masks cannot
clip it.

The resource set includes:

- Evergreen adaptive background.
- White check plus mint forward-stroke foreground.
- A simplified single-color monochrome layer for themed icons.
- Round and standard adaptive declarations.
- Rasterized legacy density variants for pre-adaptive launchers.
- A high-resolution store icon derived from the same geometry.

The launcher artwork contains no wordmark because text would not remain legible
at launcher sizes.

## Store Screenshot Narrative

All screenshots use genuine NowDoThis emulator captures. Editorial framing may
add a solid background, concise headline, and restrained brand elements, but it
must keep app UI prominent and must not invent functionality.

The upload order is:

1. Focus on what matters now
   - IT: `Fai adesso ciò che conta`
   - EN: `Do what matters now`
   - UI: Main task list populated with realistic, non-personal sample data.
2. Capture without interruption
   - IT: `Cattura un'attività in un istante`
   - EN: `Capture a task in an instant`
   - UI: Quick Capture workflow and its real entry point.
3. Write naturally
   - IT: `Scrivi come pensi`
   - EN: `Write the way you think`
   - UI: Natural-language entry resolving supported task details.
4. Plan repeating work precisely
   - IT: `Ripeti solo quando serve`
   - EN: `Repeat exactly when needed`
   - UI: Reminder and advanced recurrence editor.
5. Keep every area organized
   - IT: `Ogni impegno al suo posto`
   - EN: `A place for every commitment`
   - UI: Categories with real calendar or history context.
6. Preserve local ownership
   - IT: `I tuoi dati, sempre con te`
   - EN: `Your plans stay with you`
   - UI: Local backup and restore. The composition must not imply cloud sync.

Headlines are benefits rather than feature labels. Each composition uses one
headline only, avoids repeated copy, and keeps enough contrast and size to be
read in a reduced Play Store carousel.

## Feature Graphic

The 1024 x 500 feature graphic extends the forward-check gesture into a quiet
brand composition and incorporates a small authentic task-list detail. Important
content stays near the center so cropping on different Google Play surfaces does
not remove it. Branding complements the app icon instead of duplicating a large
icon. Text is minimal and localized only if the final composition needs text.

## Asset Organization

Store media lives outside Android runtime resources:

```text
store-assets/google-play/
  README.md
  source/
  common/
  it-IT/
    phone-screenshots/
  en-US/
    phone-screenshots/
```

`source/` contains editable brand geometry, screenshot compositions, capture
inputs, and the local renderer. `common/` contains the Play Store icon and any
language-neutral final media. Locale folders contain upload-ready screenshots,
localized feature graphics when necessary, alt text, and listing copy related to
the media.

Generated output uses stable numeric filenames matching upload order. The README
records dimensions, source capture state, rendering command, and Play Console
upload mapping.

## Capture And Rendering Flow

1. Build and install the debug app on a deterministic phone emulator.
2. Set the required Android app language for Italian or English.
3. Seed only realistic fictional tasks needed by the approved narrative.
4. Navigate to each real feature state and capture the app at native resolution.
5. Remove or normalize status-bar details that expose machine or personal state.
6. Render captures into 1080 x 1920 editorial compositions from reusable sources.
7. Export opaque 24-bit PNG files and generate locale contact sheets for review.

The renderer must be local and deterministic. Replacing a headline or source
capture should regenerate the corresponding media without hand-editing every
output file.

## Accessibility And Localization

Italian is the primary language and English is a complete equivalent set. Copy
uses natural locale-specific phrasing rather than literal truncation. Text must
fit without scaling based on viewport width and must meet readable contrast.

Each screenshot receives locale-specific alt text describing both the visible
app state and the demonstrated benefit. No screenshot contains carrier names,
notifications, personal identifiers, debug overlays, or inaccessible text baked
over visually busy content.

## Validation

Automated asset checks verify:

- Exact pixel dimensions.
- PNG format and required alpha behavior.
- Maximum file size where applicable.
- Six ordered screenshots per locale.
- Matching Italian and English asset inventories.
- Presence of alt text and regeneration documentation.

Visual checks verify:

- Icon legibility at 48 px and smaller preview sizes.
- Adaptive icon safety under common launcher masks.
- Monochrome themed-icon behavior.
- Installed launcher and Android splash appearance.
- Headline fit, contrast, and visual consistency in both locales.
- Prominence and truthfulness of actual in-app UI.
- Clean status bars and absence of personal or debug information.

The Android build and focused launcher-resource checks must pass after replacing
runtime assets. Final contact sheets are reviewed before treating files as ready
for Play Console upload.

## Success Criteria

The task is complete when the default Android Studio identity is absent, the
installed app presents the approved forward-check mark across supported launcher
behaviors, and both localized upload sets pass automated and visual validation.
Every store claim must be demonstrable in the current build, and all final files
must be reproducible from committed sources.
