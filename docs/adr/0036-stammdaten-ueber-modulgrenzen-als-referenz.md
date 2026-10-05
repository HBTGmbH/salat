# ADR-0036 Bezüge über Modulgrenzen: Stammdaten als Referenz, Bewegungsdaten über die id

Date: 2026-10-06
Status: Accepted

## Context and Problem Statement

Ein Modul verweist auf Daten eines anderen Moduls, etwa ein Budgetplan auf seinen Auftrag oder ein
JIRA-Ticket auf seinen Unterauftrag. In der Datenbank ist das immer eine Spalte mit Fremdschlüssel auf
der id. In Java gab es zwei Formen nebeneinander, und keine Regel dazu:

* **Echte Referenz** (`@ManyToOne`): im alten Kern durchgehend, `Timereport.employeeorder`,
  `Workingday.employeecontract`, `Employeeorder.employeecontract`, `Customerorder.customer`,
  `Customerorder.respEmpHbtContract` und `.responsibleHbt`, `OrderRevenueExcelMapping.employee`,
  `Employee.salatUser`, `UserPreference.salatUser`. Alle neun zeigen auf Stammdaten.
* **`Long`-id**: in den jüngeren Modulen, eingeführt in #968 (Person), #1205/#1212 (Auftrag und
  Unterauftrag im Budget) und #1322/#1323 (JIRA) — `EmployeeCostAssignment`, `OrderBudget`,
  `OrderFlatRate`, `OrderPricing`, `JiraReplicationConfig`, `JiraTicket`, `JiraWorklogSync`,
  `Favorite`, `Notification`, `ReportDefinition`, `ScheduledReportJob`, außerdem
  `TimereportBudgetAssignment.timereportId`.

Die `Long`-ids entstanden aus ADR-0021 („Entities bleiben im Modul“) und aus dem Wunsch, ein Modul
herauslösbar zu halten (ADR-0001). Innerhalb eines Moduls gilt dagegen die echte Referenz (#1347
verworfen; #1349, #1350).

Zu entscheiden war (#1351), ob Bezüge über Modulgrenzen echte Referenzen werden sollen.

**Leistung entscheidet die Frage nicht.** In der Datenbank sind beide Formen gleich: dieselbe Spalte,
derselbe Fremdschlüssel, derselbe Index, derselbe Join. Unterschiede gibt es nur in Hibernate. Eine
`@ManyToOne` ist ohne Angabe EAGER, und auch als LAZY lädt sie je Zeile nach, sobald Code sie anfasst
(`docs/performance-tips.md` §2). Eine `Long`-id kann das nicht, dafür verleitet sie dazu, je Zeile über
den Service des anderen Moduls nachzuschlagen — dasselbe N+1, nur von Hand gebaut (§6).

## Considered Options

* **(a)** `Long`-id mit Fremdschlüssel über jede Modulgrenze, Referenz nur innerhalb eines Moduls
* **(b)** Echte Referenzen über jede Modulgrenze
* **(c)** Echte Referenzen über Modulgrenzen nur auf Stammdaten und nur in erlaubter Importrichtung;
  Bewegungsdaten über die id

## Decision Outcome

Chosen: **(c)**.

Die Herauslösbarkeit, die für (a) sprach, hängt nicht an der Form des Verweises. Wird ein Modul
herausgelöst, bekommt es von den Stammdaten, die es liest, eine eigene Lesekopie — Aufträge,
Unteraufträge, Personen werden in jedes Modul repliziert, das sie braucht. Eine Referenz zeigt dann auf
die Kopie im eigenen Modul, und es ändert sich der Typ, nicht das Modell. Die `Long`-id bringt dafür
nichts, kostet aber jeden Tag: Typsicherheit, JPQL-Pfade (`plan.suborder.completeOrderSign` statt
eines Joins über die id), und Code, der Kürzel und Namen über fremde Services nachschlägt.

Bewegungsdaten werden nicht repliziert, sondern bleiben beim besitzenden Modul. Für sie gilt
weiter die id.

### Regeln

1. **Nur Stammdaten:** Kunde, Auftrag, Unterauftrag, Mitarbeiterauftrag, Person, Vertrag, Login
   (`SalatUser`). Keine Bewegungsdaten — eine Buchung (`Timereport`) bleibt eine id, auch weil sie weich
   gelöscht ist (`@SQLRestriction`) und eine Referenz auf eine gelöschte Buchung beim Zugriff
   scheitern statt sie als weg zu lesen würde (`TimereportBudgetAssignment`).
2. **Nur in erlaubter Importrichtung**, wie sie `ArchitectureTest` festlegt.
3. **Immer `LAZY`, nur lesend.** Kein Cascade über die Grenze, kein Aufruf eines Setters der fremden
   Entity. Geschrieben wird weiter über Events (ADR-0003).
4. **Navigiert wird nur innerhalb der Stammdaten**, nicht von dort weiter in Bewegungsdaten. Das hält
   die spätere Lesekopie klein: was ein Modul über die Referenz erreicht, muss es auch replizieren.
5. **Listen holen die Referenz mit**: `join fetch` in der Abfrage oder `@BatchSize`, nie das Nachladen
   je Zeile.
6. **Innerhalb eines Moduls** gilt unverändert die Referenz, auch auf Bewegungsdaten.

### Consequences

* Good: der Kern ist regelkonform, wie er ist; seine neun Referenzen waren alle schon Stammdaten
* Good: Typsicherheit und JPQL-Pfade für die jüngeren Module, und das Nachschlagen von Kürzeln und
  Namen über fremde Services entfällt, wo sie umgestellt werden
* Good: das Herauslösen bleibt eine Replikationsaufgabe; Regel 4 begrenzt, was repliziert werden muss
* Bad: eine Referenz öffnet den Weg zu Lazy Loads aus fremden Tabellen; Regel 5 hängt am Review
* Bad: über eine Referenz liest ein Modul fremde Stammdaten am zeilenweisen Lesefilter des besitzenden
  Moduls vorbei (ADR-0006). Für Stammdaten, die jeder angemeldete Nutzer in Auswahllisten sieht, ist
  das ohne Folgen; wo ein Modul Stammdaten zeigt, die nicht jeder sehen darf, verantwortet es die
  Prüfung selbst, wie bei einem Join nach ADR-0021 Punkt 5
* Bad: Regel 3 erzwingt heute kein Test; eine ArchUnit-Regel gegen Setter-Aufrufe auf Entities eines
  anderen Moduls ist ein eigenes Issue (#1371)
* Neutral: **ADR-0021 bleibt in Kraft und wird ergänzt.** Eine Referenz auf Stammdaten ist eine
  erlaubte Verbindung von Entities über die Grenze. Der Satz „Entities verlassen die Abfrage nicht“
  gilt weiter für Service-Signaturen: `ArchitectureTest.noEntityCrossesAModuleBoundaryThroughAService`
  (#1244) und sein eingefrorener Bestand bleiben unverändert
* Neutral: die Spiegelspalten, die neben den `Long`-ids für Reports standen, sind entfernt (#1202,
  #1321, #1345, #1359); eine Referenz bringt sie nicht zurück
* Neutral: die Umstellung der `Long`-ids auf Referenzen ist kein Teil dieser Entscheidung; je Modul
  gibt es ein Folge-Issue: budget #1367, jira #1368, favorites #1369, notification/reporting #1370
