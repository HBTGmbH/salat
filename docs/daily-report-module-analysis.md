# Daily Report Module — Functional Analysis

> Basis for planning the migration from Struts/JSP to Spring MVC + Thymeleaf.

---

## Core Domain Concepts

| Entity | Role |
|---|---|
| **Timereport** | A single booking: employee × date × suborder × duration × comment |
| **Workingday** | Work day metadata: start time, break duration, day type (WORKED / NOT_WORKED / …) |
| **Customerorder / Suborder** | Two-level booking hierarchy: client → project line item |
| **Employeecontract** | Ties an employee to contractual working time; scopes which orders are bookable |
| **Employeeorder** | Links a specific employee contract to a specific suborder (who can book what) |

---

## Connected Actions

| URL | Action Class | JSP | Purpose |
|---|---|---|---|
| `/do/ShowDailyReport` | `ShowDailyReportAction` | `showDailyReport.jsp` | Main daily overview and working day management |
| `/do/CreateDailyReport` | `CreateDailyReportAction` | `addDailyReport.jsp` | New time report form |
| `/do/EditDailyReport` | `EditDailyReportAction` | `addDailyReport.jsp` | Edit existing time report form |
| `/do/StoreDailyReport` | `StoreDailyReportAction` | (form action target) | Save/update time report and form interactions |
| `/do/ShowMatrix` | `ShowMatrixAction` | `showMatrix.jsp` | Monthly matrix overview |
| `/do/UpdateDailyReport` | `UpdateDailyReportAction` | — | Quick inline edit from the daily view |
| `/do/DeleteTimereportFromDailyDisplay` | `DeleteTimereportFromDailyDisplayAction` | — | Delete with confirmation from the daily view |

---

## Screen 1 — `/do/ShowDailyReport` (Daily Overview)

**Purpose:** The central hub for time tracking. An employee opens this page to view and manage bookings for a selected date or date range.

### Date navigation

Three view modes:
- **Daily** — one specific day (primary/default)
- **Monthly** — full calendar month
- **Custom** — user-defined date range

Navigation controls: ±1 day, ±7 days, ±1 month, jump to today.

### Working day metadata

Fields: **start-of-day time** (HH:MM), **break duration** (HH:MM), **working day type** (WORKED / NOT_WORKED / …).  
Stored as a `Workingday` entity, separate from `Timereport` bookings.  
Currently embedded directly on the page — acknowledged as a poor placement; should be a clearly separated component in the new UI.

Derived/calculated values shown alongside:
- Labor time total (sum of all report durations for the day)
- Max daily working time warning
- Quitting time (start + bookings + break)
- Working day end estimate

### Overtime display

- Total accumulated overtime (all time up to today)
- Current month overtime
- Negative indicator for both

### Bookings table

Columns: date, customer order sign, suborder sign, duration, training flag, comment.  
Sortable by: employee / date / order (toggle ascending/descending).  
Per-row actions: **edit** (→ EditDailyReport), **delete** (with confirmation).

### Multi-select actions

- **Mass delete** — delete multiple selected reports at once
- **Mass shift days** — move selected reports ±N days; guards against invalid dates; **rarely used in practice**, suitable for a secondary/advanced placement

### Favorites

A list of per-employee saved booking templates (order + suborder + duration + comment).  
**Critical feature — used daily by many employees.**  
Actions:
- Apply a favorite as a new report for the currently displayed date (one click)
- Create a favorite from an existing time report
- Delete a favorite

### Employee switcher (managers)

A dropdown that re-renders the entire view for the selected employee's contract.  
One employee at a time; no side-by-side comparison needed.

### Print view

Separate printable layout for the displayed date range.

---

## Screen 2 — `/do/CreateDailyReport` → form POSTs to `/do/StoreDailyReport`

**Purpose:** Enter a new time report. Rendered by `addDailyReport.jsp`.

### Form fields

| Field | Notes |
|---|---|
| Employee | Dropdown; managers may book for others |
| Reference date | Date picker + ±1/±7 day navigation arrows |
| Serial booking | Repeat the same booking across N consecutive working days |
| Customer order | Dropdown, filtered to orders with valid employee orders for the contract/date |
| Suborder | Cascades from order; shown as both sign and description dropdowns |
| Start of work day | HH:MM; only shown when a `Workingday` record exists for today |
| Begin / End time | Optional HH:MM; only shown when `workingDayIsAvailable` (= today + workingday present) |
| Duration | HH:MM; automatically derived from begin/end, or entered directly |
| "Show all minutes" | Toggle between 5-minute increments and 1-minute granularity |
| Training flag | Shown conditionally; auto-checked when suborder carries `trainingFlag = true` |
| Comment | Free-text textarea; employees describe what was done and often include issue tracker references (e.g. JIRA ticket numbers) |

### Smart pre-fill behaviour

- Begin time defaults to the end time of the last existing report of the day (chaining consecutive bookings)
- End time defaults to current time rounded to the nearest quarter hour, if reporting for today
- Duration pre-fills with the employee's daily working time if the selected order is a "standard order" (e.g. URLAUB)

### Time entry modes

Two valid ways to enter time — **both must be supported**:
- **Duration** (HH:MM) — preferred for end-of-day entry and for booking past days
- **Begin + End** (HH:MM each, duration auto-computed) — preferred for live tracking during the day

In the legacy UI, begin/end is only offered when a `Workingday` record exists for today. This conditionality is a known limitation.

### Serial booking

Repeat the same booking across N consecutive working days. Skips weekends and public holidays.  
Common use cases: holidays, sickness, recurring meetings, pre-booking project time.  
**Occasional feature** — suitable as an expandable/secondary option in the new UI.

### Buttons

- **Save** — save and return to the daily overview
- **Save & Continue** — save, clear comment and serial count, stay on the form for the next booking
- **Cancel** — discard and return to the daily overview (replaces legacy "Back" button; "Reset" button is removed)

### Sub-tasks handled by `StoreDailyReportAction` (form interactions)

| `task` parameter | Effect |
|---|---|
| `setDate` | Navigate reference date ±N days within the form |
| `toggleShowAllMinutes` | Switch minute dropdown between 5-min and 1-min options |
| `refreshOrders` | Reload order dropdown when employee changes |
| `refreshSuborders` | Reload suborder dropdown when order changes |
| `adjustBeginTime` | Re-derive begin/end times when order or date changes |
| `adjustSuborderSignChanged` | Sync sign ↔ description select menus |
| `saveBeginOfWorkingDay` | Persist working day start time without saving the report |
| `refreshHours` | Recalculate duration from begin/end |
| `refreshPeriod` | Recalculate begin/end from duration |
| `save` | Persist the time report (create or update depending on `id`) |
| `back` | Discard and go back |
| `reset` | Reset form to defaults |

---

## Screen 3 — `/do/EditDailyReport` → same form

**Purpose:** Edit an existing time report. Renders the same `addDailyReport.jsp`, pre-populated.

### Differences from Create

- Form `id` field is non-zero → `StoreDailyReport` calls `updateTimereport` instead of `createTimereports`
- Begin/End time fields are hidden (`workingDayIsAvailable = false` for edits)
- Duration is pre-populated from the existing report
- Serial booking control is still rendered but not functional for edits — **known UX bug; the control must be hidden on the edit form**

---

## Screen 4 — `/do/ShowMatrix` (Monthly Matrix View)

**Purpose:** Calendar grid for one month: rows = customer orders/suborders, columns = days. Good for a month-end review, spotting unboooked days, and checking totals per order and per day.

### Display

- Month navigation (prev / next / jump to today)
- Matrix cells: hours booked per order per day
- Column totals: total hours per calendar day
- Row totals: total hours per suborder for the month
- Optional column groups: **invoice hours**, **non-invoice hours**, **start & break time**

### Employee switcher

Same as daily view; managers only.

### Drill-down

Clicking a day cell navigates to `/do/ShowDailyReport` for that date.

### Fill NOT_WORKED

Bulk-creates `Workingday` records with type `NOT_WORKED` for all booking-free regular working days in the displayed month.  
**Used at month-end cleanup** when an employee forgot to flag days they didn't work.  
Should be accessible (one button) but not visually prominent.

### CSV import and export (Workingday data)

Transfers **working day metadata** (start time, break duration, day type) between the system and a CSV file — not timereport bookings.  
**Used by individual employees** who record clock-in data in a separate tool.

Import dialog offers two modes:
- `add` — appends records that don't yet exist
- `replace` — overwrites existing records

Export allows downloading the same data as a CSV for use in external tools or personal records.  
The import and export formats are compatible (round-trip).

Secondary feature; keep as an import/export action on the matrix view.

### Print view

Separate printable layout for the displayed month.

---

## Product Owner Inputs (collected 2026-06-01)

| Topic | Decision |
|---|---|
| Time entry modes | Both duration and begin/end must be supported; begin/end should not require a workingday pre-condition. When begin/end times are present, working day start and break duration can be derived automatically: start = begin of first booking, break = sum of gaps between consecutive bookings. |
| Working day data placement | Should be accessible on every daily-context screen, not embedded inside the booking form |
| Serial booking | Occasional — keep but as secondary/expandable affordance |
| Manager use case | Review one employee at a time via a switcher |
| Mass shift days | Rarely used — deprioritize (secondary/overflow action) |
| Fill NOT_WORKED | Month-end cleanup — keep accessible, not prominent |
| CSV import/export | Individual employees transferring clock-in data — keep as secondary import/export action on matrix; formats must be compatible |
| Favorites | **Critical** — must remain a first-class, prominent feature |
| Audit trail | `lastupdatedby` on `Timereport` is updated by both content changes (order, duration, comment) and status changes (release). These two concerns must be tracked separately in the new model so the UI can accurately indicate who last changed the booking content vs. who changed its status. |
