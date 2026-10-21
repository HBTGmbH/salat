# Medals in the application

How the profile page and the popup use the medals the build script writes. Read this when an achievement gets its medal for
the first time, when a level's tier is decided, or when the number does not sit on the plate.

## Files and names

* `src/main/resources/static/images/achievement/medal-<pictogram>-<tier>.svg`, served as
  `/images/achievement/medal-<pictogram>-<tier>.svg`.
* `<pictogram>` is the file name of the source in `assets/pictograms/` (`flag`, `calendar`, `mortarboard`, `keyboard`,
  `flask`, `book`). An achievement declares it in its `AchievementDefinition`; several achievements may share one.
* `<tier>` is one of `bronze`, `silver`, `gold`, `platinum`, `diamond`, `master`, `grandmaster` and `locked`.

## Which tier a level gets

The tier says how far someone has come, so it rises with the level. Achievements that take long climb up to `master` or
`grandmaster`; short ones end at `gold` or `platinum` - the top tiers stay rare on purpose.
The achievement declares the tier per level next to its levels; nothing computes it from the number, because "25 years" and
"25 calls of the palette" are worlds apart. A one-off achievement has a single level and usually `gold`. The next level
still to come is always shown as `locked`.

Example, Loyalist (years): 1 bronze, 5 silver, 10 gold, 15 platinum, 20 diamond, 25 master, 30 and above grandmaster - the
levels beyond the last tier share it and differ by the number only.

## Putting the number onto the plate

The medal fills 94 % of a round white disc; the plate's centre lies at y = 84 of 128 and the plate is 34 high. The page
therefore places the number at these positions - `scripts/preview.py` uses the same constants, change both together:

| Value | Popup and profile (144 px, `achievement-medal-lg`) | Small (64 px) |
|---|---|---|
| Disc | 144 px, white, `border-radius: 50%` | 64 px |
| Medal image | `inset: 3%`, 94 % wide and high | same |
| Number centre | `left: 50%; top: 64.7%`, `transform: translate(-50%, -50%)` | same |
| Number size | 16 % of the disc, about 23 px, bold | about 10 px, bold |
| Number colour | `number` of the tier in `assets/tiers.json`: white, dark on the gold plate of `grandmaster` | same |

Numbers from 1000 on are shortened to one decimal and `K` in the reader's language (`1,0K`, `2,5K`), so that four digits fit
the plate at 64 px.

## Text and accessibility

The SVG carries `role="img"` and a German `aria-label` for itself, but on the pages the medal is decorative: the name and the
level stand as text next to it. Embed it with `alt=""` there, otherwise a screen reader reads the medal and the text twice.
