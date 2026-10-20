#!/usr/bin/env python3
"""Renders an overview of the built medals the way the pages show them.

Every medal sits on a round white disc - in the light and the dark mode alike - with the level
number set as text onto its plate, at popup size (96 px) and profile size (64 px). The overview
is the place to judge a pictogram and a tier: the build script checks contrasts, not looks.

Usage:
  python3 preview.py --medals <dir> --out <file.png> [--sample flag=100;calendar=25]
  python3 preview.py --medals <dir> --out <file.png> --plan <plan.json>

Without a plan it shows every tier of every pictogram. A plan shows the achievements the way
they will be awarded, one row each, with their real levels, tiers and level names:
  [{"title": "Loyalist", "pictogram": "calendar",
    "levels": [["1", "bronze", "Angekommen"], ["5", "silver", "Treue Seele"]]}]

Writes <file.html> next to <file.png> and renders the PNG with a headless Chrome or Chromium.
Output: key=value lines. Exit codes: 0 ok, 2 no medals found, 5 no Chrome found (the HTML is
written all the same), 6 Chrome failed.
"""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

TIER_ORDER = ["bronze", "silver", "gold", "platinum", "diamond", "master", "grandmaster", "locked"]
TIERS_FILE = Path(__file__).resolve().parent.parent / "assets" / "tiers.json"

# Where the plate's centre lies on the disc, and how large the number is: the medal fills 94 %
# of the disc, the plate's centre is at y = 84 of 128, the number takes two thirds of its height.
# The pages use the same values (references/integration.md); change them together.
MEDAL_SHARE = 0.94
PLATE_CENTRE = 84 / 128
PLATE_HEIGHT = 34 / 128
NUMBER_SHARE = 0.68

CHROME_CANDIDATES = ["google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "chrome"]
CHROME_MAC = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")


def find_chrome():
    for name in CHROME_CANDIDATES:
        found = shutil.which(name)
        if found:
            return found
    return str(CHROME_MAC) if CHROME_MAC.exists() else None


def number_colours():
    data = json.loads(TIERS_FILE.read_text(encoding="utf-8"))
    return {name: tier["number"] for name, tier in data.items() if not name.startswith("_")}


NUMBER_COLOUR = number_colours()


def medal(path, size, number):
    colour = NUMBER_COLOUR.get(path.stem.rsplit("-", 1)[1], "#FFFFFF")
    top = (1 - MEDAL_SHARE) / 2 * 100 + PLATE_CENTRE * MEDAL_SHARE * 100
    font = PLATE_HEIGHT * MEDAL_SHARE * size * NUMBER_SHARE
    # a locked medal shows the number of the level still to come
    label = f'<span class="num" style="top:{top:.1f}%;font-size:{font:.1f}px;color:{colour}">{number}</span>' if number else ""
    return (f'<span class="medal" style="width:{size}px;height:{size}px">'
            f'<img src="{path.resolve().as_uri()}" alt="">{label}</span>')


def page(medals, samples):
    pictograms = sorted({p.stem.split("-")[1] for p in medals})
    by_key = {p.stem: p for p in medals}

    def row(size, tier):
        cells = []
        for pictogram in pictograms:
            path = by_key.get(f"medal-{pictogram}-{tier}")
            cells.append(f"<td>{medal(path, size, samples.get(pictogram, '25'))}</td>" if path else "<td></td>")
        return f"<tr><th>{tier}</th>{''.join(cells)}</tr>"

    head = "<tr><th></th>" + "".join(f"<th>{p}</th>" for p in pictograms) + "</tr>"
    tiers = [t for t in TIER_ORDER if any(p.stem.endswith(f"-{t}") for p in medals)]
    return f'''<!DOCTYPE html><html><head><meta charset="utf-8"><style>
 body {{ font-family: Inter, system-ui, sans-serif; margin: 24px; background: #f6f8fb; color: #182433; }}
 h2 {{ font-size: 13px; font-weight: 500; color: #667382; margin: 20px 0 8px; }}
 table {{ border-collapse: separate; border-spacing: 14px 10px; background: #fff; border-radius: 8px; }}
 .dark table {{ background: #1f1f1f; color: #e6e7e9; }}
 th {{ font-size: 12px; font-weight: 600; text-align: center; }}
 .medal {{ position: relative; display: inline-block; border-radius: 50%; background: #fff;
          box-shadow: 0 0 0 1px rgba(4,32,69,.1); }}
 .medal img {{ position: absolute; inset: {(1 - MEDAL_SHARE) / 2 * 100:.1f}%; width: {MEDAL_SHARE * 100:.0f}%; height: {MEDAL_SHARE * 100:.0f}%; }}
 .num {{ position: absolute; left: 50%; transform: translate(-50%, -50%); color: #fff; font-weight: 700;
        line-height: 1; white-space: nowrap; letter-spacing: -.01em; }}
</style></head><body>
<h2>Popup, 96 px, every tier</h2><table>{head}{''.join(row(96, t) for t in tiers)}</table>
<h2>Profile page, 64 px, light</h2><table>{head}{''.join(row(64, t) for t in tiers)}</table>
<div class="dark"><h2>Profile page, 64 px, dark</h2><table>{head}{''.join(row(64, t) for t in tiers)}</table></div>
</body></html>'''


def plan_page(medals, plan):
    by_key = {p.stem: p for p in medals}
    css = page(medals, {}).split("</style>")[0] + """
 .ach { display: flex; gap: 18px; align-items: flex-start; background: #fff; border-radius: 8px; padding: 14px 18px; margin-bottom: 10px; }
 .dark .ach { background: #1f1f1f; color: #e6e7e9; }
 .ach h3 { width: 150px; font-size: 14px; margin: 30px 0 0; }
 .lvl { width: 104px; text-align: center; font-size: 12px; }
 .lvl b { display: block; margin-top: 6px; }
</style></head><body>"""

    def rows(size):
        out = []
        for item in plan:
            cells = []
            for number, tier, name in item["levels"]:
                path = by_key.get(f"medal-{item['pictogram']}-{tier}")
                image = medal(path, size, number) if path else f"missing {item['pictogram']}-{tier}"
                cells.append(f'<div class="lvl">{image}<b>{name}</b>{number} · {tier}</div>')
            out.append(f'<div class="ach"><h3>{item["title"]}</h3>{"".join(cells)}</div>')
        return "".join(out)

    return (css + "<h2>Achievements with their levels, popup size 96 px</h2>" + rows(96)
            + '<div class="dark"><h2>Profile page, 64 px, dark</h2>' + rows(64) + "</div></body></html>")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--medals", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--sample", default="", help="number per pictogram, e.g. flag=100;calendar=25;keyboard=1,0K")
    parser.add_argument("--plan", help="JSON file with the achievements and their levels, see the module docstring")
    args = parser.parse_args()

    medals = sorted(Path(args.medals).glob("medal-*-*.svg"))
    if not medals:
        print(f"error=no_medals dir={Path(args.medals).as_posix()}")
        return 2
    samples = {}
    for pair in filter(None, args.sample.split(";" if ";" in args.sample else ",")):
        key, _, value = pair.partition("=")
        samples[key] = value
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    html = out.with_suffix(".html")
    if args.plan:
        plan = json.loads(Path(args.plan).read_text(encoding="utf-8"))
        html.write_text(plan_page(medals, plan), encoding="utf-8")
    else:
        html.write_text(page(medals, samples), encoding="utf-8")
    print(f"html={html.as_posix()}")

    chrome = find_chrome()
    if not chrome:
        print("error=no_chrome detail=\"open the HTML in a browser instead\"")
        return 5
    # three tables: one row per tier at 96 px, two at 64 px, plus headings and margins
    if args.plan:
        rows = len(plan)
        height = 200 + rows * 170 + rows * 140
    else:
        tiers = len({p.stem.rsplit("-", 1)[1] for p in medals})
        height = 220 + (tiers + 1) * 116 + 2 * (tiers + 1) * 84
    result = subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                             "--force-device-scale-factor=2", f"--window-size=1290,{height}",
                             f"--screenshot={out.resolve()}", html.resolve().as_uri()],
                            capture_output=True, text=True, timeout=120)
    if result.returncode != 0 or not out.exists():
        print(f"error=chrome_failed code={result.returncode}")
        return 6
    print(f"png={out.as_posix()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
