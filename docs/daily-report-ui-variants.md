# Daily Report Module — UI Mock Variants

Three layout concepts for Epics 1–3. All mockups are schematic; exact dimensions,
colours, and spacing are not implied.

---

## Variant A — Efficiency

**Philosophy:** Maximum information density. Working day metadata, overtime, bookings,
and favourites are co-located on one screen without scrolling. Every feature is at
most one click away. Suited to daily power users who know the system well.

**Stories covered:** 1.1–1.9 · 2.1–2.5 · 3.1–3.4 (all stories including low priority)

---

### A-1 · Daily View

```
┌──────────────────────────────────────────────────────────────────────┬───────────────────┐
│ Daily Report  [◄ D] [◄ W] [Today] [W ►] [D ►]   Mon 02 Jun 2026    │  Overtime         │
│ Employee: [Klaus Richarz                           ▼]  [Month view]  │  Total:  +12h30   │
├────────────────────────────────────────────────────────────────────  │  Month:   +2h00   │
│ Working Day   Start [08:00]  Break [00:30]  Status [WORKED        ▼] │                   │
│ Labor: 5h00   Quit: ~16:00   ✓ within contract  (8h00 required)     │  ── Favourites ── │
├─────────────────────────────────────────────────────────────────────  │                   │
│ [+ Add]  [★ Fav ▼]  [☐ Delete selected]  [↕ Move ▼]               │  ★ Daily Standup   │
├──────┬──────────────────────────┬────────┬──────┬───────────────────  │    STAND  0h30    │
│  ☐   │ Order · Suborder         │  Time  │ Trng │ Comment            │  ★ Alpha Backend  │
├──────┼──────────────────────────┼────────┼──────┼───────────────────  │    DEV    1h00    │
│  ☐   │ ALPHA · DEV-BACKEND      │  3h00  │      │ PROJ-123          │  ★ URLAUB          │
│  ☐   │ ALPHA · DEV-BACKEND      │  1h30  │      │ PROJ-124          │    URLAUB 8h00    │
│  ☐   │ INTERNAL · STANDUP-DAILY │  0h30  │      │ Sprint review     │                   │
├──────┴──────────────────────────┴────────┴──────┴───────────────────  │  [+ Save as fav] │
│ Total: 5h00 / 8h00 contractual                       [Sort ▼]       │  [Manage favs]    │
└──────────────────────────────────────────────────────────────────────┴───────────────────┘
```

- Click any row to edit (1.4). Row checkboxes enable mass delete (1.8) and "↕ Move ▼" ±N days (1.9).
- "★ Fav ▼" opens the favourite list; one click applies a favourite to the current date (1.7).
- Working day auto-derives start and break from booking timestamps when begin/end times are
  present (2.1). Values can be overridden manually.
- Employee switcher is visible to managers and people leads only (5.1).

---

### A-2 · Booking Form (modal)

```
┌──────────────────────────────────────────────────────────────────────────┐
│  New Booking                                                        [✕]  │
├──────────────────────────────────────────────────────────────────────────┤
│  Date       [◄]  [ Mon 02 Jun 2026                         ]  [►]        │
│  Serial     [ None                               ▼]  (skips wknd+hols)  │
│                                                                          │
│  Order      [ ALPHA                                                  ▼]  │
│  Suborder   [ DEV-BACKEND — Backend development                      ▼]  │
│                                                                          │
│  ○ Duration   [ 3 ] h  [ 0 ] min   [± 5 min ▼]                          │
│  ● Begin/End  [ 08:00 ]  –  [ 11:00 ]   → 3h00 derived                 │
│                                                                          │
│  Training   [ ]                                                          │
│  Comment    [ PROJ-123 initial setup                                   ] │
│                                                                          │
│  ── Favourites ──────────────────────────────────────────────────────── │
│  ★ Daily Standup   ★ Alpha Backend   ★ URLAUB   [Manage…]              │
│  (click any favourite to pre-fill all fields)                            │
├──────────────────────────────────────────────────────────────────────────┤
│  [Save as fav]              [Cancel]      [Save & Continue]      [Save]  │
└──────────────────────────────────────────────────────────────────────────┘
```

- Serial booking control is **hidden** when editing an existing booking (1.6 / UX bug fix).
- Duration and begin/end modes are always available — no workingday pre-condition (1.3 PO decision).
- "Save & Continue" clears the comment and serial count, and keeps the form open (1.3).

---

### A-3 · Monthly Matrix

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│  Matrix   [◄ May]   Jun 2026   [Jul ►]   [Today]   [Fill NOT_WORKED]   [CSV ▼]  [🖨] │
│  Show:  [▣ Invoice]  [▣ Non-invoice]  [▣ Start / Break]                              │
├──────────────────────────────┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬────┬─────────┤
│ Order · Suborder             │ 1 │ 2 │ 3 │ 4 │ 5 │ 8 │ 9 │10 │11 │12 │ … │   Σ    │
├──────────────────────────────┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼────┼─────────┤
│ ALPHA · DEV-BACKEND          │ 3 │ 4 │   │   │ 5 │ 3 │   │ 4 │   │   │    │   42h  │
│ ALPHA · DEV-FRONTEND         │   │   │   │ 2 │   │   │   │   │ 3 │   │    │   10h  │
│ INTERNAL · STANDUP-DAILY     │.5 │.5 │   │.5 │.5 │.5 │   │.5 │.5 │.5 │    │    9h  │
├──────────────────────────────┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼────┼─────────┤
│ Day total                    │3.5│4.5│ – │2.5│5.5│3.5│ – │4.5│3.5│.5 │    │   61h  │
└──────────────────────────────┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴────┴─────────┘
```

- Clicking a day cell navigates to the daily view for that date (3.3).
- "Fill NOT_WORKED" bulk-creates NOT_WORKED records for empty regular working days (2.3).
- "CSV ▼" opens a dropdown: Import (add / replace modes) and Export (2.4 / 2.5).
- Column-group toggles show/hide invoice, non-invoice, and start+break columns (3.2).
- "🖨" opens a print-optimised layout (3.4).

---

---

## Variant B — Simplicity

**Philosophy:** Clarity above completeness. Each screen has a single, obvious purpose.
Actions are labelled in plain language. No dense tables or checkboxes. A new user can
navigate the module without a tutorial.

**Stories covered:** 1.1–1.7 · 2.1–2.3 · 3.1 · 3.3 · 3.4
**Omitted (low priority):** 1.8 (mass delete) · 1.9 (move bookings) · 3.2 (booking type filter)
**Omitted (secondary):** 2.4 (CSV import) · 2.5 (CSV export)

---

### B-1 · Daily View

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Monday, 2 June 2026                              │
│              [ ← Previous day ]     [ Next day → ]                 │
│                         [ Today ]                                   │
├─────────────────────────────────────────────────────────────────────┤
│  Start  08:00  │  Break  0h30  │  ● Working day  [Mark not worked]  │
│  Booked 5h00 of 8h00   ██████████░░░░░░░░░░  62 %                  │
├─────────────────────────────────────────────────────────────────────┤
│  [ + Add booking ]                      [ ★ Apply favourite ]       │
├────────────────────────────────────┬──────────┬──────────────────────┤
│  Order · Suborder                  │   Time   │  Comment             │
├────────────────────────────────────┼──────────┼──────────────────────┤
│  ALPHA · DEV-BACKEND               │   3h00   │  PROJ-123            │  [Edit] [Delete]
│  ALPHA · DEV-BACKEND               │   1h30   │  PROJ-124            │  [Edit] [Delete]
│  INTERNAL · STANDUP-DAILY          │   0h30   │  Sprint review       │  [Edit] [Delete]
├────────────────────────────────────┴──────────┴──────────────────────┤
│                            Total: 5h00                               │
│                            [ Month view ]                            │
└─────────────────────────────────────────────────────────────────────┘
```

- Navigation is limited to ± 1 day and "Today"; the month view is a separate screen.
- Working day metadata is always visible; clicking any value opens an inline edit (2.1 / 2.2).
- Delete is always per-row and triggers a confirmation dialog (1.5). No bulk selection.

---

### B-2 · Booking Form

```
┌──────────────────────────────────────────────────────────────────┐
│  Add booking — Monday, 2 June 2026                               │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Project *                                                       │
│  [ ALPHA                                                     ▼]  │
│                                                                  │
│  Task *                                                          │
│  [ DEV-BACKEND — Backend development                         ▼]  │
│                                                                  │
│  Duration *                                                      │
│  [ 3 ] hours  [ 0 ] minutes                                      │
│  Enter begin and end time instead…                               │
│                                                                  │
│  Comment                                                         │
│  [ PROJ-123 initial setup                                      ] │
│                                                                  │
│  [ ] Training                                                    │
│                                                                  │
│  Repeat for multiple working days…                               │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│                  [ Cancel ]               [ Save ]               │
└──────────────────────────────────────────────────────────────────┘
```

- Duration is the primary input; begin/end is accessible via the secondary link (1.3).
- Serial booking is accessible via the secondary "Repeat…" link, not foregrounded (1.6).
- "Apply favourite" on the daily view pre-fills this form (1.7). Saving an existing booking
  as a favourite is a secondary action on the edit form.

---

### B-3 · Monthly Calendar

```
┌──────────────────────────────────────────────────────────────────┐
│             ◄ May             June 2026            July ►        │
│                           [ This month ]                         │
├──────┬──────┬──────┬──────┬──────┬───────────────────────────────┤
│  Mon │  Tue │  Wed │  Thu │  Fri │                               │
├──────┼──────┼──────┼──────┼──────┤  ██  Full day booked          │
│   1  │   2  │   3  │   4  │   5  │  ▓▓  Partial day booked       │
│  ██  │  ██  │      │  ██  │  ██  │  □   Unbooked working day     │
│  8h  │  8h  │      │  8h  │  8h  │  —   Not worked               │
├──────┼──────┼──────┼──────┼──────┤                               │
│   8  │   9  │  10  │  11  │  12  │                               │
│  ██  │      │  ▓▓  │  ██  │  ██  │                               │
│  8h  │  —   │  7h  │  8h  │  8h  │                               │
├──────┼──────┼──────┼──────┼──────┤                               │
│  …   │      │      │      │      │                               │
├──────┴──────┴──────┴──────┴──────┴───────────────────────────────┤
│  Monthly totals:  ALPHA/DEV-BACKEND 42h · INTERNAL/STAND 9h  …   │
├──────────────────────────────────────────────────────────────────┤
│  [ Mark open days as "Not worked" ]                   [ Print ]  │
└──────────────────────────────────────────────────────────────────┘
```

- Clicking a day opens the daily view for that date (3.3).
- Monthly totals per order/suborder are shown below the calendar grid (3.1).
- "Mark open days as Not worked" bulk-creates NOT_WORKED for booking-free working days (2.3).
- "Print" opens a print-optimised layout (3.4).

---

---

## Variant C — Beautiful / Split Views

**Philosophy:** Each domain concern lives in its own dedicated view. Forms slide in
from the side without losing context. Visual status indicators (colour-coded day tiles,
progress bars) make the state of the month readable at a glance. Secondary features are
accessible but not foregrounded. All stories including low-priority ones are reachable.

**Stories covered:** 1.1–1.9 · 2.1–2.5 · 3.1–3.4 (all stories including low priority)

**Navigation:**
```
  [ 📅  Daily ]   [ 📆  Month ]   [ ★  Favourites ]   [ ↑↓  Working Day Data ]
```

---

### C-1 · Daily View

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  Daily Report                                    Klaus Richarz  [ Switch ▼ ]   ║
╠═════════════╦══════════════════════════════════════════════════════════════════╣
║             ║                                                                   ║
║  📅  Daily  ║   Monday, 2 June 2026              [◄ Prev]  [Today]  [Next ►]   ║
║  📆  Month  ║                                                                   ║
║  ★   Favs   ║  ┌─ Working Day ─────────────────────────────────────────────┐   ║
║  ↑↓  Data   ║  │  Start  08:00    Break  0h30    ● Working day            │   ║
║             ║  │  Booked 5h00 / 8h00   ██████████░░░░░░   Quit ~16:00     │   ║
║             ║  └───────────────────────────────────────────────────────────┘   ║
║             ║                                                                   ║
║             ║  ┌─ Bookings ────────────────────────────────────────────────┐   ║
║             ║  │  [ + Add booking ]                  [ ★ Use favourite ]  │   ║
║             ║  │                                                            │   ║
║             ║  │  ┌──────────────────────────────────────────────────────┐ │   ║
║             ║  │  │  ALPHA · DEV-BACKEND                     3h00        │ │   ║
║             ║  │  │  PROJ-123 initial setup                  [✎]  [🗑]  │ │   ║
║             ║  │  └──────────────────────────────────────────────────────┘ │   ║
║             ║  │  ┌──────────────────────────────────────────────────────┐ │   ║
║             ║  │  │  ALPHA · DEV-BACKEND                     1h30        │ │   ║
║             ║  │  │  PROJ-124 refactoring                    [✎]  [🗑]  │ │   ║
║             ║  │  └──────────────────────────────────────────────────────┘ │   ║
║             ║  │  ┌──────────────────────────────────────────────────────┐ │   ║
║             ║  │  │  INTERNAL · STANDUP-DAILY                0h30        │ │   ║
║             ║  │  │  Sprint review                           [✎]  [🗑]  │ │   ║
║             ║  │  └──────────────────────────────────────────────────────┘ │   ║
║             ║  │                                                            │   ║
║             ║  │  [ ☐ Select… ]  →  [ Delete selected ]  [ Move to date ]│   ║
║             ║  └───────────────────────────────────────────────────────────┘   ║
╚═════════════╩══════════════════════════════════════════════════════════════════╝
```

- Left sidebar provides quick navigation between the four primary views.
- "Select…" toggles checkboxes on all cards, enabling mass delete (1.8) and "Move to date"
  with a date picker (1.9).
- Working day card auto-derives start and break from booking timestamps (2.1); values are
  editable inline. "Mark as not worked" appears below the card (2.2).
- Employee switcher in the top-right corner is visible to managers and people leads only (5.1).

---

### C-2 · Booking Form (slide-in panel)

```
                         ┌────────────────────────────────────────────┐
                         │  ← New booking · 2 June 2026               │
                         ├────────────────────────────────────────────┤
                         │  Project *                                  │
                         │  ┌──────────────────────────────────────┐  │
                         │  │ ALPHA                              ▼ │  │
                         │  └──────────────────────────────────────┘  │
                         │                                             │
                         │  Task *                                     │
                         │  ┌──────────────────────────────────────┐  │
                         │  │ DEV-BACKEND — Backend dev.         ▼ │  │
                         │  └──────────────────────────────────────┘  │
                         │                                             │
                         │  Time                                       │
                         │  ○ Duration   ● Begin / End                 │
                         │  ┌──────────┐     ┌──────────┐             │
                         │  │  08:00   │  –  │  11:00   │   → 3h00   │
                         │  └──────────┘     └──────────┘             │
                         │                                             │
                         │  Comment                                    │
                         │  ┌──────────────────────────────────────┐  │
                         │  │ PROJ-123 initial setup               │  │
                         │  └──────────────────────────────────────┘  │
                         │                                             │
                         │  [ ] Training                               │
                         │  Repeat  [ Not repeated              ▼ ]   │
                         │                                             │
                         ├────────────────────────────────────────────┤
                         │  [Save as fav]  [Cancel]  [+Add]  [Save]  │
                         └────────────────────────────────────────────┘
```

- The slide-in panel keeps the daily view visible underneath; no context is lost.
- "Repeat" dropdown for serial booking (1.6) — hidden when editing an existing booking.
- "[+Add]" is "Save & Continue": saves the booking and reopens the panel for the next one (1.3).
- "Save as fav" persists the current form fields as a new favourite (1.7).

---

### C-3 · Monthly Calendar

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  Daily Report  ›  June 2026                [Fill NOT_WORKED]  [CSV ▼]  [Print] ║
╠═════════════╦══════════════════════════════════════════════════════════════════╣
║             ║   ◄ May                     June 2026                    July ►  ║
║  📅  Daily  ║                                                                   ║
║  📆  Month  ║   Mon       Tue       Wed       Thu       Fri                    ║
║  ★   Favs   ║   ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐            ║
║  ↑↓  Data   ║   │   1  │  │   2  │  │   3  │  │   4  │  │   5  │            ║
║             ║   │  ██  │  │  ██  │  │      │  │  ██  │  │  ██  │            ║
║             ║   │  8h  │  │  8h  │  │  —   │  │  8h  │  │  8h  │            ║
║             ║   └──────┘  └──────┘  └──────┘  └──────┘  └──────┘            ║
║             ║   ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐            ║
║             ║   │   8  │  │   9  │  │  10  │  │  11  │  │  12  │            ║
║             ║   │  ██  │  │      │  │  ▓▓  │  │  ██  │  │  ██  │            ║
║             ║   │  8h  │  │  —   │  │  7h  │  │  8h  │  │  8h  │            ║
║             ║   └──────┘  └──────┘  └──────┘  └──────┘  └──────┘            ║
║             ║   …                                                              ║
║             ║                                                                   ║
║             ║   Filter  [ ▣ Invoiceable ]  [ ▣ Non-invoiceable ]              ║
║             ║                                                                   ║
║             ║   ─ Monthly totals ───────────────────────────────────────────   ║
║             ║   ALPHA · DEV-BACKEND          42h                               ║
║             ║   ALPHA · DEV-FRONTEND         10h                               ║
║             ║   INTERNAL · STANDUP-DAILY      9h                               ║
║             ║                                                                   ║
║             ║   ██ Full   ▓▓ Partial   □ Unbooked   — Not worked              ║
╚═════════════╩══════════════════════════════════════════════════════════════════╝
```

- Clicking a day tile opens the daily view for that date (3.3); the sidebar stays consistent.
- Monthly totals per order/suborder are shown below the grid (3.1).
- Invoiceable / non-invoiceable filter toggles update totals in real time (3.2).
- "Fill NOT_WORKED" bulk-creates records for booking-free regular working days (2.3).
- "CSV ▼" opens a dropdown for Import (add / replace) and Export (2.4 / 2.5).
- "Print" opens a print-optimised overlay (3.4).

---

### C-4 · Favourites View

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  Daily Report  ›  Favourites                                                    ║
╠═════════════╦══════════════════════════════════════════════════════════════════╣
║             ║                                                                   ║
║  📅  Daily  ║  ┌──────────────────────────────────────────────────────────┐    ║
║  📆  Month  ║  │ ★  Daily Standup                                         │    ║
║  ★   Favs   ║  │     INTERNAL · STANDUP-DAILY · 0h30                      │    ║
║  ↑↓  Data   ║  │     Sprint review                    [ Apply ]  [ 🗑 ]  │    ║
║             ║  └──────────────────────────────────────────────────────────┘    ║
║             ║  ┌──────────────────────────────────────────────────────────┐    ║
║             ║  │ ★  Alpha Backend                                         │    ║
║             ║  │     ALPHA · DEV-BACKEND · 1h00                           │    ║
║             ║  │     Regular development              [ Apply ]  [ 🗑 ]  │    ║
║             ║  └──────────────────────────────────────────────────────────┘    ║
║             ║  ┌──────────────────────────────────────────────────────────┐    ║
║             ║  │ ★  URLAUB                                                │    ║
║             ║  │     URLAUB · URLAUB · 8h00                               │    ║
║             ║  │     Vacation                         [ Apply ]  [ 🗑 ]  │    ║
║             ║  └──────────────────────────────────────────────────────────┘    ║
║             ║                                                                   ║
║             ║  "Apply" navigates to today's daily view with the booking         ║
║             ║  form pre-filled from the selected favourite.                     ║
╚═════════════╩══════════════════════════════════════════════════════════════════╝
```

- Favourites management lives in its own view rather than inside the booking form (1.7).
  This keeps the booking form focused on a single booking.
- "Apply" navigates to the current day and opens the booking form pre-filled.
- New favourites are created from the booking form via "Save as fav".

---

### C-5 · Working Day Data View (Import / Export)

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  Daily Report  ›  Working Day Data                                              ║
╠═════════════╦══════════════════════════════════════════════════════════════════╣
║             ║                                                                   ║
║  📅  Daily  ║  ┌─ Import ─────────────────────────────────────────────────┐    ║
║  📆  Month  ║  │  Upload a CSV file exported from your clock-in system.   │    ║
║  ★   Favs   ║  │                                                           │    ║
║  ↑↓  Data   ║  │  File     [ Choose file…                               ] │    ║
║             ║  │  Mode     ○ Add (skip existing)  ● Replace (overwrite)   │    ║
║             ║  │                                                           │    ║
║             ║  │                                         [ Import ]       │    ║
║             ║  └──────────────────────────────────────────────────────────┘    ║
║             ║                                                                   ║
║             ║  ┌─ Export ─────────────────────────────────────────────────┐    ║
║             ║  │  Download working day data for a selected period.         │    ║
║             ║  │                                                           │    ║
║             ║  │  Period   [ June 2026                              ▼ ]   │    ║
║             ║  │  Format   CSV (compatible with import format)             │    ║
║             ║  │                                                           │    ║
║             ║  │                                         [ Export ]       │    ║
║             ║  └──────────────────────────────────────────────────────────┘    ║
╚═════════════╩══════════════════════════════════════════════════════════════════╝
```

- Import and export formats are round-trip compatible (2.4 / 2.5).
- Import feedback (records created / updated / skipped) is shown inline after upload.
- Placed in a dedicated view to keep it clearly separate from daily booking workflows.
