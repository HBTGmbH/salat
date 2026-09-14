# ADR-0022 Präfix `f` für UiState-Parameter

Date: 2026-09-14
Status: Accepted

## Context and Problem Statement

ADR-0014 führte `UiState` ein, ADR-0016 verteilte die Schlüssel auf die Module. Phase 3 des
`UiStateFilter` legt jeden gemerkten Wert als **Fallback-Request-Parameter** unter die Anfrage
(`UiStateParameterRequestWrapper`): `getParameter`, `getParameterValues` und `getParameterMap`
liefern den gemerkten Wert überall dort, wo die Anfrage selbst keinen mitbringt. Das ist der
Mechanismus, der einen Controller ohne Boilerplate an den gemerkten Wert kommen lässt.

Das Mapping von Parametername auf Schlüssel ist global über alle Module. Der Filter unterscheidet
weder nach HTTP-Methode noch nach Pfad. Ein Name, den irgendein Modul registriert, wirkt damit in
jeder Anfrage der ganzen Anwendung — auch in Formularen, die von ihm nichts wissen:

- `year` und `month` waren die Matrix-Parameter des `dailyreport`-Moduls und zugleich
  Formularfelder in `dailyreport/csv.html`, `daily.html` und `matrix.html`. Der CSV-Export schrieb
  mit seinem `<select name="month">` den gemerkten Matrix-Monat um; umgekehrt bekam jedes Formular
  ohne eigenes `month` den gemerkten Monat als Vorgabewert.
- `orderId`, `suborderId` und `employeeContractId` waren Auswahlparameter der Listen und zugleich
  Felder von `timereport-form.html`, `invoice-form.html` und `employee-order-form.html`.

Zwei Stellen sind dem Problem vorher schon ausgewichen, statt es zu lösen:
`BudgetControllingController.show` führt den Auftrag von Hand über `UiState`, weil
`customerorderSign` ein Formularfeld von fünf Budget-Formularen ist; `BudgetUiStateKeyContributor`
präfixt seine Schalter (`budgetShowInactive` statt `showInactive`, #952). Beides sind
Einzelfallmaßnahmen: Wer den nächsten Schlüssel anlegt, muss von selbst darauf kommen.

## Considered Options

* **Option A** — Status quo: Namen frei wählen, Kollisionen im Einzelfall umgehen.
* **Option B** — Einheitliches Präfix `f` („filter") für jeden registrierten Parameternamen, beim
  Start erzwungen.
* **Option C** — Den Fallback-Mechanismus einschränken: nur GET, nur bestimmte Pfade, nur wenn kein
  `@ModelAttribute` im Spiel ist.
* **Option D** — Die Namen pro Modul mit dem Modulkürzel qualifizieren (`co…`, `eo…`, `ec…`), wie
  es die Filter- und Schalterparameter schon taten.

## Decision Outcome

Chosen: **Option B**.

Jeder in einem `UiStateKeyContributor` registrierte Parametername beginnt mit `f`, gefolgt von
einem Großbuchstaben: `fYear`, `fMonth`, `fCustomerOrderId`, `fCustomerFilter`. Damit sind der
Namensraum der Formularfelder und der der gemerkten Parameter **disjunkt** — ein Formularfeld heißt
nie `f…`, ein UiState-Parameter immer. Das ist eine Regel, die man beim Anlegen eines neuen
Schlüssels einhalten oder verletzen kann, und beides ist prüfbar.

Option C wurde verworfen, weil sie den Mechanismus unscharf macht: „der Wert kommt an, außer unter
Umständen" ist schwerer zu verstehen und zu debuggen als ein reservierter Namensraum. Option D löst
die Kollision zwischen Modulen, aber nicht die zwischen Filter und Formular — genau die hat
`year`/`month` gekostet. Mit dem Präfix entfällt der Grund für die Modulkürzel; die Namen sind
seitdem ausgeschrieben (`cFilter` → `fCustomerFilter`, `eoFilter` → `fEmployeeOrderFilter`).

### Durchsetzung

- `UiStateKeyRegistry` weist eine Registrierung ohne Präfix beim Start mit einer
  `IllegalStateException` zurück. Die Anwendung startet nicht, statt die Kollision still
  hinzunehmen.
- `UiStateParameterNamingTest` prüft zusätzlich, dass kein registrierter Name
  - als `th:field="*{…}"` an ein Formularobjekt gebunden ist,
  - als Feld eines POST-Formulars in einem Template vorkommt,
  - mit einem Feld einer `*Form`-Klasse zusammenfällt.

  Die Contributor werden dafür so gefunden, wie Spring sie findet — ein Modul, das morgen
  hinzukommt, ist ohne Änderung am Test abgedeckt.

### Folgen für die Bedienung

- **Filterformulare tragen die Parameter direkt**, nicht über ein gebundenes Formularobjekt: ein
  `<input name="fCustomerFilter">` statt `th:field="*{…}"`. `DashboardFilterForm` ist deshalb
  entfallen; der Budget-Dashboard-Controller liest zwei `@RequestParam`.
- **Anlegeformulare dürfen die Filterwerte als optionale Eingabe verwenden**: `createForm`
  übernimmt `fCustomerId`, `fCustomerOrderId` und so weiter als Vorbelegung. Das ist der erwünschte
  Teil des Mechanismus — die Vorbelegung eines neuen Eintrags mit dem, was in der Liste gerade
  ausgewählt ist.
- Gemerkte Werte im Cookie bleiben erhalten: der Cookie-Schlüssel ist der Name des `UiStateKey`
  (`matrix.Month`), nicht der Parametername. Die Umbenennung berührt den Cookie nicht.

### Consequences

* Good: Formular und Filter können sich nicht mehr gegenseitig Werte unterschieben
* Good: einem Parameternamen sieht man an, was er ist — `f…` ist gemerkter Filterzustand
* Good: die Regel bricht beim Start, nicht im Betrieb
* Bad: URLs sind länger und weniger hübsch (`?fCustomerOrderId=42`)
* Bad: bestehende Bookmarks mit alten Parameternamen verlieren ihre Auswahl — die Seite lädt, die
  Auswahl ist die zuletzt gemerkte
* Neutral: `BudgetControllingController` führt seinen Auftrag weiterhin von Hand über `UiState`.
  Der Grund ist jetzt ein anderer: Mit einem registrierten Mapping stünde der gemerkte Wert auch in
  `getParameterMap()`, und die Unterscheidung „abgeschickt oder nur aufgerufen" — an der die teure
  Auswertung hängt — wäre nicht mehr möglich. Das braucht ein eigenes Absende-Merkmal und ist als
  Folgeticket festgehalten.
