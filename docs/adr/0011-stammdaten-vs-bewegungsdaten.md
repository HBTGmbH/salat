# ADR-0011 Stammdaten vs. Bewegungsdaten

Date: 2026-05-30
Status: Accepted

> Das `hide`-Flag und die Archivierungsregeln für Stammdaten wurden nach ADR-0012 ausgelagert.

## Context and Problem Statement

Die JPA-Entitäten der Anwendung haben grundlegend verschiedene Lebenszyklen und Nutzungscharakteristika.
Ein einheitliches Modell fehlte bislang, das festlegt:

- welche Entitäten als Stammdaten (Referenz- oder Konfigurationsdaten) gelten,
- welche als Bewegungsdaten (Ereignis- oder Transaktionsdaten),
- und welche typischen Muster für jeden Typ gelten.

## Considered Options

* Keine explizite Klassifizierung (implizites Wissen im Team)
* Klassifizierung als ADR dokumentieren, Muster und Regeln ableiten

## Decision Outcome

Chosen: **Klassifizierung dokumentieren**, weil das explizite Modell Entscheidungen über
Deaktivierungsmechanismen, Softlöschung und Gültigkeitsbereiche konsistent macht.

### Consequences

* Good: klare Entscheidungsgrundlage, welche Entitäten eine Gültigkeitsspanne oder einen
  anderen Deaktivierungsmechanismus benötigen
* Good: neue Entitäten können sofort klassifiziert werden
* Neutral: Klassifizierung erfordert gelegentliche Pflege, wenn sich Domänenverständnis ändert

---

## Typische Merkmale der beiden Kategorien

### Stammdaten (Master Data)

Stammdaten definieren die *Identitäten* und *Konfigurationen*, auf die die Anwendung operiert.
Sie ändern sich selten, haben eine lange Lebensdauer und werden von Bewegungsdaten referenziert.

| Merkmal | Beschreibung |
|---|---|
| Lebensdauer | lang; Objekte werden nicht gelöscht, sondern archiviert oder deaktiviert |
| Änderungsfrequenz | gering; seltene, manuelle Pflege |
| Referenzierung | werden von Bewegungsdaten (und anderen Stammdaten) referenziert |
| Deaktivierung | `hide`-Flag (UX-Archivierung, → ADR-0012), `enabled`-Flag (Aktivierung) oder Gültigkeitsspanne (`fromDate`/`untilDate`, `validFrom`/`validUntil`, → ADR-0012) |
| Audit-Trail | immer via `AuditedEntity` |
| Löschen | i.d.R. nicht erlaubt, wenn noch Bewegungsdaten referenzieren (Veto-Event) |

### Bewegungsdaten (Transactional Data)

Bewegungsdaten halten *Ereignisse* und *Aktivitäten* fest, die die Anwendung über die Zeit aufzeichnet.

| Merkmal | Beschreibung |
|---|---|
| Lebensdauer | kurz bis mittelfristig; wachsen kontinuierlich |
| Änderungsfrequenz | initial hoch, dann zunehmend immutabel |
| Referenzierung | referenzieren Stammdaten; werden selten selbst referenziert |
| Deaktivierung | Softlöschung (`deleted`-Flag + `@SQLRestriction`) oder Status-Feld |
| Audit-Trail | optional (nicht alle Bewegungsdaten erweitern `AuditedEntity`) |
| Löschen | Soft-Delete bevorzugt, wenn historische Daten bewahrt werden müssen |

---

## Klassifizierung aller JPA-Entitäten

### Stammdaten

| Entität | Modul | `hide`-Flag | Deaktivierungsmechanismus | Anmerkung |
|---|---|---|---|---|
| `Customer` | customer | ✅ vorhanden | `hide` | Referenz für Aufträge |
| `Employee` | employee | ✅ vorhanden | `hide` | Referenz für Verträge, Berichte |
| `Employeecontract` | employee | ✅ vorhanden | `hide` + `validFrom`/`validUntil` | Vertrag eines Mitarbeiters |
| `Customerorder` | order | ✅ vorhanden | `hide` + `fromDate`/`untilDate` | Kundenauftrag |
| `Suborder` | order | ✅ vorhanden | `hide` + `fromDate`/`untilDate` | Unterauftrag |
| `Employeeorder` | order | — (erbt) | erbt `hide` von `Suborder`/`Customerorder` | kein eigenes Flag nötig |
| `Publicholiday` | dailyreport | — | — | Kalender-Fakten; nicht in Dropdowns |
| `Referenceday` | dailyreport | — | — | Kalender-Referenz; nicht in Dropdowns |
| `SalatUser` | auth | — | `Employee.hide` | Technische Auth-Entität; UX-Sichtbarkeit über Mitarbeiter gesteuert |
| `AuthorizationRule` | auth | — | `validFrom`/`validUntil` | Zeitbegrenzte Berechtigungsregel |
| `ETLDefinition` | etl | — | — | System-Konfiguration; kein Dropdown in UI |
| `JiraReplicationConfig` | jira | — | `enabled`-Flag | Integrations-Konfiguration; `enabled` reicht aus |
| `ReportDefinition` | reporting | ❌ fehlt | — | Berichtsvorlage; in `ScheduledReportJob`-Dropdown |
| `ScheduledReportJob` | reporting | — | `enabled`-Flag | Geplanter Job; `enabled` reicht aus |
| `OrderRevenueExcelMapping` | order | — | — | Kleines Mapping; kein Dropdown |

### Bewegungsdaten

| Entität | Modul | Anmerkung |
|---|---|---|
| `Timereport` | dailyreport | Zeitbuchung; Soft-Delete via `deleted`-Flag + `@SQLRestriction` |
| `Workingday` | dailyreport | Tägliche Anwesenheit |
| `Overtime` | employee | Überstunden-Korrektur |
| `Vacation` | employee | Urlaubsanspruch und -verbrauch |
| `Employeeorder` | order | Mitarbeiter-Auftragsbudget (zeitgebundene Zuweisung — Grenzfall; klassifiziert als Stammdatum wegen Konfigurationscharakter) |
| `OrderRevenue` | order | Finanzieller Umsatzeintrag |
| `ETLExecutionHistory` | etl | Systemprotokoll von ETL-Läufen |
| `ScheduledReportExecutionHistory` | reporting | Systemprotokoll von Report-Ausführungen |
| `StatisticValue` | statistic | Berechnete Aggregation |
| `JiraTicket` | jira | Replizierte externe Ticketdaten |
| `Favorite` | favorites | Benutzer-Schnellzugriff |

> **Hinweis zu `Employeeorder`**: Die Entität wird unter Stammdaten geführt, da sie Konfigurationscharakter hat
> (Budgetzuweisung eines Mitarbeiters auf einen Unterauftrag). Sie hat jedoch keine eigene `hide`-Spalte —
> ihre Sichtbarkeit ergibt sich aus dem `hide`-Flag des übergeordneten `Suborder` und `Customerorder`.

---

## Verwandte Entscheidungen

- ADR-0012: Archivierungsregeln für Stammdaten — `hide`-Flag, Gültigkeitsspannen und Dropdown-Verhalten in Create- vs. Edit-Dialogen
