import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

from PIL import Image

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
                    screenshot(order, "en-US", alt_text="" if order == 5 else f"Alt text {order}")
                    for order in range(1, 6)
                ),
            }
        )

        errors = validate_manifest(manifest_missing_en_screen_6)

        self.assertIn("en-US is missing screenshot 06", errors)
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


class CommandTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        manifest_path = self.root / "store-assets/google-play/source/media_manifest.json"
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

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def run_command(self, *arguments: str) -> tuple[int, str]:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = main([*arguments, "--root", str(self.root)])
        return code, output.getvalue()

    def test_validate_reports_missing_derived_outputs(self) -> None:
        code, output = self.run_command("validate")

        self.assertEqual(1, code)
        self.assertIn("it-IT is missing final screenshot 01-screen-1.png", output)
        self.assertIn("en-US is missing final screenshot 06-screen-6.png", output)

    def test_render_reports_missing_captures_without_creating_outputs(self) -> None:
        code, output = self.run_command("render")

        self.assertEqual(1, code)
        self.assertIn("it-IT is missing capture it-IT/01-screen-1.png", output)
        self.assertFalse((self.root / "store-assets/google-play/it-IT/phone-screenshots").exists())

    def test_render_rejects_missing_outputs_when_all_captures_exist(self) -> None:
        manifest = load_manifest(
            self.root / "store-assets/google-play/source/media_manifest.json"
        )
        captures_root = self.root / "store-assets/google-play/source/captures"
        for screenshots in manifest.locales.values():
            for copy in screenshots:
                capture = captures_root / copy.capture
                capture.parent.mkdir(parents=True, exist_ok=True)
                capture.touch()

        code, output = self.run_command("render")

        self.assertEqual(1, code)
        self.assertIn("it-IT is missing final screenshot 01-screen-1.png", output)
        self.assertIn("en-US is missing final screenshot 06-screen-6.png", output)
        self.assertFalse((self.root / "store-assets/google-play/it-IT/phone-screenshots").exists())

    def test_contact_sheet_reports_missing_derived_outputs(self) -> None:
        code, output = self.run_command("contact-sheet")

        self.assertEqual(1, code)
        self.assertIn("it-IT is missing final screenshot 01-screen-1.png", output)
        self.assertIn("en-US is missing final screenshot 06-screen-6.png", output)


if __name__ == "__main__":
    unittest.main()
