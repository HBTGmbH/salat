# Fachliche Regeln

Teil der Entwicklungsregeln: Kern und Verweistabelle stehen in [AGENTS.md](../../AGENTS.md), was hier
steht, gilt ebenso verbindlich. Lesen, bevor eine Änderung Stammdaten, das Ausblenden, Gültigkeitszeiträume
oder das Budget berührt.

---

## Entity Classification: Stammdaten vs. Bewegungsdaten
Entities are divided into two categories (→ ADR-0011):

**Stammdaten (Master Data)** — reference/configuration entities with long lifetimes; rarely changed; referenced by transactional data. Deactivation via `hide`-flag, `enabled`-flag, or validity range rather than deletion.

**Bewegungsdaten (Transactional Data)** — business-event records; append-only or near-immutable after creation; reference master data; may use soft-delete.

| Entity | Type | `hide` / deactivation |
|---|---|---|
| `Customer` | Stammdaten | `hide` |
| `Employee` | Stammdaten | `hide` |
| `Employeecontract` | Stammdaten | `hide` + `validFrom`/`validUntil` |
| `Customerorder` | Stammdaten | `hide` + `fromDate`/`untilDate` |
| `Suborder` | Stammdaten | `hide` + `fromDate`/`untilDate` |
| `Employeeorder` | Stammdaten | inherits `hide` from parent |
| `Publicholiday` | Stammdaten | — (calendar fact) |
| `Referenceday` | Stammdaten | — (calendar reference) |
| `SalatUser` | Stammdaten | `Employee.hide` |
| `AuthorizationRule` | Stammdaten | `validFrom`/`validUntil` |
| `ETLDefinition` | Stammdaten | — |
| `JiraReplicationConfig` | Stammdaten | `enabled` |
| `ReportDefinition` | Stammdaten | `hide` (proposed — not yet implemented) |
| `ScheduledReportJob` | Stammdaten | `enabled` |
| `OrderRevenueExcelMapping` | Stammdaten | — |
| `OrderFlatRate` | Stammdaten | `validFrom`/`validUntil` |
| `OrderFlatRateInstalment` | Stammdaten | inherits the validity of its `OrderFlatRate` |
| `OrderBudgetCalculation` | Stammdaten | goes with its `OrderBudget` |
| `Secret` | Stammdaten | `status` instead of `hide`; deleted for good, with its owner (ADR-0038) |
| `Timereport` | Bewegungsdaten | soft-delete (`deleted` + `@SQLRestriction`) |
| `TimereportBudgetAssignment` | Bewegungsdaten | — (gelöst oder gelöscht) |
| `Workingday` | Bewegungsdaten | — |
| `Overtime` | Bewegungsdaten | — |
| `OrderRevenue` | Bewegungsdaten | — |
| `ETLExecutionHistory` | Bewegungsdaten | — |
| `ScheduledReportExecutionHistory` | Bewegungsdaten | — |
| `StatisticValue` | Bewegungsdaten | — |
| `JiraTicket` | Bewegungsdaten | — |
| `JiraTicketImport` | Bewegungsdaten | — (geht mit seinem Geltungsbereich) |
| `JiraWorklogSync` | Bewegungsdaten | — (gelöscht, sobald das Worklog in JIRA gelöscht wird) |
| `Favorite` | Bewegungsdaten | — |
| `BetaUsage` | Bewegungsdaten | — (gelöscht mit dem Ende der Beta) |
| `BetaParticipation` | Bewegungsdaten | — (gelöscht mit dem Ende der Beta) |
| `BetaFeedback` | Bewegungsdaten | — (ohne Person und ohne `AuditedEntity`, ADR-0039; gelöscht mit dem Ende der Beta) |

---

## The `hide` Flag (UX Declutter)
The `hide` boolean flag is a UX feature (→ ADR-0012): it removes an entity from all dropdown select inputs in forms without deleting it. Hidden records remain in the database and in list management views, but are suppressed everywhere a user picks from a list.

Rules:
- Entities with a `hide` flag: `Customer`, `Customerorder`, `Suborder`, `Employee`, `Employeecontract`.
- `Employeeorder` has no own `hide` — it inherits visibility from its parent `Suborder` and `Customerorder`.
- All service/DAO methods that populate dropdowns must exclude hidden records by default (apply `notHidden()` spec or equivalent).
- The list management view exposes a “Show hidden” toggle so managers can still see and edit hidden records.

**Die Spalte ist nullbar, und `NULL` heißt nicht verborgen** (#1104). `hide` hat drei Zustände —
`true`, `false` und „nie geschrieben" —, und der dritte ist der, an dem die Frage auseinanderläuft:
in SQL ist `hide <> 1` für `NULL` *unbekannt* statt wahr, die Zeile fällt also aus dem Ergebnis,
obwohl niemand sie verborgen hat. Die Detailansicht desselben Datensatzes zeigte ihn weiter als
nicht verborgen.

**Die Regel steht genau einmal im Code: `de.hbt.salat.common.Hiding`** — beide Seiten nebeneinander, wie
`Validity` es für den Gültigkeitszeitraum tut:

```java
// in Java, auf der Entität
public Boolean getHide() {
    return Hiding.isHidden(hide);
}

// in der Abfrage, im DAO
private Specification<Suborder> notHidden() {
    return Hiding.notHidden(Suborder_.hide);
}

// wo das Flag über einen Join kommt (Employeeorder hat kein eigenes)
Hiding.notHidden(builder, root.join(Employeeorder_.suborder).get(Suborder_.hide))
```

- **`notEqual(hide, TRUE)` und `hide != true` sind Fehler**, gleichgültig ob als Specification oder
  als JPQL. Beide Schreibweisen verwerfen die `NULL`-Zeile.
- **In JPQL muss die Schreibweise wiederholt werden**, weil ein `@Query` nur eine Zeichenkette ist
  und `Hiding` nicht aufrufen kann. Es gibt dafür genau eine Schreibweise, und das ist diese:
  `(x.hide is null or x.hide = false)`. Sie hält zu `Hiding.notHidden` Schritt.
- Eine Datenmigration auf `NOT NULL DEFAULT false` ist **nicht** erfolgt und wäre ein eigenes
  Ticket: der Bestand trägt 25 solcher Zeilen in `suborder`, alle aus 2006/2007, alle abgelaufen und
  unter verborgenen Kundenaufträgen.

**The one exception: the record a select already stores** (→ #1005). A select box that carries an
already stored reference must offer the stored record even when it is hidden. Hiding declutters the
choice of something *new*; it must never make an existing record uneditable, and it must never
silently rewrite one. A select whose stored value is missing from its options cannot mark anything,
so the browser preselects the first option — and that value is what a save writes back. That is how a
hidden customer came to be displayed as a different customer, and how a hidden parent suborder moved
its whole subtree to the top level.

- Build such a list from a `getSelectable…(keep…)` method of the **owning** service —
  `CustomerService.getSelectableCustomers(keepId)`,
  `CustomerorderService.getSelectableCustomerorders(keepSign)`,
  `SuborderService.getSelectableSubordersByCustomerorderId(id, keep…)`,
  `EmployeeService.getSelectableEmployees(keepId)`. Not an ad-hoc “add it back if absent” block in
  the controller: the rule then lives in as many places as there are forms.
- Where a stored value can be filtered out for reasons **other** than `hide` — expired validity,
  authorization — a `getSelectable…` method is not enough, because it only knows `hide`. Those places
  keep their own fallback (`CustomerorderController.addFormModel`,
  `EmployeeorderController.addFormModel`); do not "unify" them away.
- Mark such an entry with `${@hiddenMarkerViewHelper.suffix(x.hide)}` appended to the option text
  (`common/viewhelper/HiddenMarkerViewHelper`, message key `main.general.hidden.suffix`). An entry
  that is only in the list because the record stores it has to say so, otherwise the form claims it is
  available for picking.

---

## Gültigkeitszeiträume: aktiv und inaktiv (→ ADR-0029)

Viele Entitäten tragen einen Gültigkeitszeitraum (`fromDate`/`untilDate` bzw.
`validFrom`/`validUntil`). Für sie alle ist „aktiv" und „inaktiv" **zeitlich** definiert, und zwar
allein über das **Ende**:

- **inaktiv** = der Gültigkeitszeitraum liegt **vollständig in der Vergangenheit**, das Ende liegt
  also vor dem heutigen Tag.
- Ein Ende **am heutigen Tag** ist noch aktiv — der Vergleich ist **einschließend** (`>= heute`).
- Ein **offenes Ende** ist nie inaktiv, gleichgültig ob es als `null` oder als Sentinel `2999-12-31`
  (`LocalDateRange.FINIT_UNTIL_BOUNDARY`) abgelegt ist. Der Sentinel braucht keinen eigenen Fall:
  kein wirklicher Tag liegt danach, also kann er nie vor heute liegen.
- Ein **Beginn in der Zukunft** ist **nicht** inaktiv, sondern noch nicht aktiv. Der Beginn gehört
  deshalb nicht in das Prädikat. Solche Datensätze bleiben sichtbar.

Warum nur das Ende zählt, steht in [ADR-0029](../adr/0029-inaktiv-ist-zeitlich-und-zaehlt-nur-das-ende.md)
samt der verworfenen Alternative.

**Die Regel steht genau einmal im Code: `de.hbt.salat.common.Validity`.** Beide Seiten derselben Frage
liegen dort nebeneinander:

```java
// in Java, auf der Entität
public boolean getCurrentlyValid() {
    return !Validity.isInactive(untilDate);
}

// in der Abfrage, im DAO
if (!TRUE.equals(showInactive)) {
    predicates.add(Validity.<Suborder>notInactive(Suborder_.untilDate).toPredicate(root, query, builder));
}
```

Eine fünfte Kopie des Prädikats ist damit ein Fehler. Wer eine weitere Entität mit
Gültigkeitszeitraum anlegt, ruft `Validity`, statt `untilDate >= today` noch einmal zu schreiben.

**Nicht jede Liste ist eine Auswahlliste (#1106).** Ob das Zeitkriterium überhaupt gilt, entscheidet
die Frage, die die Liste beantwortet:

- **Auswahl für Neues** — ein Auswahlfeld eines Formulars: worauf darf jetzt gebucht, was darf jetzt
  angelegt oder zugeordnet werden. Ein abgelaufener Datensatz ist darauf keine Antwort mehr, also
  `notHidden()` **und** `Validity.notInactive(...)`. Das ist der Normalfall, und das ist, was
  `CustomerorderService.getVisibleCustomerorders()` liefert.
- **Filter über Vorhandenes** — die Auswahl über einer Liste bereits erfasster Daten, etwa die
  Auftragsauswahl der Buchungsliste. Sie schränkt ein, was es schon gibt, und Vergangenes läuft nicht
  mit seinem Stammdatensatz ab: eine Buchung auf einem abgelaufenen Auftrag bleibt eine Buchung, die
  man über ihren Auftrag sucht. Hier gilt **nur** `notHidden()` —
  `CustomerorderService.getNotHiddenCustomerorders()`, `SuborderService.getNotHiddenSuborders()`.

`hide` gilt in **beiden** Fällen: es ist eine Entscheidung über das Anbieten, nicht über die Zeit.
Eine Methode darf nicht beide Fragen bedienen — die Methode heißt nach dem, was sie liefert, und wer
eine weitere Filterliste baut, ruft die passende, statt einer bestehenden einen Filter abzuhängen.

**Davon zu unterscheiden, und ausdrücklich nicht dasselbe:**

- **`hide`** — die manuelle Entscheidung, einen Datensatz aus Auswahllisten zu nehmen, unabhängig
  von jedem Datum (siehe „The `hide` Flag", → ADR-0012). `notHidden()` und `Validity.notInactive(...)` sind zwei
  Prädikate und werden über zwei Schalter zugeschaltet; sie gehören nie in eine Bedingung. Ein `or`
  zwischen ihnen macht beide wirkungslos.
- **Explizite Boolean-Flags** wie `OrderBudget.active`, `JiraReplicationConfig.enabled`,
  `ScheduledReportJob.enabled`. Sie heißen ebenfalls „aktiv", meinen aber eine gesetzte Entscheidung,
  kein Datum. Wo beides auf derselben Entität existiert — `OrderBudget` hat `active` **und**
  `validFrom`/`validUntil` —, sind es zwei unabhängige Kriterien und sie dürfen nicht in einem Filter
  vermischt werden. `BudgetController.list` meint mit `showInactive` heute das **Flag**; das ist
  richtig so und sagt nichts über den Zeitraum.
- **„Gilt am Tag X"** — eine Stichtagsfrage, die den Beginn **mitprüft**
  (`Suborder.isValidAt(date)`, `Employeeorder.isValidAt(date)`,
  `Validity.isInactiveOn(untilDate, date)` prüft ihn gerade nicht). Sie ist für
  Geschäftsregeln richtig („darf an diesem Tag gebucht werden") und als Aktiv-Filter falsch. Ein
  `isValidAt` hinter einem Schalter namens `showInactive` ist ein Fehler.

Entitäten mit Gültigkeitszeitraum: `Customerorder`, `Suborder`, `Employeeorder`,
`Employeecontract`, `OrderBudget`, `OrderPricing`, `OrderFlatRate` (+ `OrderFlatRateInstalment`),
`EmployeeCost`, `EmployeeCostAssignment`, `AuthorizationRule`.

Zwei Eigenheiten, die die Erhebung zu #950 zutage gefördert hat und die man kennen muss:

- **Wo das offene Ende als `null` steht und wo als Sentinel, ist nicht einheitlich.** `null` im
  Auftrags- und Vertragsbereich, Sentinel `2999-12-31` bei `order_pricing`, `order_budget`,
  `employee_cost` und `employee_cost_employee` (Spaltenvorgabe im Liquibase-Changelog, `NOT NULL`).
  `Validity` behandelt beide gleich, sonst nichts — eine Ablösung des Sentinels wäre eine
  Datenmigration.
- **Die Pauschale kennt kein offenes Ende.** Bei `OrderFlatRate` heißt ein fehlendes „bis" „noch
  nicht eingegeben", nicht „läuft weiter" (→ Konditionen an einem Budgetplan).

---

## Revenue in the Budget Module
An order earns from two sources, and they add up (#972):

- **Hourly** — a time report priced with the `OrderPricing` rate that matches its suborder, employee, budget plan and date. No booking, no revenue.
- **Flat rate** — an `OrderFlatRate` amount falling due on a date, regardless of any booking: a maintenance retainer, an initial fee, the instalments of a fixed price order. Several definitions per order are normal and add up; there is deliberately no overlap rule.

Rules that follow from this:
- `BudgetControllingRow.revenueEuro` is the **hourly** part only. Every figure derived from revenue — budget utilization, overrun, gross profit, margin — must read `totalRevenueEuro()`, which is the sum of both. Reading `revenueEuro` for those would silently drop the flat rates.
- A flat rate schedule is derived in exactly one place, `OrderFlatRate.dueAmountsWithin`. The form preview and the controlling both call it, so a rate cannot be previewed as one calendar and evaluated as another.
- Which plan a flat rate amount counts against follows `FlatRateAllocation.uniquePlanFor`: the plan the flat rate **names**, or — where it names none — the one active plan whose period contains the due date and whose scope covers it. Where several plans qualify and none is named, none is chosen; the amount is reported as being without a budget, exactly as an ambiguous booking is. Never guess a plan; double counting and silent reassignment are both worse than an explicit "without budget". A named plan is not weighed against period and scope again — the saving did that (→ Konditionen an einem Budgetplan). It holds its amounts even while it is **deactivated**: in the controlling they follow the plan into its section just as its bookings do (#1217); dashboard and alerts look at active plans only. The derivation, by contrast, only ever picks an active plan.
- The unit of allocation is a single due amount, not the definition: a monthly rate spanning two plans has each month counted against the plan it falls into.
- **Dashboard and alerts end their window today**, never at the plan's own end (`BudgetControllingService.evaluatedUntil`). They answer "where does this plan stand", which is a question about the present; reading a plan to its end counted what has not happened yet — a monthly flat rate running to December contributed all twelve months in June. The cut applies to the budget as well: an adjustment taking effect in November has not been granted yet. With both ends cut, a dashboard row says exactly what a controlling evaluation up to today says, and the row links to that window rather than to a wider one. The controlling view itself keeps its explicit `from`/`until` filter and is not capped.

---

## Konditionen an einem Budgetplan (#1065)

Kundenstundensatz und Pauschale können **zusätzlich** einem Budgetplan zugeordnet werden. Das Feld
ist optional, alle Bestandsdatensätze sind planlos, und ohne Plan verhält sich alles wie zuvor. Die
Wirkung ist auf beiden Seiten eine andere, und das ist beabsichtigt:

- **Beim Stundensatz ist der Plan eine Rangstufe, kein Schalter.** `OrderPricingLookup` prüft ihn an
  *beiden* Stellen: `Candidate.covers` lässt einen plangebundenen Satz nur für seinen Plan zu,
  `bySpecificity` ordnet ihn über den planlosen. Nur als Filter gebaut ließe er fremde Pläne
  gewinnen; nur als Rang gebaut bepreiste er jede Buchung. Die Reihenfolge lautet **Person, dann
  Plan, dann Musterlänge, dann id** — die Stufe sitzt unter dem Mitarbeitendenbezug und über dem
  Unterauftrag, und das ist die einzige Einfügung, die bestehende Zahlen nicht verändert.
- **Bei der Pauschale nagelt der Plan eine hergeleitete Zuordnung fest** (siehe oben).
- **Die Plan-id der Buchung kommt aus der gespeicherten Zuordnung**, nie aus einer Herleitung:
  `BudgetControllingService.scoreReports` reicht `planOfBooking` durch, `BudgetEmployeeService` die
  id des Plans, dessen Seite es rendert. Es gibt keine zusätzliche Abfrage je Buchung — die Plan-id
  wandert in den `MemoKey` des Lookups.
- **`OrderPricing.isOrderWide` verlangt zusätzlich, dass kein Plan gesetzt ist.** Ein plangebundener
  Satz bepreist nur die Buchungen seines Plans, deckt den Auftragszeitraum also nicht ab und darf
  die Lückenprüfung `hasUncoveredPeriod` nicht befriedigen.
- **`findOverlapping` führt den Plan im Schlüssel.** Zwei Sätze mit gleichem Auftrag, Muster und
  Kürzel, aber verschiedenen Plänen sind keine Überlappung — ein plangebundener Satz neben dem
  planlosen, den er verengt, ist der Zweck der Stufe.
- **Welche Pläne zur Auswahl stehen, entscheidet `OrderBudgetBinding`**, und zwar für Auswahl *und*
  Speichern: gleicher Auftrag, sich schneidende Geltungsbereiche, sich überschneidende
  Gültigkeiten. Eine nachweislich tote Kombination wird abgewiesen, nicht nur ausgeblendet
  (`BU-0028`, `BU-0029`). Zwei Fälle antworten ohne einen einzigen Unterauftrag: ein auftragsweiter
  Satz trifft jeden Plan seines Auftrags, ein auftragsweiter Plan jeden Satz — sonst scheiterte die
  Prüfung an einem Auftrag ohne (sichtbare) Unteraufträge.
- **Eine Bedingung der Auswahl greift erst, wenn ihr Feld gefüllt ist.** Beim Anlegen steht die
  Gültigkeit **unter** dem Plan im Formular; wer sie vorab verlangt, liefert genau dann eine leere
  Auswahl, wenn sie bedient wird — und die Leermeldung behauptet dann, kein Plan passe, obwohl
  keiner geprüft wurde. Beim Bearbeiten fällt das nicht auf, weil das Datum vorbelegt ist. Verloren
  geht dadurch nichts: die Gültigkeit ist Pflichtfeld, das Speichern prüft ohnehin alle drei
  Bedingungen, und die Auswahl verengt sich, sobald das Feld gefüllt ist. Bei der Pauschale zählt
  dafür der **ganze** Zeitraum — sie kennt kein offenes Ende, ein fehlendes „bis" heißt dort „noch
  nicht eingegeben" und nicht „läuft weiter".
- **Aktiv ist ein Auswahlkriterium, kein Speicherkriterium.** Inaktive Pläne stehen nicht zur
  Auswahl, ein bereits gespeicherter bleibt aber in der Liste und bleibt speicherbar — sonst würde
  das Deaktivieren eines Plans den Satz, der an ihm hängt, unbearbeitbar machen.

---

## Budget Assignments Follow a Changed Plan
A booking is assigned to a budget plan explicitly (#913), and that assignment is what every
evaluation reads. Editing the plan therefore has to bring its assignments back in line (#974):

- `OrderBudgetService.update` compares what the plan covers — validity period and scope — before and
  after the edit, and only then calls `TimereportBudgetAssignmentService.revalidateAssignmentsOf`. A
  renamed plan or a moved alert threshold cannot invalidate an assignment.
- Revalidation looks only at the bookings **of that plan**. A booking the change newly brings into
  the plan's reach belongs to another plan or to none; pulling it in would take it away from a
  decision somebody else made. Bulk assignment (#911) and the backfill (#910) serve that direction.
- An assignment that survives the change is left alone, including a deliberate manual one. One the
  change invalidated moves to the single other active plan covering the booking, or is dropped when
  none or several do — the booking then shows up under "without budget".
- Deactivating a plan is deliberately **not** part of its coverage: an inactive plan keeps its
  assignments, and the controlling reports its bookings under the plan, marked as deactivated
  (#1217) — in a section of its own, never together with an active plan, and only while it holds
  something in the window. "Without budget" then holds only what no plan holds, which is what its
  "assign" action is for.
- `TimereportBudgetAssignmentService` loads and authorizes a plan through `OrderBudgetRepository` +
  `BudgetAuthorization` rather than through `OrderBudgetService`. That service has to be able to call
  this one, so going the other way would close a bean cycle.
