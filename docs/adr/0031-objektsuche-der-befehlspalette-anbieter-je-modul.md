# ADR-0031 Objektsuche der Befehlspalette: ein Anbieter je Modul, Ziele auch aus fremden Modulen

Date: 2026-09-27
Status: Accepted

## Context and Problem Statement

Mit #1155 findet die Befehlspalette Seiten, Tage und Einstellungen, alles aus der gerenderten Seite
(ADR-0030). #1157 fügt Geschäftsobjekte hinzu — Aufträge, Unteraufträge, Auftraggeber, Personen —,
jedes mit seinen Zielen: ein Auftrag führt zu sich selbst, zu seinen Unteraufträgen, zu seinem
Controlling und zu seinen Budgetplänen; ein Unterauftrag auch zum Buchungsformular; eine Person zu
ihren Stammdaten, ihrem Vertrag, ihrer Einzel- und Matrixübersicht.

Drei Dinge machen das zu mehr als einer Suchanfrage:

1. **Die Ziele eines Objekts gehören verschiedenen Modulen.** Den Auftrag kennt `order`, wer sein
   Controlling sehen darf, weiß nur `budget` (`BudgetAuthorization`); ob ein Unterauftrag heute
   gebucht werden darf, entscheidet `dailyreport`. `order` darf aber weder `budget` noch
   `dailyreport` importieren — beide importieren `order`.
2. **Sichtbarkeit ist eine Frage je Ziel, nicht je Objekt.** Eine People Lead öffnet den beendeten
   Vertrag eines früheren Teammitglieds, dessen Stammdaten nicht mehr; ein Mitarbeiter sieht jeden
   Auftrag in der Liste, aber das Controlling nur seiner eigenen; eine eingeschränkte Anmeldung
   öffnet keine einzige Stammdatenseite, darf aber buchen. Die Palette darf nichts anbieten, dessen
   Seite mit 403 antwortet.
3. **Die Palette selbst darf von keinem Fachmodul abhängen** — sie ist ein Querschnitt wie die
   Sidebar, und ein Modul `palette`, das alle Module importiert, wäre der Knoten, an dem jede
   künftige Abhängigkeit hängen bliebe.

## Considered Options

Zur Sammlung:

* **A — Eine Schnittstelle `PaletteProvider` in `common`, eine Umsetzung je Modul;** ein Modul
  `palette`, das nur `common` und `auth` importiert, sammelt alle über Spring — das Muster von
  `UiStateKeyContributor`.
* **B — Das Modul `palette` fragt die Services der Fachmodule direkt.**
* **C — Jedes Modul bietet einen eigenen Endpunkt an, der Browser fragt alle.**

Zu den Zielen aus fremden Modulen:

* **D — Zwei Beiträge je Anbieter:** Objekte suchen (`search`) und Zielen fremder Objekte etwas
  hinzufügen (`targetsFor`), zusammengeführt über einen fachlichen Schlüssel.
* **E — Jedes Modul sucht die Objekte, zu denen es Ziele hat, selbst** und liefert ganze Treffer; die
  Palette verschmilzt gleiche Schlüssel.
* **F — Das besitzende Modul holt die fremden Ziele über Command-Events.**

## Decision Outcome

Chosen: **A + D.**

**A** — `common.palette` enthält nur die Verträge: `PaletteProvider`, die Records `PaletteHit`,
`PaletteTarget`, `PaletteText`, die Aufzählung `PaletteKind` und zwei Hilfen, `PaletteQuery` (Wörter,
LIKE-Muster, Rangfolge) und `PaletteLink` (kodierte Adressen). Jedes Modul setzt die Schnittstelle in
seinem `controller`-Paket um, neben seinem `UiStateKeyContributor`, und stützt sich dafür auf seine
Services. Das Modul `palette` besteht aus `PaletteSearchService` und `PaletteController`
(`GET /palette/search?q=`), die Regeln in `ArchitectureTest` halten fest, dass es nur `common` und
`auth` importiert und dass kein Modul es importiert. B hätte `palette` zum Knoten aller Module
gemacht; C hätte je Tastendruck fünf Anfragen geschickt und Rangfolge, Obergrenze und Gruppen in den
Browser verlegt.

**D** — `search` liefert die Objekte des Moduls mit den Zielen, die es selbst verantworten kann;
`targetsFor` bekommt die Treffer fremder Anbieter einer Art und ergänzt Ziele, über
`PaletteHit.key` (Auftrag: Kürzel, weil `budget` mit Kürzeln arbeitet; Unterauftrag, Auftraggeber:
id; Person: id des Vertrags, der für sie steht). `budget` ergänzt so Controlling und Budget,
`dailyreport` „Buchen auf …" und die beiden Übersichten. E hätte `budget` alle Aufträge noch einmal
suchen lassen und die Obergrenze je Art zwischen zwei Anbietern ausgehandelt; F ist nach AGENTS.md
der letzte Ausweg und hier nicht nötig, weil die Importrichtung für D stimmt.

Daraus folgen die Regeln, nach denen `PaletteSearchService` die Beiträge zusammensetzt:

* Treffer gleicher Art und gleichen Schlüssels sind ein Objekt; ihre Ziele werden vereinigt.
* Je Art wird gerankt — aktuelle vor beendeten (ADR-0029) und verborgenen (ADR-0012), dann nach der
  Güte des Treffers —, doppelt so viele wie gezeigt gehen weiter, bekommen ihre fremden Ziele, und
  **ein Treffer ohne Ziel fällt weg**. Gezeigt werden höchstens fünf je Art.
* **Jeder Anbieter entscheidet selbst, was die angemeldete Person sieht, je Treffer und je Ziel.**
  Die Services der Fachmodule prüfen beim Lesen meist nur die Anmeldung; die Rollenfrage stellt
  deshalb der Anbieter — mit denselben Klassen, die die Zielseite fragt (`EmployeecontractAuthorization`,
  `BudgetAuthorization`, `TimereportAuthorization`), nicht mit einer Kopie ihrer Regeln.
* Ein Anbieter, dessen Entscheidung in einer `AuthorizationException` endet, trägt nichts bei; sie
  beantwortete sonst die ganze Suche mit 403. Das trägt nur, weil die Suche ihre eine, lesende
  Transaktion **immer zurückrollt**: eine Ausnahme, die einen Service verlässt, setzt die umgebende
  auf Rollback, auch wenn sie danach gefangen wird, und ein Commit beantwortete die Suche mit 500.
  Ohne eigene Transaktion öffnete jeder Service-Aufruf eine, und die Suche dauerte 10 bis 24 ms
  länger (gemessen, PR zu #1157). Wer „nicht lesbar" übergehen will, fragt trotzdem nicht werfend
  (`EmployeecontractService#getReadableEmployeecontract`).

Die Suche selbst: je Art eine JPQL-Abfrage mit Konstruktor-Projektion auf einen Record
(ADR-0021), `lower(spalte) like :wort escape '!'` für bis zu drei Wörter, die alle vorkommen müssen,
verborgene und beendete Zeilen zuletzt, höchstens 30 Kandidaten. Die Rangfolge im Einzelnen und die
Markierung „beendet"/„verborgen" entscheidet Java, mit `Validity` und `Hiding`. Angezeigt werden
fachliche Schlüssel; eine Datenbank-id steht höchstens in einer Adresse.

### Consequences

* Good: Ein neues Modul mit Objekten braucht eine Klasse, keine Änderung an der Palette; ein neues
  Ziel für ein bestehendes Objekt ebenso.
* Good: Wer ein Ziel anbietet, ist derjenige, der die Zielseite schützt — die Frage „darf sie das
  öffnen?" hat keine zweite Antwort.
* Bad: Eine Suche kostet mehrere Abfragen, je Art eine und für die ergänzten Ziele weitere. Die
  Antwortzeit ist deshalb gemessen (PR zu #1157) und die Obergrenzen stehen in `PaletteQuery`.
* Bad: Wer eine Filterliste umbenennt, muss wissen, dass die Palette deren Parameter als Zeichenkette
  kennt (`fCustomerOrderFilter` …) — ein Modul wie `customer` darf die Schlüssel von `order` nicht
  importieren.
* Neutral: Die Befehle mit Parametern aus #1158 brauchen je Parametertyp Vorschläge von denselben
  Anbietern; `PaletteProvider` bekommt dafür eine weitere Methode, die Aufteilung bleibt. Umgesetzt
  als `suggest` (ADR-0032).
