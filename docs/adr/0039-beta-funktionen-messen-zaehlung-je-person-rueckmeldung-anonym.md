# ADR-0039 Beta-Funktionen messen: Zählung je Person mit Vergleichsgruppe, Rückmeldung anonym

Date: 2026-10-09
Status: Proposed

## Context and Problem Statement

Eine Beta-Funktion schaltet jede Person selbst ein (#830). Ob sie Standard wird oder zurückgezogen,
wurde bisher nach dem Eindruck aus Slack-Rückmeldungen entschieden. Wer eine Beta still nutzt
oder still wieder ausschaltet, kam darin nicht vor.

Mit #1447 soll die Entscheidung möglichst objektiv werden: Die Nutzung wird gezählt, und nach einer
gewissen Nutzung wird nachgefragt. Zu entscheiden war:

1. Wo liegt die Messung, im Modul `settings` neben den Schaltern oder in einem eigenen Modul?
2. Womit wird eine Beta verglichen?
3. Wie fein wird gezählt, und mit oder ohne Person?
4. Wie anonym ist eine Rückmeldung?
5. Wer sieht die Auswertung, und was davon?
6. Was gilt, wenn eine Person eine andere vertritt (Impersonation)?

## Considered Options

* **Vergleich:** vorher gegen nachher · mit Beta gegen ohne Beta in derselben Woche
* **Zählung:** nur Summen je Beta und Ereignis · Zeilen je Person, Tag, Ereignis und Variante
* **Rückmeldung:** mit Person · ohne Person · Wahl der Person
* **Auswertung:** nur per SQL · Seite für Admins · Seite für Manager

## Decision Outcome

1. **Eigenes Modul `beta`.** Es übernimmt die Beta-Klassen aus `settings`; `settings` bleibt der
   generische Einstellungsspeicher ohne UI. `beta` importiert nur `common`, `auth`, `settings` und
   `employee`. Ein Modul, dessen Seite eine Beta trägt, importiert `beta`, nie umgekehrt.
2. **Vergleichsgruppe statt vorher/nachher.** Eine Beta deklariert an ihrer Konstante die
   Ereignisse, die gezählt werden. Gezählt wird bei allen, mit eingeschalteter Beta als `BETA`,
   sonst als `CLASSIC`. Vorher/nachher vermischt die Beta mit allem, was sich in der Zeit sonst
   ändert, etwa Monatsende oder Urlaubszeit; die Gruppe ohne Beta in derselben Woche erlebt
   dasselbe.
3. **Zeilen je Person, Tag, Beta, Ereignis und Variante, mit Zähler** (`beta_usage`). Erst die
   Person macht aus Summen eine Aussage: wie viele Personen hinter einem Wert stehen, Mittelwert
   und Standardfehler je Person. Und erst über die Person weiß die Rückfrage, wann jemand oft genug
   genutzt hat. Ein- und Ausschalten hält `beta_participation` fest.
4. **Rückmeldung ohne Person** (`beta_feedback`): keine Personenspalte, keine Audit-Spalten — ihr
   `createdby` nennte die Person, ihre Zeitstempel verbänden die Antwort mit der Änderung von
   `beta_participation`. Gespeichert wird nur die Kalenderwoche. Je Person bleibt nur der Stand der
   Rückfrage. **Grenze:** Wer die Datenbank direkt liest, kann über die Reihenfolge der Zeilen
   Rückschlüsse ziehen. Gegenüber der Auswertung ist die Antwort anonym, gegenüber einem
   Datenbankzugriff nicht vollständig.
5. **Auswertung für Manager, nur gesammelt.** Eine Seite unter „System“ zeigt je Beta Teilnahme,
   Nutzung je Ereignis, Woche und Variante sowie die Rückmeldungen. Werte, hinter denen weniger als
   **3 Personen** stehen, zeigt sie nicht, Freitexte erst ab 3 Antworten und nach Text sortiert.
6. **Bei Impersonation wird nichts gezählt und nichts gefragt.** Die Schalter hängen am echten
   Login, `AuthorizedEmployee` folgt der Vertretung: Gezählt würde für die vertretene Person,
   was sie nicht getan hat.

Das Zählen läuft außerhalb der Transaktion des Aufrufers, jeder Schreibvorgang in einer eigenen,
und verschluckt jeden Fehler: Eine verlorene Zählung kostet weniger als eine verlorene Buchung.

### Consequences

* Good: Die Entscheidung über das Ende einer Beta steht auf Zahlen mit Vergleichsgruppe und
  Stichprobengröße, dazu auf Antworten, die ohne Namen gegeben werden.
* Good: Eine neue Beta kostet für die Messung nur ihre Ereignisse und die Schwelle N an der
  Konstante, dazu je Ereignis einen Aufruf oder ein Attribut.
* Bad: Nutzungszeilen nennen die Person. Sie verlassen den Service nie, liegen aber in der
  Datenbank, bis das Ende-Ticket sie löscht.
* Bad: Gezählt wird nur, was deklariert und angebunden ist. Was niemand vorab festlegt, fehlt in
  der Auswertung.
* Neutral: Am Ende einer Beta hält ihr Ende-Ticket die Auswertung gesammelt fest und löscht ihre
  Zeilen per Changeset; bis dahin zeigt die Seite die beendete Beta unter ihrem Schlüssel.
