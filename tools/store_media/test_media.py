import contextlib
import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock
from xml.etree import ElementTree

from PIL import Image, ImageChops, ImageDraw

from tools.store_media import media
from tools.store_media.media import (
    AssetSpec,
    MediaManifest,
    ScreenshotCopy,
    load_manifest,
    main,
    validate_asset,
    validate_manifest,
)


ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"


def android_attr(node: ElementTree.Element | None, name: str) -> str | None:
    return None if node is None else node.get(f"{{{ANDROID_NAMESPACE}}}{name}")


def screenshot(order: int, locale: str = "it-IT", **overrides: object) -> ScreenshotCopy:
    values: dict[str, object] = {
        "order": order,
        "slug": f"screen-{order}",
        "headline": f"Headline {order}",
        "alt_text": f"Alt text {order}",
        "capture": f"{locale}/{order:02d}-screen-{order}.png",
    }
    values.update(overrides)
    return ScreenshotCopy(**values)  # type: ignore[arg-type]


class AssetValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_phone_screenshot_rejects_alpha_and_wrong_size(self) -> None:
        path = self.root / "01-focus.png"
        Image.new("RGBA", (1000, 1920), (0, 0, 0, 0)).save(path)

        errors = validate_asset(path, AssetSpec.phone_screenshot())

        self.assertIn("expected 1080x1920, found 1000x1920", errors)
        self.assertIn("expected RGB image without alpha, found RGBA", errors)

    def test_asset_rejects_file_larger_than_maximum(self) -> None:
        path = self.root / "large.png"
        path.write_bytes(b"x" * 11)

        errors = validate_asset(path, AssetSpec(1, 1, "RGB", max_bytes=10))

        self.assertIn("maximum 10 bytes, found 11", errors)

    def test_asset_rejects_a_jpeg_renamed_as_png(self) -> None:
        path = self.root / "renamed.png"
        Image.new("RGB", (1, 1), (0, 0, 0)).save(path, format="JPEG")

        errors = validate_asset(path, AssetSpec(1, 1, "RGB"))

        self.assertIn("expected PNG image, found JPEG", errors)

    def test_asset_rejects_a_non_eight_bit_png(self) -> None:
        path = self.root / "sixteen-bit.png"
        Image.new("I;16", (1, 1)).save(path, format="PNG")

        errors = validate_asset(path, AssetSpec(1, 1, "RGB"))

        self.assertIn("expected 8-bit RGB PNG, found 16-bit I;16", errors)


class ManifestValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.complete = MediaManifest(
            locales={
                "it-IT": tuple(screenshot(order) for order in range(1, 7)),
                "en-US": tuple(screenshot(order, "en-US") for order in range(1, 7)),
            }
        )

    def test_locales_require_matching_order_and_alt_text(self) -> None:
        manifest_missing_en_screen_6 = MediaManifest(
            locales={
                "it-IT": self.complete.locales["it-IT"],
                "en-US": tuple(
                    screenshot(
                        order,
                        "en-US",
                        headline="" if order == 4 else f"Headline {order}",
                        alt_text="" if order == 5 else f"Alt text {order}",
                    )
                    for order in range(1, 6)
                ),
            }
        )

        errors = validate_manifest(manifest_missing_en_screen_6)

        self.assertIn("en-US is missing screenshot 06", errors)
        self.assertIn("en-US screenshot 04 is missing headline", errors)
        self.assertIn("en-US screenshot 05 is missing alt text", errors)

    def test_orders_must_be_numeric_and_contiguous(self) -> None:
        manifest = MediaManifest(
            locales={
                "it-IT": tuple(screenshot(order) for order in (1, 2, 3, 4, 5, 7)),
                "en-US": self.complete.locales["en-US"],
            }
        )

        errors = validate_manifest(manifest)

        self.assertIn("it-IT is missing screenshot 06", errors)
        self.assertIn("it-IT has unexpected screenshot 07", errors)

    def test_locales_require_unique_matching_slugs_and_capture_paths(self) -> None:
        en_screenshots = list(self.complete.locales["en-US"])
        en_screenshots[1] = screenshot(2, "en-US", slug="different")
        en_screenshots[2] = screenshot(2, "en-US")
        manifest = MediaManifest(
            locales={
                "it-IT": self.complete.locales["it-IT"],
                "en-US": tuple(en_screenshots),
            }
        )

        errors = validate_manifest(manifest)

        self.assertIn("en-US has duplicate screenshot 02", errors)
        self.assertIn("en-US screenshot 02 slug does not match it-IT", errors)
        self.assertIn(
            "en-US screenshot 02 capture must be en-US/02-different.png", errors
        )

    def test_loader_reads_the_versioned_json_schema(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "media_manifest.json"
            path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "locales": {
                            "it-IT": [
                                {
                                    "order": 1,
                                    "slug": "focus",
                                    "headline": "Fai adesso cio che conta",
                                    "alt_text": "Elenco attivita in italiano.",
                                    "capture": "it-IT/01-focus.png",
                                }
                            ],
                            "en-US": [
                                {
                                    "order": 1,
                                    "slug": "focus",
                                    "headline": "Do what matters now",
                                    "alt_text": "English task list.",
                                    "capture": "en-US/01-focus.png",
                                }
                            ],
                        },
                    }
                ),
                encoding="utf-8",
            )

            manifest = load_manifest(path)

        self.assertEqual("focus", manifest.locales["it-IT"][0].slug)


class BrandTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(__file__).resolve().parents[2]
        self.brand_path = self.root / "store-assets/google-play/source/brand.json"

    def test_brand_uses_the_approved_palette_tokens(self) -> None:
        brand = media.load_brand(self.brand_path)

        self.assertEqual(
            {"evergreen", "mint", "white", "cool_gray", "coral"},
            set(brand.colors),
        )

    def test_brand_geometry_stays_inside_safe_zone(self) -> None:
        brand = media.load_brand(self.brand_path)
        inset = brand.stroke_width / 2

        self.assertGreaterEqual(min(point.x for point in brand.points) - inset, 21)
        self.assertLessEqual(max(point.x for point in brand.points) + inset, 87)
        self.assertGreaterEqual(min(point.y for point in brand.points) - inset, 21)
        self.assertLessEqual(max(point.y for point in brand.points) + inset, 87)

    def test_launcher_renderer_writes_every_legacy_density_deterministically(self) -> None:
        brand = media.load_brand(self.brand_path)
        densities = {
            "mdpi": 48,
            "hdpi": 72,
            "xhdpi": 96,
            "xxhdpi": 144,
            "xxxhdpi": 192,
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            media.render_launcher_assets(root, brand)
            first_render = {
                path.relative_to(root): path.read_bytes()
                for path in sorted(root.rglob("*.webp"))
            }

            media.render_launcher_assets(root, brand)

            for density, size in densities.items():
                for filename in ("ic_launcher.webp", "ic_launcher_round.webp"):
                    path = root / f"app/src/main/res/mipmap-{density}/{filename}"
                    self.assertEqual(
                        first_render[path.relative_to(root)], path.read_bytes()
                    )
                    with Image.open(path) as image:
                        self.assertEqual((size, size), image.size)
                        self.assertEqual("WEBP", image.format)

    def test_legacy_launchers_use_evergreen_white_and_mint(self) -> None:
        brand = media.load_brand(self.brand_path)
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            media.render_launcher_assets(root, brand)
            standard = root / "app/src/main/res/mipmap-mdpi/ic_launcher.webp"
            round_icon = root / "app/src/main/res/mipmap-mdpi/ic_launcher_round.webp"

            with Image.open(standard).convert("RGBA") as image:
                colors = set(image.get_flattened_data())
                self.assertEqual(
                    (*brand.rgb("evergreen"), 255), image.getpixel((0, 0))
                )
                self.assertIn((*brand.rgb("white"), 255), colors)
                self.assertIn((*brand.rgb("mint"), 255), colors)
            with Image.open(round_icon).convert("RGBA") as image:
                colors = set(image.get_flattened_data())
                self.assertEqual(0, image.getpixel((0, 0))[3])
                self.assertEqual(
                    (*brand.rgb("evergreen"), 255), image.getpixel((24, 8))
                )
                self.assertIn((*brand.rgb("white"), 255), colors)
                self.assertIn((*brand.rgb("mint"), 255), colors)

    def test_android_13_icons_reference_monochrome_layer(self) -> None:
        for filename in ("ic_launcher.xml", "ic_launcher_round.xml"):
            tree = ElementTree.parse(
                self.root / "app/src/main/res/mipmap-anydpi-v33" / filename
            )
            node = tree.getroot().find("monochrome")
            self.assertIsNotNone(node)
            self.assertEqual(
                "@drawable/ic_launcher_monochrome",
                android_attr(node, "drawable"),
            )

    def test_render_launcher_command_generates_assets(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            brand_path = root / "store-assets/google-play/source/brand.json"
            brand_path.parent.mkdir(parents=True)
            brand_path.write_bytes(self.brand_path.read_bytes())

            code = media.main(["render-launcher", "--root", str(root)])

            self.assertEqual(0, code)
            self.assertTrue(
                (root / "app/src/main/res/drawable/ic_launcher_foreground.xml").is_file()
            )


class CommonAssetTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        project_root = Path(__file__).resolve().parents[2]
        self.brand_path = project_root / "store-assets/google-play/source/brand.json"
        self.brand = media.load_brand(self.brand_path)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def render(self) -> None:
        renderer = getattr(media, "render_common_assets", None)
        self.assertIsNotNone(renderer, "render_common_assets does not exist")
        renderer(self.root, self.brand)

    def run_main(self, arguments: list[str]) -> int:
        with contextlib.redirect_stderr(io.StringIO()):
            try:
                return main(arguments)
            except SystemExit as error:
                return int(error.code)

    def test_common_assets_match_play_contract(self) -> None:
        self.render()
        common = self.root / "store-assets/google-play/common"
        icon_path = common / "app-icon-512.png"
        feature_path = common / "feature-graphic-1024x500.png"
        wordmark_path = common / "wordmark.png"

        self.assertEqual(
            [],
            validate_asset(icon_path, AssetSpec(512, 512, "RGBA", 1_024 * 1_024)),
        )
        self.assertEqual(
            [],
            validate_asset(feature_path, AssetSpec(1024, 500, "RGB")),
        )
        with Image.open(wordmark_path) as wordmark:
            self.assertEqual("PNG", wordmark.format)
            self.assertEqual("RGBA", wordmark.mode)
            self.assertIsNotNone(wordmark.getchannel("A").getbbox())

        with Image.open(feature_path) as feature:
            background = Image.new("RGB", feature.size, self.brand.rgb("cool_gray"))
            foreground_bounds = ImageChops.difference(feature, background).getbbox()
            self.assertIsNotNone(foreground_bounds)
            assert foreground_bounds is not None
            central_bounds = (1024 * 0.2, 500 * 0.2, 1024 * 0.8, 500 * 0.8)
            self.assertLess(foreground_bounds[0], central_bounds[2])
            self.assertGreater(foreground_bounds[2], central_bounds[0])
            self.assertLess(foreground_bounds[1], central_bounds[3])
            self.assertGreater(foreground_bounds[3], central_bounds[1])

    def test_common_validation_rejects_fully_opaque_wordmark(self) -> None:
        self.render()
        wordmark_path = (
            self.root / "store-assets/google-play/common/wordmark.png"
        )
        Image.new("RGBA", media.WORDMARK_SIZE, (20, 40, 60, 255)).save(wordmark_path)

        errors = media.common_asset_errors(self.root)

        self.assertIn(
            "common wordmark.png: expected mixed-alpha transparency",
            errors,
        )

    def test_feature_graphic_centers_only_the_rendered_wordmark_text(self) -> None:
        marker_color = (255, 0, 255, 255)
        marker = Image.new(
            "RGBA",
            tuple(dimension * media.RENDER_SCALE for dimension in media.WORDMARK_SIZE),
            marker_color,
        )
        with (
            mock.patch.object(media, "_wordmark_image", return_value=marker) as wordmark,
            mock.patch.object(
                media.ImageDraw.ImageDraw,
                "text",
                side_effect=AssertionError("feature text must come from the wordmark"),
            ) as unexpected_text,
        ):
            media.render_common_assets(self.root, self.brand)

        wordmark.assert_called_once_with(self.brand, media.RENDER_SCALE)
        unexpected_text.assert_not_called()
        feature_path = (
            self.root
            / "store-assets/google-play/common/feature-graphic-1024x500.png"
        )
        with Image.open(feature_path) as feature:
            marker_mask = Image.new("L", feature.size)
            marker_mask.putdata(
                [
                    255 if pixel == marker_color[:3] else 0
                    for pixel in feature.get_flattened_data()
                ]
            )
            marker_bounds = marker_mask.getbbox()
            self.assertIsNotNone(marker_bounds, "rendered wordmark is missing")
            assert marker_bounds is not None
            center_x = (marker_bounds[0] + marker_bounds[2]) / 2
            center_y = (marker_bounds[1] + marker_bounds[3]) / 2
            self.assertAlmostEqual(feature.width / 2, center_x, delta=0.5)
            self.assertAlmostEqual(feature.height / 2, center_y, delta=0.5)

    def test_common_assets_regenerate_deterministically_through_cli(self) -> None:
        brand_path = self.root / "store-assets/google-play/source/brand.json"
        brand_path.parent.mkdir(parents=True)
        brand_path.write_bytes(self.brand_path.read_bytes())

        self.assertEqual(
            0,
            self.run_main(["render-common", "--root", str(self.root)]),
        )
        common = self.root / "store-assets/google-play/common"
        first_render = {
            path.name: path.read_bytes() for path in sorted(common.glob("*.png"))
        }

        self.assertEqual(
            0,
            self.run_main(["render-common", "--root", str(self.root)]),
        )
        self.assertEqual(
            first_render,
            {path.name: path.read_bytes() for path in sorted(common.glob("*.png"))},
        )
        self.assertEqual(
            0,
            self.run_main(
                ["validate", "--root", str(self.root), "--scope", "common"]
            ),
        )


class PhoneRendererTest(unittest.TestCase):
    def setUp(self) -> None:
        project_root = Path(__file__).resolve().parents[2]
        self.brand = media.load_brand(
            project_root / "store-assets/google-play/source/brand.json"
        )
        self.copy = screenshot(
            2,
            slug="quick-capture",
            headline="Cattura un'attività in un istante",
            capture="it-IT/02-quick-capture.png",
        )
        self.capture = Image.new("RGB", (1080, 2400), (250, 247, 251))
        draw = ImageDraw.Draw(self.capture)
        draw.rectangle((0, 0, 1079, 179), fill=(105, 80, 175))
        draw.rectangle((72, 260, 1007, 420), fill=(255, 255, 255))

    def renderer(self):
        renderer = getattr(media, "render_phone_screenshot", None)
        self.assertIsNotNone(renderer, "render_phone_screenshot does not exist")
        return renderer

    def headline_font(self):
        loader = getattr(media, "load_headline_font", None)
        self.assertIsNotNone(loader, "load_headline_font does not exist")
        return loader()

    def layout(self, headline: str):
        calculator = getattr(media, "calculate_phone_layout", None)
        self.assertIsNotNone(calculator, "calculate_phone_layout does not exist")
        return calculator(headline, self.headline_font())

    def test_phone_composition_is_opaque_play_size_with_prominent_ui(self) -> None:
        output = self.renderer()(self.capture, self.copy, self.brand)

        self.assertEqual((1080, 1920), output.size)
        self.assertEqual("RGB", output.mode)
        layout = self.layout(self.copy.headline)
        ui_fraction = (
            layout.capture_box.width
            * layout.capture_box.height
            / (output.width * output.height)
        )
        self.assertGreaterEqual(ui_fraction, 0.70)

    def test_longest_headlines_stay_inside_safe_text_box(self) -> None:
        for headline in (
            "Cattura un'attività in un istante",
            "A place for every commitment",
        ):
            with self.subTest(headline=headline):
                layout = self.layout(headline)
                self.assertLessEqual(layout.headline_box.right, 1008)
                self.assertGreaterEqual(layout.headline_box.left, 72)
                self.assertFalse(layout.overlaps_capture)

    def test_headline_wraps_only_at_word_boundaries(self) -> None:
        phrase = "Capture a task in an instant"
        layout = self.layout(f"{phrase} {phrase}")

        self.assertEqual((phrase, phrase), layout.headline_lines)

    def test_headline_font_covers_localized_copy_and_rejects_missing_glyphs(
        self,
    ) -> None:
        detector = getattr(media, "missing_glyphs", None)
        self.assertIsNotNone(detector, "missing_glyphs does not exist")
        font = self.headline_font()
        unsupported = chr(0x1F9D9)

        self.assertEqual((), detector("attività è ciò l’impegno", font))
        self.assertEqual((unsupported,), detector(f"Plan {unsupported}", font))
        with self.assertRaisesRegex(ValueError, "missing glyph"):
            self.layout(f"Plan {unsupported}")

    def test_capture_cover_crop_preserves_square_geometry(self) -> None:
        capture = Image.new("RGB", (600, 1200), "white")
        ImageDraw.Draw(capture).rectangle((100, 200, 199, 299), fill=(0, 200, 200))

        output = self.renderer()(capture, screenshot(1, slug="focus"), self.brand)
        pixels = output.load()
        cyan = [
            (x, y)
            for y in range(480, 1920)
            for x in range(1080)
            if pixels[x, y][0] < 8
            and pixels[x, y][1] > 192
            and pixels[x, y][2] > 192
        ]
        left = min(x for x, _ in cyan)
        top = min(y for _, y in cyan)
        right = max(x for x, _ in cyan) + 1
        bottom = max(y for _, y in cyan) + 1

        self.assertAlmostEqual(right - left, bottom - top, delta=1)

    def test_recurrence_crop_keeps_complete_semantic_control_pairs(self) -> None:
        background = (10, 20, 30)
        category_edge = (180, 20, 20)
        due_selector = (40, 120, 180)
        end_repeat_heading = (180, 120, 40)
        end_repeat_selector = (40, 180, 100)
        navigation_edge = (120, 40, 180)
        capture = Image.new("RGB", (1080, 2400), background)
        draw = ImageDraw.Draw(capture)
        draw.rectangle((0, 768, 1079, 770), fill=category_edge)
        draw.rectangle((0, 898, 1079, 1002), fill=due_selector)
        draw.rectangle((0, 2153, 1079, 2180), fill=end_repeat_heading)
        draw.rectangle((0, 2211, 1079, 2336), fill=end_repeat_selector)
        draw.rectangle((0, 2364, 1079, 2399), fill=navigation_edge)
        recurrence = screenshot(
            4, slug="recurrence", headline="Repeat exactly when needed"
        )

        output = self.renderer()(capture, recurrence, self.brand)
        visible_rows = [output.getpixel((540, y)) for y in range(480, 1920)]

        self.assertNotIn(category_edge, visible_rows)
        self.assertEqual(105, visible_rows.count(due_selector))
        self.assertEqual(28, visible_rows.count(end_repeat_heading))
        self.assertEqual(126, visible_rows.count(end_repeat_selector))
        self.assertNotIn(navigation_edge, visible_rows)

    def test_coral_accent_is_reserved_for_recurrence(self) -> None:
        focus = self.renderer()(self.capture, screenshot(1, slug="focus"), self.brand)
        recurrence = self.renderer()(
            self.capture,
            screenshot(4, slug="recurrence", headline="Repeat exactly when needed"),
            self.brand,
        )

        self.assertNotIn(
            self.brand.rgb("coral"),
            focus.crop((0, 0, 1080, 480)).get_flattened_data(),
        )
        self.assertIn(
            self.brand.rgb("coral"),
            recurrence.crop((0, 0, 1080, 480)).get_flattened_data(),
        )

    def test_identical_input_has_identical_png_hash(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            first = Path(temp_dir) / "first.png"
            second = Path(temp_dir) / "second.png"
            media._save_png(first, self.renderer()(self.capture, self.copy, self.brand))
            media._save_png(
                second, self.renderer()(self.capture, self.copy, self.brand)
            )

            self.assertEqual(
                hashlib.sha256(first.read_bytes()).hexdigest(),
                hashlib.sha256(second.read_bytes()).hexdigest(),
            )

    def test_contact_sheet_uses_manifest_order(self) -> None:
        renderer = getattr(media, "render_contact_sheet", None)
        self.assertIsNotNone(renderer, "render_contact_sheet does not exist")
        colors = (
            (200, 20, 20),
            (20, 200, 20),
            (20, 20, 200),
            (200, 200, 20),
            (200, 20, 200),
            (20, 200, 200),
        )
        screenshots = [Image.new("RGB", (1080, 1920), color) for color in colors]

        sheet = renderer(screenshots, self.brand)

        self.assertEqual((906, 1032), sheet.size)
        self.assertEqual("RGB", sheet.mode)
        centers = (
            (159, 264),
            (453, 264),
            (747, 264),
            (159, 768),
            (453, 768),
            (747, 768),
        )
        self.assertEqual(colors, tuple(sheet.getpixel(point) for point in centers))


class CommandTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.capture_colors = {
            order: (25 * order, 210 - 20 * order, 30 * order)
            for order in range(1, 7)
        }
        manifest_path = (
            self.root / "store-assets/google-play/source/media_manifest.json"
        )
        manifest_path.parent.mkdir(parents=True)
        manifest_path.write_text(
            json.dumps(
                {
                    "version": 1,
                    "locales": {
                        locale: [
                            {
                                "order": order,
                                "slug": f"screen-{order}",
                                "headline": f"Headline {order}",
                                "alt_text": f"Alt text {order}",
                                "capture": f"{locale}/{order:02d}-screen-{order}.png",
                            }
                            for order in range(1, 7)
                        ]
                        for locale in ("it-IT", "en-US")
                    },
                }
            ),
            encoding="utf-8",
        )
        project_root = Path(__file__).resolve().parents[2]
        (manifest_path.parent / "brand.json").write_bytes(
            (project_root / "store-assets/google-play/source/brand.json").read_bytes()
        )

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def run_command(self, *arguments: str) -> tuple[int, str]:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = main([*arguments, "--root", str(self.root)])
        return code, output.getvalue()

    def write_captures(self) -> None:
        manifest = load_manifest(
            self.root / "store-assets/google-play/source/media_manifest.json"
        )
        captures_root = self.root / "store-assets/google-play/source/captures"
        for screenshots in manifest.locales.values():
            for copy in screenshots:
                capture = captures_root / copy.capture
                capture.parent.mkdir(parents=True, exist_ok=True)
                image = Image.new(
                    "RGB", (1080, 2400), self.capture_colors[copy.order]
                )
                image.putpixel((0, 0), (0, 0, 0))
                image.save(capture)

    def test_validate_reports_missing_derived_outputs(self) -> None:
        code, output = self.run_command("validate")

        self.assertEqual(1, code)
        self.assertIn("it-IT is missing final screenshot 01-screen-1.png", output)
        self.assertIn("en-US is missing final screenshot 06-screen-6.png", output)

    def test_render_reports_missing_captures_without_creating_outputs(self) -> None:
        code, output = self.run_command("render")

        self.assertEqual(1, code)
        self.assertIn("it-IT is missing capture it-IT/01-screen-1.png", output)
        self.assertFalse(
            (
                self.root
                / "store-assets/google-play/it-IT/phone-screenshots"
            ).exists()
        )

    def test_render_writes_localized_outputs_and_alt_text(self) -> None:
        self.write_captures()

        code, output = self.run_command("render")

        self.assertEqual(0, code, output)
        self.assertEqual("", output)
        for locale in ("it-IT", "en-US"):
            locale_root = self.root / "store-assets/google-play" / locale
            screenshots = locale_root / "phone-screenshots"
            self.assertEqual(
                [f"{order:02d}-screen-{order}.png" for order in range(1, 7)],
                [path.name for path in sorted(screenshots.glob("*.png"))],
            )
            for path in screenshots.glob("*.png"):
                self.assertEqual([], validate_asset(path, AssetSpec.phone_screenshot()))
            self.assertEqual(
                "".join(
                    f"{order:02d}-screen-{order}.png: Alt text {order}\n"
                    for order in range(1, 7)
                ),
                (locale_root / "alt-text.txt").read_text(encoding="utf-8"),
            )

        validate_code, validate_output = self.run_command(
            "validate", "--scope", "phone"
        )
        self.assertEqual(0, validate_code, validate_output)
        self.assertEqual("", validate_output)

    def test_render_regenerates_identical_localized_files(self) -> None:
        self.write_captures()
        first_code, first_output = self.run_command("render")
        self.assertEqual(0, first_code, first_output)
        first_hashes = {
            path.relative_to(self.root): hashlib.sha256(
                path.read_bytes()
            ).hexdigest()
            for path in sorted(
                (self.root / "store-assets/google-play").glob(
                    "*/phone-screenshots/*.png"
                )
            )
        }

        second_code, second_output = self.run_command("render")

        self.assertEqual(0, second_code, second_output)
        self.assertEqual(
            first_hashes,
            {
                path.relative_to(self.root): hashlib.sha256(
                    path.read_bytes()
                ).hexdigest()
                for path in sorted(
                    (self.root / "store-assets/google-play").glob(
                        "*/phone-screenshots/*.png"
                    )
                )
            },
        )

    def test_phone_validation_rejects_stale_alt_text(self) -> None:
        self.write_captures()
        render_code, render_output = self.run_command("render")
        self.assertEqual(0, render_code, render_output)
        alt_text = self.root / "store-assets/google-play/it-IT/alt-text.txt"
        alt_text.write_text("stale\n", encoding="utf-8")

        code, output = self.run_command("validate", "--scope", "phone")

        self.assertEqual(1, code)
        self.assertIn("it-IT alt-text.txt does not match the manifest", output)

    def test_phone_validation_rejects_unexpected_final_screenshot(self) -> None:
        self.write_captures()
        render_code, render_output = self.run_command("render")
        self.assertEqual(0, render_code, render_output)
        extra = (
            self.root
            / "store-assets/google-play/it-IT/phone-screenshots/07-stale.png"
        )
        Image.new("RGB", (1080, 1920), "white").save(extra)

        code, output = self.run_command("validate", "--scope", "phone")

        self.assertEqual(1, code)
        self.assertIn("it-IT has unexpected final screenshot 07-stale.png", output)

    def test_validate_captures_reports_missing_and_blank_pngs(self) -> None:
        manifest = load_manifest(
            self.root / "store-assets/google-play/source/media_manifest.json"
        )
        captures_root = self.root / "store-assets/google-play/source/captures"
        for screenshots in manifest.locales.values():
            for copy in screenshots:
                if copy.capture == "en-US/06-screen-6.png":
                    continue
                capture = captures_root / copy.capture
                capture.parent.mkdir(parents=True, exist_ok=True)
                Image.new("RGB", (1080, 2400), "white").save(capture)

        code, output = self.run_command("validate-captures")

        self.assertEqual(1, code)
        self.assertIn("it-IT/01-screen-1.png is blank", output)
        self.assertIn("en-US is missing capture en-US/06-screen-6.png", output)

    def test_validate_captures_accepts_twelve_nonblank_pngs(self) -> None:
        manifest = load_manifest(
            self.root / "store-assets/google-play/source/media_manifest.json"
        )
        captures_root = self.root / "store-assets/google-play/source/captures"
        for screenshots in manifest.locales.values():
            for copy in screenshots:
                capture = captures_root / copy.capture
                capture.parent.mkdir(parents=True, exist_ok=True)
                image = Image.new("RGB", (1080, 2400), "white")
                image.putpixel((0, 0), (0, 0, 0))
                image.save(capture)

        code, output = self.run_command("validate-captures")

        self.assertEqual(0, code)
        self.assertEqual("", output)

    def test_validate_captures_rejects_non_native_dimensions(self) -> None:
        self.write_captures()
        capture = (
            self.root
            / "store-assets/google-play/source/captures/it-IT/05-screen-5.png"
        )
        image = Image.new("RGB", (1080, 2399), "white")
        image.putpixel((0, 0), (0, 0, 0))
        image.save(capture)

        code, output = self.run_command("validate-captures")

        self.assertEqual(1, code)
        self.assertIn(
            "it-IT/05-screen-5.png must be 1080x2400, found 1080x2399",
            output,
        )

    def test_contact_sheet_reports_missing_derived_outputs(self) -> None:
        code, output = self.run_command("contact-sheet")

        self.assertEqual(1, code)
        self.assertIn("it-IT is missing final screenshot 01-screen-1.png", output)
        self.assertIn("en-US is missing final screenshot 06-screen-6.png", output)

    def test_contact_sheet_command_writes_both_locales_in_manifest_order(self) -> None:
        self.write_captures()
        render_code, render_output = self.run_command("render")
        self.assertEqual(0, render_code, render_output)

        code, output = self.run_command("contact-sheet")

        self.assertEqual(0, code, output)
        centers = (
            (159, 264),
            (453, 264),
            (747, 264),
            (159, 768),
            (453, 768),
            (747, 768),
        )
        expected = tuple(self.capture_colors[order] for order in range(1, 7))
        for locale in ("it-IT", "en-US"):
            path = self.root / "store-assets/google-play" / locale / "contact-sheet.png"
            with Image.open(path) as sheet:
                self.assertEqual((906, 1032), sheet.size)
                self.assertEqual("RGB", sheet.mode)
                self.assertEqual(
                    expected,
                    tuple(sheet.getpixel(point) for point in centers),
                )


if __name__ == "__main__":
    unittest.main()
