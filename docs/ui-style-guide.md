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
| **Farben** | ausschließlich Tabler-Tokens (`--tblr-*`); keine eigene Marken-Palette. Die verbliebenen Literale sind in [§7.1](#71-kontrast--verbindlicher-maßstab) benannt |
| **Projekt-CSS** | `static/css/salat.css` — Token-Bridging (`--bs-*` → `--tblr-*`), TomSelect-Angleichung, Textselektion, die Kontrastkorrekturen aus [§7.1](#71-kontrast--verbindlicher-maßstab) und der vergrößerbare Dialog |
| **Diagramme** | ApexCharts aus dem Tabler-WebJar, nur auf Seiten mit Diagramm eingebunden; Regeln in [§5.8](#58-diagramme) |
| **Theme** | Light/Dark umschaltbar (Tabler-Theme-Script, Buttons in der Kopfzeile); Sidebar ist **immer** dunkel (`data-bs-theme="dark"`) |
| **Druck** | Kopfzeile/Fußzeile via `d-print-none` ausgeblendet; eine dedizierte Druckansicht (`invoice/invoice-print.html`) |

Es gibt **keine** eigene Design-Token-Datei, kein Storybook, keine visuellen Regressionstests.
Die Referenz ist der Tabler-Standard.

## 3. Layout & Navigation

```
┌──────────┬──────────────────────────────────────────────┐
│ Sidebar  │ page-header:  pretitle / page-title │ Aktionen│
│ (dunkel) ├──────────────────────────────────────────────┤
│ 16rem    │ page-body                                    │
│ ⇄ 4rem   │   Alerts (Toast-Bereich)                     │
│          │   container-fluid → layout:fragment="content" │
│ Nutzer-  ├──────────────────────────────────────────────┤
│ block    │ footer: Links, Version, Server-Zeit          │
└──────────┴──────────────────────────────────────────────┘
```

- **Shell:** `templates/layout/base.html`, eingebunden per `layout:decorate` (Thymeleaf Layout Dialect).
- **Sidebar:** vertikale Navbar mit 7 Bereichen (Buchungen, Mitarbeiter, Aufträge, Budget, Reports,
  Backoffice, System), jeder als aufklappbares Dropdown. Sichtbarkeit rollenabhängig
  (`#authorization.expression(...)` bzw. ViewHelper-Bean).
- **Faltbar** über einen Knopf in der Markenzone (`data-bs-toggle="sidebar-folded"`, Tabler 1.5).
  Er erscheint, sobald die Maus auf der Sidebar liegt oder der Tastaturfokus hineinwandert. Tabler
  setzt `data-bs-sidebar="folded-hover"` am `<html>`, merkt die Wahl unter
  `localStorage['tabler-sidebar']` und stellt sie beim nächsten Laden vor dem ersten Bildaufbau
  wieder her. Gefaltet ist die Leiste 4rem breit; Hover **und** Tastaturfokus klappen sie wieder auf,
  wobei sie den Inhalt überlagert statt ihn zu schieben. Eigenes CSS oder JavaScript braucht das
  nicht mehr.
- **Aufgeklappte Sektion:** der Bereich der aktuellen Seite wird serverseitig geöffnet gerendert —
  `show` am Menü **und** `aria-expanded` am Umschalter. Das Attribut ist keine Kür: Tabler findet die
  offenen Bäume nur darüber und ließe ein Menü beim Falten sonst als Panel neben der Leiste stehen.
  Gefaltet räumt Tabler es beim Laden weg, dort steht also nichts vorab offen.
- **Aktive Markierung:** `section` / `subSection` werden pro Seite via `th:with` gesetzt und steuern
  `active`-Klassen.
- **Kopfzeile:** `page-pretitle` (Bereich) + `page-title` (Seite), rechts ein `btn-list` mit
  dem Einstieg in die Befehlspalette („Suchen …", [§3.2](#32-befehlspalette-1155)), „Neue
  Buchung", Benachrichtigungsglocke, Einstellungen, Theme-Umschalter — sowie im Bereich
  *Buchungen* ein globaler Vertrags-Selektor (`globalEmployeeContractId`).
- **Nutzerblock** in der `navbar-footer`-Zone der Sidebar: Bild, Name und darunter das Rollen-Badge
  bleiben unten stehen, während die Menüliste darüber scrollt. Alles Weitere — Gravatar-Link,
  Login-Kürzel, dasselbe Rollen-Badge, Benutzerwechsel (Impersonation) über ein Modal, Abmelden —
  liegt in einem nach oben klappenden Menü. Unterhalb von `md` rückt der Block in die mobile
  Kopfzeile, dort bleibt nur das Bild und das Menü klappt nach unten.
  - Das Badge steht im `nav-link-title` und trägt deshalb `nav-link-badge`: ohne diese Klasse macht
    Tabler aus jedem `.badge` in einem `.nav-link` einen absolut gesetzten Eckpunkt und schöbe es
    über den Rand der Sidebar ([§7.1](#71-kontrast--verbindlicher-maßstab) misst die Farbe,
    `salat.css` hält die Position). Im `nav-link-title` klappt es mit der Sidebar ein und
    verschwindet unterhalb von `md` mitsamt dem Namen.
  - Läuft ein **Benutzerwechsel**, zeigen Bild, Name und Rolle die übernommene Person. Ein zweites
    Badge (`bg-orange-lt`, `ti ti-switch-2`) sagt das schon am zugeklappten Auslöser — vorher war
    der einzige Hinweis dort das eigene Kürzel neben dem fremden Namen (#1033).

**Seitentitel** kommen aus dem Model (`title`, `sectionTitle`, `pageTitle`); Browser-Titel ist
immer `SALAT - <pageTitle>`.

### 3.1 Tastaturbedienung: Reihenfolge und Einstieg (#1064)

**Die Tab-Reihenfolge ist die Reihenfolge des Dokuments.** Es gibt keinen positiven `tabindex` —
weder im Markup noch aus JavaScript. Ein positiver Wert reiht das Element vor allem ein, was 0
trägt, und zwingt damit jedem später eingefügten Element eine Nummer auf, die es nicht hat. Der
einzige erlaubte Wert ist `-1` für etwas, das nur gezielt angesprungen wird (Dialoge, das
Sprungziel `#page-content`). Wer die Reihenfolge ändern will, ändert die Reihenfolge im Markup.

**Der Einstieg liegt im ersten Feld.** `focusEntryField()` in `salat.js` setzt den Fokus beim Laden
auf das erste sichtbare, bedienbare Feld des ersten Formulars in `.page-body` — bei einer Liste ist
das das erste Feld des Filters. Knöpfe und Links sind bewusst keine Kandidaten, und Felder
ausserhalb eines Formulars auch nicht.

- Nur beim ersten Laden, **nicht** nach `htmx:after:swap`: die Tagesansicht tauscht Fragmente,
  während getippt wird, und ein erneut gesetzter Fokus nähme den Cursor aus dem Feld.
- Nur mit Maus oder Zeigegerät (`(hover: hover) and (pointer: fine)`): auf einem Touchgerät öffnete
  er beim Laden die Bildschirmtastatur über der halben Seite.
- Ein `autofocus` im Markup hat Vorrang, ebenso ein Dialog, der beim Laden schon den Fokus hält.
- Bei einem TomSelect-Feld ist das Ziel `tomselect.focus_node`, nicht das versteckte `<select>` —
  und `openOnFocus` wird für diesen einen Aufruf abgeschaltet, sonst klappte auf jeder Seite ein
  Dropdown auf.
- **Ein Feld, das beim Verlassen speichert, braucht einen Vergleich mit dem gespeicherten Wert.**
  Start und Pause der Tagesansicht hängen am `blur` und posteten bisher bedingungslos. Mit dem
  Einstiegsfokus im Feld löste der erste Klick irgendwohin eine Speicherung aus und tauschte die
  Buchungsliste unter genau diesem Klick weg — samt dem Knopf, der ihn bekommen sollte. `saveTime`
  in `daily.html` speichert deshalb nur, wenn sich der Wert seit dem letzten Stand geändert hat.

**Der Sprunglink** (`.skip-link` in `layout/base.html`) ist das erste fokussierbare Element der
Seite und führt auf `#page-content`. Er ist der vorgesehene Weg an der Navigation vorbei; sichtbar
wird er nur, solange er den Fokus trägt (`visually-hidden-focusable`). Er liegt auf `z-index: 1040`
— über der fixierten Sidebar (1030), unter einem Dialog (1055).

### 3.2 Befehlspalette (#1155)

`Ctrl+K` (macOS `⌘K`) öffnet sie von jeder Seite, auch mit dem Fokus in einem Feld; ebenso der
Eintrag „Suchen …" in der Kopfzeile, der auf dem Telefon als Lupe stehen bleibt und dort der
Einstieg ist. Die Entscheidung und ihre Alternativen stehen in
[ADR-0030](adr/0030-befehlspalette-liest-die-seite-und-speichert-nichts.md).

- **Bedienung:** Pfeiltasten wählen, Enter führt aus, Esc schließt, ebenso das Kreuz im Suchfeld
  und ein Klick neben die Palette — aber nur einer, der auch dort begonnen hat: wer den Text mit
  der Maus markiert und über den Rand hinaus loslässt, will nicht schließen. Danach steht der Fokus
  wieder dort, wo er beim Öffnen war; kam er aus der aufgeklappten Liste eines Auswahlfelds, dann
  auf dem Auswahlfeld, ohne dass es erneut aufklappt. Maus und Tipp führen einen Treffer direkt
  aus; das Eingabefeld behält dabei den Fokus, damit die Bildschirmtastatur den Treffer nicht unter
  dem Finger wegschiebt.
- **Treffer:** alle Seiten der Sidebar, die Einstellungen (Moduswechsel und Einstellungsseite aus
  der Kopfzeile, Falten aus der Sidebar) und Tagessprünge in die Einzelübersicht — `heute`, `gestern`,
  `vorgestern`, `morgen` ab drei Buchstaben, ein Wochentag ab zwei (`fr` ist der jüngste Freitag,
  heute eingeschlossen), `T.M.`, `T.M.JJ`, `T.M.JJJJ`. Die Zeile nennt immer das aufgelöste Datum.
  Heute ist der Tag des Servers in dessen Zeitzone, weitergezählt mit der Uhr des Browsers: ein
  Tab, der über Mitternacht offen bleibt, rechnet vom neuen Tag aus, gleich in welcher Zone der
  Browser läuft.
- **Rangfolge:** Wortanfang vor Tagessprung vor Teilwort vor Buchstaben in Folge. Dahinter kommt,
  was nur ein weiteres Suchwort trifft, von dem also nichts Sichtbares passt (`dunkel`, `modus`) —
  sonst stünde bei `mo` der Moduswechsel vor dem Montag und vor „Meine Konten" —, und ganz hinten,
  ab drei Buchstaben, alle Einträge eines Sidebar-Bereichs, dessen Name passt. Der passende Teil ist hervorgehoben —
  mit derselben Tönung wie in den Auswahlfeldern.
- **Leere Eingabe:** die zuletzt ausgeführten Befehle, höchstens zehn; solange es keine gibt, alle
  Seiten und darunter die Einstellungen, jeweils als eigene Gruppe. Ein Tagessprung wird als
  Ausdruck gemerkt und beim Öffnen neu aufgelöst.
- **Barrierefreiheit:** ARIA-Combobox (`role="combobox"` am Feld, `role="listbox"` darunter,
  `aria-activedescendant` für die gewählte Zeile). Die Zeilen selbst sind nicht fokussierbar, die
  Liste trägt `tabindex="-1"` — scrollt sie, nähmen Chrome und Firefox sie sonst in die
  Tab-Reihenfolge auf, und dort wirken weder Pfeile noch Enter. „Keine Treffer" steht in einer
  Statusregion, die immer im Dokument bleibt und nur ihren Text wechselt; erschiene sie zusammen
  mit dem Text, läse ein Screenreader sie nicht vor.
- **Telefon:** unter `sm` über die ganze Breite und oben angeschlagen, ohne die Tastenhinweise. Das
  Kreuz ist dort der Weg hinaus: Esc gibt es nicht, und eine lange Liste lässt vom Hintergrund zum
  Danebentippen nichts übrig.

**Gemessener Kontrast** (hell gegen die Karte, dunkel gegen die dunkle Karte, Moduswechsel
ausgeklungen): kein Text unter 4,5:1.

| Probe | hell | dunkel |
|---|---|---|
| Eingabe, Treffer | 10,31 | 11,86 |
| Treffer, gewählte Zeile | 8,74 | 10,70 |
| Bereich rechts (`--tblr-secondary`), Gruppenkopf, Platzhalter, Fußzeile | 4,83 | 5,78 |
| Bereich in der gewählten Zeile (Schriftfarbe) | 8,74 | 10,70 |
| Hervorhebung, gewählte / andere Zeile | 7,82 / 8,79 | 4,71 / 4,98 |
| Tastenbeschriftung (`kbd`) | 9,37 | 14,33 |
| Eintrag „Suchen …" in der Kopfzeile, auch mit Hover | 10,31 | 11,86 |
| „Keine Treffer" | 4,83 | 5,78 |
| Kreuz (Nicht-Text), Ruhe / Hover | 4,83 / 4,63 | 5,78 / 5,78 |
| Balken der gewählten Zeile gegen Zeile / Karte (Nicht-Text, 3:1) | 4,24 / 5,00 | 4,44 / 4,92 |

Zwei Werte waren zunächst zu knapp und sind in `salat.css` korrigiert: der Bereich in Sekundärfarbe
auf der getönten Zeile (hell 4,10:1) und Tablers `kbd` (hell 2,84:1). Die getönte Fläche der
gewählten Zeile erreicht gegen die Karte nur 1,18:1 bzw. 1,11:1 — die Markierung trägt deshalb der
Balken am linken Rand.

#### Geschäftsobjekte (#1157)

Ab zwei Zeichen fragt die Palette den Server, sobald das Tippen etwa 150 ms ruht, und hängt die
Antwort unter die Treffer der Seite: **Aufträge, Unteraufträge, Auftraggeber, Personen**, je Gruppe
höchstens fünf. Die Anbieter je Modul und ihre Regeln stehen in
[ADR-0031](adr/0031-objektsuche-der-befehlspalette-anbieter-je-modul.md).

- **Die gewählte Zeile springt nicht.** Die Objekte kommen unter die Treffer der Seite; war dort
  nichts gewählt, wird das erste Objekt gewählt. Eine Antwort, die ein weiterer Tastendruck überholt
  hat, wird verworfen; bis die neue da ist, bleibt die vorige stehen, eingeschränkt auf das, was noch
  passt — sonst blinkten die Objekte mit jedem Buchstaben weg. Ist das gewählte Objekt mit der neuen
  Antwort weg, rückt die Wahl auf das erste Objekt, sonst auf den ersten Treffer der Seite, und wird
  sichtbar gehalten.
- **Zeile:** fachlicher Schlüssel und Beschreibung (`MUSTER-01 · Wartungsvertrag · Musterkunde`),
  rechts „beendet", „verborgen" und ein Hinweis wie der Auftraggeber eines Unterauftrags oder
  „Vertrag seit 01.01.2024". Beendete und verborgene Objekte stehen hinter den laufenden. Eine
  Datenbank-id steht nirgends.
- **Ziele:** Enter öffnet das erste. `→` mit dem Cursor am Ende der Eingabe zeigt alle, die Zeile
  darüber nennt Suche und Objekt (`muster › MUSTER-01 · …`), `←` am Anfang der Eingabe, die Rücktaste
  im leeren Feld oder der Pfeil links davor führen zurück, das Objekt ist dann wieder gewählt. Eine
  gehaltene Taste wechselt nur einmal zwischen Treffern und Zielen, und während eine
  Eingabemethode (IME) zusammensetzt, gehören die Tasten ihr. Für Maus und Finger steht in jeder
  Zeile mit mehreren Zielen ein `→`: 24 × 24 px, für den Finger über die ganze Zeilenhöhe bis an
  ihren rechten Rand. Ein langer Suchtext in der Zeile darüber wird auf 40 % gekürzt, damit das Objekt Platz behält.

  | Objekt | Ziele, das erste zuerst |
  |---|---|
  | Auftrag | Auftrag öffnen (Geschäftsführung: bearbeiten, sonst die Liste auf ihn gefiltert), Unteraufträge, Controlling ausgewertet, Budget |
  | Unterauftrag | Unterauftrag öffnen, Buchen auf … |
  | Auftraggeber | Auftraggeber öffnen, Aufträge des Auftraggebers |
  | Person | Mitarbeiter, Vertrag, Einzelübersicht, Matrixübersicht |

  Angeboten wird nur, was sich öffnen lässt — je Rolle, je Ziel. „Buchen auf" steht nur da, wo der
  gewählte Vertrag heute buchen darf; Controlling und Budget nur für die Aufträge, deren Zahlen die
  Person sehen darf, das Budget nur, wo es Pläne gibt.
- **Zuletzt verwendet** merkt kein Objekt: ob es sich noch öffnen lässt, beantwortet nur der Server.
- „Keine Treffer" kommt erst mit der Antwort, nicht schon, während sie aussteht.

| Probe | hell | dunkel |
|---|---|---|
| Objekt, Ziel | 10,31 | 11,86 |
| Hinweis rechts mit „beendet"/„verborgen", Gruppenkopf, `→` | 4,83 | 5,78 |
| `→` in der gewählten Zeile | 8,74 | 10,70 |
| Zeile über den Zielen: Suche / Objekt | 4,83 / 10,31 | 5,78 / 11,86 |
| Pfeil zurück (Nicht-Text) | 4,83 | 5,78 |

#### Befehle mit Parametern (#1158)

Sechs Befehle nehmen Parameter; die Entscheidung steht in
[ADR-0032](adr/0032-befehle-mit-parametern-in-der-befehlspalette.md). Angeboten wird ein Befehl nur,
wenn die Sidebar den Eintrag seiner Seite zeigt (`data-palette-verb`) — `abnahme` also nur einer
People Lead.

| Befehl | Parameter (`?` optional) | Enter öffnet |
|---|---|---|
| `buchen` | Tag?, Unterauftrag, Dauer?, Ticket?, Kommentar? | das Buchungsformular, vorbelegt |
| `tag` | Tag | die Einzelübersicht des Tages |
| `matrix` | Monat, Person? | die Matrixübersicht, ohne Monat den laufenden |
| `freigabe` | Monat | die Prüfseite vor der Freigabe |
| `abnahme` | Person, Monat | die Prüfseite vor der Abnahme |
| `controlling` | Auftrag | das Controlling, ausgewertet |

- **Bedienung:** Der Befehl wird Chip, sobald sein Wort mit einem Leerzeichen dasteht oder Tab ihn
  übernimmt. Rechts steht, welcher Parameter dran ist (`Tag?`). Tab übernimmt den gewählten
  Vorschlag, die Rücktaste im leeren Feld nimmt den letzten Chip zurück, zuletzt den Befehl; ein
  Klick auf einen Chip nimmt genau ihn zurück. Die Pfeile überspringen, was sich nicht wählen lässt.
  Ein optionaler Parameter ohne jeden Vorschlag — das Ticket eines Unterauftrags ohne Tickets — wird
  übersprungen.
- **Platz:** Chips werden nicht gekürzt. Reicht die Zeile nicht für Chips und Feld, rutscht das Feld
  unter die Chips; gekürzt wird nur ein Chip, der allein breiter ist als die Zeile. Solange ein Befehl
  eingegeben wird, wird die Palette ab `sm` bis zu 56rem breit, soweit der Bildschirm es zulässt.
  Auch die Parameter rechts neben einem Befehl werden nicht gekürzt wie der Hinweis eines Objekts,
  sie brechen rechtsbündig um.
- **In einem Zug:** jedes vollständige Wort, das genau einen Wert meint, wird Chip. Passt ein Wort
  nicht zu einem optionalen Parameter, bleibt dieser leer (`buchen wart 1,5` bucht heute). Was
  mehrdeutig ist, bleibt offen und wird vorgeschlagen; die Wörter dahinter bleiben stehen.
- **Werte:** Tag wie in der Palette, dazu `letzter Arbeitstag` (Wochenende und Feiertage übersprungen,
  vom Server). Monat: `9`, `9/2026`, `sep`, `september`, `letzter`; ohne Jahr der jüngste, der
  laufende eingeschlossen. Dauer wie im Zeitfeld (`1:30`, `1h30`, `90m`, `1,5`). Unterauftrag: was
  der Vertrag am Tag buchen darf, Favorit und zuletzt Gebuchtes vorn; was er heute, aber nicht an
  dem Tag buchen darf, steht ausgegraut mit dem Grund. Ein Ticket nur mit seiner ganzen Nummer, sonst
  beginnt dort der Kommentar; ohne eigenen Kommentar wird er mit Nummer und Titel des Tickets
  vorbelegt, wie beim Auswählen im Formular. Person und Auftrag vom Server, ab dem ersten Buchstaben.
- **Vorschau:** zwischen Eingabe und Liste steht, was Enter öffnet, mit den Werten — genau so, wie
  Enter sie übernimmt. Darunter in Fehlerfarbe, was nicht stimmt: ein Unterauftrag, der nach einem
  Tageswechsel nicht mehr buchbar ist (sein Chip ebenso), „Kommentar erforderlich".
- **Formular:** Der Fokus steht im ersten Feld, das die Palette nicht belegt hat (Dauer, Unterauftrag,
  Kommentar), sind alle belegt, auf „Speichern" (`focus=` in der Adresse, `data-entry-focus`).
- **Rangfolge:** Das ganze Wort eines Befehls steht vor allem; ein Befehl, der nur mit seinem Anfang
  passt, unter den Seiten und dem Tagessprung — `mat` und Enter führen weiter zur Matrixübersicht.

| Probe | hell | dunkel |
|---|---|---|
| Chip (`badge bg-primary-lt`), Fehler-Chip (`bg-danger-lt`) | 5,00 / 5,10 | 5,00 / 5,10 |
| Parameter rechts, Werte der Vorschau, ausgegrauter Vorschlag | 4,83 | 5,78 |
| Warnung der Vorschau (`text-danger`) | 5,10 | 4,92 |

### 3.3 Tastenkürzel (#1016)

| Taste | wo | was |
|---|---|---|
| `Ctrl K` / `⌘ K` | überall, auch in einem Feld | Befehlspalette ([§3.2](#32-befehlspalette-1155)) |
| `?` | ohne Fokus in einem Eingabefeld | Übersicht der Tastenkürzel |
| `i` | ohne Fokus in einem Eingabefeld | Neue Buchung — das Ziel des Knopfs in der Kopfzeile, also mit Tag und Vertrag der Seite; auf dem Buchungsformular, wo der Knopf fehlt, nichts |
| `Ctrl ↵` / `⌘ ↵` | im Buchungsformular, auch im Kommentar — und mit dem Fokus neben dem Formular | Speichern |
| `↑` `↓`, mit `Shift` bzw. `Alt` | im Zeitfeld mit Schnelleingabe | ein Schritt, eine Stunde, eine Minute ([§6](#6-eingabekomponenten-nach-datentyp)) |

- **Die Übersicht** öffnet mit `?`, über den Eintrag „Tastenkürzel" im Fuß (ab `md`) und aus der
  Befehlspalette. Sie ist ein Bootstrap-Modal wie die übrigen Dialoge und gibt den Fokus beim
  Schließen zurück. Die Pfeiltasten im Zeitfeld hängen an der Schnelleingabe (Beta); ist sie aus,
  sagt die Übersicht das und verweist auf die Einstellungen.
- **`Ctrl ↵` nimmt mit, was noch getippt wird:** ein Ticket, das erst beim Verlassen des Felds
  übernommen würde, und eine Dauer, die erst beim Verlassen ins Format kommt. Gespeichert wird über
  den Knopf „Speichern", also mit derselben Prüfung wie per Klick, und bei gehaltener Taste einmal.
- **Wo `Ctrl ↵` nicht speichert:** auf einem Link — dort öffnet der Browser ihn in einem neuen Tab —
  und auf einem Absendeknopf, der selbst speichert („Speichern und neu"). Steht der Fokus neben dem
  Formular, etwa auf einem Eintrag der letzten Kommentare, gilt das eine Formular der Seite, das das
  Kürzel anbietet; in einem offenen Dialog gilt keines.
- **Aufgeklappte Auswahlfelder** schließt die Übersicht beim Öffnen zuerst: TomSelect setzt beim
  Zuklappen den Fokus auf sein Feld zurück und nähme ihn der Übersicht sonst gleich wieder weg.
- **Beschriftung:** `Ctrl`, `Shift`, `Alt` bzw. auf macOS `⌘`, `⇧`, `⌥` (`data-platform-label`).

**Gemessener Kontrast** der Übersicht, hell / dunkel: Titel 17,74 / 14,68, Bedeutung 10,31 /
11,86, Gruppenkopf und Hinweise 4,83 / 5,78, Tasten 9,37 / 14,33, Verweis auf die Einstellungen
5,00 / 5,87; der Eintrag im Fuß wie die übrigen Fußlinks 4,63 / 6,99.

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
| Zeilenaktion Anzeigen (schreibgeschützt) | `btn btn-outline-secondary` | `ti ti-notes` |
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
| Tag als nicht gearbeitet markieren (Prüfseite, „Rest nicht gearbeitet") | `btn btn-azure`, auf der Prüfseite `btn-sm` | `ti ti-calendar-off` |

Icon-only-Buttons tragen `m-0` am `<i>`, Buttons mit Text `me-1`. In Listen sind Zeilenaktionen
**ohne** Textlabel.

**Anzeigen trägt kein Auge.** Die Zeile fehlte in dieser Tabelle, und genau deshalb konnte das Auge
doppelt belegt werden: als Zeilenaktion „Anzeigen" (das schreibgeschützte Gegenstück zu Bearbeiten,
4×) und zugleich als Zustands-Badge für Ein-/Ausblenden in der Flags-Spalte derselben Zeile
([§5.4](#54-badges--flags-spalte)). Aufgelöst ist der Konflikt auf der Seite der Aktion: Auge und
durchgestrichenes Auge sind ein natürliches Gegensatzpaar für Sichtbarkeit, das ein Zustands-Umschalter
braucht; für „Anzeigen" gibt es gleichwertige Alternativen. Gewählt ist `ti ti-notes` — von allen
Kandidaten der kräftigste Umriss und damit bei 16 px am besten lesbar, klar vom Auge unterscheidbar
und im Projekt sonst nicht belegt.

Geprüft und verworfen (alle in der laufenden Anwendung in Button-Größe gegeneinander gestellt):

| Kandidat | Grund |
|---|---|
| `ti-list-details`, `ti-file-info`, `ti-file-description`, `ti-clipboard-list`, `ti-table-row`, `ti-id-badge-2` | tragfähig, aber bei 16 px feiner gezeichnet als `ti-notes`; `ti-clipboard-*` legt „kopieren" nahe, `ti-id-badge-2` passt nur zu Personen |
| `ti-details` | heißt so, **ist aber ein Warndreieck** und kollidiert mit `ti-alert-triangle` (8×) |
| `ti-info-circle` | bereits als Hinweis-Icon belegt |
| `ti-external-link` | sagt „öffnet woanders"; die Detailseite öffnet im selben Tab |
| `ti-zoom-scan`, `ti-viewfinder`, `ti-file-search`, `ti-zoom-in` | lesen sich als scannen/zielen/suchen, und `ti-list-search` ist bereits Suche |
| `ti-article`, `ti-book-2`, `ti-layout-list` | bei 16 px ein Fleck ohne Aussage |
| `ti-chevron-right`, `ti-arrow-right`, `ti-square-arrow-right` | reine Navigation, schon fürs Blättern belegt |

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
| `btn-danger` | 3 | Danger Zone und deren Modal-Bestätigung. Im gemeinsamen Bestätigungsdialog steht die Variante nicht im Template, sondern kommt aus `data-confirm-variant` (#1032) |
| `btn-outline-warning` | 2 | Deaktivieren (Zustands-Toggle) |
| `btn-ghost-warning` | 2 | Erinnerungsmail bei überfälliger Freigabe/Abnahme |
| `btn-ghost-secondary` | 2 | Icon-Aktionen in der Nutzerkarte |
| `btn-warning` | 1 | Monat wieder öffnen (Admin, `acceptance.html:117`) |
| `btn-azure` | 3 | Tag als nicht gearbeitet markieren: Prüfseite, „Rest nicht gearbeitet" in Liste und Matrix (#1159) |
| `btn-link` | 3 | Abbrechen im Modal (`link-secondary me-auto`) — auch im gemeinsamen Bestätigungsdialog, Hinweis ausblenden (`daily.html:56`) |

**Nicht verwendet:** `btn-info`, `btn-dark`, `btn-light` und die Tabler-Sonderfarben
(`btn-purple`, …). `purple`, `green`, `red` erscheinen ausschließlich bei Badges und Text, nie an
Buttons. Einzige Sonderfarbe an einem Button ist `btn-azure` für „Nicht gearbeitet" (siehe unten).

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

**„Nicht gearbeitet" ist gefüllt, auch als Zeilenaktion** (#1159). Die Aktionen, die einen Tag als
nicht gearbeitet markieren — „Nicht gearbeitet" in einer Tageszeile der Prüfseite, „Rest nicht
gearbeitet" unter Liste und Matrix — tragen `btn-azure` mit `ti-calendar-off`, also Ton und Icon der
Markierung, die sie erzeugen ([§5.4](#54-badges--flags-spalte)). Das weicht bewusst von
„`btn-outline-*` für Zeilenaktionen" ab: die Markierung ist eine gefüllte Badge, und ein Outline-Knopf
daneben sähe aus wie eine andere Sache. Vorher war es auf der Prüfseite `btn-outline-secondary` mit
`ti-moon` — dem Icon des Dunkelmodus in der Kopfleiste — und unter Liste und Matrix
`btn-outline-warning`, die Farbe der Feiertage. Tablers `btn-azure` trägt auf dem vollen Ton nur
2,9:1; `salat.css` gibt ihm deshalb die Füllung der Badge (siehe
[§7.1](#buttons-die-textfarbe-muss-der-füllung-folgen)). Im Bestätigungsdialog von „Rest nicht
gearbeitet" ist der bestätigende Knopf aus demselben Grund `azure`.

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
zentral initialisiert in `salat.js`, auch nach HTMX-Swaps (`htmx:after:swap`).
Klassenvertrag: `tomselect` = Einzelauswahl, `tomselect tomselect-multi` + `multiple` = Mehrfachauswahl.
Optionen können über `data-subtext` eine zweite Zeile anzeigen (z. B. Vertragslaufzeit).

### 5.4 Badges & Flags-Spalte
Boolesche Zustände in Listen stehen gesammelt in einer **Flags-Spalte**
(`d-none d-lg-table-cell`), nie inline neben dem Namen — einzige Ausnahme ist „Inaktiv"
([unten](#inaktiv-in-listen-1220)). Jedes Flag ist eine Badge mit Icon und
`title`-Tooltip. Die Klassen heißen weiterhin `bg-*-lt`, Badges sind aber **gefüllt und tragen helle
Schrift** statt der Tönung — ihr Farbsignal steckt seit der Kontrastkorrektur allein in der Fläche
([§7.1](#badges-gefüllt-statt-getönt)); die hellen Töne Gelb, Grün und Lime sind die Ausnahme und
tragen dunkle Schrift auf voller Farbe. Großflächige getönte Bereiche bleiben getönt:

| Flag | Farbe | Icon |
|---|---|---|
| ausgeblendet (`hide`) | `bg-danger-lt` | `bi-eye-slash` |
| eingeblendet (`hide`, Umschalter) | `bg-success-lt` | `bi-eye` |
| fakturierbar | `bg-success-lt` | `bi-cash-stack` |
| Standard | `bg-warning-lt` | `bi-bookmark-star-fill` |
| Kommentar erforderlich | `bg-danger-lt` | `bi-chat-square-text` |
| Festpreis | `bg-primary-lt` | `bi-tag-fill` |
| Schulung | `bg-purple-lt` | `bi-mortarboard` |

Für Manager ist die `hide`-Badge ein **klickbarer Inline-Toggle** (HTMX-POST, tauscht nur die
Zelle) — realisiert in `fragments/hide-toggle.html`, dem einzigen verbliebenen Fragment mit
fachlicher Logik. Sie zeigt **den Zustand, in dem der Datensatz ist**, nicht die Aktion, die der
Klick auslöst: eingeblendet grün mit offenem Auge, ausgeblendet rot mit durchgestrichenem. Beide
Zustände sind damit eine Aussage, nicht nur einer.

Eingeblendet war vorher eine graue Badge mit einem auf 35 % abgeblendeten Auge — auf der gefüllten
Sekundärfarbe kam das Icon auf **1,91:1**, in beiden Farbmodi: Die feinen Linien des Umrisses waren
praktisch unsichtbar. Das ist ein Folgefehler der gefüllten Badge — auf der früheren 10-%-Tönung war
die Fläche hell genug, dass ein abgeblendetes Icon darauf noch trug. Auf der grünen Füllung steht das
Icon jetzt bei **5,20:1**.

**Wer nicht umschalten darf, sieht weiter nur den ausgeblendeten Zustand** als Flag. Ein Zustand, der
die Regel ist, braucht keine Markierung in jeder Zeile; der Umschalter dagegen muss zeigen, woran man
gerade dreht.

#### „Inaktiv" in Listen (#1220)

Ein inaktiver Eintrag steht in einer Liste **ohne getönte Zeile**. Er trägt hinter seinem Namen
bzw. Kürzel ein Badge „Inaktiv": `fragments/inactive-badge :: badge(${inaktiv})`, `bg-secondary-lt`
mit dem Text aus `main.general.inactive.text`. Keine Liste baut es selbst, und keine setzt für
inaktive Einträge ein `table-*` an das `<tr>`. `InactiveRowMarkingTest` prüft beides.

Das ist die eine Ausnahme von „nie inline neben dem Namen": „Inaktiv" beschreibt nicht eine
Eigenschaft unter mehreren, sondern ob der Eintrag überhaupt noch gilt. Und die Flags-Spalte ist
unterhalb von `lg` ausgeblendet.

- **Was inaktiv heißt, bleibt je Liste, wie es war.** Beim Budgetplan ist es das Flag `active`, bei
  Einträgen mit Gültigkeitszeitraum `currentlyValid` (→ ADR-0029: nur das Ende zählt, ein Beginn in
  der Zukunft ist nicht inaktiv).
- **Das Badge steht in der ersten Spalte, die immer sichtbar ist.** In Kunden- und
  Unterauftragsliste sind die Spalten vor dem Kürzel unterhalb von `md` ausgeblendet. Dort steht das
  Badge hinter dem Kürzel des Auftrags, sonst verschwände es auf dem Smartphone mit der Spalte.
- **Mitarbeiterliste:** Ausgeblendete Personen (`hide`) sind nicht inaktiv. Sie stehen ohne Badge in
  der Liste und sind über die Flags-Spalte markiert.
- In der Budgetplanliste sind die Kürzel der Mitarbeitenden ebenfalls `bg-secondary-lt`.
  Verwechseln kann man die beiden trotzdem nicht: Das Badge steht in der ersten Spalte hinter dem
  Plannamen, die Kürzel stehen in einer eigenen Spalte.

**Warum keine Tönung.** Vorher hinterlegten die Listen inaktive Zeilen mit `table-danger` bzw.
`table-secondary`. Das ging aus zwei Gründen schief:

- **Kontrast.** Tablers `table-*`-Tönungen sind im dunklen Modus helle Flächen. Helle Schrift wie
  Links und `text-muted` verliert darauf fast allen Kontrast. Auch im hellen Modus blieb sie unter
  4,5:1. Die Tönung steckt dabei im Innenschatten der Zelle, nicht im Hintergrund
  ([§7.1](#messverfahren)).

  | Zeile | Text | dunkel | hell |
  |---|---|---|---|
  | `table-secondary` (Budgetpläne) | Link (Planname) | **1,94** ✘ | **3,89** ✘ |
  | `table-secondary` (Budgetpläne) | `text-muted` | **1,97** ✘ | **3,76** ✘ |
  | `table-danger` (Kundenstundensätze) | `text-muted` | **1,89** ✘ | **3,60** ✘ |

- **Ecken.** Tabler rundet in Kartentabellen die erste und letzte Zelle **jeder** Zeile
  (`.card-table tr td:first-child { border-start-start-radius: var(--tblr-card-border-radius) }`,
  entsprechend `:last-child`). Ohne eigenen Hintergrund sieht man das nicht. Mit Tönung hatte jede
  inaktive Zeile oben links und oben rechts eine Rundung, mitten in der Tabelle.

Das Badge umgeht beides: Die Zeile behält den Untergrund der Karte, und das Badge ist eine gefüllte
Fläche mit eigener Schriftfarbe ([Badges](#badges-gefüllt-statt-getönt)). Nachgemessen in der
laufenden Anwendung, in beiden Modi nach dem Ausklingen der Übergänge, über alle Texte der
inaktiven Zeilen in Budgetplänen, Kundenstundensätzen, Aufträgen, Unteraufträgen,
Mitarbeiteraufträgen und Verträgen:

| Probe | hell | dunkel |
|---|---|---|
| Badge „Inaktiv" | 4,83 | 4,84 |
| Link (Planname) | 5,00 | 5,87 |
| `text-muted` | 4,83 | 5,78 |
| Standardtext | 10,31 | 11,86 |
| Icons der Zeilenaktionen (Nicht-Text, 3:1) | 4,83 | 4,92 |

Die Rundung der ersten und letzten Zelle bleibt in Tablers Regel stehen. Sichtbar wird sie nicht
mehr, weil die Zelle weder Hintergrund noch Innenschatten trägt.

**Wer eine Zeile in einer `card-table` tönen will, hat dasselbe Problem**, auch wenn der Grund
nicht „inaktiv" ist. Offen ist das bei `table-warning` in der Mitarbeiterauftragsliste: ein
Auftrag, der nicht zu seinen übergeordneten Objekten passt (`fitsToSuperiorObjects`). Dort sind
es dunkel `text-muted` 2,18:1 und die Icons der Zeilenaktionen 2,56:1 (unter 3:1), hell `text-muted`
4,15:1, und die Ecken sind sichtbar.

#### „Nicht gearbeitet" (#1159)

Ein nicht gearbeiteter Tag ist an jeder Stelle dieselbe Badge: `fragments/not-worked :: badge(compact)`
— gefüllt in `bg-azure-lt` mit `ti-calendar-off` und dem Text aus `main.general.notworked.text`. Keine
Stelle baut sie selbst; vorher tat das jede, und so standen vier Farben, zwei Icons und ein Punkt ohne
Text nebeneinander.

| Stelle | Variante |
|---|---|
| Tagesansicht: Wochenleiste, Liste, Legende der Liste | voll (Icon und Text) |
| Prüfseite (Freigabe, Abnahme) | voll |
| Matrix und Dashboard: Tageszelle über Beginn, Pause und Ende (`rowspan="3"`) | kompakt |
| Matrix eines externen Vertrags (ohne Beginn, Pause, Ende): Zeile GESAMT | kompakt |
| Legende der Matrix | voll |
| Schalter „Nicht gearbeitet" im Formular Arbeitstag | nur das Icon (`:: icon`), in `text-azure` vor dem Label |

- **Kompakt** heißt: nur das Icon, mit `title`-Tooltip und dem Text als `visually-hidden`; das Icon
  selbst ist in beiden Varianten `aria-hidden`.
- **Azure, weil ein nicht gearbeiteter Tag ein freier Tag ist** — es ist die Farbe des Wochenendes.
  `warning` steht in Matrix, Dashboard und Legenden für Feiertage, und `ti-moon` schaltet den
  Dunkelmodus. Unterschieden vom Wochenende wird die Markierung durch das Icon; die Legende führt sie
  deshalb mit.
- In der Matrix gibt es keine eigene Zeile „Nicht gearbeitet" mehr. An diesem Tag sind Beginn, Pause
  und Ende ohnehin leer; die Zelle der Zeile Beginn reicht über alle drei. Weil `cellIndex` in den
  beiden Zeilen darunter danach nicht mehr die Spalte zählt, rechnet das Skript der Matrix die Spalte
  für Hover und Klick aus dem Raster.
- Im Druck der Matrix (#1148) trägt die Badge ihre Füllung auch ohne „Hintergrundgrafiken": die
  Tabellenzellen setzen `print-color-adjust: exact`, und die Eigenschaft wird vererbt.

### 5.5 Rückmeldungen
- **Erfolg/Fehler nach Redirect:** Flash-Attribute `toastSuccess` / `toastError` / `toastErrors`
  → in `base.html` als schließbarer `alert alert-success|danger` **oberhalb des Seiteninhalts**
  (trotz des Namens keine echten Toasts: keine Overlay-Position, kein Auto-Dismiss).
  `toastSuccess` kann eine Folgeaktion als Button tragen (`toastAction` / `toastActionLabel`).
  Mehrere Fehler werden zusammengefasst: erster sichtbar, Rest in einem `<details>`.
- **Bestätigungen:** ein gemeinsamer Modal-Dialog für die ganze Anwendung (#1032, → ADR-0027).
  Er liegt einmal als Fragment (`fragments/confirm-dialog.html`) in `layout/base.html`; die
  auslösende Aktion beschreibt ihn deklarativ über `data-confirm-*` am Formular (oder an einem
  einzelnen Submit-Knopf), der delegierte Handler in `salat.js` füllt und öffnet ihn. Kein
  Template bringt dafür eigenes JavaScript mit. Natives `confirm()`, `alert()` und `prompt()`
  kommen nicht mehr vor.

  | Attribut | Bedeutung |
  |---|---|
  | `data-confirm` | markiert die Aktion als bestätigungspflichtig |
  | `data-confirm-title` | Überschrift; ohne Angabe die allgemeine Rückfrage |
  | `data-confirm-text` | was die Aktion tut — nur, wo es etwas zu erklären gibt |
  | `data-confirm-detail` / `-detail-secondary` | die fachlichen Schlüsselinformationen (siehe unten) |
  | `data-confirm-detail-input` | Selektor eines Feldes desselben Formulars, dessen aktueller Wert die zweite Zeile vervollständigt |
  | `data-confirm-label` | Beschriftung des bestätigenden Knopfs |
  | `data-confirm-variant` | `danger` beim Löschen, `azure` beim Markieren als nicht gearbeitet, sonst `warning`, `success` oder `primary` |

  Layout wie gehabt: `modal-sm modal-dialog-centered`, Abbrechen links (`me-auto`), Bestätigung
  rechts. Ohne JavaScript entsteht der bestätigende Knopf gar nicht erst — `salat.css` blendet
  ihn aus, bis `salat.js` die Klasse `salat-confirm-ready` gesetzt hat.

- **Modale Dialoge nennen das Geschäftsobjekt.** Ein Dialog legt sich über die Zeile, an der man
  gegenprüfen würde; „Soll der Eintrag wirklich gelöscht werden?" ist in diesem Moment nicht mehr
  nachzuvollziehen. Deshalb gilt für **jeden** modalen Dialog mit einer Meldung, nicht nur für
  Bestätigungen: er nennt die fachlichen Schlüsselinformationen des betroffenen Objekts — so viel,
  dass zwei benachbarte Zeilen derselben Liste auseinanderzuhalten sind. Was das heißt, folgt dem,
  was die Liste zur Unterscheidung ohnehin zeigt: Kunde → Kürzel und Name; Auftrag → Nummer,
  Kunde und Bezeichnung; Mitarbeitervertrag → Mitarbeiter und Gültigkeitszeitraum; Buchung →
  Auftrag, Dauer und Buchungstext. Die Datenbank-ID ist keine fachliche Information und gehört
  nicht in den Text. Gibt es kein einzelnes Objekt, benennt der Text den **Umfang** der Aktion —
  „Rest nicht gearbeitet" nennt den Monat, das Öffnen den Monat, ab dem geöffnet wird.

- **Prüfseite statt Dialog** (#760, #1122, → ADR-0027, Nachtrag): Vor der Freigabe und vor der
  Abnahme steht eine eigene Seite, die Person, Zeitraum und Folge nennt und jede erfasste Buchung
  zeigt; ihr Knopf handelt ohne Dialog, wie schon die Vorschau vor dem Umbuchen. Gesperrt ist er,
  solange es Befunde an Tagen gibt — `disabled` mit sichtbarem Grund daneben (`aria-describedby`).
  Ein Befund über den ganzen Zeitraum steht an Stelle von Bilanz, Buchungen und Knopf; die Abnahme
  kennt nur solche. Die Sperre selbst liegt im Service.
  Einen Arbeitstag ohne Buchung markiert „Nicht gearbeitet" in der Übersicht vor der Freigabe an
  seinem Tag ebenfalls ohne Dialog: die Tagesansicht nimmt die Markierung zurück.

- **Einmal absenden** (#1237, → ADR-0033): Ein Formular mit `data-submit-once` geht einmal hinaus.
  Vom Absenden bis zur Antwort tragen seine Knöpfe die Klasse `disabled` und `aria-disabled`, nicht
  das Attribut `disabled` — das nähme den gedrückten Knopf aus den Formulardaten. Ein zweiter Klick
  oder Enter schickt nichts ab, und der Bestätigungsdialog fragt dann nicht noch einmal. Gesetzt
  ist es an den Knöpfen der beiden Prüfseiten, deren Antwort ein, zwei Sekunden braucht, am
  Öffnen auf der Seite der Abnahme, das denselben Vertrag schreibt, und am Buchungsformular (#1239),
  dessen zweites Absenden eine zweite Buchung anlegt. Für HTMX gilt es nicht, HTMX verhindert das
  Absenden selbst.

- **Eigenständige modale Dialoge** bleiben, wo mehr als eine Bestätigung verlangt wird:
  Benutzerwechsel (`layout/base.html`, die Auswahl findet im Dialog selbst statt), Teilen
  (`daily.html`), Anonymisieren (`employee-form.html`, Danger Zone mit Doppelbestätigung) und der
  Feldauswahl-Dialog der JIRA-Replikation. Die Regel oben gilt auch für sie.

### 5.6 Hilfetext — drei Stufen, nach Länge und Nachschlagehäufigkeit

| Was | Wie | Wann |
|---|---|---|
| Ein Satz zu **einem Feld** | `th:helpText` am `salat:`-Tag → `div.form-text` unter dem Control, immer sichtbar | der Regelfall; kurz genug, dass er nicht stört |
| Eine **Regel über mehrere Felder** | Info-Icon + Popover (siehe unten) | beim Nachschlagen gefragt, nicht bei jedem Aufruf |
| Ein **Hinweis zur ganzen Seite** | `alert alert-info` über dem Inhalt | betrifft jeden Aufruf und darf Platz kosten |

**Erklärung hinter einem Info-Icon** (#1065). Der Auslöser ist ein `<button type="button">` mit
`class="info-popover-toggle"`, `bi bi-info-circle`, `title` und `aria-label`; `data-info-popover`
trägt den CSS-Selektor des verborgenen Blocks mit dem Text. Aufgebaut wird das Popover zentral in
`salat.js` (`initInfoPopovers`, auch nach `htmx:after:swap`) — **kein Template bringt dafür eigenes
JavaScript mit**, dieselbe Zusage wie beim Bestätigungsdialog ([§5.5](#55-rückmeldungen), ADR-0027).

- `trigger: 'hover focus'` deckt Maus, Tastatur und Berührung ab; der Inhalt wird bei jedem Öffnen
  neu gelesen, ein per OOB getauschter Block wird also mitgenommen.
- `customClass: 'info-popover'` hebt Bootstraps 276-px-Deckel auf; die Farben des Auslösers stehen
  in `salat.css` und sind die gemessenen von [§7.1](#71-kontrast--verbindlicher-maßstab)
  (Ruhe 4,83:1 / 5,78:1, Hover und Tastaturfokus 10,31:1 / 11,86:1).
- **Deklarativ ginge auch** — Tabler baut jedes `[data-bs-toggle="popover"]` beim Laden selbst —
  aber nur mit dem Text als Attributwert. Eine Erklärung mit Aufzählung zöge damit Markup in die
  Message-Bundles; deshalb der verborgene Block.
- Ältere Einzelstücke: die Tagesdetails der Matrix (`matrix.html`) und die Überstunden-Legende des
  Dashboards (`dashboard.html`, #1030) bauen ihr Popover noch selbst. Sie sind das Vorbild dieses
  Bausteins und wandern bei Gelegenheit darauf.

### 5.7 HTMX-Muster
`th:hx-post` / `hx-get`, `hx-include="closest form"`, `hx-target`, `hx-swap`; Controller erkennt
`HX-Request` und liefert `"view :: fragment"`. Eingesetzt für: abhängige Auswahlfelder in
Auftragsformularen, Inline-Toggles, Benachrichtigungsglocke, Buchungs-Popover in der Matrix.
CSRF-Token werden in `salat.js` per `htmx:config:request` nachgezogen.

**Ein Auslöser, den ein OOB-Swap ersetzt, nimmt seinen Listener mit.** Wo ein Feld selbst getauscht
wird und zugleich ein Nachladen auslösen soll, gehören die `hx-*`-Attribute an das getauschte
Fragment, nicht an ein umgebendes Element mit `hx-trigger="change from:#feld"` — nur das erste wird
nach dem Swap neu verdrahtet (`flat-rate-form :: suborderSelect`, #1065). Umgekehrt ist das
umgebende Element das Mittel der Wahl, wo die Felder bleiben, aber `salat:`-Tags keine
`hx-*`-Attribute durchreichen (`pricing-form.html`).

### 5.8 Diagramme

Referenz ist die Kontenübersicht (`dailyreport/my-accounts.html`, #1175): Arbeitszeit, Urlaub und
Fortbildung. Die Regeln gelten für jedes neue Diagramm.

**Bibliothek.** ApexCharts, und zwar das aus dem Tabler-WebJar
(`/webjars/tabler__core/dist/libs/apexcharts/dist/apexcharts.min.js`). Keine eigene Abhängigkeit
und keine zweite Diagrammbibliothek (Plotly ist mit #1175 entfernt). Das Skript steht in der
Seite mit dem Diagramm, nicht in `base.html`. Tablers `tabler-vendors.css` wird **nicht**
eingebunden: Sie gestaltet auch Tom Select um, und die Vertragsauswahl im Kopf jeder Seite sähe
dort anders aus als überall sonst. Die Diagrammregeln daraus stehen in `salat.css`.

**Farben.**
- Aus den Tabler-Tokens, zur Laufzeit über `--tblr-<name>` gelesen, nie als Literal im Skript
  ([§7](#7-farbsemantik)).
- Was auf der Seite schon eine Farbe hat, behält sie im Diagramm. Urlaub steht in Azure wie sein
  Fortschrittsbalken darüber, die Soll-Markierung in einer Farbe, die sich von der Säule abhebt.
- Jede Art in einem Stapel bekommt einen eigenen Farbton, etwa Urlaub und Sonderurlaub oder
  reguläre und projektbezogene Fortbildung.
- **Geplant** (gebucht, aber noch nicht erreicht) ist derselbe Farbton in hellerer Stufe, Deckkraft
  0,45. Das ist dieselbe Stufe wie `.progress-bar-planned` in den Balken.

**Beschriftung.** Achsen und Legende in der normalen Textfarbe und der Seitenschrift (Inter),
13 px: hell 10,31:1, dunkel 11,86:1. ApexCharts setzt seine Beschriftungsfarbe als SVG-Attribut
auf ein helles Grau; `salat.css` überschreibt das mit `fill: var(--tblr-body-color)`. Gitter,
Tooltip und Legende folgen den Tokens in beiden Farbmodi. Im Skript steht deshalb nichts
Modusabhängiges, und beim Umschalten des Farbmodus muss nichts nachgezogen werden.

**Form.**
- Säulen sind an beiden Enden abgerundet (Radius 5), wie die Fortschrittsbalken. Ein Stapel wird
  als ein Stück gerundet, nicht jedes Teilstück.
- Teile eines Ganzen stehen **gestapelt**, nicht nebeneinander.
- Ein Ziel- oder Sollwert steht als **Markierung** auf der Säule („Column with Markers“, ApexCharts
  `goals`), nicht als zweite Säule daneben. Ob ein Wert darüber oder darunter liegt, ist so an
  einer Säule abzulesen.
- Die **Differenz zum Soll** steht als Etikett über der Säule („+1 h“, „−2,7 h“), über Säule oder
  Markierung, was höher ist (ApexCharts `annotations.points`, Ankerpunkt unsichtbar). Die Schrift
  hat die Textfarbe, die Fläche ist nur getönt: grün, wenn das Soll erreicht ist; im Ton der
  Markierung, wenn etwas fehlt; grau bei null. Eine Teilfläche in der Säule taugt dafür nicht, denn
  eine Stunde auf 150 wäre kaum einen Pixel hoch. Die y-Achse bekommt oben Luft für die Etiketten.

**Legende.** Oben links. Bei nur einer Reihe entfällt sie, außer sie erklärt eine Markierung
(`customLegendItems`). Eine Reihe ohne Wert im gezeigten Zeitraum fehlt in Legende und Popup,
statt als leerer Eintrag dazustehen.

**Popup.**
- Bei gestapelten Säulen **ein** Popup je Säule (`shared`, `intersect: false`) mit allen Teilen und
  ihrer Summe im Titel, zum Beispiel „Okt. '26: 6 Tage“. Teile ohne Wert fehlen darin
  (`hideEmptySeries`).
- Zahlen stehen im Format der Sprache, mit Einheit und Einzahl: „1 Tag“, „5 Tage“, „151 h“.
- Jede Zeile hat denselben Aufbau: Zeichen, Bezeichnung, rechtsbündiger Wert. Das Zeichen ist
  dasselbe wie in der Legende, ein Kästchen für eine Säule und ein Strich für eine Markierung.
  Das eingebaute Popup stellt eine Markierung (`goals`) anders dar als eine Säule. Wo das so ist,
  baut `tooltip.custom` das Popup aus den Klassen `.chart-tooltip*` in `salat.css`. Die Legende
  bekommt dieselben Zeichen über `legend.markers.customHTML`. Bei Ist und Soll steht die Differenz
  als eigene Zeile mit Vorzeichen darunter.

**Zeitachse.**
- Sie deckt den gewählten Zeitraum ab. Liegen schon geplante Werte dahinter, reicht sie bis zum
  letzten davon (Urlaub bis in das Folgejahr).
- Monate heißen wie in Java kurz („Jan.“, „März“). Das Jahr steht nur dabei, wenn die Achse über
  ein Kalenderjahr hinausreicht („Feb. '27“).

**Zeitraum wählen.** Eine `btn-group` aus Links (`a.btn`), die gewählte mit `active` und
`aria-current`; jede Wahl lädt die Seite neu und öffnet denselben Reiter wieder. Daneben steht der
gewählte Zeitraum als Datum („01.10.2025 – 28.09.2026“). Ein unbekannter Parameterwert fällt auf
die Voreinstellung zurück.

**Verhalten.**
- Beim ersten Zeichnen bauen sich die Säulen kurz auf (etwa 600 ms). Bei
  `prefers-reduced-motion: reduce` stehen sie sofort da.
- Ein Diagramm in einem Reiter wird erst gezeichnet, wenn der Reiter sichtbar ist. ApexCharts misst
  in einem verborgenen Element die Breite 0. Das gilt auch, wenn Tabler den Reiter aus dem
  Adress-Fragment öffnet.

**Nie allein.** Ein Diagramm ergänzt, es trägt keine Information allein. Die Werte, auf die es
ankommt, stehen auch als Text daneben (Kennzahlen, Tabelle, „davon … geplant“). Deshalb gilt für
die Säulen gegen den Hintergrund kein eigener Kontrastnachweis.

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
| `success` (grün) | Anlegen, fakturierbar, positiver Saldo, eingeblendet |
| `warning` (gelb) | Aufmerksamkeit ohne Fehler (abgelaufener Vertrag, fehlende Freigabe), Standard-Flag, Rücknahme eines Vorgangs |
| `danger` (rot) | Löschen, Fehler, ausgeblendet, Kommentarpflicht |
| `secondary` (grau) | neutral/inaktiv, Abbrechen |
| `purple` | Rollen-Badge, Schulung, „Beta"-Markierung |
| `azure` / `blue` | informative Kennzeichnung in Listen; `azure` zusätzlich Wochenende und „Nicht gearbeitet" (freie Tage) |

`bg-*-lt` für Badges und getönte Flächen — an einer Badge färbt die Klasse allerdings voll, nicht
getönt ([§7.1](#badges-gefüllt-statt-getönt)). `text-*` für Zahlen und Fließtext-Akzente,
`btn-*` gefüllt für Primäraktionen, `btn-outline-*` für Zeilenaktionen — mit der Ausnahme
„Nicht gearbeitet" ([§5.2](#farblogik-der-buttons)).
Häufigste Utility überhaupt: `text-muted` (222×) für sekundären Text.

Die Farbwahl bei Buttons ist ausführlich in [§5.2 Farblogik der Buttons](#farblogik-der-buttons)
beschrieben, Gewicht und Dämpfung von Text in [§8.1](#81-textgewicht--wann-fettdruck) und
[§8.2](#82-sekundärtext--wann-text-muted-wann-small).

### 7.1 Kontrast — verbindlicher Maßstab

Die Entscheidung und die verworfenen Alternativen stehen in
[ADR-0025](adr/0025-wcag-aa-als-verbindlicher-kontrastmassstab.md); hier stehen die Werte und das
Verfahren.

**Maßstab: WCAG AA.** 4,5:1 für **jeden** Text, unabhängig von seiner Rolle — Fließtext,
Sekundärangabe, Metazeile, Badge. 3:1 für reine Nicht-Text-Elemente (Rahmen, Zustandsflächen,
Icons ohne begleitende Beschriftung, WCAG 1.4.11). Es gibt keine Kulanzstufe für „nur sekundär":
eine Angabe, die zu unwichtig für lesbaren Kontrast wäre, gehört nicht auf die Seite.

Der Maßstab gilt in **beiden Farbmodi**. Die Sidebar trägt `data-bs-theme="dark"` und ist damit
immer dunkel — Textfarben darin sind auch im hellen Modus gegen `#1f2937` zu prüfen, nicht gegen
Weiß.

#### Messverfahren

Die Werte unten sind gemessen, nicht geschätzt: eine eigenständige Seite bindet die echten
Tabler-Stylesheets plus `salat.css` ein, jede Probe steht in der Struktur, in der sie in der
Anwendung vorkommt (Karte, Seitenfläche, Fußzeile, Sidebar). Aufgelöst werden die Farben über ein
Canvas — nötig, weil Chrome `color-mix()` als `color(srgb …)` zurückgibt und eine naive Auswertung
dabei Unsinn liefert. Je Probe wird der Hintergrund aus allen Schichten bis zum nächsten deckenden
Elternelement zusammengesetzt, teiltransparente Vordergrundfarben werden darüber komponiert.

Maßgeblich ist je Modus der **ungünstigste** Untergrund: hell die Seitenfläche `#f9fafb`, dunkel
die Karte `#1f2937`.

**In Tabellen liegt die Tönung der Zeile nicht im Hintergrund.** Streifung und `table-active`
setzen in Bootstrap 5.3 keinen `background-color`, sondern einen deckenden Innenschatten
(`box-shadow: inset 0 0 0 9999px var(--bs-table-bg-type)`); die Zelle selbst meldet
`rgba(0, 0, 0, 0)`. Wer beim Zusammensetzen der Schichten nur `background-color` liest, misst eine
Badge in einer Tabellenzeile gegen den falschen Untergrund — und übersieht dabei ausgerechnet die
ungünstigste Probe (#1039).

**Wer den Farbmodus zur Laufzeit umschaltet, darf nicht sofort messen.** Tabler animiert die Farbe
eines `.btn`; unmittelbar nach dem Setzen von `data-bs-theme` steht das Element mitten in der
Interpolation und trägt noch die Farbe des alten Modus. Gemessen an `btn-link` auf einer Karte im
Dunkelmodus: sofort 2,24:1, nach dem Ausklingen 4,9:1 — und der zweite Wert ist der richtige, er
deckt sich mit der Tabelle unten. Die Sofortmessung meldet also einen Kontrastfehler, den es nicht
gibt.

Erkennbar ist der Zustand an der Ausgabeform: Chrome gibt einen **interpolierenden** Wert als
`oklab(…)` zurück, einen fertigen als `color(srgb …)`. Wo das auffällt, hilft keine Spezifität —
eine Gegenprobe mit `!important` liefert denselben Endwert.

Daraus das Verfahren: nach jedem Moduswechsel eine Sekunde warten und Sofort- gegen Nachmessung
vergleichen. Sind beide gleich, hängt am Element kein Übergang — das ist bei reinem Text und
getönten Badges der Fall, deren Werte damit sofort stehen. Weichen sie ab, zählt allein der Wert
nach dem Ausklingen. Eine eigenständige Messseite, die den Modus fest verdrahtet statt ihn zu
schalten, kennt das Problem nicht; es trifft die Messung **in der laufenden Anwendung**.

#### Gemessene Werte (Tabler 1.5)

Der Befund stammt nicht vom Sprung auf Tabler 1.5 — 1.4 und 1.5 messen im Dunkelmodus identisch.
Im hellen Modus hat 1.5 genau eine Verschlechterung gebracht: `.text-body-tertiary` fiel von
3,05:1 auf 1,99:1.

| Probe | hell vorher | hell nachher | dunkel vorher | dunkel nachher |
|---|---|---|---|---|
| Standardtext auf Karte | 10,31 | unverändert | 11,86 | unverändert |
| `.text-muted` (267×) | 4,83 | 4,83 | **3,04** ✘ | 5,78 |
| `.text-secondary` (31×) | 4,83 | unverändert | 5,78 | unverändert |
| `.text-body-secondary` | **3,00** ✘ | 4,83 | 7,32 | 5,78 |
| `.text-body-tertiary` (Fußzeile) | **1,99** ✘ | 4,83 | **4,13** ✘ | 5,78 |
| Sidebar-Link | 7,97 | unverändert | 7,97 | unverändert |
| `bg-*-lt` (18 Tönungen) | **1,97–4,35** ✘ *(alle)* | 8,90–9,53 | **2,68**–5,78 *(13 von 18 ✘)* | 9,90–11,09 |
| `text-*` (semantisch) | **2,13**–5,00 | 4,83–6,61 | **2,94**–6,88 | 4,80–9,34 |
| Sponsor-Herz Fußzeile | 9,58 | 6,33 | **1,77** ✘ | 5,80 |

Nach der Änderung liegt keine der 86 gemessenen Kombinationen unter 4,5:1; das Minimum ist hell
4,63:1 und dunkel 4,80:1.

Die Zeile `text-*` gibt den Stand von #1022 wieder; die Mischung wurde mit #1043 auf Schwarz und
Weiß umgestellt, die geltenden Werte stehen unter
[Wo Farbe erhalten bleibt](#wo-farbe-erhalten-bleibt).

#### Warum `.text-muted` betroffen war

Die Ursache ist eine Token-Verwechslung in Tabler selbst: `.text-muted` zeigt auf `--tblr-muted`
(`#6b7280`, eine Tabler-*Themefarbe*), nicht auf ein modusabhängiges Token. Der Wert ist deshalb in
beiden Farbmodi derselbe und wird für Dunkel nie neu gesetzt — auf Weiß ergibt er 4,83:1, auf
Dunkelgrau 3,04:1. `--tblr-muted` global umzubiegen scheidet aus: die Variable hängt an 42 weiteren
Stellen im Tabler-CSS, unter anderem an `.bg-muted` und den List-Group-Tokens.

Die Korrektur hängt die Klasse stattdessen an `--tblr-secondary`, das den Moduswechsel mitmacht.
`.text-muted` und `.text-secondary` sind damit deckungsgleich — was [§8.2](#82-sekundärtext--wann-text-muted-wann-small)
ohnehin als ihre gemeinsame Bedeutung beschreibt (→ W12). Die 267 Templatestellen bleiben
unberührt. Der größere Weg — Migration auf `text-body-secondary` — wurde **verworfen**: gemessen
verschlechtert er den hellen Modus (4,83:1 → 3,00:1) und tauscht damit ein Problem gegen ein anderes.

#### Warum die getönten Flächen betroffen waren

`bg-*-lt` setzt in Tabler nicht nur den Hintergrund, sondern auch den Text auf den vollen Farbton.
Auf der 10-%-Tönung erreicht der im hellen Modus in **keiner** der 18 Tönungen 4,5:1. Die Tönung
bleibt Bedeutungsträgerin, der Text bekommt die normale Textfarbe.

Die Regel gilt für jede getönte Fläche, nicht nur für Badges: dieselbe Klasse trägt die Wochenend-
und Feiertagsspalten der Matrix, die Fehlerzellen, die Kacheln des Dashboards und die Avatare.
Tablers eigene Abstufungen taugen als Ersatz nicht — `-darken` ist auf hellem Grund *heller* als der
Grundton (1,73:1–3,18:1) und `-fg` ist ein Fastweiß für gefüllte Flächen (1,04:1–1,11:1).

#### Ein satteres Gelb als Tablers

Tabler kennt genau ein Gelb: `--tblr-yellow` und `--tblr-warning` sind beide `#f59f00`. Das ist ein
Bernstein — als Fläche liest es sich als Ocker, nicht als Gelb, und abgedunkelt, damit helle Schrift
darauf trägt, wird daraus vollends ein Braun. Der Ton ist deshalb einmal zentral ersetzt:

| Token | Tabler | Salat |
|---|---|---|
| `--tblr-yellow`, `--tblr-warning` | `#f59f00` — oklch(0,77 0,16 67) | `#ffcc00` — oklch(0,87 0,18 92) |

Heller **und** im Farbton weiter weg vom Orange. Der Eingriff steht an einer Stelle, weil alles
Abgeleitete Tabler aus dem Token mischt und von selbst folgt: Füllung, Hover (`-darken`), Tönung
(`-lt`, `-200`) und die zur Laufzeit über `--tblr-<name>` gelesene Diagrammfarbe. Nur die
`-rgb`-Varianten stehen bei Tabler als Literal daneben und sind mitgezogen.

Nachgemessen in beiden Farbmodi, über Seitenfläche und Karte:

| Probe | vorher | nachher |
|---|---|---|
| Badge (`bg-warning-lt`, dunkle Schrift) | 6,88 | 9,71 |
| `btn-warning` | 6,88 | 9,71 |
| `btn-warning` im Hover (`-darken`) | — | 10,43 hell / 6,69 dunkel |
| `text-warning` hell (Anteil 60 % → 53 %) | 5,18 | 4,81 |
| `text-warning` dunkel (volle Farbe) | 6,88 | 9,71 |
| getönte Fläche `bg-warning-lt` | 8,90–11,09 | 9,37–11,70 |
| TomSelect-Trefferhervorhebung | 7,62 / 7,02 | 8,54 / 5,88 |

Es ist der einzige Farbwert im Projekt, der nicht aus einem Tabler-Token kommt
([Verbliebene Literale](#verbliebene-literale)). Das ist bewusst: Ein zweites Gelb daneben — Badge
satt, Button bernstein — wäre genau die Drift, vor der dieser Abschnitt sonst warnt.

#### Badges: gefüllt statt getönt

Aus normalfarbigem Text folgt: Was eine Badge farblich aussagt, steckt vollständig in ihrer Fläche —
und Tablers 10 % sind dafür zu wenig. In einer Flags-Spalte mit mehreren Badges nebeneinander ist
nicht mehr abzulesen, welche Farbe welche ist. Badges tragen deshalb die **volle Farbe mit heller
Schrift** (#1039).

Die helle Schrift gibt dabei die Richtung vor, nicht umgekehrt: Auf Tablers Grundtönen trägt sie nur
dort, wo der Ton dunkel genug ist — auf `--tblr-success` (`#2fb344`) wären es 2,63:1. Jeder Ton wird
deshalb so weit gegen Schwarz gemischt, dass 4,8:1 stehen, und keinen Schritt weiter. Das ist
derselbe Grundsatz wie bei den `text-*`-Utilities — und seit #1043 im hellen Modus auch derselbe
Anteil, der farbige Text trägt dort genau diesen Ton
([Wo Farbe erhalten bleibt](#wo-farbe-erhalten-bleibt)):

| Farbanteil | Farbtöne | Kontrast |
|---|---|---|
| 100 % (unverändert) | `primary`, `blue`, `indigo`, `purple` | 4,85–5,00 |
| 95 % | `danger`, `red`, `pink` | 5,05–5,10 |
| 75 % | `info`, `azure`, `orange`, `teal`, `cyan` | 5,03–5,20 |
| 100 %, **dunkle** Schrift | `warning`, `yellow` | 9,71 |
| 100 %, **dunkle** Schrift | `success`, `green` | 5,35 |
| 100 %, **dunkle** Schrift | `lime` | 6,02 |

`secondary` ist der einzige Ton mit zwei Werten: Tabler hellt die Sekundärfüllung im Dunkelmodus von
`#6b7280` auf `#9ca3af` auf. Hell trägt sie helle Schrift unverändert (4,83:1), dunkel muss sie dafür
erst auf 70 % abgedunkelt werden (4,84:1).

**Die hellen Töne gehen den umgekehrten Weg.** Gelb, Grün und Lime tragen helle Schrift auf dem
vollen Ton nicht — 1,45:1, 2,63:1 und 2,33:1. Abdunkeln bis 4,8:1 geht, kostet aber jedes Mal genau
das, was die Badge ausmacht:

- **Gelb** verliert dabei den Farbton. Wer helle Schrift auf einem Gelb bei 4,8:1 halten will, muss
  unter eine relative Leuchtdichte von 0,17 — und dort ist jedes Gelb ein Braun, bei jeder
  Sättigung. Die Badge lag bei `#935f00` und wurde als Braun gelesen, in beiden Farbmodi, weil die
  Badge-Füllung modusunabhängig ist.
- **Grün** verliert die Übereinstimmung mit dem Button. `btn-success` trägt den vollen Ton mit
  dunkler Schrift, die Badge trug `#217d30` mit weißer — auf derselben Seite standen damit zwei
  Grüntöne mit zwei Schriftfarben für dieselbe Aussage. Genau die Drift, vor der dieser Abschnitt
  sonst warnt.

Diese drei tragen deshalb die **volle Farbe mit dunkler Schrift** (`--tblr-dark`) — dieselbe Wahl,
die `--tblr-warning-fg` und `--tblr-success-fg` für `btn-warning` und `btn-success` treffen. Badge
und Button desselben Namens zeigen jetzt denselben Ton.

**Die Grenze verläuft an der Helligkeit, nicht an der Bedeutung.** Ein Ton, auf dem helle Schrift
4,5:1 trägt, bleibt in der Tabelle oben. Wer einen Ton hinzunimmt, misst beide Richtungen und nimmt
die, die trägt; tragen beide, gilt weiter helle Schrift.

Der Preis steht unter [Wo Farbe erhalten bleibt](#wo-farbe-erhalten-bleibt): Für diese drei fallen
Badge-Füllung und gleichnamige Textfarbe im hellen Modus auseinander. Anders ist es nicht zu haben —
farbiger Text auf heller Fläche kann nicht hell sein.

Gemessen wurden 18 Farbtöne × zwei Farbmodi × Seitenfläche, Karte, Tabellenzeile (gerade, ungerade,
`table-active`) und Sidebar, mit Text und nur mit Icon. **Kein Wert unter 4,83:1**; die Töne mit
heller Schrift liegen zwischen 4,83:1 und 5,42:1 — eng beieinander, weil jeder an seiner eigenen
Grenze sitzt —, die drei hellen Töne darüber, weil ihre Schrift nicht an einer Grenze klebt.

Verworfen wurde die naheliegende Zwischenstufe, die Tönung bloß kräftiger zu ziehen: Eine getönte
Fläche trägt die normale Textfarbe und ist damit bei 40 % ausgereizt (bei 45 % fällt `warning` auf
einer `table-active`-Zeile im Dunkelmodus auf 4,60:1, bei 50 % auf 4,21:1). Die volle Füllung bekommt
dagegen ihre eigene Textfarbe und ist damit nicht nur kräftiger, sondern auch frei in der Wahl.

**Nur `.badge` ist gemeint**, nicht `bg-*-lt` allgemein: dieselbe Klasse färbt ganze Tabellenzeilen,
die Wochenend- und Feiertagsspalten der Matrix, die ungelesenen Meldungen des Glocken-Dropdowns, die
Kacheln des Dashboards und die Avatare. Die bleiben getönt — eine ganze Tabellenzeile in voller Farbe
wäre eine andere Änderung. Die höhere Spezifität von `.badge.bg-*-lt` ist zugleich nötig, weil
Tablers eigene Regel `!important` trägt.

Die klickbare `hide`-Badge ([§5.4](#54-badges--flags-spalte)) bleibt im Hover unverändert: weder
Tabler noch `salat.css` bringen eine `:hover`-Regel mit, die den Button (`border-0 bg-transparent`)
oder die Badge darin trifft — geprüft über alle geladenen Stylesheets und am gehoverten Element
nachgemessen (5,10:1 in Ruhe wie im Hover; der eingeblendete Zustand liegt auf Grün bei 5,35:1).

**Für diese Prüfung muss die Messseite über HTTP ausgeliefert werden.** Bei einem Aufruf über
`file://` sperrt Chrome den Zugriff auf `cssRules` der verlinkten Stylesheets: `document.styleSheets`
listet sie zwar, der Zugriff wirft, und eine Prüfung, die das stillschweigend überspringt, sieht nur
noch den Inline-Block der Seite — im konkreten Fall 1 von 3 Stylesheets und damit weder Tabler noch
`salat.css`. Über `http://127.0.0.1` sind es 3 von 3.

#### Fremd-Stylesheets bringen eigene Paletten mit

Tabler ist nicht das einzige eingebundene Stylesheet. **TomSelect** bringt eine eigene Palette mit
und codiert sie hart — `.ts-control, .ts-control input, .ts-dropdown { color: #343a40 }` und weitere.
Diese Werte kennen keinen Farbmodus, und weil sie in einem WebJar liegen, fallen sie bei einer
Suche über `src/` nicht auf.

Der auffälligste Fall: **getippter Text landet in `.ts-control > input`, nicht in `.ts-control`.**
Aufgefangen war nur Letzteres, weshalb Text im Dunkelmodus während der Eingabe bei 1,54:1 lag und
erst lesbar wurde, sobald die Auswahl stand und der Wert wieder in `.ts-control` gerendert wurde. Bei
den Auswahlfeldern fällt es nicht auf, weil das `dropdown_input`-Plugin die Eingabe ins Dropdown
verlegt — nur das freie Textfeld (→ [AGENTS.md, Freitextfeld mit Vorschlägen](../AGENTS.md)) tippt
direkt in `.ts-control > input`.

| Element | hell vorher | hell nachher | dunkel vorher | dunkel nachher |
|---|---|---|---|---|
| Eingabe im Textfeld | 11,51 | 10,31 | **1,54** ✘ | 14,33 |
| Dropdown-Wurzel | 11,01 | 9,86 | **1,54** ✘ | 14,33 |
| „Übernehmen"-Zeile | **2,73** ✘ | 4,63 | **1,21** ✘ | 6,99 |
| Gruppenkopf | **4,49** ✘ | 4,63 | **3,78** ✘ | 6,99 |
| Platzhalter | **2,24** ✘ | 4,83 | 7,93 | 6,99 |
| Trefferhervorhebung | 9,19 | 8,54 | **4,32** ✘ | 5,88 |
| Chip der Mehrfachauswahl | 10,01 | 7,69 | 10,01 | 9,71 |

Der Chip war kontrastseitig in Ordnung, aber ein festes Hellgrau — im Dunkelmodus ein greller Fleck.
Er ist jetzt ebenfalls aus einem Token gemischt.

**Merksatz:** Ein neu eingebundenes Fremd-Stylesheet ist erst dann fertig eingebunden, wenn seine
Farbwerte gegen beide Farbmodi geprüft sind. Ein Blick in `src/` reicht dafür nicht.

#### Buttons: die Textfarbe muss der Füllung folgen

Tabler färbt den Text **jeder** gefüllten Variante über `--tblr-<farbe>-fg`, und das ist für alle
dieselbe Fastweiß-Farbe (`--tblr-light`). Wie hell die Füllung darunter ist, spielt dabei keine
Rolle — auf Grün ergab das 2,63:1, auf Gelb 1,45:1, in **beiden** Farbmodi. Im Dunkelmodus hellt
Tabler zusätzlich die Sekundärfüllung von `#6b7280` auf `#9ca3af` auf, lässt den Text aber weiß:
das war der gemeldete Abbrechen-Button mit 2,43:1.

| Variante | hell vorher | hell nachher | dunkel vorher | dunkel nachher |
|---|---|---|---|---|
| `btn-secondary` (12×) | 4,63 | 4,63 | **2,43** ✘ | 5,78 |
| `btn-success` (23×) | **2,63** ✘ | 5,35 | **2,63** ✘ | 5,35 |
| `btn-warning` | **1,45** ✘ | 9,71 | **1,45** ✘ | 9,71 |
| `btn-danger` | **4,46** ✘ | 4,66 | **4,46** ✘ | 4,66 |
| `btn-outline-primary` (29×) | 5,00 | 6,54 | **2,94** ✘ | 4,86 |
| `btn-outline-danger` (19×) | 4,66 | 6,61 | **3,15** ✘ | 4,92 |
| `btn-outline-success` | **2,74** ✘ | 5,22 | 5,35 | 7,88 |
| `btn-outline-warning` | **2,13** ✘ | 4,81 | 6,88 | 9,71 |
| `btn-ghost-warning` | **2,13** ✘ | 4,81 | 6,88 | 9,71 |
| `btn-link` | **4,13** ✘ | 6,54 | **3,55** ✘ | 4,86 |
| `btn-azure` (#1159), Hover | **2,92** ✘ | 5,05, Hover 6,36 | **2,92** ✘ | 5,05, Hover 6,36 |

Drei Dinge sind dabei zu wissen:

- **`--tblr-<farbe>-fg` ist der richtige Hebel.** Die Variable färbt die gefüllte Variante, deren
  Hover- und Aktivzustand und die gefüllte Hover-Fläche der Outline- und Ghost-Varianten — eine
  Stelle statt vieler. Outline und Ghost im Ruhezustand brauchen zusätzlich dieselbe Mischung wie
  die `text-*`-Utilities, mit denselben Anteilen.
- **`btn-link` lässt sich nicht über die Variable korrigieren.** Tabler setzt `--tblr-btn-color`
  zwar auf `--tblr-link-color`, überschreibt die Farbe im selben Stylesheet aber mit einem festen
  `color: rgb(7, 124, 234)`. Ein gewöhnlicher Link löst aus demselben Token korrekt auf (5,00:1 /
  5,87:1) — nur der Button nicht. Hier muss `color` direkt gesetzt werden.
- **Tablers Hover-Füllung heißt `-darken`, mischt aber mit 20 % Transparenz gegen den Untergrund.**
  Auf hellem Grund hellt sie damit auf, auf dunklem dunkelt sie ab — jeweils in die Richtung, in der
  der Text verliert. Hell fielen `btn-primary`, `btn-secondary` und `btn-danger` im Hover auf
  3,39:1, 3,14:1 und 3,51:1; dunkel `btn-secondary` auf 4,24:1 und `btn-success` auf 3,93:1. Die
  Korrektur dreht die Richtung je Modus um. **Ein Ruhezustand über 4,5:1 sagt nichts über den
  Hover** — beide sind zu messen.

Nach der Änderung liegt keine Variante in keinem der beiden Modi unter 4,5:1, weder im Ruhe- noch im
Hover-Zustand, weder auf der Karte noch in der stets dunklen Sidebar; Minimum hell 4,63:1, dunkel
4,66:1.

#### Feldhöhe bei ersetzten Bedienelementen

TomSelect ersetzt das Feld durch eigenes Markup und bringt dabei eigene Maße mit: Innenabstand
`.375rem` und Zeilenhöhe `1.5`, während Bootstrap hier `1.4285` rechnet. Sichtbar wurde das als
Sprung der Feldhöhe, sobald TomSelect ein Feld übernahm.

| Feld | vorher | nachher |
|---|---|---|
| `input.form-control` (Referenz) | 40 px | 40 px |
| TomSelect auf `<input>` | **35 px** | 40 px |
| TomSelect auf `<select>` | **41 px** | 40 px |
| TomSelect Mehrfachauswahl | — | 40 px |

Der Innenabstand war nur für die Auswahl-Variante korrigiert; ein `<input>` bekommt von TomSelect
`.form-control` statt `.form-select` an den Wrapper und fiel deshalb durch die Regel. Beide
Varianten übernehmen jetzt zusätzlich die Zeilenhöhe des umgebenden Feldes.

#### Wo Farbe erhalten bleibt

Die semantischen `text-*`-Utilities behalten ihren Farbton und werden **gegen Schwarz bzw. Weiß**
gemischt — hell abgedunkelt, dunkel aufgehellt, je Modus mit eigenem Anteil (#1043). Im hellen Modus
ist der Anteil derselbe wie bei der Badge-Füllung: **ein farbiger Text trägt dort genau den Ton der
Badge desselben Namens.** Ausgenommen sind die hellen Töne Gelb, Grün und Lime, deren Badges dunkle
Schrift auf voller Farbe tragen und damit aus dieser Rechnung heraus sind
([Badges](#badges-gefüllt-statt-getönt)).

| Farbton | hell: Anteil gegen Schwarz | Ton | hell | dunkel: Anteil gegen Weiß | Ton | dunkel |
|---|---|---|---|---|---|---|
| `primary`, `blue` | 100 % | `#066fd1` | 4,78 | 70 % | `#519adf` | 4,92 |
| `danger`, `red` | 95 % | `#cb3636` | 4,88 | sRGB-Rand statt Mischung | `#ff5f5a` | 4,92 |
| `purple` | 100 % | `#ae3ec9` | 4,64 | 70 % | `#c678d9` | 4,96 |
| `indigo` | 100 % | `#4263eb` | 4,77 | 70 % | `#7b92f1` | 5,05 |
| `orange` | 75 % | `#b94d05` | 4,87 | 100 % | `#f76707` | 4,82 |
| `azure`, `info` | 75 % | `#3273a9` | 4,83 | 100 % | `#4299e1` | 4,81 |
| `teal` | 75 % | `#097c5a` | 4,97 | 95 % | `#18aa7f` | 4,96 |
| `success`, `green` | 70 % | `#217d30` | 4,97 | 100 % | `#2fb344` | 5,35 |
| `warning`, `yellow` | 53 % | `#876c00` | 4,80 | 100 % | `#ffcc00` | 9,71 |

Zwei Töne fallen aus dem Mischschema heraus:

- **`danger` im dunklen Modus** wird nicht mehr mit Weiß aufgehellt. Weiß hellt zwar auf, nimmt dem
  Ton dabei aber die Sättigung — `#e27474` stand neben der Badge desselben Namens wie ein
  ausgeblichenes Rot. Bei der vollen Farbe bleiben ist keine Alternative, `--tblr-danger` trägt auf
  der Karte nur 3,15:1. Beides zugleich geht nur über den **Rand des sRGB-Raums**: In
  `oklch(from var(--tblr-danger) 0.7 0.2 h)` wird die Helligkeit gesetzt und die Buntheit höher
  angegeben, als der Farbraum an dieser Stelle hergibt; der Browser bildet auf den Rand ab und
  liefert damit das sättigste Rot, das bei dieser Helligkeit möglich ist — `#ff5f5a`, 4,92:1. Der
  Farbton stammt weiter aus dem Token (`from … h`), gesetzt werden nur Helligkeit und Buntheit.
  0,70 ist der Wert, bei dem der Rand noch 4,5:1 trägt.
- **`warning`/`yellow` im hellen Modus** ist der Ton, der am meisten verliert, und das lässt sich
  nicht beheben: Ein Text mit 4,5:1 auf der hellen Seitenfläche liegt unter einer relativen
  Leuchtdichte von 0,17, und dort ist jedes Gelb ein Braun. Der Anteil ist mit dem satteren Gelb
  von 60 % auf 53 % gesunken, weil der neue Ton heller ist.

Vorher wurde gegen `--tblr-body-color` gemischt, mit **einem** Anteil für beide Modi. Beides hat
Farbe gekostet:

- **Der Mischpartner war ein Blaugrau** (`#374151` im hellen Modus). Die Mischung dagegen dunkelt
  nicht nur ab, sie entsättigt und zieht ins Blau: `warning` wurde als Text zu `#836731`, einem
  Graubraun, während dieselbe Farbe als Badge ein gesättigtes `#935f00` ist. Zwei Töne für dieselbe
  Aussage, nebeneinander im Bild — auf dem Dashboard steht die Ampelzahl direkt neben der Badge.
- **Ein Anteil für beide Modi heißt: jeder Ton hängt an seinem schlechteren Modus.** `danger` hing
  an Dunkel (4,80:1) und war hell auf 65 % gedeckelt, `warning` umgekehrt an Hell (5,09:1) und
  dunkel auf 40 %. Je Modus eine eigene Regel lässt jeden Ton an seine eigene Grenze: dunkel tragen
  `success`, `warning`, `azure` und `orange` jetzt die volle Farbe, hell alle bis auf `warning` und
  `success` mindestens 75 %.

Dass der Badge-Ton als Textfarbe trägt, ist kein Zufall, sondern rechnet sich: Wer weiße Schrift auf
einem Ton mit 4,8:1 hält, hat einen Ton mit einer relativen Leuchtdichte um 0,17 — und der ergibt auf
der Seitenfläche wieder rund 4,6:1. Die beiden Blöcke in `salat.css` gehören damit zusammen: **wer
einen Anteil ändert, ändert den der Badge mit** — außer bei den hellen Tönen, deren Badges keinen
Anteil mehr haben.

**Im dunklen Modus ist derselbe Hex-Wert ausgeschlossen.** Die Badge-Füllung ist modusunabhängig
dunkel und trägt helle Schrift; ein Text auf der dunklen Karte muss umgekehrt hell sein. Erreichbar
ist dort derselbe Farbton bei gleicher Sättigung, nicht derselbe Wert.

**Die Aufschrift eines Outline- oder Ghost-Buttons ist farbiger Text** und folgt denselben Anteilen
([§7.1](#buttons-die-textfarbe-muss-der-füllung-folgen)) — sonst stünden auf einer Seite zwei Rottöne
für dieselbe Aussage, einer im Utility und einer auf dem Knopf daneben. Der Rahmen nimmt die Mischung
mit (3:1 als Nicht-Text-Element, gemessen 4,78–9,71); Hover und Aktivzustand kommen aus
`--tblr-btn-hover-bg`/`-fg` und sind unberührt.

**Der Dunkelmodus-Zweig der `text-*`-Regeln steht in `:where()`** und trägt damit keine eigene
Spezifität. Sonst schlüge er die `bg-*-lt`-Regel, die danach steht — und die Fehlerzellen der Matrix
(`bg-danger-lt text-danger`) und die Avatare (`bg-azure-lt text-azure`) bekämen den Farbton auf der
Tönung zurück, wo nur 4,45:1 bis 4,51:1 stehen. Mit `:where()` entscheidet weiter die Reihenfolge;
nachgemessen liegen die getönten Flächen bei 8,56:1 bis 11,06:1, tragen also unverändert die normale
Textfarbe.

Gemessen wurden 14 Textklassen, 11 Buttonvarianten und 12 Tönungsproben (Fläche und Badge) über
Seitenfläche, Karte und die stets dunkle Sidebar in beiden Farbmodi — **222 Kombinationen, keine
unter 4,5:1**; Minimum hell 4,64:1 (`purple`), dunkel 4,81:1 (`azure`).

**Ein neuer Farbton braucht einen eigenen, gemessenen Eintrag in `salat.css`** — und zwar zwei, einen
je Modus. Ohne ihn gilt Tablers Grundton, und der fällt durch.

#### Verbliebene Literale

Die Regel „ausschließlich Tabler-Tokens" ([§2](#2-design-fundament)) gilt; diese Ausnahmen sind
benannt und begründet:

| Ort | Literal | Begründung |
|---|---|---|
| `salat.css`, `--tblr-yellow`/`--tblr-warning` | `#ffcc00` | Tabler kennt genau ein Gelb (`#f59f00`), und das ist ein Bernstein — als Fläche gelesen ein Ocker. Siehe [Ein satteres Gelb](#ein-satteres-gelb-als-tablers) |
| `salat.css`, `::selection` | `#fff` | Mischpartner zum Aufhellen der Primärfarbe. Für den dunklen Modus hält Tabler kein helles Blau bereit: `--tblr-blue-200` ist `color-mix(… 20%, transparent)` und wird auf dunklem Grund selbst dunkel |
| `matrix.html`, Popover-Schatten | `rgba(0,0,0,…)`, `rgba(255,255,255,…)` | Für Schattenfarben gibt es in Tabler kein Token |
| `static/style/invoiceprint.css` | `1px solid black` | Reines Druck-Stylesheet, kein Bildschirmkontrast |

Serverseitig (Java) gibt es keine Farbwerte.

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
Flags-Spalte erst ab `lg`. Die Sidebar kollabiert unter `md` in einen Navbar-Toggler; ab `md` lässt
sie sich zur 4rem breiten Icon-Leiste falten.
Es gibt keine dedizierten mobilen Layouts, keine Karten-Ansicht als Tabellen-Ersatz.
Faktisch ist SALAT eine Desktop-Anwendung, die auf kleinen Displays benutzbar bleibt.

### Reservierte URL-Parameter

`tabler-theme.min.js` läuft auf **jeder** Seite und wertet dabei zehn Abfrageparameter aus:
`theme`, `theme-base`, `theme-font`, `theme-primary`, `theme-radius`, `layout`, `navbar`,
`navbar-position`, `navbar-theme` und `sidebar`. Jeder gefundene Wert landet dauerhaft in
`localStorage` und als `data-bs-*` am `<html>`. Diese Namen sind also für Formular- und Filterfelder
gesperrt — die `f`-Konvention aus ADR-0022 hält sie ohnehin auseinander.

Eine Stolperfalle steckt in `navbar-position`: sobald jemand eine waagerechte Navbar als direktes
Kind von `.page` einzieht, verschwindet die Sidebar kommentarlos, solange
`data-bs-navbar-position="vertical"` fehlt. Solange nur das `<aside>` dort steht — wie heute —
greift die Regel nicht, und das Attribut wird bewusst nicht gesetzt.

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
4. ~~**Natives `confirm()`** für alle 23 Löschvorgänge: keine Angabe *was* gelöscht wird, nicht
   stilisiert, nicht übersetzbar über den i18n-Text hinaus.~~ **Beantwortet (#1032, → ADR-0027).**
   Ein gemeinsamer Modal-Dialog für die ganze Anwendung, deklarativ über `data-confirm-*`
   ausgelöst, und die Regel dazu: ein modaler Dialog nennt die fachlichen Schlüsselinformationen
   des betroffenen Geschäftsobjekts (→ §5.5). Soft-Delete mit Undo bleibt offen — es ist eine
   andere Frage als die nach der Rückfrage.
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
18. ~~**Danger-Zone-Muster mit Doppelbestätigung** existiert genau einmal (Mitarbeiter
    anonymisieren), während 23 Löschvorgänge mit `confirm()` auskommen.~~ **Beantwortet
    (#1032, → ADR-0027).** Drei Stufen, nach Umkehrbarkeit: eine Aktion ohne bleibenden Schaden
    fragt nicht; eine löschende oder in die Vergangenheit greifende Aktion geht über den
    gemeinsamen Dialog, der benennt, was sie trifft; die Doppelbestätigung bleibt dem einen Fall
    vorbehalten, der unwiderruflich Daten überschreibt.

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
23. ~~**Kontrast** der `bg-*-lt`-Badges und von `text-muted` (die häufigste Textklasse) im
    Dark-Theme ist ungeprüft.~~ **Beantwortet (#1022).** Gemessen, nicht geschätzt: `text-muted`
    lag im Dunkelmodus bei 3,04:1, die `bg-*-lt`-Tönungen im **hellen** Modus durchweg unter 4,5:1
    (1,97:1 bis 4,35:1) — dort also schlechter als im dunklen. Maßstab, Messverfahren, alle Werte
    und die Korrekturen stehen in [§7.1](#71-kontrast--verbindlicher-maßstab).
24. **Sidebar öffnet im gefalteten Zustand per Hover.** Für die Tastatur ist das seit Tabler 1.5
    gelöst — die Faltregeln greifen `:has(:focus-visible)` mit ab, ein Tabulatorsprung in die Leiste
    klappt sie auf. Für Touch bleibt es offen.

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
267× `text-muted` in Templates, 31× `text-secondary` — `base.html` selbst nutzt für Alert-Texte und
die Nutzerkarte `text-secondary`, alle Modul-Templates `text-muted`. Bootstrap 5.3 hat `text-muted`
zugunsten von `text-body-secondary` abgekündigt; die häufigste Textklasse der Anwendung steht damit
auf einem veralteten Token.

*Aufgelöst durch Angleichung statt Migration (#1022):* `salat.css` hängt `.text-muted` an dasselbe
Token wie `.text-secondary`, beide ergeben nun exakt denselben Wert. Die Migration auf
`text-body-secondary` wurde verworfen — sie hätte den hellen Modus von 4,83:1 auf 3,00:1
verschlechtert (→ [§7.1](#71-kontrast--verbindlicher-maßstab)). Die Klasse bleibt in den Templates
stehen; dass sie in Bootstrap abgekündigt ist, bleibt offen.

**W13. Zwei Wege für Datumsfelder.**
`fragments/form-fields :: dateInput` (Auftragsformulare) und `salat:textInput type="date"`
(Budget-Modul) erzeugen dasselbe Ergebnis. Der Dialekt hat kein eigenes `dateInput`-Tag, obwohl
ADR-0005 die vollständige Ablösung der Fragmente vorsieht.

**W14. Als `@deprecated` markierte Fragmente sind weiter im Einsatz.**
`form-fields.html` markiert `textInput`, `textInputHelp`, `textareaInput`, `textareaInputHelp`,
`selectInput`, `selectInputHelp` als veraltet mit dem Hinweis *„use … once templates are migrated"*
bzw. *„will be merged into …"*. Die Zusammenführung ist nie erfolgt, und `sub-order-form`,
`customer-order-form`, `employee-form`, `employee-contract-form` und `report-form` nutzen weiterhin
die veralteten Varianten. Damit existieren für ein Textfeld drei Wege: Dialekt-Tag, aktuelles
Fragment, deprecated Fragment.

**W15. „New" und „Beta" folgen keiner erkennbaren Ordnung.**
`/dailyreport/dashboard` und `/my-accounts` sind „New" (grün), `/dailyreport/timereports/new`,
`/daily`, `/matrix`, `/csv` sind „Beta" (violett) — die neue Buchungsmaske ist also „Beta", das
darauf aufbauende Dashboard „New". Ohne definierte Bedeutung lesen Nutzende die Farben als
Reifegrad, was hier nicht zutrifft.

**W16. Der Filter-Button ist dreifach unterschiedlich beschriftet.**
`customer-list.html:16` nur Icon **ohne** `title`; `customer-order-list.html:52` und
`sub-order-list.html:62` Icon mit englischem `title="Apply text filter"`; `reports-list.html:15`
wieder ohne. Dasselbe Element, drei Zustände von Benennung.

**W17. Prototypen-Platzhaltertexte wechseln die Sprache.**
Die statischen Texte in `th:text`-Elementen (sichtbar nur beim Öffnen der Templates ohne Server,
nicht in der laufenden Anwendung) sind in älteren Modulen englisch („No records found", „Short Name"),
im Budget-Modul deutsch („Keine Einträge vorhanden"). Kosmetisch, aber ein Hinweis darauf, dass es
keine Konvention dafür gibt.
