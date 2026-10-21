---
name: achievement-medals
description: Draws the pictograms of Salat's achievement medals and builds the medal SVGs from them - flat badges in material tiers (bronze, silver, gold, platinum, diamond, master, grand master, plus a grey locked one) with a plate on which the page sets the level number as text. Use this skill whenever an achievement needs its medal, a pictogram is to be drawn or changed, a tier colour, the medal layout or the tier of a level is to change, or a medal looks wrong on the profile page or in the achievement popup - also when the user only says badge, Auszeichnung, Abzeichen, Medaille or Trophäe. Not for HBT corporate-design icons, presentation pictograms or slides (that is hbt-icon-generator), not for the Tabler icons of the user interface, and not for the Java side of an achievement such as its contributor, levels or texts.
---

# Achievement medals

## Goal

Every achievement has a medal SVG per tier under `src/main/resources/static/images/achievement/`, built by
`scripts/build_medals.py` from one pictogram, and the overview from `scripts/preview.py` shows it readable at 64 and 96 px in
the light and the dark mode.

## How a medal is built

Every medal has the same anatomy, so that only two things tell medals apart - the pictogram says *what* was achieved, the
material says *how far*:

* **Rim** with rivets: none on bronze, more with every tier, a stone on top for diamond, three stones for master, and a
  crown of stars instead of rivets for grand master - a large one on top, two smaller ones on each side, a wreath
  round the rest. The step up has to be visible without reading the number, which is
  what the single-colour line-art medals failed at. The tier names never appear as text; the material says it.
* **Face** with the **pictogram** in the upper half, one flat colour.
* **Plate** in the lower half. The page sets the level number onto it as HTML text (`25`, `100`, `1,0K`) - it is never part of
  the SVG, so number and picture cannot collide and eleven levels do not need eleven files.
* **Imprint** "SALAT · HBT", fine and in the rim's dark colour, along the bottom of the rim; rivets and stars leave that
  arc free.
* **Locked** is a tier of its own in grey; it shows the number of the level still to come.

The medal always sits on a round white disc, in the dark mode too, so that every medal looks the same on every page. This is
the one deliberate exception to Salat's rule that colours derive from `--tblr-*` tokens: the tiers are material colours, and
their contrasts are checked by the build script instead.

## Workflow

1. **Pick the pictogram.** One object for one concept, the object people expect: a flag for the first 100 days, a calendar
   sheet for years with the company, a mortarboard for training. Check the existing ones first - reuse beats a second
   drawing of the same thing:

   | Pictogram | Stands for |
   |---|---|
   | `assets/pictograms/flag.svg` | Die ersten 100 Tage |
   | `assets/pictograms/calendar.svg` | Loyalist, years with the company |
   | `assets/pictograms/mortarboard.svg` | Fortbildung, hours of training |
   | `assets/pictograms/keyboard.svg` | Palette Junkie, calls of the command palette |
   | `assets/pictograms/flask.svg` | Beta-Pionier, betas tried |
   | `assets/pictograms/book.svg` | Fortbildungsjahr, working days of regular training per year |
2. **Draw it** as `assets/pictograms/<name>.svg`, copying an existing one as the start. The format is strict, and the build
   script refuses anything else:
   * `viewBox="0 0 60 42"`, shapes only (`path`, `rect`, `circle`, `ellipse`, `polygon`), no `fill`, `stroke`, `style`,
     `class`, `opacity` or `transform` - colour comes from the tier.
   * Solid, chunky forms. Details are cut out as holes with a second subpath (the build sets `fill-rule="evenodd"`), not
     drawn as thin lines: at 64 px the pictogram is about 28 px wide, and anything thinner than 3 units disappears.
   * Fill the box. A pictogram that uses half of it reads as a dot on the profile page.
3. **Build.**
   `python3 .claude/skills/achievement-medals/scripts/build_medals.py --out src/main/resources/static/images/achievement`
   (`--only <name>` for one pictogram). It checks the contrasts of every tier first and writes
   `medal-<pictogram>-<tier>.svg`. Exit 3 names the pictogram attribute that breaks the format, exit 4 the tier whose
   colours fail - fix the source, never the generated file.
4. **Look at it.**
   `python3 .claude/skills/achievement-medals/scripts/preview.py --medals src/main/resources/static/images/achievement --out <dir>/overview.png "--sample=flag=100;calendar=25"`
   shows every tier of every pictogram; with `--plan <plan.json>` instead it shows the achievements as they will be awarded,
   with their real levels, tiers and level names (format in the script's docstring). Open the PNG and judge every pictogram
   at 64 px in both modes: the object has to be recognizable, and no medal may look like another one. The script checks
   the format, only the eye checks the look.
5. **Wire it up**, if the achievement is new: `references/integration.md` says which pictogram name the achievement
   declares, how a level maps to a tier and which values the page uses to put the number onto the plate.

## References and Scripts

* Run `scripts/build_medals.py` to build and check the medals; it is the only way medal SVGs are written.
* Run `scripts/preview.py` for the overview at 64 and 96 px, light and dark; it needs a Chrome or Chromium and writes the HTML
  anyway when none is found (exit 5).
* Load `references/integration.md` when an achievement gets its medal for the first time, when a level's tier is decided, or
  when the number does not sit on the plate.
* `assets/tiers.json` holds the colours, rivets and stone per tier; `assets/pictograms/` the pictogram sources.

## Safety

* **Never edit a generated `medal-*.svg` by hand.** The next build overwrites it, and the change is gone without a trace;
  change the pictogram or `tiers.json` and build again.
* **No HBT logo, claim or corporate-design icon inside a medal** - only the plain imprint "SALAT · HBT" along the bottom of
  the rim, set as text. The repository is public, and the medals are a game element of the application, not brand
  material.

## Pitfalls

* **Line art does not work here.** The first medals were HBT-style line icons inside a ring: they did not look like an award,
  numbers collided with the drawing, and all medals looked alike. Hence flat material tiers, solid pictograms and the plate.
* **A pictogram with many fine parts turns into a smudge at 64 px** - a keyboard drawn key by key did. Cut few, large holes.
* **`--sample` takes `;` as separator** as soon as a number contains a comma (`keyboard=1,0K`).

## Done When

* [ ] `build_medals.py` exits 0, and every pictogram has one medal per tier in `src/main/resources/static/images/achievement/`.
* [ ] The overview from `preview.py` was looked at: every pictogram recognizable at 64 px in the light and the dark mode, no
  two medals confusable, the number inside the plate.
* [ ] A new achievement declares its pictogram and maps its levels to tiers as `references/integration.md` describes.
