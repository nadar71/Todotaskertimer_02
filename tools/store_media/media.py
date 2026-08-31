"""Deterministic validation entry point for Google Play media assets."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from PIL import (
    Image,
    ImageChops,
    ImageDraw,
    ImageFont,
    ImageOps,
    UnidentifiedImageError,
)


MANIFEST_PATH = Path("store-assets/google-play/source/media_manifest.json")
BRAND_PATH = Path("store-assets/google-play/source/brand.json")
CAPTURES_PATH = Path("store-assets/google-play/source/captures")
EXPECTED_ORDERS = tuple(range(1, 7))
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PALETTE_TOKENS = frozenset({"evergreen", "mint", "white", "cool_gray", "coral"})
LEGACY_DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
RENDER_SCALE = 4
WORDMARK_SIZE = (440, 96)
PHONE_SIZE = (1080, 1920)
PHONE_SAFE_MARGIN = 72
PHONE_CAPTURE_TOP = 480
PHONE_CAPTURE_SIZE = (1080, 1440)
PHONE_HEADLINE_ORIGIN = (72, 174)
PHONE_HEADLINE_SPACING = 14
PHONE_WORDMARK_SIZE = (220, 48)
PHONE_WORDMARK_POSITION = (72, 54)
HEADLINE_FONT_SIZE = 74
HEADLINE_FONT_PATH = Path(__file__).with_name("fonts") / "RobotoCondensed-Bold.ttf"
# Native source rows 771-2210 isolate complete recurrence controls at both edges.
RECURRENCE_CROP_ANCHOR = 771 / 960
CONTACT_SHEET_SIZE = (906, 1032)
CONTACT_SHEET_MARGIN = 24
CONTACT_SHEET_GAP = 24
CONTACT_THUMBNAIL_SIZE = (270, 480)


@dataclass(frozen=True)
class AssetSpec:
    width: int
    height: int
    mode: str
    max_bytes: int | None = None

    @classmethod
    def phone_screenshot(cls) -> "AssetSpec":
        return cls(1080, 1920, "RGB")


COMMON_ASSET_SPECS = {
    "app-icon-512.png": AssetSpec(512, 512, "RGBA", 1_024 * 1_024),
    "feature-graphic-1024x500.png": AssetSpec(1024, 500, "RGB"),
    "wordmark.png": AssetSpec(*WORDMARK_SIZE, "RGBA"),
}


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


@dataclass(frozen=True)
class Point:
    x: float
    y: float


@dataclass(frozen=True)
class Box:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top

    def intersects(self, other: "Box") -> bool:
        return not (
            self.right <= other.left
            or self.left >= other.right
            or self.bottom <= other.top
            or self.top >= other.bottom
        )


@dataclass(frozen=True)
class PhoneLayout:
    headline_box: Box
    capture_box: Box
    wordmark_box: Box
    accent_box: Box
    headline_lines: tuple[str, ...]

    @property
    def overlaps_capture(self) -> bool:
        return self.headline_box.intersects(self.capture_box)


@dataclass(frozen=True)
class Brand:
    canvas: int
    colors: dict[str, str]
    stroke_width: float
    check: tuple[Point, ...]
    forward: tuple[tuple[Point, ...], ...]

    @property
    def points(self) -> tuple[Point, ...]:
        return self.check + tuple(point for path in self.forward for point in path)

    def rgb(self, token: str) -> tuple[int, int, int]:
        value = self.colors[token]
        return (
            int(value[1:3], 16),
            int(value[3:5], 16),
            int(value[5:7], 16),
        )


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


def load_brand(path: Path) -> Brand:
    """Load and validate the version-one normalized brand geometry."""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"unable to load brand {path}: {error}") from error

    if not isinstance(data, dict) or data.get("version") != 1:
        raise ValueError("brand must be a JSON object with version 1")
    if data.get("canvas") != 108:
        raise ValueError("brand canvas must be 108")
    colors = data.get("colors")
    if not isinstance(colors, dict) or set(colors) != PALETTE_TOKENS:
        raise ValueError("brand colors must contain the five approved palette tokens")
    if any(
        not isinstance(value, str)
        or len(value) != 7
        or not value.startswith("#")
        or any(character not in "0123456789abcdefABCDEF" for character in value[1:])
        for value in colors.values()
    ):
        raise ValueError("brand colors must use #RRGGBB values")

    geometry = data.get("geometry")
    if not isinstance(geometry, dict):
        raise ValueError("brand geometry must be an object")
    stroke_width = geometry.get("stroke_width")
    if (
        isinstance(stroke_width, bool)
        or not isinstance(stroke_width, (int, float))
        or stroke_width <= 0
    ):
        raise ValueError("brand stroke_width must be positive")
    check = _load_point_path(geometry.get("check"), "check")
    forward_data = geometry.get("forward")
    if not isinstance(forward_data, list) or not forward_data:
        raise ValueError("brand forward geometry must contain paths")
    forward = tuple(
        _load_point_path(path_data, f"forward path {index}")
        for index, path_data in enumerate(forward_data, start=1)
    )
    brand = Brand(
        canvas=108,
        colors=dict(colors),
        stroke_width=float(stroke_width),
        check=check,
        forward=forward,
    )
    inset = brand.stroke_width / 2
    if any(
        point.x - inset < 21
        or point.x + inset > 87
        or point.y - inset < 21
        or point.y + inset > 87
        for point in brand.points
    ):
        raise ValueError("brand geometry must stay inside the 66x66 safe zone")
    return brand


def _load_point_path(value: object, name: str) -> tuple[Point, ...]:
    if not isinstance(value, list) or len(value) < 2:
        raise ValueError(f"brand {name} must contain at least two points")
    points: list[Point] = []
    for coordinates in value:
        if (
            not isinstance(coordinates, list)
            or len(coordinates) != 2
            or any(
                isinstance(number, bool) or not isinstance(number, (int, float))
                for number in coordinates
            )
        ):
            raise ValueError(f"brand {name} contains an invalid point")
        points.append(Point(float(coordinates[0]), float(coordinates[1])))
    return tuple(points)


def render_launcher_assets(project_root: Path, brand: Brand) -> None:
    """Render all Android launcher and splash resources from one brand source."""
    resource_root = project_root / "app/src/main/res"
    drawable_root = resource_root / "drawable"
    drawable_root.mkdir(parents=True, exist_ok=True)

    _write_text(
        drawable_root / "ic_launcher_background.xml", _background_vector(brand)
    )
    _write_text(
        drawable_root / "ic_launcher_foreground.xml", _foreground_vector(brand)
    )
    _write_text(
        drawable_root / "ic_launcher_monochrome.xml", _monochrome_vector(brand)
    )
    logo = _logo_vector(brand)
    _write_text(drawable_root / "ic_logo_light.xml", logo)
    _write_text(drawable_root / "ic_logo_dark.xml", logo)

    stale_foreground = resource_root / "drawable-v24/ic_launcher_foreground.xml"
    if stale_foreground.exists():
        stale_foreground.unlink()

    for qualifier in ("mipmap-anydpi-v26", "mipmap-anydpi-v33"):
        include_monochrome = qualifier.endswith("v33")
        for filename in ("ic_launcher.xml", "ic_launcher_round.xml"):
            _write_text(
                resource_root / qualifier / filename,
                _adaptive_icon_xml(include_monochrome),
            )

    splash = _splash_xml(brand.colors["evergreen"])
    _write_text(resource_root / "values/splash.xml", splash)
    _write_text(
        resource_root / "values-night/splash.xml",
        splash.replace("ic_logo_light", "ic_logo_dark"),
    )

    for density, size in LEGACY_DENSITIES.items():
        output = resource_root / f"mipmap-{density}"
        _render_legacy_icon(output / "ic_launcher.webp", brand, size, round_icon=False)
        _render_legacy_icon(output / "ic_launcher_round.webp", brand, size, round_icon=True)


def render_common_assets(project_root: Path, brand: Brand) -> None:
    """Render the language-neutral Google Play graphics from approved geometry."""
    output = project_root / "store-assets/google-play/common"
    output.mkdir(parents=True, exist_ok=True)

    icon = _render_store_icon(brand)
    _save_png(output / "app-icon-512.png", icon)

    wordmark_source = _wordmark_image(brand, RENDER_SCALE)
    wordmark = wordmark_source.resize(WORDMARK_SIZE, Image.Resampling.LANCZOS)
    _save_png(output / "wordmark.png", wordmark)

    feature = _render_feature_graphic(brand, wordmark_source)
    _save_png(output / "feature-graphic-1024x500.png", feature)


def load_headline_font() -> ImageFont.FreeTypeFont:
    """Load the committed headline face used by every localized composition."""
    try:
        return ImageFont.truetype(HEADLINE_FONT_PATH, HEADLINE_FONT_SIZE)
    except OSError as error:
        raise ValueError(
            f"unable to load headline font {HEADLINE_FONT_PATH}: {error}"
        ) from error


def missing_glyphs(
    text: str, font: ImageFont.FreeTypeFont
) -> tuple[str, ...]:
    """Return distinct characters that FreeType maps to its missing-glyph box."""

    def signature(character: str) -> tuple[tuple[int, int], bytes]:
        mask = font.getmask(character, mode="L")
        return mask.size, bytes(mask)

    missing_signature = signature(chr(0x10FFFF))
    missing: list[str] = []
    for character in text:
        if (
            not character.isspace()
            and signature(character) == missing_signature
            and character not in missing
        ):
            missing.append(character)
    return tuple(missing)


def calculate_phone_layout(
    headline: str, font: ImageFont.FreeTypeFont
) -> PhoneLayout:
    """Calculate the fixed editorial regions and word-boundary headline wrap."""
    words = headline.split()
    if not words:
        raise ValueError("headline must not be empty")
    if missing := missing_glyphs(headline, font):
        rendered = ", ".join(f"U+{ord(character):04X}" for character in missing)
        raise ValueError(f"headline contains missing glyphs: {rendered}")

    available_width = PHONE_SIZE[0] - 2 * PHONE_SAFE_MARGIN
    lines: list[str] = []
    current: list[str] = []
    for word in words:
        if font.getlength(word) > available_width:
            raise ValueError(f"headline word does not fit safe text box: {word}")
        candidate = " ".join((*current, word))
        if current and font.getlength(candidate) > available_width:
            lines.append(" ".join(current))
            current = [word]
        else:
            current.append(word)
    lines.append(" ".join(current))

    text = "\n".join(lines)
    measure = ImageDraw.Draw(Image.new("L", (1, 1)))
    bounds = measure.multiline_textbbox(
        PHONE_HEADLINE_ORIGIN,
        text,
        font=font,
        spacing=PHONE_HEADLINE_SPACING,
    )
    headline_box = Box(*bounds)
    capture_box = Box(0, PHONE_CAPTURE_TOP, *PHONE_SIZE)
    if (
        headline_box.left < PHONE_SAFE_MARGIN
        or headline_box.right > PHONE_SIZE[0] - PHONE_SAFE_MARGIN
        or headline_box.bottom >= PHONE_CAPTURE_TOP - PHONE_SAFE_MARGIN
    ):
        raise ValueError("headline does not fit safe text box")

    wordmark_left, wordmark_top = PHONE_WORDMARK_POSITION
    return PhoneLayout(
        headline_box=headline_box,
        capture_box=capture_box,
        wordmark_box=Box(
            wordmark_left,
            wordmark_top,
            wordmark_left + PHONE_WORDMARK_SIZE[0],
            wordmark_top + PHONE_WORDMARK_SIZE[1],
        ),
        accent_box=Box(945, 356, 988, 428),
        headline_lines=tuple(lines),
    )


def render_phone_screenshot(
    capture: Image.Image, copy: ScreenshotCopy, brand: Brand
) -> Image.Image:
    """Compose one upload-ready screenshot without a simulated device frame."""
    font = load_headline_font()
    layout = calculate_phone_layout(copy.headline, font)
    image = Image.new("RGB", PHONE_SIZE, brand.rgb("cool_gray"))

    wordmark = _wordmark_image(brand, RENDER_SCALE).resize(
        PHONE_WORDMARK_SIZE, Image.Resampling.LANCZOS
    )
    image.paste(
        wordmark,
        (layout.wordmark_box.left, layout.wordmark_box.top),
        wordmark,
    )

    draw = ImageDraw.Draw(image)
    draw.multiline_text(
        PHONE_HEADLINE_ORIGIN,
        "\n".join(layout.headline_lines),
        font=font,
        fill=brand.rgb("evergreen"),
        spacing=PHONE_HEADLINE_SPACING,
    )
    accent = brand.rgb("coral" if copy.slug == "recurrence" else "mint")
    _draw_down_accent(draw, accent)

    crop_anchor = RECURRENCE_CROP_ANCHOR if copy.slug == "recurrence" else 0.0
    fitted_capture = ImageOps.fit(
        capture.convert("RGB"),
        PHONE_CAPTURE_SIZE,
        method=Image.Resampling.LANCZOS,
        centering=(0.5, crop_anchor),
    )
    image.paste(fitted_capture, (layout.capture_box.left, layout.capture_box.top))
    return image


def _draw_down_accent(
    draw: ImageDraw.ImageDraw, color: tuple[int, int, int]
) -> None:
    width = 8
    draw.line(((966, 360), (966, 423)), fill=color, width=width)
    draw.line(
        ((949, 406), (966, 423), (983, 406)),
        fill=color,
        width=width,
        joint="curve",
    )
    radius = width // 2
    for x, y in ((966, 360), (949, 406), (983, 406)):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=color)


def render_contact_sheet(
    screenshots: Sequence[Image.Image], brand: Brand
) -> Image.Image:
    """Arrange six screenshots in upload order for visual review."""
    if len(screenshots) != 6:
        raise ValueError(
            f"contact sheet requires 6 screenshots, found {len(screenshots)}"
        )
    sheet = Image.new("RGB", CONTACT_SHEET_SIZE, brand.rgb("cool_gray"))
    for index, screenshot in enumerate(screenshots):
        column = index % 3
        row = index // 3
        left = CONTACT_SHEET_MARGIN + column * (
            CONTACT_THUMBNAIL_SIZE[0] + CONTACT_SHEET_GAP
        )
        top = CONTACT_SHEET_MARGIN + row * (
            CONTACT_THUMBNAIL_SIZE[1] + CONTACT_SHEET_GAP
        )
        thumbnail = screenshot.convert("RGB").resize(
            CONTACT_THUMBNAIL_SIZE, Image.Resampling.LANCZOS
        )
        sheet.paste(thumbnail, (left, top))
    return sheet


def _render_store_icon(brand: Brand) -> Image.Image:
    rendered_size = 512 * RENDER_SCALE
    image = Image.new(
        "RGBA",
        (rendered_size, rendered_size),
        (*brand.rgb("evergreen"), 255),
    )
    draw = ImageDraw.Draw(image)
    scale = rendered_size / brand.canvas
    width = round(brand.stroke_width * scale)
    _draw_paths(draw, (brand.check,), brand.rgb("white"), scale, width)
    _draw_paths(draw, brand.forward, brand.rgb("mint"), scale, width)
    return image.resize((512, 512), Image.Resampling.LANCZOS)


def _wordmark_image(brand: Brand, scale: int) -> Image.Image:
    width, height = (dimension * scale for dimension in WORDMARK_SIZE)
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=70 * scale)
    first, accent = "NowDo", "This"
    first_width = draw.textlength(first, font=font)
    total_width = first_width + draw.textlength(accent, font=font)
    bounds = draw.textbbox((0, 0), first + accent, font=font)
    x = (width - total_width) / 2
    y = (height - (bounds[3] - bounds[1])) / 2 - bounds[1]
    draw.text((x, y), first, font=font, fill=(*brand.rgb("evergreen"), 255))
    draw.text(
        (x + first_width, y),
        accent,
        font=font,
        fill=(*brand.rgb("mint"), 255),
    )
    return image


def _render_feature_graphic(brand: Brand, wordmark: Image.Image) -> Image.Image:
    width, height = 1024 * RENDER_SCALE, 500 * RENDER_SCALE
    image = Image.new("RGB", (width, height), brand.rgb("cool_gray"))
    draw = ImageDraw.Draw(image)

    gesture_scale = 3.4 * RENDER_SCALE
    gesture_offset = Point(20 * RENDER_SCALE, 65 * RENDER_SCALE)
    gesture_width = round(brand.stroke_width * gesture_scale)
    _draw_offset_paths(
        draw,
        (brand.check,),
        brand.rgb("evergreen"),
        gesture_scale,
        gesture_offset,
        gesture_width,
    )
    _draw_offset_paths(
        draw,
        brand.forward,
        brand.rgb("mint"),
        gesture_scale,
        gesture_offset,
        gesture_width,
    )

    _draw_task_row_motif(draw, brand, RENDER_SCALE)
    wordmark_position = (
        (width - wordmark.width) // 2,
        (height - wordmark.height) // 2,
    )
    image.paste(wordmark, wordmark_position, wordmark)
    return image.resize((1024, 500), Image.Resampling.LANCZOS).convert("RGB")


def _draw_task_row_motif(
    draw: ImageDraw.ImageDraw, brand: Brand, scale: int
) -> None:
    def box(values: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
        left, top, right, bottom = values
        return left * scale, top * scale, right * scale, bottom * scale

    for top, checked, line_width in ((166, True, 126), (260, False, 92)):
        draw.rounded_rectangle(
            box((774, top, 974, top + 76)),
            radius=8 * scale,
            fill=brand.rgb("white"),
        )
        check_box = box((794, top + 27, 816, top + 49))
        draw.rounded_rectangle(
            check_box,
            radius=4 * scale,
            fill=brand.rgb("mint") if checked else brand.rgb("white"),
            outline=brand.rgb("evergreen"),
            width=2 * scale,
        )
        if checked:
            draw.line(
                [
                    (800 * scale, (top + 38) * scale),
                    (806 * scale, (top + 44) * scale),
                    (813 * scale, (top + 33) * scale),
                ],
                fill=brand.rgb("evergreen"),
                width=2 * scale,
                joint="curve",
            )
        draw.rounded_rectangle(
            box((834, top + 28, 834 + line_width, top + 35)),
            radius=3 * scale,
            fill=brand.rgb("evergreen"),
        )
        draw.rounded_rectangle(
            box((834, top + 44, 900, top + 49)),
            radius=2 * scale,
            fill=brand.rgb("mint"),
        )


def _draw_offset_paths(
    draw: ImageDraw.ImageDraw,
    paths: Sequence[Sequence[Point]],
    color: tuple[int, int, int],
    scale: float,
    offset: Point,
    width: int,
) -> None:
    radius = width / 2
    for path in paths:
        coordinates = [
            (offset.x + point.x * scale, offset.y + point.y * scale)
            for point in path
        ]
        draw.line(coordinates, fill=color, width=width, joint="curve")
        for x, y in coordinates:
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=color)


def _save_png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def _write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def _path_data(paths: Sequence[Sequence[Point]]) -> str:
    commands: list[str] = []
    for path in paths:
        commands.append(f"M{_number(path[0].x)},{_number(path[0].y)}")
        commands.extend(f"L{_number(point.x)},{_number(point.y)}" for point in path[1:])
    return " ".join(commands)


def _number(value: float) -> str:
    return str(int(value)) if value.is_integer() else str(value)


def _vector_path(path_data: str, color: str, stroke_width: float) -> str:
    return (
        "    <path\n"
        "        android:fillColor=\"#00000000\"\n"
        f"        android:pathData=\"{path_data}\"\n"
        f"        android:strokeColor=\"{color}\"\n"
        "        android:strokeLineCap=\"round\"\n"
        "        android:strokeLineJoin=\"round\"\n"
        f"        android:strokeWidth=\"{_number(stroke_width)}\" />\n"
    )


def _vector_document(paths: str, width: int = 108) -> str:
    return (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
        f"    android:width=\"{width}dp\"\n"
        f"    android:height=\"{width}dp\"\n"
        "    android:viewportWidth=\"108\"\n"
        "    android:viewportHeight=\"108\">\n"
        f"{paths}"
        "</vector>\n"
    )


def _background_vector(brand: Brand) -> str:
    path = (
        "    <path\n"
        f"        android:fillColor=\"{brand.colors['evergreen']}\"\n"
        "        android:pathData=\"M0,0h108v108h-108z\" />\n"
    )
    return _vector_document(path)


def _foreground_vector(brand: Brand) -> str:
    paths = _vector_path(
        _path_data((brand.check,)), brand.colors["white"], brand.stroke_width
    )
    paths += _vector_path(
        _path_data(brand.forward), brand.colors["mint"], brand.stroke_width
    )
    return _vector_document(paths)


def _monochrome_vector(brand: Brand) -> str:
    combined = (brand.check,) + brand.forward
    path = _vector_path(_path_data(combined), "#FF000000", brand.stroke_width)
    return _vector_document(path)


def _logo_vector(brand: Brand) -> str:
    paths = _vector_path(
        _path_data((brand.check,)), brand.colors["white"], brand.stroke_width
    )
    paths += _vector_path(
        _path_data(brand.forward), brand.colors["mint"], brand.stroke_width
    )
    return _vector_document(paths, width=240)


def _adaptive_icon_xml(include_monochrome: bool) -> str:
    monochrome = (
        "    <monochrome android:drawable=\"@drawable/ic_launcher_monochrome\" />\n"
        if include_monochrome
        else ""
    )
    return (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
        "    <background android:drawable=\"@drawable/ic_launcher_background\" />\n"
        "    <foreground android:drawable=\"@drawable/ic_launcher_foreground\" />\n"
        f"{monochrome}"
        "</adaptive-icon>\n"
    )


def _splash_xml(background: str) -> str:
    return (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        "<resources>\n"
        "    <style name=\"Theme.App.MySplash\" parent=\"Theme.SplashScreen\">\n"
        f"        <item name=\"windowSplashScreenBackground\">{background}</item>\n"
        "        <item name=\"windowSplashScreenAnimatedIcon\">@drawable/ic_logo_light</item>\n"
        "        <item name=\"postSplashScreenTheme\">@style/Theme.ToDoCompose</item>\n"
        "    </style>\n"
        "</resources>\n"
    )


def _render_legacy_icon(path: Path, brand: Brand, size: int, round_icon: bool) -> None:
    scale = size * 4 / brand.canvas
    rendered_size = size * 4
    background = brand.rgb("evergreen")
    if round_icon:
        image = Image.new("RGBA", (rendered_size, rendered_size), (0, 0, 0, 0))
        ImageDraw.Draw(image).ellipse(
            (0, 0, rendered_size - 1, rendered_size - 1),
            fill=(*background, 255),
        )
    else:
        image = Image.new("RGB", (rendered_size, rendered_size), background)
    draw = ImageDraw.Draw(image)
    width = round(brand.stroke_width * scale)
    _draw_paths(draw, (brand.check,), brand.rgb("white"), scale, width)
    _draw_paths(draw, brand.forward, brand.rgb("mint"), scale, width)
    image = image.resize((size, size), Image.Resampling.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="WEBP", lossless=True, method=6, exact=True)


def _draw_paths(
    draw: ImageDraw.ImageDraw,
    paths: Sequence[Sequence[Point]],
    color: tuple[int, int, int],
    scale: float,
    width: int,
) -> None:
    radius = width / 2
    for path in paths:
        coordinates = [(point.x * scale, point.y * scale) for point in path]
        draw.line(coordinates, fill=color, width=width, joint="curve")
        for x, y in coordinates:
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=color)


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
            if not screenshot.headline.strip():
                errors.append(
                    f"{locale} screenshot {screenshot.order:02d} is missing headline"
                )
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
        output = root / "store-assets/google-play" / locale / "phone-screenshots"
        expected_filenames = {
            f"{screenshot.order:02d}-{screenshot.slug}.png"
            for screenshot in screenshots
        }
        for screenshot in screenshots:
            path = final_asset_path(root, locale, screenshot)
            filename = path.name
            if not path.is_file():
                errors.append(f"{locale} is missing final screenshot {filename}")
            else:
                errors.extend(
                    f"{locale} {filename}: {error}"
                    for error in validate_asset(path, AssetSpec.phone_screenshot())
                )
        if output.is_dir():
            for path in sorted(output.glob("*.png")):
                if path.name not in expected_filenames:
                    errors.append(
                        f"{locale} has unexpected final screenshot {path.name}"
                    )
    return errors


def alt_text_content(screenshots: Sequence[ScreenshotCopy]) -> str:
    return "".join(
        f"{screenshot.order:02d}-{screenshot.slug}.png: {screenshot.alt_text}\n"
        for screenshot in sorted(screenshots, key=lambda item: item.order)
    )


def alt_text_errors(root: Path, manifest: MediaManifest) -> list[str]:
    errors: list[str] = []
    for locale, screenshots in manifest.locales.items():
        path = root / "store-assets/google-play" / locale / "alt-text.txt"
        if not path.is_file():
            errors.append(f"{locale} is missing alt-text.txt")
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except OSError as error:
            errors.append(f"{locale} unable to read alt-text.txt: {error}")
            continue
        if content != alt_text_content(screenshots):
            errors.append(f"{locale} alt-text.txt does not match the manifest")
    return errors


def common_asset_errors(root: Path) -> list[str]:
    """Validate every language-neutral Play asset against its upload contract."""
    common = root / "store-assets/google-play/common"
    errors: list[str] = []
    for filename, spec in COMMON_ASSET_SPECS.items():
        errors.extend(
            f"common {filename}: {error}"
            for error in validate_asset(common / filename, spec)
        )
    return errors


def render_all(root: Path) -> None:
    """Render every localized phone screenshot and matching alt-text inventory."""
    manifest = load_manifest(root / MANIFEST_PATH)
    brand = load_brand(root / BRAND_PATH)
    errors = validate_manifest(manifest)
    errors.extend(capture_errors(root, manifest))
    if errors:
        raise ValueError("\n".join(errors))

    font = load_headline_font()
    for screenshots in manifest.locales.values():
        for screenshot in screenshots:
            calculate_phone_layout(screenshot.headline, font)

    for locale, screenshots in manifest.locales.items():
        ordered = sorted(screenshots, key=lambda item: item.order)
        for screenshot in ordered:
            source = root / CAPTURES_PATH / screenshot.capture
            with Image.open(source) as capture:
                capture.load()
                output = render_phone_screenshot(capture, screenshot, brand)
            _save_png(final_asset_path(root, locale, screenshot), output)
        _write_text(
            root / "store-assets/google-play" / locale / "alt-text.txt",
            alt_text_content(ordered),
        )

    errors = final_asset_errors(root, manifest)
    errors.extend(alt_text_errors(root, manifest))
    if errors:
        raise ValueError("\n".join(errors))


def render_contact_sheets(root: Path) -> None:
    """Generate one ordered visual-review sheet for each locale."""
    manifest = load_manifest(root / MANIFEST_PATH)
    brand = load_brand(root / BRAND_PATH)
    errors = validate_manifest(manifest)
    errors.extend(final_asset_errors(root, manifest))
    if errors:
        raise ValueError("\n".join(errors))

    for locale, screenshots in manifest.locales.items():
        images: list[Image.Image] = []
        for screenshot in sorted(screenshots, key=lambda item: item.order):
            with Image.open(final_asset_path(root, locale, screenshot)) as image:
                image.load()
                images.append(image.convert("RGB"))
        sheet = render_contact_sheet(images, brand)
        _save_png(
            root / "store-assets/google-play" / locale / "contact-sheet.png",
            sheet,
        )


def capture_errors(root: Path, manifest: MediaManifest) -> list[str]:
    errors: list[str] = []
    for locale, screenshots in manifest.locales.items():
        for screenshot in screenshots:
            capture = root / CAPTURES_PATH / screenshot.capture
            if not capture.is_file():
                errors.append(f"{locale} is missing capture {screenshot.capture}")
                continue
            try:
                with Image.open(capture) as image:
                    image.load()
                    if image.format != "PNG":
                        errors.append(
                            f"{screenshot.capture} must be PNG, found {image.format or 'unknown'}"
                        )
                        continue
                    rgb = image.convert("RGB")
                    background = Image.new("RGB", rgb.size, rgb.getpixel((0, 0)))
                    if ImageChops.difference(rgb, background).getbbox() is None:
                        errors.append(f"{screenshot.capture} is blank")
            except (OSError, UnidentifiedImageError) as error:
                errors.append(f"unable to read capture {screenshot.capture}: {error}")
    return errors


def command_errors(command: str, root: Path, scope: str = "all") -> list[str]:
    if command == "render-launcher":
        try:
            render_launcher_assets(root, load_brand(root / BRAND_PATH))
        except ValueError as error:
            return str(error).splitlines()
        return []
    if command == "render-common":
        try:
            render_common_assets(root, load_brand(root / BRAND_PATH))
        except ValueError as error:
            return str(error).splitlines()
        return []
    if command == "render":
        try:
            render_all(root)
        except ValueError as error:
            return str(error).splitlines()
        return []
    if command == "contact-sheet":
        try:
            render_contact_sheets(root)
        except ValueError as error:
            return str(error).splitlines()
        return []
    if command == "validate" and scope == "common":
        return common_asset_errors(root)
    try:
        manifest = load_manifest(root / MANIFEST_PATH)
    except ValueError as error:
        return [str(error)]

    errors = validate_manifest(manifest)
    if command == "validate-captures":
        return errors + capture_errors(root, manifest)
    phone_errors = errors + final_asset_errors(root, manifest)
    phone_errors.extend(alt_text_errors(root, manifest))
    if command == "validate" and scope == "all":
        return common_asset_errors(root) + phone_errors
    return phone_errors


def main(arguments: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in (
        "render",
        "validate",
        "contact-sheet",
        "render-launcher",
        "render-common",
        "validate-captures",
    ):
        command_parser = subparsers.add_parser(command)
        command_parser.add_argument("--root", type=Path, default=Path("."))
        if command == "validate":
            command_parser.add_argument(
                "--scope", choices=("all", "common", "phone"), default="all"
            )
    args = parser.parse_args(arguments)
    errors = command_errors(args.command, args.root, getattr(args, "scope", "all"))
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
