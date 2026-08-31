# Google Play Media

This directory contains the upload-ready Google Play media for NowDoThis and
the deterministic sources used to regenerate it. Italian (`it-IT`) is the
primary locale; English (`en-US`) has an equivalent six-image phone story.

## Environment

Create the local renderer environment from the repository root:

```bash
python3 -m venv .venv-store-media
.venv-store-media/bin/python -m pip install -r tools/store_media/requirements.txt
```

The renderer is local and does not upload data or assets. Raw emulator captures
under `source/captures/` are transient and ignored by Git. The deterministic
fixture, capture automation, manifest, brand source, and final generated outputs
are committed.

## Capture

Use an API 33 or newer Android emulator whose physical display is 1080 x 2400 at
420 dpi. Never run the capture script against a physical device; before any
build, install, or fixture call, the script verifies `ro.kernel.qemu=1`, API
level, physical display size, and physical density.

```bash
tools/store_media/capture.sh --locale it-IT --serial emulator-5554
tools/store_media/capture.sh --locale en-US --serial emulator-5554
.venv-store-media/bin/python -m tools.store_media.media validate-captures --root .
```

The reproducible capture contract is portrait 1080 x 2400 at 420 dpi, font scale
1.0, light mode, `Europe/Rome` timezone with automatic timezone disabled, and
window/transition/animator scales set to zero. The script also fixes display
rotation and normalizes the status bar to 10:00, full battery, full Wi-Fi, and no
notifications. The connected test verifies density, font scale, light mode,
timezone, and each native screenshot's dimensions from inside the app process.

The script snapshots and restores display overrides, density overrides, font
scale, UI night mode, timezone, automatic timezone, rotation, animation scales,
app locale, and demo-status-bar state through its exit trap. The fixture
transaction intentionally replaces the debug app's tasks and categories with
fictional data and resets task sorting to Default; that content state is not
restored. The exported debug-only fixture provider accepts only the app UID and
Android shell UID, remains absent from release variants, and does not schedule
real reminders.

## Regenerate And Validate

Regenerate the Android launcher resources and all store media from committed
sources plus the transient captures:

```bash
.venv-store-media/bin/python -m tools.store_media.media render-launcher --root .
.venv-store-media/bin/python -m tools.store_media.media render-common --root .
.venv-store-media/bin/python -m tools.store_media.media render --root .
.venv-store-media/bin/python -m tools.store_media.media contact-sheet --root .
.venv-store-media/bin/python -m tools.store_media.media validate --root .
```

`source/media_manifest.json` is the authority for locale copy, alt text,
capture names, and ordering. Review both locale contact sheets after every render.

## Asset Inventory

| Asset class | Path | Dimensions | PNG mode | Play Console use |
|---|---|---:|---|---|
| Play app icon | `common/app-icon-512.png` | 512 x 512 | RGBA | App icon |
| Feature graphic | `common/feature-graphic-1024x500.png` | 1024 x 500 | RGB | Feature graphic |
| Wordmark | `common/wordmark.png` | 440 x 96 | RGBA | Source/supporting artwork; do not upload separately |
| Italian phone screenshots | `it-IT/phone-screenshots/*.png` | 1080 x 1920 each | RGB | Phone screenshots, Italian listing |
| English phone screenshots | `en-US/phone-screenshots/*.png` | 1080 x 1920 each | RGB | Phone screenshots, English (United States) listing |
| Locale contact sheets | `<locale>/contact-sheet.png` | 906 x 1032 | RGB | Review only; do not upload |
| Raw emulator captures | `source/captures/<locale>/*.png` | 1080 x 2400 each | RGB | Transient renderer inputs; do not upload or commit |
| Legacy launcher icons | `app/src/main/res/mipmap-*/ic_launcher*.webp` | 48-192 px by density | Lossless WebP; standard icons RGB, round icons RGBA | Packaged runtime resources |
| Adaptive/themed launcher icon | `app/src/main/res/mipmap-anydpi-v26/`, `mipmap-anydpi-v33/` | 108 dp canvas; 66 dp safe zone | Android XML/vector layers | Packaged runtime resources |

The Play icon is below the 1 MiB limit enforced by the validator. Phone
screenshots and the feature graphic are opaque 24-bit PNG files.

## Upload Order

Upload the language-neutral app icon and feature graphic first. Then upload each
locale's phone screenshots in this exact numeric order:

| Order | File | Italian headline | English headline |
|---:|---|---|---|
| 1 | `01-focus.png` | Fai adesso ciò che conta | Do what matters now |
| 2 | `02-quick-capture.png` | Cattura un'attività in un istante | Capture a task in an instant |
| 3 | `03-natural-language.png` | Scrivi come pensi | Write the way you think |
| 4 | `04-recurrence.png` | Ripeti solo quando serve | Repeat exactly when needed |
| 5 | `05-organize.png` | Ogni impegno al suo posto | A place for every commitment |
| 6 | `06-portability.png` | I tuoi dati, sempre con te | Your plans stay with you |

The locale-specific accessibility descriptions are mapped by filename in
`it-IT/alt-text.txt` and `en-US/alt-text.txt`. Use those descriptions when the
target publishing surface offers image alt text; keep the same filename/order
mapping if assets are copied into another listing system.

## Final Paths

- App icon: `store-assets/google-play/common/app-icon-512.png`
- Feature graphic: `store-assets/google-play/common/feature-graphic-1024x500.png`
- Italian screenshots: `store-assets/google-play/it-IT/phone-screenshots/`
- English screenshots: `store-assets/google-play/en-US/phone-screenshots/`
- Italian alt text: `store-assets/google-play/it-IT/alt-text.txt`
- English alt text: `store-assets/google-play/en-US/alt-text.txt`

Do not upload the contact sheets, wordmark, source files, or raw captures. Google
Play Console upload remains a manual release action and is outside the renderer.
