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
   `employee`.
   **Die Betas gehören den Modulen**, nach dem Muster von `UiStateKeyContributor` (ADR-0016) und
   `PaletteProvider` (ADR-0031): Ein Modul deklariert seine Betas als `BetaFeature` und liefert sie
   über einen `BetaFeatureContributor`; es fragt und zählt über das Interface `Betas`. Alle drei
   liegen in `common.beta`. `beta` sammelt die Beiträge in `BetaFeatureRegistry` und kennt keine
   Beta selbst. Außer `settingseditor`, das die Schalter zeigt, importiert kein Modul `beta`
   (`ArchitectureTest.onlySettingseditorShouldAccessBeta`). Ein Enum der Betas in `beta` oder in
   `common` hätte jede neue Beta zu einer Änderung an einem fremden Modul gemacht.
2. **Vergleichsgruppe statt vorher/nachher.** Gezählt wird bei allen, mit eingeschalteter Beta
   als `BETA`, sonst als `CLASSIC`. Vorher/nachher vermischt die Beta mit allem, was sich in der Zeit sonst
   ändert, etwa Monatsende oder Urlaubszeit; die Gruppe ohne Beta in derselben Woche erlebt
   dasselbe.
   **Die Ereignisse gehören dem Modul, das zählt.** `beta` macht keine Annahme über sie und kennt
   keine Liste: Es zählt jeden formal gültigen Schlüssel für eine Beta, die es gibt. Eine Liste an
   der Beta hätte `beta` Wissen über die Vorgänge anderer Module aufgeladen; jede neue
   Messung hätte zwei Module geändert.
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
* Good: Eine neue Beta kostet ihr Modul eine Konstante mit der Schwelle N, einen Eintrag im
  Contributor und je Ereignis einen Aufruf oder ein Attribut; `beta` ändert sich dafür nicht.
* Bad: Nutzungszeilen nennen die Person. Sie verlassen den Service nie, liegen aber in der
  Datenbank, bis das Ende-Ticket sie löscht.
* Bad: Gezählt wird nur, was angebunden ist. Was niemand vorab festlegt, fehlt in der Auswertung.
* Bad: Ohne Liste prüft `beta` Ereignisse aus dem Browser nur formal. Eine Person kann ihre eigenen
  Zählungen verfälschen, nicht die anderer; die Auswertung je Person und Woche begrenzt die Wirkung
  auf eine Stimme.
* Neutral: Am Ende einer Beta hält ihr Ende-Ticket die Auswertung gesammelt fest und löscht ihre
  Zeilen per Changeset; bis dahin zeigt die Seite die beendete Beta unter ihrem Schlüssel.
