# ADR-0024 Konfigurierbare Jira-Felder als JSON-Spalte

Date: 2026-09-15
Status: Accepted

## Context and Problem Statement

Die Jira-Replikation liest eine feste Feldliste und schreibt sie in benannte Spalten von
`jira_ticket`. Für Auswertungen werden darüber hinaus Felder gebraucht, die pro Kunde und pro
Jira-Instanz verschieden sind — mal ein Custom Field, mal ein Standardfeld, mal eine Ebene innerhalb
eines Feldes. Die Feldliste ist damit Konfiguration, nicht Schema.

Zusätzlich gilt: welchen Fachbegriff ein Feld trägt, ist kundenspezifisch. Das Modul `jira` darf
diesen Bezug nicht enthalten — sonst steht in einem generischen Modul, wofür ein bestimmter Kunde
`customfield_10123` benutzt.

Zwei Fragen waren zu entscheiden: **wo** die Werte landen, und **wann** ein bereits replizierter
Datensatz nach einer Konfigurationsänderung neu geschrieben wird.

## Considered Options

* **A — Spalte pro Feld**: für jedes benötigte Feld eine Migration und eine Entity-Property
* **B — Schmale Nebentabelle** `jira_ticket_field (ticket_id, field, value, from_key)`
* **C — JSON-Spalte** auf `jira_ticket`, gemappt über `@JdbcTypeCode(SqlTypes.JSON)`

Für die Invalidierung:

* **I1 — Watermark bei jeder Config-Änderung zurücksetzen**, Ticket ohne weitere Prüfung neu schreiben
* **I2 — Hash der Feldliste am Ticket**, verglichen beim Upsert
* **I3 — beides**

## Decision Outcome

Chosen: **Option C** für die Ablage und **I3** für die Invalidierung.

Zwei JSON-Spalten, weil die beiden Fragen verschieden sind: `custom_fields` hält die Rohwerte, wie am
Ticket selbst gesetzt; `custom_fields_effective` hält je vererbtem Feld den aufgelösten Wert samt
Herkunft (`{"customfield_10123": {"value": "…", "from": "PROJ-1"}}`, `from = null` heißt: am Ticket
selbst gesetzt). Ein Bericht, der nur den geltenden Wert braucht, liest die zweite Spalte; wer wissen
muss, ob der Wert gepflegt oder geerbt ist, liest beide.

Die fachliche Benennung passiert bewusst außerhalb des Moduls, in einer kundenspezifischen View. Im
Modul steht nur der Response-Key.

Beide Invalidierungsschritte sind nötig, weil je einer allein nicht reicht: der Hash am Ticket greift
nur bei Datensätzen, die die Suche überhaupt liefert — und der Watermark hält genau die bereits
replizierten Tickets heraus, denn deren `updated` hat sich in Jira nicht bewegt. Umgekehrt reicht das
Zurücksetzen des Watermarks allein nicht, weil der Upsert einen unveränderten Datensatz sonst wieder
überspringt. Der Hash wird aus den konfigurierten Werten gebildet (getrimmt, sortiert), nicht aus dem
gespeicherten JSON: MySQL normalisiert JSON beim Schreiben, das Dokument ist also keine stabile
Eingabe.

### Consequences

* Good: Ein weiteres Feld ist eine Konfigurationsänderung, keine Migration und kein Deployment.
* Good: Kein Kundenbezug im Modul — der Response-Key ist die einzige Kopplung, die Benennung liegt in
  der View.
* Good: Die Herkunft steht am Wert. Ein Bericht kann „geerbt von PROJ-1" von „selbst gepflegt"
  unterscheiden, ohne die Elternkette erneut zu laufen.
* Bad: Die Extraktion ist MySQL-spezifisch. H2 2.4 kennt weder `JSON_VALUE` noch `JSON_EXTRACT`, also
  gehört sie ausschließlich in ETL-/Report-SQL und in die View — nie in eine JPQL- oder
  Repository-Abfrage, die auch im Test laufen soll. Das Persistenzmodell selbst bleibt portabel:
  Hibernate bringt `H2JsonJdbcType` mit, und der Round-Trip ist gegen H2 getestet
  (`JiraTicketCustomFieldsTest`).
* Bad: Keine Fremdschlüssel, keine Typen, keine Constraints auf den Werten. Ein Tippfehler in der
  Feld-ID fällt erst im Log auf, und nur, wenn Jira das Feld gar nicht erst mitliefert — ein
  Tippfehler *hinter* dem ersten Punkt eines Pfades ist von einem leeren Feld nicht zu unterscheiden.
* Bad: Eine native `json`-Spalte kann keinen Leerstring enthalten. Das ist hier erwünscht:
  `JSON_EXTRACT` auf einem Leerstring bricht mit `ERROR 3141 – The document is empty` ab und würde ein
  komplettes ETL-Statement scheitern lassen. Der leere Zustand ist deshalb `NULL`, nicht `{}`.
* Neutral: `@JdbcTypeCode(SqlTypes.JSON)` wurde damit erstmals im Projekt verwendet.
* Neutral: Eine Änderung der Feldliste zieht einen vollständigen Resync der betroffenen Config nach
  sich. Bei großen Aufträgen dauert der nächste Lauf entsprechend lange — einmalig, und die Alternative
  wäre, dass die neuen Felder an bestehenden Tickets stumm leer bleiben.

### Warum nicht die anderen Optionen

**A — Spalte pro Feld**: Jedes zusätzliche Feld eines Kunden wäre eine Migration im generischen Modul,
und die Spaltennamen trügen den Kundenbezug, den das Modul gerade nicht enthalten soll.

**B — Nebentabelle**: Sauberer relational und der einzige Weg zu Constraints, aber jede Auswertung
bräuchte einen Join pro Feld, und die Vererbung müsste ihre Herkunft in einer zweiten Spalte je Zeile
führen. Der Gewinn an Integrität ist gering, solange die Werte ohnehin ungetypter Text aus einem
Fremdsystem sind.
