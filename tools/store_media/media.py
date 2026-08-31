"""Deterministic validation entry point for Google Play media assets."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from PIL import Image, UnidentifiedImageError


MANIFEST_PATH = Path("store-assets/google-play/source/media_manifest.json")
CAPTURES_PATH = Path("store-assets/google-play/source/captures")
EXPECTED_ORDERS = tuple(range(1, 7))
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


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


def load_manifest(path: Path) -> MediaManifest:
    """Load the version-one store-media manifest from JSON."""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"unable to load manifest {path}: {error}") from error

    if not isinstance(data, dict) or data.get("version") != 1:
        raise ValueError("media manifest must be a JSON object with version 1")
    locales = data.get("locales")
    if not isinstance(locales, dict):
        raise ValueError("media manifest locales must be an object")

    parsed_locales: dict[str, tuple[ScreenshotCopy, ...]] = {}
    fields = ("order", "slug", "headline", "alt_text", "capture")
    for locale, screenshots in locales.items():
        if not isinstance(locale, str) or not isinstance(screenshots, list):
            raise ValueError("each locale must contain a screenshot list")
        copies: list[ScreenshotCopy] = []
        for item in screenshots:
            if not isinstance(item, dict) or any(field not in item for field in fields):
                raise ValueError(f"{locale} has an invalid screenshot entry")
            if not isinstance(item["order"], int) or any(
                not isinstance(item[field], str) for field in fields[1:]
            ):
                raise ValueError(f"{locale} has an invalid screenshot entry")
            copies.append(ScreenshotCopy(**{field: item[field] for field in fields}))
        parsed_locales[locale] = tuple(copies)
    return MediaManifest(locales=parsed_locales)


def validate_asset(path: Path, spec: AssetSpec) -> list[str]:
    """Return all applicable format errors for one PNG asset."""
    errors: list[str] = []
    if not path.is_file():
        return [f"missing asset {path}"]
    if spec.max_bytes is not None and path.stat().st_size > spec.max_bytes:
        errors.append(f"maximum {spec.max_bytes} bytes, found {path.stat().st_size}")
    try:
        with Image.open(path) as image:
            if image.format != "PNG":
                errors.append(f"expected PNG image, found {image.format or 'unknown'}")
            elif (bit_depth := _png_bit_depth(path)) != 8:
                errors.append(
                    f"expected 8-bit {spec.mode} PNG, found {bit_depth}-bit {image.mode}"
                )
            if image.size != (spec.width, spec.height):
                errors.append(
                    f"expected {spec.width}x{spec.height}, found {image.width}x{image.height}"
                )
            if image.mode != spec.mode:
                if spec.mode == "RGB":
                    errors.append(f"expected RGB image without alpha, found {image.mode}")
                else:
                    errors.append(f"expected {spec.mode} image, found {image.mode}")
    except (OSError, UnidentifiedImageError) as error:
        errors.append(f"unable to read image: {error}")
    return errors


def _png_bit_depth(path: Path) -> int:
    with path.open("rb") as source:
        header = source.read(26)
    if header[:8] != PNG_SIGNATURE or header[12:16] != b"IHDR":
        raise OSError("invalid PNG header")
    return header[24]


def validate_manifest(manifest: MediaManifest) -> list[str]:
    """Verify required locale inventory, ordered screenshots, and accessible copy."""
    errors: list[str] = []
    expected_locales = ("it-IT", "en-US")
    for locale in expected_locales:
        screenshots = manifest.locales.get(locale, ())
        orders = {screenshot.order for screenshot in screenshots}
        for order in sorted(orders):
            if sum(screenshot.order == order for screenshot in screenshots) > 1:
                errors.append(f"{locale} has duplicate screenshot {order:02d}")
        for order in EXPECTED_ORDERS:
            if order not in orders:
                errors.append(f"{locale} is missing screenshot {order:02d}")
        for order in sorted(orders - set(EXPECTED_ORDERS)):
            errors.append(f"{locale} has unexpected screenshot {order:02d}")
        for screenshot in screenshots:
            if not screenshot.alt_text.strip():
                errors.append(f"{locale} screenshot {screenshot.order:02d} is missing alt text")
            expected_capture = f"{locale}/{screenshot.order:02d}-{screenshot.slug}.png"
            if screenshot.capture != expected_capture:
                errors.append(
                    f"{locale} screenshot {screenshot.order:02d} capture must be {expected_capture}"
                )
    italian_screenshots = {
        screenshot.order: screenshot for screenshot in manifest.locales.get("it-IT", ())
    }
    for screenshot in manifest.locales.get("en-US", ()):
        italian = italian_screenshots.get(screenshot.order)
        if italian is not None and screenshot.slug != italian.slug:
            errors.append(f"en-US screenshot {screenshot.order:02d} slug does not match it-IT")
    for locale in sorted(set(manifest.locales) - set(expected_locales)):
        errors.append(f"unexpected locale {locale}")
    return errors


def final_asset_path(root: Path, locale: str, screenshot: ScreenshotCopy) -> Path:
    return root / "store-assets/google-play" / locale / "phone-screenshots" / (
        f"{screenshot.order:02d}-{screenshot.slug}.png"
    )


def final_asset_errors(root: Path, manifest: MediaManifest) -> list[str]:
    errors: list[str] = []
    for locale, screenshots in manifest.locales.items():
        for screenshot in screenshots:
            path = final_asset_path(root, locale, screenshot)
            filename = path.name
            if not path.is_file():
                errors.append(f"{locale} is missing final screenshot {filename}")
            else:
                errors.extend(f"{locale} {filename}: {error}" for error in validate_asset(path, AssetSpec.phone_screenshot()))
    return errors


def render_all(root: Path) -> None:
    """Validate source captures until later tasks provide the renderer."""
    manifest = load_manifest(root / MANIFEST_PATH)
    errors = validate_manifest(manifest)
    errors.extend(capture_errors(root, manifest))
    errors.extend(final_asset_errors(root, manifest))
    if errors:
        raise ValueError("\n".join(errors))


def capture_errors(root: Path, manifest: MediaManifest) -> list[str]:
    errors: list[str] = []
    for locale, screenshots in manifest.locales.items():
        for screenshot in screenshots:
            capture = root / CAPTURES_PATH / screenshot.capture
            if not capture.is_file():
                errors.append(f"{locale} is missing capture {screenshot.capture}")
    return errors


def command_errors(command: str, root: Path) -> list[str]:
    if command == "render":
        try:
            render_all(root)
        except ValueError as error:
            return str(error).splitlines()
        return []
    try:
        manifest = load_manifest(root / MANIFEST_PATH)
    except ValueError as error:
        return [str(error)]

    errors = validate_manifest(manifest)
    return errors + final_asset_errors(root, manifest)


def main(arguments: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("render", "validate", "contact-sheet"):
        command_parser = subparsers.add_parser(command)
        command_parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args(arguments)
    errors = command_errors(args.command, args.root)
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
