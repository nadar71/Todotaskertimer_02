# Google Play Release Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the default launcher identity with the approved forward-check brand and produce reproducible, upload-ready Italian and English Google Play phone media.

**Architecture:** Keep runtime branding in Android resources and all marketing tooling under `tools/store_media` plus `store-assets/google-play`. Extend the existing debug fixture provider for deterministic fictional data, drive six real UI states through a connected capture test, and use a pinned local Pillow renderer to produce and validate opaque Play assets without changing production behavior.

**Tech Stack:** Android vector/adaptive icons, Kotlin, Jetpack Compose UI tests, UI Automator, Android per-app locales, Python 3.11+, Pillow 12.3.0, standard-library `unittest`, ADB, PNG/WebP.

**Spec:** `docs/superpowers/specs/2026-08-28-google-play-release-media-design.md`

## Global Constraints

- Phone media only: six 1080 x 1920 portrait screenshots in `it-IT` and the equivalent six in `en-US`.
- Google Play icon: opaque visual composition exported as a 512 x 512 32-bit PNG under 1024 KB.
- Feature graphic: 1024 x 500 opaque 24-bit PNG with important content centered.
- Screenshot files: opaque 24-bit PNG, real app UI prominent, no carrier, notification, personal, or debug information.
- Brand palette: deep evergreen, mint, white, cool light gray, and limited coral only for reminder context.
- Launcher artwork: 108 x 108 dp adaptive layers with essential geometry inside the 66 x 66 dp safe zone and a monochrome themed layer.
- Italian is primary; English must have matching inventory, natural copy, and localized alt text.
- Do not claim cloud sync, paid features, or functionality absent from the current build.
- Do not change production domain, data, navigation, or feature behavior.
- Use committed source assets and deterministic commands; do not hand-edit generated outputs.

---

### Task 1: Deterministic Store-media Toolkit

**Files:**
- Create: `tools/store_media/requirements.txt`
- Create: `tools/store_media/media.py`
- Create: `tools/store_media/test_media.py`
- Create: `store-assets/google-play/source/media_manifest.json`
- Create: `store-assets/google-play/.gitignore`

**Interfaces:**
- Consumes: PNG source captures and JSON media manifest.
- Produces: `load_manifest(path: Path) -> MediaManifest`, `validate_asset(path: Path, spec: AssetSpec) -> list[str]`, `render_all(root: Path) -> None`, and CLI commands `render`, `validate`, and `contact-sheet` used by Tasks 2, 3, 5, and 6.

- [ ] **Step 1: Write failing manifest and image-validation tests**

Use `unittest` temporary directories and Pillow-generated fixtures. Cover exact dimensions, `RGB` output, disallowed alpha, maximum byte size, missing locale peers, numeric screenshot order, and required alt text.

```python
class AssetValidationTest(unittest.TestCase):
    def test_phone_screenshot_rejects_alpha_and_wrong_size(self):
        path = self.root / "01-focus.png"
        Image.new("RGBA", (1000, 1920), (0, 0, 0, 0)).save(path)
        errors = validate_asset(path, AssetSpec.phone_screenshot())
        self.assertIn("expected 1080x1920, found 1000x1920", errors)
        self.assertIn("expected RGB image without alpha, found RGBA", errors)

    def test_locales_require_matching_order_and_alt_text(self):
        errors = validate_manifest(self.manifest_missing_en_screen_6)
        self.assertIn("en-US is missing screenshot 06", errors)
        self.assertIn("en-US screenshot 06 is missing alt text", errors)
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
python3 -m venv .venv-store-media
.venv-store-media/bin/pip install -r tools/store_media/requirements.txt
.venv-store-media/bin/python -m unittest tools.store_media.test_media -v
```

Expected: FAIL because `media.py`, `AssetSpec`, and manifest validation do not exist.

- [ ] **Step 3: Implement the minimal typed manifest and CLI**

Pin `Pillow==12.3.0`. Use frozen dataclasses and JSON from the standard library:

```python
@dataclass(frozen=True)
class AssetSpec:
    width: int
    height: int
    mode: str
    max_bytes: int | None = None

    @classmethod
    def phone_screenshot(cls) -> "AssetSpec":
        return cls(1080, 1920, "RGB")

@dataclass(frozen=True)
class ScreenshotCopy:
    order: int
    slug: str
    headline: str
    alt_text: str
    capture: str

@dataclass(frozen=True)
class MediaManifest:
    locales: dict[str, tuple[ScreenshotCopy, ...]]
```

The CLI returns exit code `1` and prints every validation error; successful commands return `0`. Ignore `.venv-store-media/` and transient `source/captures/`, but do not ignore upload-ready output.

- [ ] **Step 4: Run toolkit tests and verify GREEN**

Run: `.venv-store-media/bin/python -m unittest tools.store_media.test_media -v`

Expected: PASS for manifest, dimensions, mode, file-size, inventory, and alt-text tests.

- [ ] **Step 5: Commit**

```bash
git add tools/store_media store-assets/google-play/source/media_manifest.json store-assets/google-play/.gitignore
git commit -m "build: add reproducible store media toolkit"
```

---

### Task 2: Forward-check Launcher And Splash Identity

**Files:**
- Create: `store-assets/google-play/source/brand.json`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/drawable/ic_logo_light.xml`
- Modify: `app/src/main/res/drawable/ic_logo_dark.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`
- Modify: `app/src/main/res/values/splash.xml`
- Modify: `app/src/main/res/values-night/splash.xml`
- Replace generated: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp`
- Replace generated: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.webp`
- Modify: `tools/store_media/media.py`
- Modify tests: `tools/store_media/test_media.py`

**Interfaces:**
- Consumes: `brand.json` with colors `evergreen`, `mint`, `white`, `cool_gray`, `coral` and normalized forward-check geometry.
- Produces: `render_launcher_assets(project_root: Path, brand: Brand) -> None`, Android adaptive/legacy resources, monochrome icon, and matching splash artwork.

- [ ] **Step 1: Write failing brand and launcher tests**

Add tests that require exact palette tokens, check normalized geometry stays inside the adaptive safe zone, verify every legacy density output, and parse v33 XML for `<monochrome android:drawable="@drawable/ic_launcher_monochrome" />`.

```python
def test_brand_geometry_stays_inside_safe_zone(self):
    brand = load_brand(self.brand_path)
    self.assertGreaterEqual(min(point.x for point in brand.points), 21)
    self.assertLessEqual(max(point.x for point in brand.points), 87)

def test_android_13_icons_reference_monochrome_layer(self):
    tree = ElementTree.parse(self.root / "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml")
    node = tree.getroot().find("monochrome")
    self.assertEqual("@drawable/ic_launcher_monochrome", android_attr(node, "drawable"))
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `.venv-store-media/bin/python -m unittest tools.store_media.test_media.BrandTest -v`

Expected: FAIL because approved brand geometry and v33 monochrome resources are absent.

- [ ] **Step 3: Implement vector and raster launcher assets**

Use one flat evergreen background. Build the foreground from a white check and mint forward stroke inside `(21, 21)..(87, 87)` on the 108-unit canvas. The monochrome vector combines both into a single solid path. Render legacy sizes at 48, 72, 96, 144, and 192 px; render round variants from the same full-bleed background and safe foreground. Update splash vectors and splash background to the same identity without changing splash timing or navigation.

The v33 declaration is:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

- [ ] **Step 4: Generate resources and verify Android packaging**

Run:

```bash
.venv-store-media/bin/python -m tools.store_media.media render-launcher --root .
./gradlew :app:processDebugResources :app:assembleDebug
```

Expected: generated WebP assets at every density and `BUILD SUCCESSFUL` with no resource-linking errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res store-assets/google-play/source/brand.json tools/store_media
git commit -m "feat: add NowDoThis launcher identity"
```

---

### Task 3: Play Icon, Feature Graphic, And Wordmark

**Files:**
- Create generated: `store-assets/google-play/common/app-icon-512.png`
- Create generated: `store-assets/google-play/common/feature-graphic-1024x500.png`
- Create generated: `store-assets/google-play/common/wordmark.png`
- Modify: `tools/store_media/media.py`
- Modify tests: `tools/store_media/test_media.py`

**Interfaces:**
- Consumes: Task 2 `Brand` and normalized forward-check geometry.
- Produces: `render_common_assets(root: Path, brand: Brand) -> None` and three validated common media files used by the Play listing and screenshot renderer.

- [ ] **Step 1: Write failing common-asset tests**

```python
def test_common_assets_match_play_contract(self):
    render_common_assets(self.root, self.brand)
    self.assertEqual([], validate_asset(
        self.root / "store-assets/google-play/common/app-icon-512.png",
        AssetSpec(512, 512, "RGBA", 1_024 * 1_024),
    ))
    self.assertEqual([], validate_asset(
        self.root / "store-assets/google-play/common/feature-graphic-1024x500.png",
        AssetSpec(1024, 500, "RGB"),
    ))
```

Also assert that the feature graphic's non-background bounding box intersects the central 60% of the canvas and that no text outside the NowDoThis wordmark is present.

- [ ] **Step 2: Run tests and verify RED**

Run: `.venv-store-media/bin/python -m unittest tools.store_media.test_media.CommonAssetTest -v`

Expected: FAIL because common renderers and output files do not exist.

- [ ] **Step 3: Implement common renderers**

Render the store icon from the adaptive composition at 4x antialiasing, then downsample with Lanczos. Render the feature graphic as an evergreen-and-cool-gray flat composition with an enlarged forward gesture, centered wordmark, and a small authentic-looking task-row motif that contains no invented controls or claims. Keep the wordmark asset transparent for reuse, but flatten the feature graphic to RGB.

- [ ] **Step 4: Generate and validate common assets**

Run:

```bash
.venv-store-media/bin/python -m tools.store_media.media render-common --root .
.venv-store-media/bin/python -m tools.store_media.media validate --root . --scope common
```

Expected: PASS; icon is 512 x 512 and under 1024 KB, feature graphic is opaque 1024 x 500.

- [ ] **Step 5: Commit**

```bash
git add store-assets/google-play/common tools/store_media
git commit -m "feat: add Google Play brand graphics"
```

---

### Task 4: Deterministic Localized Capture Fixture

**Files:**
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/debug/java/com/indiewalkabout/nowdothis/storemedia/StoreMediaFixture.kt`
- Modify: `app/src/debug/java/com/indiewalkabout/nowdothis/benchmark/BenchmarkFixtureProvider.kt`
- Create test: `app/src/androidTest/java/com/indiewalkabout/nowdothis/storemedia/StoreMediaFixtureTest.kt`

**Interfaces:**
- Consumes: existing `AppDatabase`, task/category entities, typed recurrence rules and mappers, plus provider call argument `it-IT` or `en-US`.
- Produces: provider method `prepare_store_media`, constants `STORE_MEDIA_FIXTURE_METHOD` and `STORE_MEDIA_LOCALE_ARG`, and a deterministic localized database graph for capture automation.

- [ ] **Step 1: Write failing connected fixture tests**

Test both locales. Require stable category order, six representative tasks, one reminder, one selected-weekday recurrence, subtasks, one completed item for history, and locale-appropriate fictional titles.

```kotlin
@Test fun italianFixture_containsLocalizedStoreStory() = runBlocking {
    providerCall(PREPARE_STORE_MEDIA_FIXTURE_METHOD, "it-IT")
    val tasks = database.taskDao().getAllTaskEntities()
    assertEquals(6, tasks.size)
    assertTrue(tasks.any { it.title == "Preparare la presentazione" })
    assertTrue(tasks.any { it.reminderAt != null })
    assertTrue(tasks.any { it.recurrence != "NONE" })
}
```

- [ ] **Step 2: Run connected test and verify RED**

Run:

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.storemedia.StoreMediaFixtureTest
```

Expected: FAIL because the provider method and store fixture do not exist.

- [ ] **Step 3: Implement the debug-only fixture**

Declare the existing provider only in `src/debug/AndroidManifest.xml`; keep it absent from release. Add `prepare_store_media` to the provider allowlist and delegate to `StoreMediaFixture.prepare(localeTag)`. Use fixed IDs and timestamps near the capture date, clear data transactionally, insert categories before tasks, and ensure reminder scheduling is not invoked by fixture preparation.

Use these locale title pairs so captures remain aligned:

```kotlin
private val titles = mapOf(
    "it-IT" to listOf("Preparare la presentazione", "Chiamare il dentista", "Rivedere il piano di rilascio", "Comprare i biglietti del treno", "Allenamento del mattino", "Inviare la nota spese"),
    "en-US" to listOf("Prepare the presentation", "Call the dentist", "Review the release plan", "Buy train tickets", "Morning workout", "Submit the expense report"),
)
```

- [ ] **Step 4: Run connected fixture tests and release-manifest check**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.indiewalkabout.nowdothis.storemedia.StoreMediaFixtureTest
./gradlew :app:processReleaseMainManifest
rg -n "BenchmarkFixtureProvider|prepare_store_media" app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml && exit 1 || true
```

Expected: connected tests PASS and the release manifest contains no fixture provider.

- [ ] **Step 5: Commit**

```bash
git add app/src/debug app/src/androidTest/java/com/indiewalkabout/nowdothis/storemedia
git commit -m "test: add deterministic store media fixture"
```

---

### Task 5: Six-screen Localized Capture Journey

**Files:**
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/storemedia/StoreMediaCaptureTest.kt`
- Create: `tools/store_media/capture.sh`
- Modify: `store-assets/google-play/source/media_manifest.json`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskListScreen.kt` only if a missing stable test tag blocks capture.
- Modify: feature screens only to add missing semantics test tags; do not alter visual or behavioral code.

**Interfaces:**
- Consumes: Task 4 provider method, existing Compose semantics, Navigation 3 destinations, Android app-locale APIs, and connected device serial.
- Produces: twelve source captures named `{locale}/{01-focus,02-quick-capture,03-natural-language,04-recurrence,05-organize,06-portability}.png` in the transient capture directory.

- [ ] **Step 1: Write the capture-state assertions before screenshot output**

Create one parameterized connected test per locale. Before each capture, assert the visible localized title or control that proves the correct state; use existing test tags whenever available.

```kotlin
private fun capture(name: String, requiredTag: String) {
    composeRule.onNodeWithTag(requiredTag).assertIsDisplayed()
    val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    writePng(bitmap, File(outputDir, "$name.png"))
}

@Test fun captureItalianPhoneStory() {
    prepareLocale("it-IT")
    openTaskList()
    capture("it-IT/01-focus", "task-list")
    openQuickCapture()
    capture("it-IT/02-quick-capture", "quick-capture-list")
    openNaturalLanguageEditor()
    capture("it-IT/03-natural-language", "natural-language-input")
}
```

Continue the journey through the advanced recurrence editor, categories/calendar context, and portability screen. Normalize status-bar time, battery, Wi-Fi, and notification state in `capture.sh` before running tests, then restore changed emulator settings in a shell trap.

- [ ] **Step 2: Run one locale and verify the journey fails at the first missing contract**

Run: `bash tools/store_media/capture.sh --locale it-IT --serial "$ANDROID_SERIAL"`

Expected: FAIL before screenshots are accepted because output pull/manifest completeness or required semantics are not implemented.

- [ ] **Step 3: Implement capture output and minimal semantics hooks**

Write captures under app external files, pull them with ADB after instrumentation completes, and fail when any of the six expected names is missing. Add only stable `Modifier.testTag(...)` hooks needed to reach or assert real states. Do not seed data from the test process or bypass production navigation.

The script invocation contract is:

```bash
bash tools/store_media/capture.sh --locale it-IT --serial emulator-5554
bash tools/store_media/capture.sh --locale en-US --serial emulator-5554
```

- [ ] **Step 4: Capture both locales and verify inventory**

Run:

```bash
bash tools/store_media/capture.sh --locale it-IT --serial "$ANDROID_SERIAL"
bash tools/store_media/capture.sh --locale en-US --serial "$ANDROID_SERIAL"
.venv-store-media/bin/python -m tools.store_media.media validate-captures --root .
```

Expected: twelve nonblank source captures, six per locale, with matching order and no missing required UI state.

- [ ] **Step 5: Commit capture automation and source-state metadata**

Do not commit transient raw captures. Commit the journey, script, tags, and manifest only.

```bash
git add app/src/androidTest app/src/main tools/store_media/capture.sh store-assets/google-play/source/media_manifest.json
git commit -m "test: automate localized store screenshots"
```

---

### Task 6: Editorial Screenshot Renderer And Localized Output

**Files:**
- Modify: `tools/store_media/media.py`
- Modify tests: `tools/store_media/test_media.py`
- Modify: `store-assets/google-play/source/media_manifest.json`
- Create generated: `store-assets/google-play/it-IT/phone-screenshots/01-focus.png` through `06-portability.png`
- Create generated: `store-assets/google-play/en-US/phone-screenshots/01-focus.png` through `06-portability.png`
- Create: `store-assets/google-play/it-IT/alt-text.txt`
- Create: `store-assets/google-play/en-US/alt-text.txt`
- Create generated: `store-assets/google-play/it-IT/contact-sheet.png`
- Create generated: `store-assets/google-play/en-US/contact-sheet.png`

**Interfaces:**
- Consumes: twelve Task 5 captures, Task 3 wordmark, and localized manifest copy.
- Produces: `render_phone_screenshot(capture: Image, copy: ScreenshotCopy, brand: Brand) -> Image`, twelve upload-ready screenshots, two alt-text files, and two contact sheets.

- [ ] **Step 1: Write failing composition tests**

```python
def test_phone_composition_is_opaque_play_size(self):
    output = render_phone_screenshot(self.capture, self.copy, self.brand)
    self.assertEqual((1080, 1920), output.size)
    self.assertEqual("RGB", output.mode)

def test_headline_stays_inside_safe_text_box(self):
    layout = calculate_phone_layout("Cattura un'attività in un istante", self.font)
    self.assertLessEqual(layout.headline_box.right, 1008)
    self.assertGreaterEqual(layout.headline_box.left, 72)
    self.assertFalse(layout.overlaps_capture)
```

Test the longest Italian and English headlines, missing glyph detection, capture aspect-fit without distortion, deterministic file hashes for identical input, and contact-sheet order.

- [ ] **Step 2: Run renderer tests and verify RED**

Run: `.venv-store-media/bin/python -m unittest tools.store_media.test_media.PhoneRendererTest -v`

Expected: FAIL because phone composition and layout functions do not exist.

- [ ] **Step 3: Implement the restrained editorial template**

Use a 1080 x 1920 flat cool-gray canvas, a 72 px side safe margin, one headline at the top, a small wordmark, and the real app capture below without a drawn phone shell. Keep at least 70% of the composition devoted to authentic UI. Use evergreen for headline text and mint for a single small directional accent; coral appears only on the reminder screenshot. Fit text by wrapping at word boundaries, never by viewport-based font scaling.

Populate the manifest with the six approved headline pairs and natural alt text. Example:

```json
{
  "order": 4,
  "slug": "recurrence",
  "headline": "Ripeti solo quando serve",
  "alt_text": "Editor di NowDoThis con promemoria e ricorrenza avanzata configurati per un'attività.",
  "capture": "it-IT/04-recurrence.png"
}
```

- [ ] **Step 4: Render and validate both locale sets**

Run:

```bash
.venv-store-media/bin/python -m tools.store_media.media render --root .
.venv-store-media/bin/python -m tools.store_media.media validate --root . --scope phone
.venv-store-media/bin/python -m tools.store_media.media contact-sheet --root .
```

Expected: twelve opaque 1080 x 1920 screenshots, complete alt text, matching locale inventories, and two ordered contact sheets.

- [ ] **Step 5: Commit localized media**

```bash
git add tools/store_media store-assets/google-play
git commit -m "feat: add localized Google Play screenshots"
```

---

### Task 7: End-to-end Media Verification And Handoff

**Files:**
- Create: `store-assets/google-play/README.md`
- Create: `docs/release/google-play-media-evidence-2026-08-28.md`
- Modify: `docs/release/checklist.md`

**Interfaces:**
- Consumes: all runtime and store assets from Tasks 1-6.
- Produces: exact regeneration/upload instructions, final validation evidence, Play Console upload order, and documented manual icon checks.

- [ ] **Step 1: Write the upload and regeneration documentation**

Document environment creation, both locale capture commands, render/validate commands, final paths, screenshot ordering, and alt-text mapping. State that raw captures are transient and final generated outputs are committed. Include a table with required dimensions and modes for every asset class.

- [ ] **Step 2: Run complete automated verification**

Run:

```bash
.venv-store-media/bin/python -m unittest tools.store_media.test_media -v
.venv-store-media/bin/python -m tools.store_media.media validate --root .
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
```

Expected: all media tests PASS, validation reports zero errors, JVM tests PASS, lint has no errors, debug APK and release AAB build successfully.

- [ ] **Step 3: Verify installed launcher and splash behavior**

Install the debug APK on an API 33+ emulator. Capture launcher previews under circle, squircle, and rounded-square masks where the launcher supports them; enable themed icons and verify the monochrome mark; launch the app and verify the splash uses the same forward-check identity without clipping.

Record device/API, launcher, mask results, themed-icon result, and splash result in `docs/release/google-play-media-evidence-2026-08-28.md`. Any unsupported launcher mask is recorded as not available rather than claimed as passed.

- [ ] **Step 4: Review final locale contact sheets**

Inspect both contact sheets at full size and reduced carousel size. Require readable headlines, no clipped text, no accidental personal/debug content, no misleading cloud or paid claims, clean status bars, consistent order, and actual UI occupying at least 70% of each composition. Record explicit PASS/FAIL for every screenshot in the evidence document and regenerate any failed item before continuing.

- [ ] **Step 5: Run final repository checks and commit**

Run:

```bash
git diff --check
git status --short
git log --oneline -7
```

Then commit only documentation and any corrections found during review:

```bash
git add store-assets/google-play/README.md docs/release
git commit -m "docs: record Google Play media release evidence"
```

Expected: clean worktree and a complete upload-ready asset inventory. Do not upload to Play Console and do not push Git branches without explicit user instruction.
