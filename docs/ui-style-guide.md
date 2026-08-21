# SALAT UI Style-Guide (Ist-Stand)

Kompakte Beschreibung der tatsächlich verwendeten UI-Muster in SALAT — als gemeinsame Grundlage
für ein Gespräch mit UX. Beschreibt **was ist**, nicht was sein sollte. Offene Punkte stehen
gesammelt am Ende in [Diskussionspunkte für UX](#diskussionspunkte-für-ux).

Verwandte Dokumente: [AGENTS.md](../AGENTS.md) (verbindliche Entwicklungsregeln),
[ADR 0002](adr/0002-thymeleaf-spring-mvc-als-ui-stack.md) (UI-Stack),
[ADR 0004](adr/0004-salat-thymeleaf-dialect.md) / [0005](adr/0005-alle-fragmente-durch-salat-dialect-ersetzen.md) (Komponenten-Dialekt),
[ADR 0010](adr/0010-deutsch-first-i18n.md) (Sprache).

---

## 1. Kontext

| | |
|---|---|
| **Anwendung** | Zeiterfassung, Auftragsverwaltung, Budget & Reporting für ein IT-Beratungsunternehmen |
| **Nutzergruppen** | Mitarbeitende (täglich, wenige Minuten), People Leads, Backoffice, Manager, Admins |
| **Nutzungsmuster** | Interne Fach-Anwendung, Desktop-dominiert, hohe Wiederholungsfrequenz, tastaturlastig |
| **Sprache** | **Deutsch-first**; Englisch als Zweitsprache (`MessageResources[_en].properties`) |
| **Rendering** | Server-side (Spring MVC + Thymeleaf), kein SPA-Framework; HTMX für partielle Updates |

**Konsequenz für UX:** Kein Client-State, kein Routing im Browser. Jede Interaktion ist entweder
ein Full-Page-Load oder ein HTMX-Fragment-Swap. Optimistic UI, Undo-Stacks oder komplexe
Drag&Drop-Interaktionen sind mit dem Stack nur mit erheblichem Aufwand umsetzbar.

## 2. Design-Fundament

| Ebene | Umsetzung |
|---|---|
| **Design-System** | [Tabler](https://tabler.io) auf Bootstrap 5 — via WebJars, unverändert eingebunden |
| **Icon-Sets** | **zwei parallel**: Tabler Icons (`ti ti-*`, ~138 Verwendungen) und Bootstrap Icons (`bi bi-*`, ~71) |
| **Schrift** | Inter Var (extern von `rsms.me`), Fallback System-Sans; `font-feature-settings: "cv03","cv04","cv11"` |
| **Farben** | ausschließlich Tabler-Tokens (`--tblr-*`); keine eigene Marken-Palette |
| **Projekt-CSS** | `static/css/salat.css` — 243 Zeilen, nur Token-Bridging (`--bs-*` → `--tblr-*`), TomSelect-Angleichung und die einklappbare Sidebar |
| **Theme** | Light/Dark umschaltbar (Tabler-Theme-Script, Buttons in der Kopfzeile); Sidebar ist **immer** dunkel (`data-bs-theme="dark"`) |
| **Druck** | Kopfzeile/Fußzeile via `d-print-none` ausgeblendet; eine dedizierte Druckansicht (`invoice/invoice-print.html`) |

Es gibt **keine** eigene Design-Token-Datei, kein Storybook, keine visuellen Regressionstests.
Die Referenz ist der Tabler-Standard.

## 3. Layout & Navigation

```
┌──────────┬──────────────────────────────────────────────┐
│ Sidebar  │ page-header:  pretitle / page-title │ Aktionen│
│ (dunkel) ├──────────────────────────────────────────────┤
│ 14rem    │ page-body                                    │
│ ⇄ 3.5rem │   Alerts (Toast-Bereich)                     │
│          │   container-fluid → layout:fragment="content" │
│ Nutzer-  ├──────────────────────────────────────────────┤
│ karte    │ footer: Links, Version, Server-Zeit          │
└──────────┴──────────────────────────────────────────────┘
```

- **Shell:** `templates/layout/base.html`, eingebunden per `layout:decorate` (Thymeleaf Layout Dialect).
- **Sidebar:** vertikale Navbar mit 6 Bereichen (Buchungen, Mitarbeiter, Aufträge, Budget, Reports,
  Backoffice), jeder als aufklappbares Dropdown. Sichtbarkeit rollenabhängig
  (`#authorization.expression(...)` bzw. ViewHelper-Bean).
- **Einklappbar** über einen freistehenden Kreis-Button am Sidebar-Rand; im eingeklappten Zustand
  öffnet Hover die Navigation temporär. Zustand in `body.nav-collapsed`, persistiert clientseitig.
- **Aktive Markierung:** `section` / `subSection` werden pro Seite via `th:with` gesetzt und steuern
  `active`-Klassen.
- **Kopfzeile:** `page-pretitle` (Bereich) + `page-title` (Seite), rechts ein `btn-list` mit
  Benachrichtigungsglocke, Einstellungen, Theme-Umschalter — sowie im Bereich *Buchungen* ein
  globaler Vertrags-Selektor (`globalEmployeeContractId`).
- **Nutzerkarte** unten in der Sidebar: Gravatar, Name, Login-Kürzel, Rollen-Badge, Logout,
  optional Benutzerwechsel (Impersonation) über ein Modal.

**Seitentitel** kommen aus dem Model (`title`, `sectionTitle`, `pageTitle`); Browser-Titel ist
immer `SALAT - <pageTitle>`.

## 4. Seitentypen

### 4.1 Listenansicht (Standardfall, 12+ Seiten)

Kanonischer Aufbau — Referenz: `customer/customer-list.html`, `order/sub-order-list.html`:

1. **Filterkarte** (`card` > `card-body` > `form method="get"`): Freitextfeld, Filter-Button
   (`btn-primary`, nur Icon), rechtsbündig die Anlege-Aktion (`btn-success`), darunter eine
   zweite Zeile mit Umschaltern.
2. **Tabellenkarte** (`table-responsive card` > `table table-vcenter card-table`).
3. **Aktionsspalten** rechts, je `<th class="w-1">`: Bearbeiten (`btn-outline-primary`, Stift),
   Löschen (`btn-outline-danger`, Mülleimer, in einem POST-Formular mit `confirm()`).
4. **Flags-Spalte** vor den Aktionen (siehe 5.4).
5. **Leerzustand** als einzelne Tabellenzeile: `text-center text-muted py-4`, Text
   `#{main.general.norecords.text}`.
6. **Auto-Submit-Skript** am Seitenende: Änderung einer Checkbox löst `form.requestSubmit()` aus.

**Filter-Umschalter** (dokumentiert in AGENTS.md): zwei unabhängige Booleans —
`show` (abgelaufene/ungültige Datensätze einbeziehen) und `showHidden` (ausgeblendete einbeziehen),
beide als `form-check form-switch`.

**Gruppierte Listen:** Wenn nach einem kategorialen Feld partitioniert wird, entsteht *pro Gruppe*
eine eigene Karte mit Tabelle; die Gruppenspalte entfällt dafür in der Tabelle. Leere Gruppen
werden ausgeblendet. Beispiele: Mitarbeiterliste (nach Status), Vertragsliste (Intern/Freelancer).

**Nicht vorhanden:** Paginierung, sortierbare Spaltenköpfe, Mehrfachauswahl/Bulk-Aktionen,
Spaltenkonfiguration, Export direkt aus der Liste. Listen werden vollständig gerendert.

### 4.2 Formular

Referenz: `customer/customer-form.html` — durchgängig über den `salat:`-Dialekt:

```html
<salat:form th:action="@{/customers/store}" th:object="${customer}" th:id-property="*{id}">
  <salat:inputs>                          <!-- card-body -->
    <div class="row">                      <!-- 2 Spalten via col-md-6 -->
      <salat:textInput th:field="*{shortName}" th:label="#{...}" required="true" maxlength="12"/>
      <salat:select    th:field="*{segmentId}" th:label="#{...}">…</salat:select>
    </div>
    <salat:textarea       th:field="*{address}" th:label="#{...}" rows="3"/>
    <salat:checkboxSwitch th:field="*{hide}"    th:label="#{...}"/>
  </salat:inputs>
  <salat:buttons>                          <!-- card-footer -->
    <salat:formButtons th:saveLabel="…" th:cancelHref="@{/customers}"/>
  </salat:buttons>
</salat:form>
```

**Grundgerüst:** Ein Formular = **eine Karte**. Felder im `card-body`, Aktionen im `card-footer`.
Das Markup wird vom `FormProcessor` erzeugt (`<form method="post" class="card">`), inklusive
verstecktem ID-Feld. Formulare nehmen die **volle Breite** des `container-fluid` ein — es gibt
keine Maximalbreite und keine zentrierte Formularspalte.

#### Label-Positionierung

| Regel | Umsetzung |
|---|---|
| **Position** | Label **immer oberhalb** des Controls, linksbündig, eigene Zeile |
| **Klasse** | `form-label`; Pflichtfeld ⇒ zusätzlich `required` (Tabler setzt einen roten Marker nach dem Text) |
| **Verknüpfung** | `for="<feldname>"` — vom `salat:`-Dialekt und den Fragmenten automatisch gesetzt; in handgeschriebenem Markup teilweise **fehlend** (z. B. `timereport-form.html:35`, `:81`) |
| **Abstand** | Feldblock in `mb-3`; kein eigener Abstand zwischen Label und Control (Bootstrap-Default) |
| **Ausnahme Schalter** | Bei `form-check form-switch` **umschließt** das Label den Input, Text steht **rechts** vom Schalter (`form-check-label`) — der einzige Fall mit Label nicht oben |
| **Ausnahme horizontal** | Nur `dailyreport/acceptance.html` verwendet 2× `col-form-label` (Label neben dem Feld) |
| **Abweichung** | `invoice-form.html` setzt zusätzlich `fw-semibold`, teils `text-muted small` bei Unter-Labels |

Reihenfolge im Feldblock, durchgängig:
`label.form-label` → Control → `div.form-text` (Hilfetext, optional) → `div.invalid-feedback` (Fehler).
Das Control erhält im Fehlerfall `is-invalid` über `th:errorclass`.

#### Felder pro Zeile

Raster über Bootstrap-`row`/`col-*`, Umbruch immer am **`md`**-Breakpoint (≥768 px):

| Muster | Verwendung | Beispiel |
|---|---|---|
| 1 Feld, volle Breite | Langtexte, Einzel-Selects, Schalter — häufigster Fall | `customer-form` (Name, Adresse), `budget-form` (Name) |
| 2 Felder — `col-md-6` | Standard-Paarung, z. B. Von-/Bis-Datum | `customer-form`, `budget-form`, `employee-contract-form` |
| 3 Felder — `col-md-4` | dichte Zeilen, meist Selects oder Betrag + Zeitraum | `sub-order-form` (Kunde/Auftrag/Übergeordnet), `pricing-form` |
| 5 Felder — `col-md-2` | Reihe von Schaltern (Flags) | `sub-order-form:123-149` |
| 3 Felder — `col-12 col-md-4` | einziger Ort mit explizitem Mobil-Verhalten | `invoice-form` |
| gemischt — `col-md-3 / -2 / -5 / col-auto` | Inline-Erfassungszeile mit `align-items-end`, Button in der Zeile | `employee-contract-form:129-146` (Überstunden) |
| `col-auto` | Feld in natürlicher Breite, Breite per Inline-`style` | `timereport-form` (Datum + Dauer) |

**Faustregel im Bestand:** maximal 3 Felder pro Zeile für Eingaben, bis zu 5 für Schalter.
Gutter-Klassen sind nicht vereinheitlicht — `row` ohne Angabe (Bootstrap-Default, kein vertikaler
Abstand), `g-2` in Filterzeilen, `g-3` in `timereport-form` und `invoice-form`.
`employee-form` verwendet **kein** Raster: jedes Feld eine eigene, volle Zeile.

Zweispaltiges **Seitenlayout** nur in `timereport-form`: `col-lg-7` Formular, `col-lg-5` Kontext-Sidebar
(Buchungen des Tages, letzte Kommentare zum Wiederverwenden).

#### Button-Positionierung im Formular

- **Ort:** immer im `card-footer`, also unterhalb des Feldbereichs, durch die Kartenkante abgesetzt.
- **Ausrichtung:** `d-flex gap-2` — **linksbündig**, in Leserichtung unter dem ersten Feld.
  Keine rechtsbündigen Formular-Footer im Projekt.
- **Reihenfolge:** Primäraktion zuerst, dann Abbrechen:
  `[💾 Speichern] [Abbrechen]`
- **Speichern** `btn btn-primary` mit `ti ti-device-floppy`; Label kontextabhängig
  (`main.button.label.save.text` / `.edit.text` bzw. `…button.create.text`).
- **Abbrechen** `btn btn-secondary` als `<a>` zurück zur Liste — **Text hartcodiert `"Cancel"`**
  (sowohl im `FormButtonsProcessor` als auch im Fragment; siehe Diskussionspunkt 11).
- **Zusatzaktionen** werden zwischen Speichern und Abbrechen eingeschoben, Abbrechen wandert dann
  per `ms-auto` nach rechts — nur `timereport-form:165`:
  `[💾 Speichern] [★ Als Favorit speichern] ······ [Abbrechen]`
- **Inline-Buttons im Feldbereich** stehen direkt neben ihrem Feld in einem `d-flex gap-2`
  (Kürzel generieren in `sub-order-form:73`, `btn-outline-secondary`, nur Icon).
- **Progressive Disclosure:** optionale Blöcke werden über einen `btn-outline-secondary` im
  Feldbereich aufgeklappt (Serienbuchung in `timereport-form:143`), nicht über Tabs.

#### Destruktive Aktionen im Formular

Nicht im Footer, sondern als **eigene Karte unterhalb** des Formulars: `card border-danger` mit
`card-title text-danger` („Danger Zone"), erklärendem Absatz und `btn-danger`, der ein Modal öffnet.
Im Modal muss das Mitarbeiterkürzel **zweimal** eingegeben werden; der Submit-Button bleibt bis zur
Übereinstimmung `disabled`. Referenz: `employee/employee-form.html:76-130` — die einzige Stelle mit
dieser Absicherung.

#### Validierung

Serverseitig. Feldfehler via `BindingResult` → `th:errors` am Feld; fachliche Fehler
(`ErrorCodeException`) als `errors`-Liste im Alert-Bereich oben auf der Seite. Bei Fehlern wird das
Formular neu gerendert, **kein** Redirect — eingegebene Werte bleiben erhalten.
`required` wird zusätzlich als HTML-Attribut gesetzt, die Browser-Validierung greift also zuerst.
Dynamische Pflichtfelder sind möglich: bei Auswahl eines Unterauftrags mit Kommentarpflicht wird
`required` per JS an Label und Feld gesetzt (`updateCommentRequired`).

**Tabulator-Reihenfolge** wird per Skript (`applyFormTabOrder` in `salat.js`) über alle fokussierbaren
Elemente in `.page-body` neu gesetzt (`tabIndex = i + 1`), bei TomSelect auf das interne Suchfeld.

### 4.3 Dashboard

`dailyreport/dashboard.html`, `budget/dashboard.html`: Raster gleich hoher Karten (`card h-100 w-100`).
KPI-Karten zeigen Label (`text-muted`, klein) über einer großen Zahl (`h1` bzw. `display-5 fw-bold`),
Status-KPIs als große Badge in Semantikfarbe. Darunter Listen-/Tabellenkarten mit
`card-header` + `card-title` (Titel mit vorangestelltem Icon in `text-muted`) und Links als
`card card-link` (ganze Karte klickbar).

### 4.4 Spezialansichten

`dailyreport/matrix.html` (Matrixübersicht), `dailyreport/daily.html` (Einzelübersicht),
`budget/controlling.html`, `reporting/report-result.html`. Diese weichen bewusst ab, tragen die
meisten Inline-Styles und sind am dichtesten — die primären Kandidaten für eine UX-Betrachtung.

## 5. Komponenten-Inventar

### 5.1 Karten
Einziger Container. `card mb-3`, optional `card-header` + `h3.card-title`, Inhalt in `card-body`,
Aktionen in `card-footer`. Tabellen sitzen direkt in der Karte (`card-table`, ohne `card-body`).

### 5.2 Buttons

| Verwendung | Klasse | Icon |
|---|---|---|
| Primäraktion Formular (Speichern) | `btn btn-primary` | `ti ti-device-floppy` |
| Abbrechen | `btn btn-secondary` | — |
| Anlegen (in Listen, rechts oben) | `btn btn-success` | `ti ti-*-plus` |
| Filter anwenden | `btn btn-primary` | `ti ti-filter` |
| Zeilenaktion Bearbeiten | `btn btn-outline-primary` | `ti ti-pencil` |
| Zeilenaktion Löschen | `btn btn-outline-danger` | `ti ti-trash` |
| Sekundäre Zeilenaktion | `btn btn-outline-secondary btn-sm` | wechselnd |
| Icon-Button Kopfzeile | `btn btn-icon` | `ti ti-*` |
| Destruktiv (Danger Zone, Modal) | `btn btn-danger` | — |
| Modal-Abbruch | `btn btn-link link-secondary me-auto` | — |
| Umschaltgruppe (Dauer ↔ Von/Bis) | `btn-group` > `btn btn-outline-secondary` + `active` | `ti ti-hourglass` / `ti ti-clock-2` |
| Zeilenaktion in dichten Flächen | `btn btn-ghost-danger btn-sm` | `ti ti-trash` |
| Zustands-Toggle (Budget aktiv/inaktiv) | `btn-outline-warning` ⇄ `btn-outline-success` | `ti ti-player-pause` ⇄ `ti ti-player-play` |
| Vorgang zurücknehmen (Monat wieder öffnen) | `btn btn-warning` | — |
| Erinnerungsmail in Tabellenzelle | `btn btn-sm btn-ghost-warning` | `bi bi-envelope` |

Icon-only-Buttons tragen `m-0` am `<i>`, Buttons mit Text `me-1`. In Listen sind Zeilenaktionen
**ohne** Textlabel.

#### Farblogik der Buttons

Weder AGENTS.md noch ein ADR regeln Button-Farben; die folgende Systematik ist **aus dem Bestand
abgeleitet**. Vollständiges Inventar (Vorkommen in `src/main/resources/templates`, 148 insgesamt;
die vom `FormButtonsProcessor` in Java erzeugten Speichern-/Abbrechen-Buttons sind nicht mitgezählt):

| Variante | Anzahl | Wofür im Bestand |
|---|---|---|
| `btn-outline-secondary` | 31 | alles Sekundäre: Zeilenaktionen ohne Semantik, Aufklapp-Buttons, Dropdown-Trigger, Umschaltgruppen |
| `btn-primary` | 28 | Speichern, Filter anwenden, Vorgang absenden (Freigeben, Import, Vorschau, Drucken, Teilen) |
| `btn-success` | 19 | Anlegen (14×) **sowie** Report/Job ausführen (2×), CSV-Export, Monat abnehmen, Folgeaktion im Erfolgs-Alert (`toastAction`) |
| `btn-outline-primary` | 18 | Zeilenaktion Bearbeiten |
| `btn-outline-danger` | 16 | Zeilenaktion Löschen |
| `btn-secondary` | 12 | Abbrechen/Zurück (10×) — plus zwei Fälle als **zweite gleichrangige Aktion**: „Anlegen & Neu" (`employee-order-form:129`) und Excel-Export (`invoice-form:353`) |
| `btn-ghost-danger` | 7 | Löschen in dichten Flächen (Benachrichtigungen, Favoriten, Nutzerkarte) |
| `btn-outline-success` | 4 | Aktivieren (Zustands-Toggle, 2×), Buchung teilen (`daily.html:330`, `daily-list-card.html:66`) |
| `btn-danger` | 4 | Danger Zone und deren Modal-Bestätigung |
| `btn-outline-warning` | 2 | Deaktivieren (Zustands-Toggle) |
| `btn-ghost-warning` | 2 | Erinnerungsmail bei überfälliger Freigabe/Abnahme |
| `btn-ghost-secondary` | 2 | Icon-Aktionen in der Nutzerkarte |
| `btn-warning` | 1 | Monat wieder öffnen (Admin, `acceptance.html:117`) |
| `btn-link` | 2 | Abbrechen im Modal (`link-secondary me-auto`), Hinweis ausblenden (`daily.html:56`) |

**Nicht verwendet:** `btn-info`, `btn-dark`, `btn-light` und die Tabler-Sonderfarben
(`btn-azure`, `btn-purple`, …). `purple`, `azure`, `green`, `red` erscheinen ausschließlich bei
Badges und Text, nie an Buttons.

Drei Achsen bestimmen die Klasse:

**1. Füllung = Gewicht im Layout** (nicht Wichtigkeit der Aktion)

| Füllung | Bedeutung | Ort |
|---|---|---|
| gefüllt `btn-*` | Aktion, die Daten ändert oder einen Vorgang startet — **höchstens eine farbige pro Kartenbereich**, weitere gefüllte Buttons daneben sind `btn-secondary` | Formular-Footer, Filterzeile, Karten-Footer |
| `btn-outline-*` | Zeilen- und Nebenaktion, tabellenweise wiederholt | Tabellen-Aktionsspalten, Inline-Buttons |
| `btn-ghost-*` | wie outline, aber rahmenlos — Farbe erst beim Hover; für dichte Flächen, immer mit `btn-sm` | Benachrichtigungsliste, Favoritenliste, Nutzerkarte, `daily.html` |
| `btn-link` | Aktion, die wie ein Link lesen soll | Modal-Abbruch, Hinweis wegklicken |

**2. Farbe = Bedeutung der Aktion**

| Farbe | Aussage | Beispiele |
|---|---|---|
| `primary` | „Standardweg dieses Formulars" | Speichern, Filter, Freigeben, Import, Teilen, Drucken |
| `success` | „es entsteht etwas Neues" **oder** „führe aus" | Anlegen, Report ausführen, CSV-Export, Monat abnehmen |
| `warning` | „Rücknahme / Zurückschalten" — reversibel, aber begründungspflichtig | Monat wieder öffnen, Budget deaktivieren, Erinnerungsmail |
| `danger` | „unwiderruflicher Verlust" | Löschen, Anonymisieren |
| `secondary` | „kein Effekt auf Daten" | Abbrechen, Ansicht umschalten, Aufklappen |

**3. Zustandsabhängige Farbe** — genau ein Muster: der Aktiv-Toggle in `budget-list.html:94` und
`budget-detail.html:25` wechselt zwischen `btn-outline-warning` (aktiv ⇒ Klick deaktiviert) und
`btn-outline-success` (inaktiv ⇒ Klick aktiviert), Icon parallel `ti-player-pause` ⇄ `ti-player-play`.
Die Farbe beschreibt also die **Wirkung des Klicks**, nicht den aktuellen Zustand.

Als Referenz für den Prozessfall dient `dailyreport/acceptance.html`: drei Formulare in einer Karte,
drei Farben für drei Schritte desselben Ablaufs — Freigeben `btn-primary` (Z. 84),
Abnehmen `btn-success` (Z. 100), Wieder-Öffnen `btn-warning` (Z. 117, nur Admin).
Die Farbe kodiert hier den Prozessschritt, nicht die Hierarchie (siehe Diskussionspunkt 28).

**Zwei gleichrangige Aktionen** nebeneinander werden nach dem Muster *erste gefüllt-farbig, zweite
grau* gebaut: Speichern `btn-primary` + „Anlegen & Neu" `btn-secondary`
(`employee-order-form:125/129`), Druckansicht `btn-primary` + Excel-Export `btn-secondary`
(`invoice-form:351/353`). Derselbe Export ist in `csv.html:234` dagegen grün — die Regel gilt also
nicht durchgängig.

**Ausrichtung nach Ort:**

| Ort | Ausrichtung |
|---|---|
| Formular-Footer | linksbündig, Primäraktion zuerst (`d-flex gap-2`) |
| Filterzeile in Listen | Filter-Button links neben dem Feld, Anlege-Aktion rechts (`col-auto ms-auto`) |
| Zeilenaktionen in Tabellen | rechts, je eigene Spalte `w-1`, Reihenfolge Bearbeiten → Löschen |
| Modal-Footer | Abbrechen links (`me-auto`), Bestätigung rechts |
| Karten-Footer mit Zusatzaktion | Primär + Zusatz links, Abbrechen per `ms-auto` rechts |

### 5.3 Auswahlfelder
Alle `<select>` werden per [TomSelect](https://tom-select.github.io/) zu Suchfeldern aufgewertet —
zentral initialisiert in `salat.js`, auch nach HTMX-Swaps (`htmx:afterSettle`).
Klassenvertrag: `tomselect` = Einzelauswahl, `tomselect tomselect-multi` + `multiple` = Mehrfachauswahl.
Optionen können über `data-subtext` eine zweite Zeile anzeigen (z. B. Vertragslaufzeit).

### 5.4 Badges & Flags-Spalte
Boolesche Zustände in Listen stehen gesammelt in einer **Flags-Spalte**
(`d-none d-lg-table-cell`), nie inline neben dem Namen. Jedes Flag ist eine Badge in gedeckter
Tabler-Tönung (`bg-*-lt`) mit Icon und `title`-Tooltip:

| Flag | Farbe | Icon |
|---|---|---|
| ausgeblendet (`hide`) | `bg-danger-lt` | `bi-eye-slash` (bzw. `bi-eye` transparent, wenn sichtbar) |
| fakturierbar | `bg-success-lt` | `bi-cash-stack` |
| Standard | `bg-warning-lt` | `bi-bookmark-star-fill` |
| Kommentar erforderlich | `bg-danger-lt` | `bi-chat-square-text` |
| Festpreis | `bg-primary-lt` | `bi-tag-fill` |
| Schulung | `bg-purple-lt` | `bi-mortarboard` |

Für Manager ist die `hide`-Badge ein **klickbarer Inline-Toggle** (HTMX-POST, tauscht nur die
Zelle) — realisiert in `fragments/hide-toggle.html`, dem einzigen verbliebenen Fragment mit
fachlicher Logik.

### 5.5 Rückmeldungen
- **Erfolg/Fehler nach Redirect:** Flash-Attribute `toastSuccess` / `toastError` / `toastErrors`
  → in `base.html` als schließbarer `alert alert-success|danger` **oberhalb des Seiteninhalts**
  (trotz des Namens keine echten Toasts: keine Overlay-Position, kein Auto-Dismiss).
  `toastSuccess` kann eine Folgeaktion als Button tragen (`toastAction` / `toastActionLabel`).
  Mehrere Fehler werden zusammengefasst: erster sichtbar, Rest in einem `<details>`.
- **Löschbestätigung:** natives `confirm()` per `th:onsubmit` — 23 Verwendungen.
- **Modale Dialoge:** nur an 4 Stellen (Benutzerwechsel, Buchungsdetails, Mitarbeiterformular).

### 5.6 HTMX-Muster
`th:hx-post` / `hx-get`, `hx-include="closest form"`, `hx-target`, `hx-swap`; Controller erkennt
`HX-Request` und liefert `"view :: fragment"`. Eingesetzt für: abhängige Auswahlfelder in
Auftragsformularen, Inline-Toggles, Benachrichtigungsglocke, Buchungs-Popover in der Matrix.
CSRF-Token werden in `salat.js` per `htmx:configRequest` nachgezogen.

## 6. Eingabekomponenten nach Datentyp

Übersicht aller im Projekt tatsächlich verwendeten Controls. Zahlen = Vorkommen in Templates.

| Datentyp | Control | Anzahl | Details |
|---|---|---|---|
| Kurztext | `input type="text"` + `maxlength` | 33 | `maxlength` immer gesetzt (12 / 30 / 70 / 255), spiegelt die DB-Spalte |
| Langtext | `textarea rows="3"` | — | `rows` explizit, Standard 3; `monospace=true` für SQL (bis `rows=15`) |
| Auswahl | `select.form-select.tomselect` | — | siehe 5.3; suchbar, optional `data-subtext` als zweite Zeile |
| Ja/Nein | `input type="checkbox"` in `form-check form-switch` | 42 | **immer als Schalter**, nie als klassische Checkbox-Optik |
| Datum | `input type="date"` | 19 | **native Browser-Datumsauswahl**, kein JS-Datepicker im Projekt |
| Monat | `input type="month"` | 6 | für monatsbezogene Vorgänge (Freigabe, Abnahme, Rechnung, Matrix) |
| Uhrzeit | `input type="time"` | 7 | nur im Von/Bis-Modus der Buchungserfassung |
| Dauer | `input type="text"` + Eingabemaske | — | Sonderfall, siehe unten |
| Zahl | `input type="number"` | 3 | mit `step="0.01"` (Betrag) bzw. `min="0" max="100"` (Prozent) |
| Geldbetrag | `input type="text"` | — | `priceEuro` / `costEuro` als **Text**, Einheit nur im Label („€/h") |
| Datei | `input type="file"` | 2 | CSV-Import, Umsatz-Upload |
| Einfachauswahl aus 2 Optionen | `input type="radio"` | 2 | seltene Ausnahme |

**Kein** Datentyp verwendet `input-group` (Prefix/Suffix-Addons) — Einheiten wie €, Stunden oder
Prozent stehen ausschließlich im Label, nicht am Feld.

### Dauer — der Sonderfall

Dauern sind die häufigste Eingabe der Anwendung und **kein Standard-Control**. Format durchgängig
`H:MM` (`DurationUtils.format`, Null-Wert = `0:00`, negative Werte mit führendem `-`).

Erfassung in `timereport-form.html` über zwei Modi, umschaltbar per Icon-`btn-group`:

1. **Dauer** (Standard) — `input type="text"`, `placeholder="HH:MM"`, `maxlength="5"`,
   `inputmode="numeric"`, `style="width:100px"`, `onfocus="this.select()"`.
   Eine JS-Maske (`durationMask`) setzt den Doppelpunkt während der Eingabe, `durationBlur`
   normalisiert beim Verlassen: `7` → `07:00`, `30` → `00:30`, `130` → `01:30`.
2. **Von/Bis** — zwei `input type="time"` (`width:120px`) mit „–" dazwischen; die berechnete Dauer
   erscheint live als `badge bg-secondary-lt` rechts daneben.

Der gewählte Modus liegt in einem versteckten Feld (`durationMode`) und wird nach einem
Validierungsfehler wiederhergestellt. Bei einer laufenden Buchung („live booking") schaltet die
Maske automatisch in den Von/Bis-Modus, füllt die Startzeit und rundet das Ende auf 15 Minuten auf.

**Andere Dauer-Felder nutzen diesen Komfort nicht:** Überstunden in `employee-contract-form:136`
sind ein einfaches Textfeld mit `placeholder="e.g. 1:30"` (englischer Platzhaltertext im deutschen
Formular), Sollstunden in `sub-order-form:154` ein Textfeld mit `maxlength="10"` ohne Maske und
ohne Format-Hinweis.

### Anzeige (nicht Eingabe)

| Wert | Darstellung |
|---|---|
| Dauer in Tabellen/Badges | `H:MM` rechtsbündig (`text-end`), in Badges `bg-blue-lt` |
| Dauer als Arbeitstage | `TT:HH:MM` (`formatWithWorkingdays`, z. B. Fortbildungskonten) |
| Datum in Listen | `text-nowrap`, Formatierung über `#temporals.format` mit `#locale` |
| Geldbetrag | rechtsbündig, Einheit im Spaltenkopf („Budget (€)") |
| Saldo/Delta | Vorzeichenfarbe `text-success` / `text-danger` plus Richtungs-Icon (`bi-arrow-up-circle-fill` / `-down-`) |

## 7. Farbsemantik

| Farbe | Bedeutung im Projekt |
|---|---|
| `primary` (blau) | Standard-/Bestätigungsaktion, Bearbeiten |
| `success` (grün) | Anlegen, fakturierbar, positiver Saldo |
| `warning` (gelb) | Aufmerksamkeit ohne Fehler (abgelaufener Vertrag, fehlende Freigabe), Standard-Flag, Rücknahme eines Vorgangs |
| `danger` (rot) | Löschen, Fehler, ausgeblendet, Kommentarpflicht |
| `secondary` (grau) | neutral/inaktiv, Abbrechen |
| `purple` | Rollen-Badge, Schulung, „Beta"-Markierung |
| `azure` / `blue` | informative Kennzeichnung in Listen |

`bg-*-lt` (light tint) für Badges/Flächen, `text-*` für Zahlen und Fließtext-Akzente,
`btn-*` gefüllt für Primäraktionen, `btn-outline-*` für Zeilenaktionen.
Häufigste Utility überhaupt: `text-muted` (222×) für sekundären Text.

Die Farbwahl bei Buttons ist ausführlich in [§5.2 Farblogik der Buttons](#farblogik-der-buttons)
beschrieben, Gewicht und Dämpfung von Text in [§8.1](#81-textgewicht--wann-fettdruck) und
[§8.2](#82-sekundärtext--wann-text-muted-wann-small).

## 8. Typografie & Abstände

- Nur Tabler-Skala: `page-title` (h2) für Seiten, `card-title` (h3) für Karten,
  `h1` / `display-5 fw-bold` für KPI-Zahlen, `small` / `text-muted` für Sekundärtext.
- Vertikaler Rhythmus über Bootstrap-Utilities: `mb-3` zwischen Karten und Formularfeldern,
  `my-4` um Alerts, `g-2` in Filterzeilen, `gap-2` in Button-Gruppen.
- Zahlen und Zeitwerte in Tabellen rechtsbündig (`text-end`, 80×), Datums-/Kürzelspalten
  `text-nowrap` (82×).

Explizite Größenklassen sind die Ausnahme: `fs-3` / `fs-4` / `fs-5` je **1×**, `display-5` 8×
(KPI-Zahlen), `display-4` 1× (Fehlercode). Alles andere folgt der Vererbung aus `card-title`,
`page-title` und `small`.

### 8.1 Textgewicht — wann Fettdruck

Drei Gewichtsklassen im Einsatz, mit klar getrennten Rollen (Anzahl = Vorkommen in Templates):

| Klasse | Anzahl | Rolle im Bestand |
|---|---|---|
| `fw-medium` | 41 | **Bezeichner in dichten Flächen** — erste Zeile einer mehrzeiligen Zelle oder Karte: Buchungszeile (`Auftrag · Unterauftrag`), Favoritenlabel, Benachrichtigungstitel, Mitarbeitername in der Impersonation-Liste, Feldnamen der CSV-Doku, Zwischensummen und Gruppenzeilen der Matrix, Beschriftung der Schnellzugriff-Kacheln (`card-link`) |
| `fw-bold` | 26 | **Zahlen und Summen** — KPI-Werte (`display-5 fw-bold`), Summenzeilen (`<tr class="fw-bold">` in `my-accounts`, `invoice-form`, `controlling` zusätzlich `table-active`), Gesamtspalte der Matrix; außerdem der schreibgeschützte Mitarbeitername (`form-control-plaintext fw-bold`) und das „Total:"-Label im Überstundenblock |
| `fw-semibold` | 23 | **zwei Rollen**: (a) Labels in `acceptance`, `release`, `invoice-form` — Abweichung vom Standard-`form-label` (siehe §4.2); (b) Hervorhebung eines Werts gegenüber seiner Umgebung: ungelesene Benachrichtigung (`<tr class="… fw-semibold">`), überfällige Frist (`text-danger fw-semibold`), Prozentwert im KPI-Block |
| `fw-normal` | 1 | einmalig, um eine geerbte Fettung zurückzunehmen |

Die im Bestand erkennbare Regel:

- **Zahl mit Aussagekraft → `fw-bold`.** Summen- und Gesamtzeilen sind immer fett, nie zusätzlich
  farbig; die Abgrenzung kommt über `table-active` bzw. `table-group-divider`.
- **Bezeichner einer Zeile → `fw-medium`**, die Zusatzinformation direkt darunter
  `text-muted small`. Das ist das Standardpaar für zweizeilige Zellen und Listeneinträge.
- **„Muss beachtet werden" → `fw-semibold` + Semantikfarbe**, nie Fettung allein
  (`text-danger fw-semibold` bei überfälliger Freigabe).
- **Fließtext, normale Tabellenzellen, Standard-Labels → keine Gewichtsklasse.** Tabler setzt
  `form-label` und `card-title` schon ab; zusätzliches `fw-semibold` am Label ist die Ausnahme
  in drei Templates, kein Muster.
- Fettung ersetzt keine Überschrift: für Titel gilt weiterhin `card-title` / `page-title`.

### 8.2 Sekundärtext — wann `text-muted`, wann `small`

`text-muted` ist die häufigste Utility-Klasse der Anwendung (222×), `small` die zweithäufigste
(204×); das kanonische Paar ist **`text-muted small`** (41×).

| Ausprägung | Verwendung |
|---|---|
| `text-muted` allein (63×) | Hilfetext unter einem Feld (`form-text`), KPI-Label über der Zahl, Icon vor einem `card-title`, sekundäre Tabellenspalte (z. B. People-Lead-Spalte), Beschriftungsspalte in Detailtabellen (`text-muted ps-4 pe-2 py-2 text-nowrap`, 11×) |
| `text-muted small` (41×) | zweite Zeile unter einem `fw-medium`-Bezeichner, Metainformation (Zeitstempel, Kürzel, Herkunft), Hinweistext neben einem Button |
| `text-center text-muted py-4` (10×) | **Leerzustand** — einheitlich in Tabellenzeilen und leeren Karten (`card-body text-center text-muted py-4`, 3×) |
| `small` allein (10×) | reine Größenreduktion ohne Bedeutungswechsel, meist in dichten Tabellen |
| `text-muted text-decoration-line-through` (1×) | entwerteter Wert (überschriebene Position) |

**Abgrenzung:** `text-muted` senkt die **Bedeutung**, `small` die **Fläche**. Beides zusammen nur
für echte Metainformation — eine sekundäre Tabellenspalte bleibt in Normalgröße und wird nur
gedämpft, damit Zeilen nicht unterschiedlich hoch werden.

`text-secondary` (25×) sieht faktisch gleich aus und ist **nicht** nach Bedeutung, sondern nach
Herkunft verteilt: es steht in den zentral gepflegten bzw. später gebauten Templates
(`layout/base.html`: Nutzerkarte und Alert-Texte; `notification/*`; `error.html`; Zwischenzeilen in
`invoice-form`; `revenue-upload`), während alle Modul-Templates `text-muted` verwenden. Für
Neubauten gilt daher: `text-muted` folgt dem Bestand, ist aber in Bootstrap 5.3 abgekündigt
(→ W12).

**Ergebnis-Leiter** von stark nach schwach, wie sie sich im Bestand zeigt:

```
display-5 fw-bold      KPI-Zahl
fw-bold                Summe, Gesamtwert
fw-semibold (+ Farbe)  Wert, der Aufmerksamkeit braucht
fw-medium              Bezeichner einer Zeile
(ohne Klasse)          Fließtext, Tabellenwert
text-muted             sekundäre Angabe, Hilfetext, Label
text-muted small       Metainformation, zweite Zeile
```

## 9. Responsive-Strategie

Ein einziges Muster: **Spalten ausblenden statt umbrechen.** Tabellenspalten tragen
`d-none d-sm-table-cell` / `d-md-` / `d-lg-` und verschwinden von rechts nach links; die
Flags-Spalte erst ab `lg`. Die Sidebar kollabiert unter `md` in einen Navbar-Toggler.
Es gibt keine dedizierten mobilen Layouts, keine Karten-Ansicht als Tabellen-Ersatz.
Faktisch ist SALAT eine Desktop-Anwendung, die auf kleinen Displays benutzbar bleibt.

## 10. Sprache & Terminologie

Alle Texte kommen aus `MessageResources.properties` (Deutsch, Default) und
`MessageResources_en.properties` — UTF-8, alphabetisch sortiert, Schlüsselschema `main.<modul>.<ding>.text`.

| Deutsch (UI) | Technisch | Bedeutung |
|---|---|---|
| Buchung | Timereport | einzelner Zeiteintrag |
| Auftraggeber | Customer | Kunde |
| Auftrag | Customerorder | Kundenauftrag |
| Unterauftrag | Suborder | Gliederungsebene unter dem Auftrag |
| Mitarbeiterauftrag | Employeeorder | Zuordnung Person ↔ Unterauftrag |
| Freigabe | Release | Mitarbeitende geben ihren Monat frei |
| Abnahme | Acceptance | People Lead nimmt ab |
| ausblenden | `hide` | aus allen Auswahllisten entfernen, ohne zu löschen |

Neue Features werden in der Navigation mit `badge bg-green-lt` **New** bzw. `bg-purple-lt`
**Beta** markiert (aktuell 7 Einträge).

---

## Diskussionspunkte für UX

Bewusst als Fragen formuliert — offene Punkte, kein beschlossenes Backlog.

**Konsistenz**
1. **Zwei Icon-Sets** parallel (Tabler + Bootstrap Icons), in 7 Dateien gemischt. Auf eines
   konsolidieren? Tabler passt zum Design-System, Bootstrap Icons deckt die Fachflags ab.
2. **Anlegen ist grün (`btn-success`), Speichern blau (`btn-primary`)** — beides Primäraktionen.
   Gewollte Unterscheidung oder historisch gewachsen?
3. **„New"/„Beta"-Badges** in der Navigation haben kein Verfallsdatum. Wann wird etwas „normal"?

**Interaktion**
4. **Natives `confirm()`** für alle 23 Löschvorgänge: keine Angabe *was* gelöscht wird, nicht
   stilisiert, nicht übersetzbar über den i18n-Text hinaus. Alternative: Modal mit Objektnamen,
   oder Soft-Delete mit Undo.
5. **Rückmeldungen heißen „Toast", sind aber Inline-Alerts** oben auf der Seite — nach einem
   Redirect ggf. außerhalb des Blickfelds, kein Auto-Dismiss.
6. **Filter-Umschalter senden bei jeder Änderung das Formular ab** (Full Page Reload). Bei
   langen Listen spürbar; Scrollposition geht verloren.
7. **Zwei semantisch ähnliche, aber unabhängige Filter** (`show` = ungültige einbeziehen,
   `showHidden` = ausgeblendete einbeziehen). Für Nutzende schwer unterscheidbar — der
   Unterschied „manuell ausgeblendet" vs. „zeitlich abgelaufen" ist ein Modell-, kein UI-Problem.

**Listen**
8. **Keine Paginierung, keine Spaltensortierung, keine Bulk-Aktionen.** Listen werden komplett
   gerendert und in der Reihenfolge der Datenbankabfrage ausgegeben — die Sortierung ist
   teilweise nicht die der ersten sichtbaren Spalte (siehe Issue #824). Was brauchen die
   Vielnutzer wirklich: Sortierung, Suche, gespeicherte Filter?
9. **Responsive = Spalten verstecken.** Auf dem Smartphone bleiben von 7 Spalten 2 übrig,
   inklusive versteckter Flags — Information verschwindet ohne Hinweis.

**Formulare — Layout**
10. **Felder pro Zeile ist nicht geregelt** — 1, 2, 3 oder 5 Felder je nach Formular,
    `employee-form` ohne Raster. Formulare nehmen die volle Bildschirmbreite ein; ein zweispaltiges
    Formular ist auf einem 27"-Monitor sehr breit und die Labelspalte weit vom Wert entfernt.
    Braucht es eine Maximalbreite und eine feste Spaltenzahl?
11. **Der Abbrechen-Button heißt „Cancel"** — hartcodiert im `FormButtonsProcessor` und im
    Fragment, obwohl der passende Schlüssel existiert (→ W3). In einer deutsch-first-Anwendung
    auf jedem Formular sichtbar.
12. **Formular-Footer sind linksbündig**, Primäraktion zuerst — Zeilenaktionen in Listen dagegen
    rechts. Gewollter Unterschied?
13. **Drei Schreibweisen für dasselbe Formular** koexistieren: `salat:`-Dialekt (Zielzustand,
    ADR 0005), `th:replace`-Fragmente (Altbestand) und handgeschriebenes Bootstrap-Markup — teils
    **innerhalb eines Formulars** (`sub-order-form`). Sichtbare Folge: `for`-Attribute und
    Hilfetexte fehlen genau dort, wo handgeschrieben wurde.

**Formulare — Eingabekomponenten**
14. **Dauer-Eingabe ist zweiklassig:** die Buchungsmaske hat Eingabemaske, Von/Bis-Umschaltung und
    Live-Berechnung, alle anderen Dauer-Felder (Überstunden, Sollstunden) sind nackte Textfelder —
    eines davon mit englischem Platzhalter `e.g. 1:30`. Kandidat für eine gemeinsame Komponente.
15. **Geldbeträge sind Textfelder** ohne `step`, ohne Hinweis auf den Dezimaltrenner, Einheit nur
    im Label. Zahleneingabe generell inkonsistent: 3× `type="number"`, sonst `type="text"`.
16. **Keine `input-group`-Addons** — Einheiten (€, h, %) stehen nie am Feld. Bewusst schlicht oder
    einfach nie gebaut?
17. **Native Datums-/Zeit-Controls** (`type="date"`, `month`, `time`): Format und Bedienung folgen
    dem Betriebssystem, nicht der App — browserübergreifend also nicht konsistent, dafür
    barrierefrei und ohne JS. Bewusst so beibehalten?
18. **Danger-Zone-Muster mit Doppelbestätigung** existiert genau einmal (Mitarbeiter anonymisieren),
    während 23 Löschvorgänge mit `confirm()` auskommen. Welche Aktionen verdienen welche Hürde?

**Barrierefreiheit** (nicht auditiert, Beobachtungen aus dem Code)
19. **Icon-only-Buttons ohne `aria-label`**: `aria-label` erscheint nur in 3 Templates plus
    `base.html`. Zeilenaktionen (Bearbeiten/Löschen), der Filter-Button und die Flag-Toggles
    tragen teils nur ein `title`-Attribut, teils nichts — für Screenreader unbenannt.
20. **Fehlende `for`-Verknüpfung** an handgeschriebenen Labels (u. a. Datum und Dauer in der
    Buchungsmaske) — Klick auf das Label fokussiert das Feld nicht, Screenreader nennen es nicht.
21. **Zustand allein über Farbe/Icon**: Flags haben nur ein Tooltip als Textalternative.
22. **`applyFormTabOrder` setzt positive `tabindex`-Werte** (1..n) auf alle fokussierbaren
    Elemente in `.page-body`. Sollte gegen die natürliche DOM-Reihenfolge geprüft werden —
    positive `tabindex` gelten allgemein als Anti-Pattern.
23. **Kontrast** der `bg-*-lt`-Badges und von `text-muted` (die häufigste Textklasse) im
    Dark-Theme ist ungeprüft.
24. **Sidebar öffnet im eingeklappten Zustand per Hover** — mit Tastatur oder Touch nicht
    gleichwertig erreichbar.

**Struktur**
25. **Die dichten Spezialansichten** (Matrixübersicht, Einzelübersicht, Soll-Ist-Controlling)
    folgen eigenen Mustern und tragen die meisten Inline-Styles. Sie sind die eigentlichen
    Arbeitsflächen der Vielnutzer und der lohnendste Startpunkt für UX-Arbeit.
26. **Sidebar ist immer dunkel**, unabhängig vom Light/Dark-Theme. Absicht oder Tabler-Default?
27. **Kein Onboarding, keine kontextuelle Hilfe** außer `form-text`-Hilfetexten an einzelnen
    Feldern; die Fachbegriffe (Unterauftrag vs. Mitarbeiterauftrag, Freigabe vs. Abnahme) sind
    erklärungsbedürftig.

**Farbe & Textgewicht**
28. **Grün trägt zwei Bedeutungen** (§5.2): „es entsteht etwas Neues" (Anlegen, 14×) und
    „führe aus" (Report starten, CSV-Export, Monat abnehmen, 5×). Am deutlichsten in
    `acceptance.html`: **Freigeben ist blau, Abnehmen grün, Wieder-Öffnen gelb** — drei Schritte
    desselben Ablaufs in drei Farben. Soll die Farbe den Prozessschritt oder die Hierarchie
    kodieren?
29. **Vier Füllstufen** (`btn-*`, `btn-outline-*`, `btn-ghost-*`, `btn-link`) ohne dokumentierte
    Zuordnung. `btn-ghost-*` (11×) existiert nur in den jüngsten Templates (Benachrichtigungen,
    Einzelübersicht, Nutzerkarte) — bewusste Stufe für dichte Flächen oder parallel gewachsen?
    Praktische Folge: rahmenlose Buttons sind ohne Hover kaum als Buttons erkennbar.
30. **Drei Gewichtsstufen, `fw-semibold` in zwei Rollen** (§8.1): einmal als Label-Auszeichnung
    (`acceptance`, `release`, `invoice-form` — dort weichen die Labels vom restlichen Projekt ab),
    einmal als inhaltliche Hervorhebung (ungelesen, überfällig). Braucht es eine feste Leiter
    (Bezeichner = `fw-medium`, Zahl/Summe = `fw-bold`, Hervorhebung = `fw-semibold`) und ein
    Verbot von `fw-semibold` am Label?

---

## Widersprüche & Inkonsistenzen

Anders als die Diskussionspunkte oben sind das keine Designfragen, sondern **Stellen, an denen zwei
Quellen einander widersprechen** oder gleiche Sachverhalte unterschiedlich gelöst sind. Jeder Punkt
ist am Code verifiziert. Die meisten sind ohne UX-Entscheidung auflösbar — sie gehören ins Backlog,
nicht ins Gespräch.

### A — Dokumentierte Regel ≠ Implementierung

**W1. „Alle `<select>` nutzen TomSelect" trifft nicht zu.**
AGENTS.md („TomSelect Dropdowns"): *„All `<select>` elements use TomSelect for search-as-you-type."*
Tatsächlich: 51 Selects mit `tomselect`-Klasse, **13 ohne** — `orderType` und `debithoursunit` (je in
Auftrags-, Unterauftrags- und Mitarbeiterauftragsformular), `gender` und `status` (Mitarbeiter),
`numberOfSerialDays` (Buchung), `invoiceview` und `suborderdescription` (Rechnung),
Mapping-Selects im Umsatz-Upload, Parametertyp im Report-Formular.
Für Nutzende heißt das: **manche Dropdowns sind durchsuchbar, andere nicht**, ohne erkennbare Regel.
(Plausible, aber nirgends dokumentierte Regel: kurze Enum-Listen bleiben nativ.)

**W2. Das Reports-Modul ist nicht lokalisiert.**
ADR-0010 und AGENTS.md: deutsch-first, alle Texte über die Message-Bundles.
Tatsächlich enthalten alle 6 Templates unter `reporting/` **null** `#{…}`-Referenzen; Spaltenköpfe
(„Last Updated", „Last Updated By"), Labels („SQL") und Button-Texte („Save Report", „Create Report")
sind hartcodiertes Englisch. Vergleich: `dailyreport` 353, `order` 180, `budget` 180 i18n-Referenzen.
Auch der Navigationseintrag „Scheduled Reports" in `base.html:206` ist hartcodiert.

**W3. Weitere hartcodierte Texte außerhalb des Reports-Moduls.**
`fragments/form-fields.html:125` und `FormButtonsProcessor:52` → „Cancel" — obwohl der Schlüssel
`main.general.button.cancel.text` („Abbrechen" / „Cancel") existiert und in
`employee-order-form.html:133` bereits genutzt wird; die Behebung ist also reine Textersetzung;
`base.html:256` → „Angemeldet als", `:299` → „Logout";
`invoice-form.html:201-202` → „Alle" / „Keine";
`title`-Attribute gemischt: `customer-order-list.html:52` und `sub-order-list.html:62` → englisch
(„Apply text filter"), `base.html:277,307` → deutsch („Benutzer wechseln", „Navigation ein-/ausklappen").
In der englischen Sprachfassung erscheinen also deutsche Tooltips und umgekehrt.

**W4. `<html>` hat kein `lang`-Attribut.**
`layout/base.html:2` deklariert nur die XML-Namespaces. Bei zwei Sprachfassungen (ADR-0010) fehlt
damit die Sprachauszeichnung für Screenreader, Silbentrennung und Übersetzungsdienste.

**W5. Die Beschreibung der Filter-Persistenz ist veraltet.**
AGENTS.md (Controller Pattern, Zeile 375): *„store to session on explicit submit, read from session
otherwise"*, und (List View Filter Toggles, Zeile 424): *„store both flags in session … session key
names follow the pattern `<module>.<entity>.show`"*.
Tatsächlich arbeitet `CustomerController` mit Request-Parametern (`cFilter`, `cShowHidden`) und
`UiState` (ADR-0014/0016), nicht mit `HttpSession`. Die Regel beschreibt den Legacy-Zustand.

**W6. Der `salat:`-Dialekt kann mehr als dokumentiert.**
AGENTS.md listet für `<salat:textInput>` nur `th:field`, `th:label`, `required`, `maxlength`,
`th:helpText`. Der `TextInputProcessor` unterstützt zusätzlich **`type`** und **`th:placeholder`** —
und genau darüber werden im Budget-Modul Datums- und Zahlenfelder gebaut
(`type="date"`, `type="number"`). Wer nur AGENTS.md liest, kennt diesen Weg nicht.

### B — Widersprüche innerhalb der Dokumentation

**W7. AGENTS.md gibt die überholte Fragment-Regel wieder.**
ADR-0005 (*Accepted*, supersedes ADR-0004) entscheidet: *„Alle Fragmente durch Salat-Dialect-Tags
ersetzen, einschließlich `master-table` und weiterer struktureller Layout-Komponenten."*
AGENTS.md Zeile 36 sagt dagegen: *„Thymeleaf fragments remain valid for structural/layout reuse
(e.g. `master-table`, layout decorators)"* — das ist die von ADR-0005 ersetzte Position aus ADR-0004.

**W8. AGENTS.md widerspricht sich zur Session-Nutzung.**
Zeile 375 und 424 schreiben Session-Speicherung vor, Zeile 380 verbietet sie
(*„No direct `HttpSession` access (→ ADR-0013)"*). Beide Regeln stehen im selben Dokument.

**W9. AGENTS.md schreibt für Flags Bootstrap Icons fest, sonst gilt Tabler.**
Der Abschnitt „Flags Column Pattern" nennt ausdrücklich `bi-cash-stack`, `bi-bookmark-star-fill`,
`bi-chat-square-text`, `bi-tag-fill`, `bi-mortarboard` — während Navigation, Buttons und
Formularaktionen durchgängig Tabler-Icons (`ti ti-*`) verwenden. Der Doppel-Icon-Satz ist damit
nicht nur gewachsen, sondern regelseitig festgeschrieben.

### C — Inkonsistenzen innerhalb der Implementierung

**W10. `colspan` der Leerzustands-Zeile passt in 3 von 7 Listen nicht zur Spaltenzahl.**

| Liste | Spalten | `colspan` |
|---|---|---|
| `customer-list.html:55` | 7 | 6 |
| `sub-order-list.html:179` | 11 | 12 |
| `employee-order-list.html:141` | 10 | 9 |
| `customer-order-list.html`, `budget-list`, `pricing-list` | ✓ passend | |

Folge: „Keine Einträge vorhanden" ist nicht über die Tabellenbreite zentriert bzw. erzeugt eine
Zelle zu viel.

**W11. Nur 12 von ~29 Schaltern tragen `role="switch"`.**
Vorhanden in `invoice-form` (8×) und je 1× in den drei Auftragslisten und der Vertragsliste — fehlt
in `customer-list`, `employee-list`, `budget-list`, `timereport-form`, `daily` und im gemeinsamen
`checkboxSwitch`-Fragment bzw. `CheckboxSwitchProcessor`. Optisch identische Elemente werden
Screenreadern unterschiedlich angekündigt. Da der zentrale Baustein betroffen ist, ist das an einer
Stelle behebbar.

**W12. `text-muted` und `text-secondary` werden gleichbedeutend eingesetzt.**
222× `text-muted` in Templates, 25× `text-secondary` — `base.html` selbst nutzt für Alert-Texte und
die Nutzerkarte `text-secondary`, alle Modul-Templates `text-muted`. Bootstrap 5.3 hat `text-muted`
zugunsten von `text-body-secondary` abgekündigt; die häufigste Textklasse der Anwendung steht damit
auf einem veralteten Token.

**W13. Der Body trägt eine Klasse aus dem Icon-Namensraum.**
`base.html:14` setzt `<body class="bi-layout-sidebar">`. `bi-*` ist der Präfix der Bootstrap-Icons-
Font, die Klasse erzeugt daher ein Pseudo-Element mit Ersatzglyphe — das
`salat.css:38-40` per `.bi-layout-sidebar::before { display: none !important; }` wieder unterdrückt.
Ein Workaround gegen einen Namenskonflikt, kein Layout-Feature; die Klasse hat sonst keine Funktion.

**W14. Zwei Wege für Datumsfelder.**
`fragments/form-fields :: dateInput` (Auftragsformulare) und `salat:textInput type="date"`
(Budget-Modul) erzeugen dasselbe Ergebnis. Der Dialekt hat kein eigenes `dateInput`-Tag, obwohl
ADR-0005 die vollständige Ablösung der Fragmente vorsieht.

**W15. Als `@deprecated` markierte Fragmente sind weiter im Einsatz.**
`form-fields.html` markiert `textInput`, `textInputHelp`, `textareaInput`, `textareaInputHelp`,
`selectInput`, `selectInputHelp` als veraltet mit dem Hinweis *„use … once templates are migrated"*
bzw. *„will be merged into …"*. Die Zusammenführung ist nie erfolgt, und `sub-order-form`,
`customer-order-form`, `employee-form`, `employee-contract-form` und `report-form` nutzen weiterhin
die veralteten Varianten. Damit existieren für ein Textfeld drei Wege: Dialekt-Tag, aktuelles
Fragment, deprecated Fragment.

**W16. „New" und „Beta" folgen keiner erkennbaren Ordnung.**
`/dailyreport/dashboard` und `/my-accounts` sind „New" (grün), `/dailyreport/timereports/new`,
`/daily`, `/matrix`, `/csv` sind „Beta" (violett) — die neue Buchungsmaske ist also „Beta", das
darauf aufbauende Dashboard „New". Ohne definierte Bedeutung lesen Nutzende die Farben als
Reifegrad, was hier nicht zutrifft.

**W17. Der Filter-Button ist dreifach unterschiedlich beschriftet.**
`customer-list.html:16` nur Icon **ohne** `title`; `customer-order-list.html:52` und
`sub-order-list.html:62` Icon mit englischem `title="Apply text filter"`; `reports-list.html:15`
wieder ohne. Dasselbe Element, drei Zustände von Benennung.

**W18. Prototypen-Platzhaltertexte wechseln die Sprache.**
Die statischen Texte in `th:text`-Elementen (sichtbar nur beim Öffnen der Templates ohne Server,
nicht in der laufenden Anwendung) sind in älteren Modulen englisch („No records found", „Short Name"),
im Budget-Modul deutsch („Keine Einträge vorhanden"). Kosmetisch, aber ein Hinweis darauf, dass es
keine Konvention dafür gibt.
