import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from tools.store_media.media import (
    AssetSpec,
    MediaManifest,
    ScreenshotCopy,
    load_manifest,
    main,
    validate_asset,
    validate_manifest,
)


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
