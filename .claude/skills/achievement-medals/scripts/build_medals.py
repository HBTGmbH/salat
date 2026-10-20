#!/usr/bin/env python3
"""Builds the achievement medals of Salat: one SVG per pictogram and material tier.

Every medal has the same anatomy, so that only the pictogram and the tier tell them apart:
rim with rivets (more of them the higher the tier, a stone on top for diamond), face, the
pictogram in the upper half and a plate in the lower half. The page sets the level number as
text onto the plate - it is never part of the SVG, so a number and the picture cannot collide.

Usage:
  python3 build_medals.py --out <dir> [--pictograms <dir>] [--tiers <file>] [--only flag,calendar]

Writes <out>/medal-<pictogram>-<tier>.svg for every pictogram and tier.
Output: one key=value line per medal, then a summary line.
Exit codes: 0 ok, 2 input missing or unreadable, 3 a pictogram breaks the format,
            4 a tier fails a contrast check.
"""
import argparse
import json
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
SVG_NS = "http://www.w3.org/2000/svg"

# Pictograms are drawn in a 60 x 42 box and placed in the upper half of the face.
PICTOGRAM_VIEWBOX = "0 0 60 42"
PICTOGRAM_AT = (34, 20)
ALLOWED_SHAPES = {"path", "rect", "circle", "ellipse", "polygon"}
# A pictogram is one flat colour: colour, stroke and styling come from the tier, never from the drawing.
FORBIDDEN_ATTRS = {"fill", "stroke", "stroke-width", "style", "class", "opacity", "fill-opacity", "transform"}

# WCAG: text needs 4.5:1, a graphic 3:1. The number sits on the plate, the pictogram on the face.
MIN_NUMBER_CONTRAST = 4.5
MIN_GLYPH_CONTRAST = 3.0

# The imprint along the bottom of the rim: plain text, fine, in the rim's dark colour. Rivets and
# stars leave the bottom arc free for it (half the gap on each side of the bottom, in degrees).
IMPRINT = "SALAT · HBT"
IMPRINT_GAP = 38

TIER_KEYS = {"rim_dark", "rim", "face_dark", "face", "plate", "glyph", "number", "studs", "gems", "stars", "star"}


def luminance(hex_colour):
    channels = [int(hex_colour[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast(a, b):
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def load_tiers(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    tiers = {name: tier for name, tier in data.items() if not name.startswith("_")}
    for name, tier in tiers.items():
        missing = TIER_KEYS - tier.keys()
        if missing:
            raise ValueError(f"tier {name} lacks {sorted(missing)}")
    return tiers


def pictogram_shapes(path):
    """The shapes of a pictogram as SVG text; raises ValueError naming what breaks the format."""
    root = ET.fromstring(path.read_text(encoding="utf-8"))
    if root.get("viewBox") != PICTOGRAM_VIEWBOX:
        raise ValueError(f"viewBox must be '{PICTOGRAM_VIEWBOX}', is '{root.get('viewBox')}'")
    shapes = []
    for element in root:
        tag = element.tag.split("}")[-1]
        if tag not in ALLOWED_SHAPES:
            raise ValueError(f"element <{tag}> not allowed, only {sorted(ALLOWED_SHAPES)}")
        bad = FORBIDDEN_ATTRS & set(element.attrib)
        if bad:
            raise ValueError(f"<{tag}> carries {sorted(bad)}; colour and form come from the tier")
        attrs = " ".join(f'{key}="{value}"' for key, value in element.attrib.items())
        shapes.append(f"<{tag} {attrs}/>")
    if not shapes:
        raise ValueError("no shapes")
    return "\n    ".join(shapes)


def studs(count, colour):
    if count == 0:
        return ""
    dots = []
    for k in range(count):
        degrees = -90 + (k + 0.5) * 360 / count
        if abs(degrees - 90) < IMPRINT_GAP:
            continue
        angle = math.radians(degrees)
        dots.append(f'<circle cx="{64 + 54.5 * math.cos(angle):.1f}" cy="{64 + 54.5 * math.sin(angle):.1f}" r="2.2"/>')
    return f'<g fill="{colour}">' + "".join(dots) + "</g>"


def star(cx, cy, outer):
    points = []
    for p in range(10):
        radius = outer if p % 2 == 0 else outer * 0.43
        angle = math.radians(-90 + p * 36)
        points.append(f"{cx + radius * math.cos(angle):.1f},{cy + radius * math.sin(angle):.1f}")
    return f'<polygon points="{" ".join(points)}"/>'


# The crown of the highest tier: a large star on top, two smaller ones on each side, then the
# wreath running round the rest of the rim. (angle from the top in degrees, outer radius)
CROWN = [(0, 10.5), (19, 8.0), (36, 6.6)]
WREATH_FROM, WREATH_STEP, WREATH_SIZE = 54, 21, 5.6


def stars(fill, outline):
    """Stars round the rim instead of rivets - the mark of the highest tier."""
    placed = []
    for offset, size in CROWN:
        placed += [(offset, size)] if offset == 0 else [(offset, size), (-offset, size)]
    offset = WREATH_FROM
    while offset < 180 - IMPRINT_GAP:
        placed += [(offset, WREATH_SIZE), (-offset, WREATH_SIZE)]
        offset += WREATH_STEP
    shapes = []
    for offset, size in placed:
        # the large stars sit a little further in, so that their tips stay inside the artboard
        reach = 54.5 - max(0, size - 6.5) * 0.6
        angle = math.radians(-90 + offset)
        shapes.append(star(64 + reach * math.cos(angle), 64 + reach * math.sin(angle), size))
    return f'<g fill="{fill}" stroke="{outline}" stroke-width="0.9" stroke-linejoin="round">' + "".join(shapes) + "</g>"


def stone(x, y, size, tier):
    """A cut stone on the rim: lozenge with two facet lines."""
    h = size
    return (f'<polygon points="{x},{y - h} {x + h * 0.85},{y} {x},{y + h * 1.1} {x - h * 0.85},{y}" fill="{tier["face"]}" '
            f'stroke="{tier["rim_dark"]}" stroke-width="2.2" stroke-linejoin="round"/>'
            f'<path d="M{x - h * 0.85} {y}H{x + h * 0.85}M{x} {y - h}V{y + h * 1.1}" stroke="{tier["rim_dark"]}" stroke-width="1.3"/>')


def gems(count, tier):
    if count == 0:
        return ""
    if count == 1:
        return stone(64, 11, 10, tier)
    # three stones: a large one on top, two smaller ones beside it on the rim
    side = [(64 + 54.5 * math.cos(math.radians(a)), 64 + 54.5 * math.sin(math.radians(a))) for a in (-118, -62)]
    return stone(64, 11, 10, tier) + "".join(stone(round(x, 1), round(y, 1), 6.5, tier) for x, y in side)


def imprint(colour):
    """The text along the bottom of the rim, upright, on an arc through the bottom."""
    return (f'<path id="imprint-arc" d="M8.5 64A55.5 55.5 0 0 0 119.5 64" fill="none"/>'
            f'<text font-family="Arial, Helvetica, sans-serif" font-size="6.4" font-weight="700" letter-spacing="1.4" '
            f'fill="{colour}" text-anchor="middle" dy="2.2"><textPath href="#imprint-arc" startOffset="50%">{IMPRINT}</textPath></text>')


def medal(name, tier_name, tier, shapes):
    rim_detail = "" if tier["stars"] else studs(tier["studs"], tier["rim_dark"])
    # drawn after the face: the large stars of the crown reach into it
    crown = stars(tier["star"], tier["rim_dark"]) if tier["stars"] else ""
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="128" height="128" role="img" aria-label="Medaille {name} ({tier_name})">
  <title>Achievement-Medaille: {name}, Stufe {tier_name}</title>
  <!-- generated by .claude/skills/achievement-medals/scripts/build_medals.py - change the pictogram or tiers.json, not this file -->
  <circle cx="64" cy="64" r="62" fill="{tier["rim_dark"]}"/>
  <circle cx="64" cy="64" r="59" fill="{tier["rim"]}"/>
  {rim_detail}
  {imprint(tier["rim_dark"])}
  <circle cx="64" cy="64" r="50" fill="{tier["face_dark"]}"/>
  <circle cx="64" cy="64" r="46" fill="{tier["face"]}"/>
  {crown}
  <g transform="translate({PICTOGRAM_AT[0]} {PICTOGRAM_AT[1]})" fill="{tier["glyph"]}" fill-rule="evenodd">
    {shapes}
  </g>
  <!-- the plate: the page sets the level number onto it as text -->
  <rect x="24" y="67" width="80" height="34" rx="9" fill="{tier["plate"]}"/>
  {gems(tier["gems"], tier)}
</svg>
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="target directory, e.g. src/main/resources/static/images/achievement")
    parser.add_argument("--pictograms", default=str(SKILL / "assets" / "pictograms"))
    parser.add_argument("--tiers", default=str(SKILL / "assets" / "tiers.json"))
    parser.add_argument("--only", help="comma separated pictogram names")
    args = parser.parse_args()

    pictogram_dir, tiers_file, out = Path(args.pictograms), Path(args.tiers), Path(args.out)
    if not pictogram_dir.is_dir() or not tiers_file.is_file():
        print(f"error=input_missing pictograms={pictogram_dir.as_posix()} tiers={tiers_file.as_posix()}")
        return 2
    try:
        tiers = load_tiers(tiers_file)
    except (ValueError, json.JSONDecodeError) as error:
        print(f"error=tiers_invalid file={tiers_file.as_posix()} detail=\"{error}\"")
        return 2

    failed_contrast = False
    for name, tier in tiers.items():
        number = contrast(tier["number"], tier["plate"])
        glyph = contrast(tier["glyph"], tier["face"])
        ok = number >= MIN_NUMBER_CONTRAST and glyph >= MIN_GLYPH_CONTRAST
        failed_contrast |= not ok
        print(f"tier={name} number_on_plate={number:.2f} glyph_on_face={glyph:.2f} result={'pass' if ok else 'fail'}")
    if failed_contrast:
        print(f"error=contrast detail=\"number needs {MIN_NUMBER_CONTRAST}:1 on the plate, pictogram {MIN_GLYPH_CONTRAST}:1 on the face\"")
        return 4

    wanted = set(args.only.split(",")) if args.only else None
    pictograms = sorted(p for p in pictogram_dir.glob("*.svg") if wanted is None or p.stem in wanted)
    if not pictograms:
        print(f"error=no_pictograms dir={pictogram_dir.as_posix()} only={args.only}")
        return 2
    out.mkdir(parents=True, exist_ok=True)
    written = 0
    for pictogram in pictograms:
        try:
            shapes = pictogram_shapes(pictogram)
        except (ValueError, ET.ParseError) as error:
            print(f"error=pictogram_invalid file={pictogram.as_posix()} detail=\"{error}\"")
            return 3
        for tier_name, tier in tiers.items():
            target = out / f"medal-{pictogram.stem}-{tier_name}.svg"
            target.write_text(medal(pictogram.stem, tier_name, tier, shapes), encoding="utf-8")
            written += 1
            print(f"medal={target.as_posix()}")
    print(f"summary pictograms={len(pictograms)} tiers={len(tiers)} written={written}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
