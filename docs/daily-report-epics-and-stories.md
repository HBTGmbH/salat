# Daily Report Module — Epics and User Stories

> Requirements captured as epics and user stories. Solutions are intentionally left open
> so that different technical approaches can satisfy the same requirements.

---

## Epic 1: Record Time Bookings

An employee can record, review, edit, and delete time bookings (time reports) for their working days.

---

### Story 1.1 — View my bookings for a day

**As an** employee  
**I want to** see all my time bookings for a specific day  
**So that** I can verify what I have already recorded and spot missing bookings.

**Acceptance criteria:**
- I can navigate to any calendar day.
- I see a list of all my bookings for that day, each showing at least: order/suborder, duration, and comment.
- I can see the total duration of all bookings for the day.
- I can see whether my total booked time exceeds the allowed maximum for a working day.
- I can see whether my total booked time fits my contractual working time for that day.

---

### Story 1.2 — View my bookings for a date range

**As an** employee  
**I want to** view my bookings across a range of days (e.g. a full month or a custom period)  
**So that** I can review my time tracking at a glance and prepare for month-end release.

**Acceptance criteria:**
- I can switch between at least: a single-day view and a full-month view.
- A custom date range view is a low-priority option that may be added later.
- See also Epic 3 for additional monthly overview requirements (gap detection, per-order totals, drill-down).
- Navigation between periods is quick (e.g. previous/next buttons, a "today" shortcut).
- I can see bookings across the selected range in a single view.

---

### Story 1.3 — Create a time booking

**As an** employee  
**I want to** create a new time booking for a day  
**So that** my working time on a project is recorded.

**Acceptance criteria:**
- I can select the date the booking applies to.
- I can select the customer order and suborder I worked on. Only orders I am authorised to book are shown.
- I can record the duration of work in hours and minutes.
- I can optionally enter a begin time and end time instead of a plain duration; the duration is derived automatically.
- I can add a free-text comment describing the work done. The comment may contain plain text, references to issues or tickets from external trackers (e.g. JIRA ticket numbers), or a combination of both.
- I can flag a booking as training time.
- The booking is saved and immediately visible in my booking list.
- I can cancel the form and return to my booking list at any point without saving.
- There is no requirement to reset the form to default values.

---

### Story 1.4 — Edit an existing time booking

**As an** employee  
**I want to** edit a time booking I have already created  
**So that** I can correct mistakes in duration, order, suborder, or comment.

**Acceptance criteria:**
- I can open any of my existing bookings for editing.
- All fields (date, order, suborder, duration, training flag, comment) are editable.
- Changes are saved and reflected immediately in the booking list.
- I can cancel the edit and return to my booking list without saving any changes.
- I cannot edit bookings that I have already released.

---

### Story 1.5 — Delete a time booking

**As an** employee  
**I want to** delete a time booking  
**So that** I can remove incorrectly entered records.

**Acceptance criteria:**
- I can delete any of my own unlocked bookings.
- I am asked to confirm the deletion before it takes effect.
- The booking is immediately removed from the list after deletion.
- Deleted bookings can be recovered in case of accidental deletion (low priority).

---

### Story 1.6 — Repeat a booking across multiple consecutive working days (serial booking)

**As an** employee  
**I want to** create the same booking across multiple consecutive working days in a single action  
**So that** I do not have to enter identical records day by day for holidays, sickness, or recurring meetings.

**Acceptance criteria:**
- I can specify the number of consecutive working days to repeat the booking.
- Weekends and public holidays are automatically skipped.
- Each resulting booking is created independently and can be edited individually afterwards.
- The feature is available but does not dominate the standard single-booking flow.
- Serial booking is only available when creating a new booking; it is not shown when editing an existing one.

---

### Story 1.7 — Re-book quickly using a saved favourite

**As an** employee  
**I want to** apply a saved favourite booking template with a single action  
**So that** I can record my most common recurring bookings (e.g. daily standups, regular project work) without re-selecting order, suborder, duration, and comment each time.

**Acceptance criteria:**
- I can see my list of saved favourites while creating a booking.
- Applying a favourite pre-fills all its fields (order, suborder, duration, comment) for the selected date.
- I can save any existing booking as a new favourite.
- I can delete a favourite I no longer need.
- Favourites are personal and not shared with other employees.

---

### Story 1.8 — Delete multiple bookings at once _(low priority)_

**As an** employee  
**I want to** select and delete multiple bookings in a single action  
**So that** I can efficiently clean up incorrect or duplicate entries across several days.

**Acceptance criteria:**
- I can select multiple bookings from my list.
- A single confirm-and-delete action removes all selected bookings.
- I am asked to confirm the deletion before it takes effect.

---

### Story 1.9 — Move bookings to a different date

**As an** employee  
**I want to** move one or more bookings to a different date  
**So that** I can correct bookings that were entered on the wrong day without deleting and re-creating them.

**Acceptance criteria:**
- I can specify how many days forward or backward the selected bookings should be shifted.
- Bookings that cannot be shifted (e.g. they fall outside my contract validity or a locked period) are not moved, and I receive feedback about which ones failed.
- Successfully moved bookings appear on the new date.

> Note: This is a low-frequency operation and may be placed in a secondary/advanced action area.

---

## Epic 2: Record Working Day Metadata

An employee can record the start time, break duration, and type of each working day, independently of the time bookings themselves.

---

### Story 2.1 — Record my start time and break for a working day

**As an** employee  
**I want to** record when my working day started and how long my break was  
**So that** the system can calculate my quitting time and verify my total daily working time.

**Acceptance criteria:**
- I can enter a start time (HH:MM) for any working day.
- I can enter a break duration (HH:MM) for any working day.
- The system shows me the calculated quitting time and working-day end based on these values and my booked durations.
- I can update start time and break independently, without having to submit a time booking.
- This metadata is accessible from both the booking list view and the monthly overview.
- **When begin and end times are recorded on bookings**, the system should derive working day metadata automatically where possible:
  - The begin time of the first booking of the day is the natural working day start and should be captured or suggested automatically.
  - Time gaps between consecutive bookings represent potential break time; the system should evaluate these gaps and capture or suggest the total break duration automatically.
- Automatically derived values may be overridden manually if they do not reflect reality (e.g. a gap between bookings was not an actual break).

---

### Story 2.2 — Mark a day as not worked

**As an** employee  
**I want to** mark a day as not worked (e.g. holiday, sick leave, part-time day off)  
**So that** the system treats that day correctly for overtime and working-day completeness calculations.

**Acceptance criteria:**
- I can set the working day type (e.g. WORKED, NOT_WORKED) for any day within my contract period.
- Days marked as NOT_WORKED are visually distinguishable from worked days.

---

### Story 2.3 — Bulk-mark open days as not worked (month-end cleanup)

**As an** employee  
**I want to** mark all booking-free regular working days of a month as NOT_WORKED in a single action  
**So that** I can quickly complete the month-end record when I have several days without bookings.

**Acceptance criteria:**
- A single action sets all days in the displayed month that have no bookings and no working-day record to NOT_WORKED.
- Days that already have a record or bookings are not affected.
- I receive confirmation or a count of how many days were updated.
- The action is scoped to my own contract period within the month.

---

### Story 2.4 — Import working day data from an external source

**As an** employee  
**I want to** import my working day start times and break durations from a file exported by my clock-in system  
**So that** I do not have to manually re-enter data I have already recorded elsewhere.

**Acceptance criteria:**
- I can upload a file (CSV format) containing working day metadata.
- I can choose between two import modes:
  - **Add**: only creates records for days that do not yet have a working day entry.
  - **Replace**: overwrites existing working day entries with the imported data.
- I receive feedback on how many records were created or updated.
- Records that cannot be imported (e.g. date outside my contract period) are skipped and reported.

---

### Story 2.5 — Export working day data

**As an** employee  
**I want to** export my working day data (start times, break durations, day types) to a file  
**So that** I can use it in external tools, keep a personal record, or hand it over to payroll.

**Acceptance criteria:**
- I can export working day data for a selected period (e.g. a calendar month) in CSV format.
- The export covers my own records only.
- The exported format is compatible with the import format described in Story 2.4.

---

## Epic 3: Monthly Overview

> **Overlap note:** Story 1.2 already covers viewing bookings for a full month. Epic 3 is additive: it addresses the need for an overview format that goes beyond a plain booking list — emphasising at-a-glance gap detection, totals per order across the month, and direct drill-down navigation to a specific day. Whether this is a separate screen or an alternative display mode of the monthly view from Story 1.2 is a design decision.

An employee (and managers for other employees) can view a compact monthly summary of all bookings and navigate by day. The specific display format (e.g. matrix/grid, calendar, list grouped by day) is intentionally left open — a matrix is one option but not the only valid solution.

---

### Story 3.1 — View a monthly booking overview

**As an** employee  
**I want to** see my bookings for a full calendar month in a single overview  
**So that** I can quickly identify unbooked days, check per-day totals, and review distribution across orders.

**Acceptance criteria:**
- I can see all my bookings for a full calendar month in one view.
- I can tell at a glance which days have bookings and which are empty.
- I can see the total booked hours per day.
- I can see the total booked hours per order or suborder for the month.
- I can navigate to the previous and next month.
- I can jump directly to the current month.

---

### Story 3.2 — Filter the monthly overview by booking type _(low priority)_

**As an** employee  
**I want to** filter the monthly overview to show only invoiceable, only non-invoiceable, or all bookings  
**So that** I can focus on the subset relevant to my current review.

**Acceptance criteria:**
- I can toggle visibility of invoiceable and non-invoiceable bookings independently.
- Totals update to reflect the active filter.

---

### Story 3.3 — Navigate from the monthly overview to the daily view

**As an** employee  
**I want to** select a specific day in the monthly overview and go directly to the detailed daily view for that day  
**So that** I can quickly drill down from the overview to add or correct bookings for a specific day.

**Acceptance criteria:**
- I can select any day in the monthly overview to open the daily booking list for that date.
- I can return to the monthly overview afterwards.

---

### Story 3.4 — Print the monthly overview

**As an** employee  
**I want to** print the monthly booking overview  
**So that** I can provide a paper copy for approval or personal records.

**Acceptance criteria:**
- A print-optimised view of the monthly overview is available.
- The printout contains my name, the month, bookings grouped by order/suborder, daily totals, and monthly totals.

---

## Epic 4: Overtime and Vacation Balance

> **Superseded.** This functionality is already provided by `/my-accounts`. No new implementation required as part of the daily report migration.

~~An employee can see their current overtime balance and remaining vacation entitlement to understand whether they are ahead or behind their contractual working time.~~

---

### Story 4.1 — View my current overtime balance

**As an** employee  
**I want to** see my accumulated overtime  
**So that** I know whether I am working more or less than my contract requires.

**Acceptance criteria:**
- I can see my total overtime balance (positive = extra hours worked, negative = hours owed).
- I can see the overtime balance for the current calendar month separately.
- Both values are clearly labelled and colour-coded (e.g. red for negative).
- The balance is visible on the daily overview without requiring a separate page visit.

---

### Story 4.2 — View my overtime balance up to a specific date

**As an** employee  
**I want to** see what my overtime balance would be as of a particular date  
**So that** I can plan ahead or verify the balance at the end of a reporting period.

**Acceptance criteria:**
- I can request the overtime balance calculated up to a chosen end date rather than today.
- The result is shown alongside the regular balance.

---

## Epic 5: Oversight for Managers and People Leads

> **Cross-cutting concern.** Epic 5 does not introduce standalone screens. It extends the functionality defined in Epics 1–4 by allowing a manager or people lead to perform those same actions in the context of another employee. Any story from Epics 1–4 that a manager or people lead needs to execute on behalf of an employee is in scope here. The employee-switching mechanism is the single enabling capability; everything else reuses existing screens and flows.

A manager or people lead can switch into any employee's context and perform time reporting actions — viewing, creating, editing, deleting bookings; managing working day metadata; viewing the monthly overview — on behalf of that employee or for oversight purposes.

---

### Story 5.1 — Switch to another employee's context

**As a** manager or people lead  
**I want to** switch to any employee's context within my area of responsibility  
**So that** I can perform time reporting actions on their behalf or review their records.

**Acceptance criteria:**
- I can select any employee within my area of responsibility from a switcher available on all daily report screens.
- All screens and actions from Epics 1–3 function identically in the switched context, with data scoped to the selected employee.
- Records created or modified while in a switched context are attributed to the selected employee, not to me.
- Records I create or modify on behalf of an employee are marked with my identity as the creator/modifier, so it is visible that a manager, supervisor, or people lead made the change rather than the employee themselves.
- The indication specifically reflects who last changed the booking's content (order, suborder, duration, comment) — not who last changed its status (e.g. released it). Status transitions must not overwrite the content-change author. This may require a dedicated audit field or separate tracking for content changes vs. state changes.
- I can return to my own context at any time without losing my place.
- The set of employees I can switch to is determined by my role: managers can switch to any employee; people leads can switch to the members of their team only.
