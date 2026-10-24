# UI-Regeln

Teil der Entwicklungsregeln: Kern und Verweistabelle stehen in [AGENTS.md](../../AGENTS.md), was hier
steht, gilt ebenso verbindlich. Lesen, bevor ein Template, `salat.js`, `salat.css` oder ein Controller
mit Liste oder Filter geändert wird. Wie die Oberfläche heute aussieht, beschreibt
[`docs/ui-style-guide.md`](../ui-style-guide.md).

---

## Controller and View Guidelines (target stack)
- Controllers:
  - Use Spring Web MVC controllers to expose application capabilities.
  - Keep controller logic thin; delegate to services within the same module.
- Views:
  - Prefer Thymeleaf templates backed by the module’s controllers.
  - Adopt Bootstrap 5 + Tabler components for UI layout and widgets.
  - **Prefer the `salat:` custom dialect** for reusable form and table components over raw `th:replace` fragment calls. The dialect provides cleaner, attribute-based tags that are easier to read and IDE-friendly.
  - Thymeleaf fragments remain valid for structural/layout reuse (e.g. `master-table`, layout decorators); the `salat:` dialect targets leaf-level components (inputs, selects, buttons).
  - Shared layout and fragments should live under a common templates/layout and templates/fragments structure.
  - **No SpEL type expression `T(...)` in templates.** A value from Java comes from the controller as
    a model attribute (`@ModelAttribute` method if every rendering path needs it); formatting goes
    through `#temporals` or `#numbers`. `TemplateTypeExpressionTest` enforces this.
  - **Die Tab-Reihenfolge ist die Reihenfolge des Dokuments** (#1064). Kein positiver `tabindex`,
    im Markup so wenig wie aus JavaScript; erlaubt ist allein `-1` für ein Ziel, das nur gezielt
    angesprungen wird. Wo der Einstieg liegt, entscheidet nicht die Reihenfolge, sondern
    `focusEntryField()` in `salat.js`: beim Laden das erste Feld des ersten Formulars in
    `.page-body` — bei einer Liste das erste Feld des Filters. An der Navigation vorbei führt der
    Sprunglink in `layout/base.html`. Einzelheiten in [`docs/ui-style-guide.md`
    §3.1](../ui-style-guide.md).
  - **A rationale belongs in a parser comment `<!--/* … */-->`** (#1056). Thymeleaf removes that form
    while parsing, so it never reaches the browser; a plain `<!-- … -->` is passed through unchanged
    and stands in the source of the delivered page. A comment that explains *why* something is built
    the way it is — with or without an issue number — addresses the development team and has no place
    there. A pure section marker (`<!-- DAILY MODE -->`, `<!-- Row 1: Date + Duration -->`) may stay
    an HTML comment. Nothing in the build checks this: telling a rationale from a section marker is a
    judgement, and a rule triggering on every `#NNN` would misfire on the next section marker that
    carries one.

---

## Bestätigungen und modale Dialoge (→ ADR-0027)

**Es gibt kein `confirm()`, `alert()` oder `prompt()`.** Jede Rückfrage läuft über den gemeinsamen
Bestätigungsdialog: einmal als Fragment (`fragments/confirm-dialog.html`) in `layout/base.html`,
ausgelöst über `data-confirm`-Attribute am Formular oder an einem einzelnen Submit-Knopf, gefüllt
vom delegierten Handler in `static/js/salat.js`. Kein Template bringt dafür eigenes JavaScript mit,
und kein zweiter handgeschriebener Bestätigungsdialog kommt daneben.
Eine Prüfseite, die Person, Zeitraum und Folge nennt, ersetzt den Dialog für ihre Aktion (Freigabe, #760; Abnahme, #1122; ADR-0027, Nachtrag).
Was unwiderruflich überschreibt oder mit seinen Folgen löscht (Anonymisieren, Budgetplan löschen,
#1424), bekommt statt des Dialogs die Doppelbestätigung: Karte „Gefahrenbereich“, eigener Dialog,
Schlüssel des Objekts zweimal eingetippt (`data-confirm-typed`, `data-confirm-typed-input`), der
Service vergleicht erneut (ADR-0027, Nachtrag #1424).

```html
<form th:action="@{/customers/delete}" method="post"
      data-confirm
      th:data-confirm-title="#{main.customer.delete.confirm.title}"
      th:data-confirm-detail="${cu.shortName}"
      th:data-confirm-detail-secondary="${cu.name}"
      th:data-confirm-label="#{main.general.button.delete.text}"
      data-confirm-variant="danger">
```

**Ein modaler Dialog nennt in seiner Meldung die fachlichen Schlüsselinformationen des betroffenen
Geschäftsobjekts** — so viel, dass zwei benachbarte Zeilen derselben Liste auseinanderzuhalten
sind. Das gilt für **jeden** Dialog mit einer Meldung, nicht nur für Bestätigungen. Die
Datenbank-ID ist keine fachliche Information. Wo es kein einzelnes Objekt gibt, benennt der Text
den **Umfang** der Aktion (Zeitraum, Anzahl, Bereich); wo der Umfang erst eingegeben wird, holt
`data-confirm-detail-input` ihn aus dem Feld. Die Attributliste steht in
[`docs/ui-style-guide.md` §5.5](../ui-style-guide.md).

Zwei Fallstricke, die beide daher kommen, dass der Dialog zwischen Klick und Aktion steht:

- **`th:attr` wertet seine Ausdrücke eingeschränkt aus** — eine Bohne (`${@durationUtils…}`) ist
  dort so wenig erreichbar wie in einem Nachrichtenparameter (siehe „Globally Accessible Objects").
  Erst mit `th:with` binden, dann im Attribut verwenden.
- **Der Handler hängt am `submit`-Ereignis in der Capture-Phase** und stoppt es dort; alles andere
  als Capture ließe ein `hx-post` abgehen, bevor die Frage beantwortet ist. Ausgelöst wird danach
  mit `requestSubmit()`, nicht mit `submit()`: nur das erste behält den auslösenden Knopf und die
  HTML5-Validierung.

Solange `salat.js` nicht geladen ist, gibt es keinen bestätigungspflichtigen Absendeknopf:
`salat.css` blendet ihn aus, bis `body` die Klasse `salat-confirm-ready` trägt. Die Selektoren dort
decken einen Absendeknopf in einem Formular mit `data-confirm` und einen Knopf mit `data-confirm` ab.

---

## Befehlspalette (→ ADR-0030, ADR-0031, ADR-0032)

`Ctrl+K` / `⌘K` und der Eintrag „Suchen" in der Kopfzeile öffnen sie auf jeder Seite: einmal als
Fragment (`fragments/command-palette.html`) in `layout/base.html`, das Verhalten in
`static/js/salat.js`.

- **Die Palette navigiert oder öffnet ein vorbelegtes Formular. Sie speichert nichts und schickt
  kein Formular ab** — gespeichert wird mit dem Knopf der Seite, Freigabe und Abnahme laufen über
  ihre Prüfseiten. Deshalb braucht kein Befehl eine Rückfrage. Das gilt für jeden Befehl, der
  dazukommt. Einzige Ausnahme ist die Anmeldung selbst (#1231, ADR-0030, Nachtrag): Benutzerwechsel
  und dessen Ende lösen das Formular des Wechseldialogs bzw. des Nutzermenüs aus. Sie und das
  Abmelden werden nicht unter „Zuletzt verwendet" gemerkt (`data-palette-forget`).
- **Was sie anbietet, liest sie aus der gerenderten Seite**, statt eine zweite Liste zu führen: die
  Navigation aus `#sidebar-menu .dropdown-item[href]`, die Einstellungen aus den Bedienelementen
  mit `data-palette-command` (die Einträge des Nutzermenüs, der Faltknopf der Sidebar, die
  Tastenkürzel im Fuß), die Personen für den Benutzerwechsel aus den Formularen von
  `#loginSwitchModal`. Ein Bedienelement wird nur angeboten, solange es angezeigt wird — im
  Nutzermenü (`data-palette-menu`), sobald es gerendert ist. Die Rollenfrage beantwortet damit
  allein die Sidebar; eine Seite, die dort nicht steht, findet auch die Palette nicht.
  `CommandPaletteE2ETest` vergleicht je Rolle die Einträge der Palette mit denen der Sidebar. Wo ein
  Element der Seite das bessere Ziel kennt, verweist der Sidebar-Eintrag mit
  `data-palette-href-from` darauf.
- Seiten, Tage und zuletzt Verwendetes kommen **ohne Anfrage an den Server** aus. Heute ist der Tag
  des Servers, nicht der des Browsers: der Dialog trägt Zeitpunkt und Zeitzone des Servers
  (`data-now`, `data-time-zone`), und die Uhr des Browsers zählt von dort weiter.
- **Geschäftsobjekte kommen vom Server** (#1157, ADR-0031): jedes Modul mit Objekten oder Zielen
  setzt `de.hbt.salat.common.palette.PaletteProvider` um, in seinem `controller`-Paket neben dem
  `UiStateKeyContributor`; das Modul `palette` sammelt sie (`GET /palette/search`). **Jeder Anbieter
  entscheidet selbst, was die Person sieht, je Treffer und je Ziel** — mit den Klassen, die die
  Zielseite fragt, nicht mit einer Kopie ihrer Regel —, und die Palette zeigt nichts, dessen Seite
  mit 403 antwortet; ein Treffer ohne Ziel fällt weg. Die Services der Fachmodule prüfen beim Lesen
  meist nur die Anmeldung, eingeschränkte Anmeldungen hält also der Anbieter fern. Angezeigt werden
  fachliche Schlüssel, eine Datenbank-id höchstens in der Adresse. Ein Objekt wird nicht unter
  „Zuletzt verwendet" gemerkt.
- **Befehle mit Parametern** (#1158, ADR-0032: `buchen`, `tag`, `matrix`, `freigabe`, `abnahme`,
  `controlling`) bietet der Sidebar-Eintrag ihrer Seite an (`data-palette-verb`); was sie tun, steht
  in `PALETTE_VERBS` in `salat.js`. Tag, Monat und Dauer liest der Browser, alle übrigen Werte
  schlägt **das Modul vor, dem die Zielseite gehört** (`PaletteProvider#suggest`), aus derselben
  Quelle wie diese Seite — die Unteraufträge aus der Liste des Buchungsformulars, die Monate aus
  denen der Prüfseiten. Enter öffnet das vorbelegte Formular oder die Prüfseite, nie mehr.

---

## Tastenkürzel (#1016)

Neben `Ctrl+K` gibt es `?` (Übersicht aller Kürzel), `i` (Neue Buchung, dasselbe Ziel wie der
Knopf in der Kopfzeile) und `Ctrl+Enter` / `⌘Enter` (speichert das Formular über seinen Knopf mit
`data-submit-shortcut`, heute das Buchungsformular; auf einem Link und einem Absendeknopf bleibt
deren eigene Wirkung). Das Verhalten steht in `static/js/salat.js`.

- **Jedes Kürzel steht in der Übersicht** (`fragments/shortcut-help.html`). Ein Kürzel, das dort
  fehlt, findet niemand.
- **Eine Einzeltaste wirkt nur ohne Fokus in einem Eingabefeld** und ohne offenen Dialog — dort ist
  sie Text.
- **Keine Kombination, die Browser oder System für sich behalten** (`Ctrl+T`, `Ctrl+W`, `Ctrl+N`;
  `Ctrl+M` minimiert je nach System das Fenster). Auf macOS gilt `⌘` statt `Ctrl`, und die andere
  Belegung bleibt frei; die Beschriftung setzt `data-platform-label`.

---

## Sortierbare Listen (#1414)

Eine Reihenfolge, die eine Person selbst festlegt, wird über die Attribute `data-sortable*` in
`static/js/salat.js` gezogen (SortableJS, Maus und Touch) **und** mit Pfeilknöpfen verschoben —
ohne die Pfeile ist sie mit der Tastatur nicht bedienbar. Der Server liest die neue Ordnung aus den
versteckten Feldern der Seite in Dokumentreihenfolge und zeichnet den Bereich neu; der Browser führt
kein eigenes Modell. Attribute und Regeln in [`docs/ui-style-guide.md` §5.9](../ui-style-guide.md).

---

## Farben und Kontrast (→ ADR-0025)

Farbwerte kommen **ausschließlich aus Tabler-Tokens** (`--tblr-*`); es gibt keine eigene
Marken-Palette. Ein Literal im Stylesheet oder in einem Template ist ein Fehler, solange es nicht
in der Ausnahmeliste von [`docs/ui-style-guide.md` §7.1](../ui-style-guide.md) steht — dort sind
die fünf verbliebenen benannt und begründet, eines davon die Farben der Mails (unten, „Mails“). Eines davon ist das Gelb selbst: `salat.css` setzt
`--tblr-yellow` und `--tblr-warning` auf `#ffcc00`. Alles Abgeleitete mischt Tabler aus dem Token
und folgt von selbst.

Wo ein Wert zur Laufzeit gebraucht wird (Diagramme), wird das Token gelesen statt abgeschrieben:
`--tblr-<name>` vom `body`. **Seit Tabler 1.6 steht dort `oklch(…)`, kein Hex** — wer daraus eine
Transparenzstufe oder Kanalwerte bildet, lässt den Browser umrechnen (Canvas oder `color-mix()`)
und zerlegt den String nie selbst. **Modusabhängige Tokens taugen dafür nicht** —
`--tblr-body-color` löst zu einem `light-dark(…)`-Ausdruck auf, den keine Diagrammbibliothek
parst; der aufgelöste Wert steht in der berechneten Textfarbe des `body`.

**Der verbindliche Kontrastmaßstab ist WCAG AA: 4,5:1 für jeden Text**, unabhängig von seiner
Rolle, in **beiden** Farbmodi; 3:1 für reine Nicht-Text-Elemente. Es gibt keine Kulanzstufe für
Sekundärtext. Die Sidebar ist immer dunkel (`data-bs-theme="dark"`) — Textfarben darin sind auch im
hellen Modus gegen `#262626` zu prüfen.

Werte werden **gemessen, nicht geschätzt**; Verfahren, Messtabellen und die Korrekturen in
`salat.css` stehen im Style Guide. Fallstricke, die eine naive Prüfung verfehlt:

- Chrome gibt `color-mix()` als `color(srgb …)` zurück — ohne Auflösung über ein Canvas liefert die
  Auswertung Unsinn.
- **Wer den Farbmodus zur Laufzeit umschaltet, misst zu früh.** Tabler animiert die Farbe eines
  `.btn`; direkt nach dem Setzen von `data-bs-theme` trägt es noch die Farbe des alten Modus
  (`btn-link` dunkel: sofort 2,24:1, ausgeklungen 4,9:1). Chrome gibt einen interpolierenden Wert
  als `oklab(…)` zurück statt als `color(srgb …)`. Nach dem Moduswechsel warten und Sofort- gegen
  Nachmessung vergleichen.
- `bg-*-lt` setzt nicht nur den Hintergrund, sondern auch die **Textfarbe**. Eine getönte Fläche
  ohne eigenes `text-*` ist deshalb trotzdem eingefärbt.
- **In Tabellen steckt die Tönung der Zeile im Innenschatten, nicht im Hintergrund.** Streifung und
  `table-active` setzen `box-shadow: inset 0 0 0 9999px …`; die Zelle meldet `background-color:
  rgba(0, 0, 0, 0)`. Wer die Schichten nur über `background-color` zusammensetzt, misst gegen den
  falschen Untergrund — und dort sitzt regelmäßig die ungünstigste Probe.
- **Eingebundene Fremd-Stylesheets bringen eigene Paletten mit** und codieren sie hart. Sie liegen
  in WebJars und fallen bei einer Suche über `src/` nicht auf. TomSelect ist der bekannte Fall
  (`salat.css` fängt es ab); ein neu eingebundenes Stylesheet ist erst fertig eingebunden, wenn
  seine Farben gegen **beide** Farbmodi geprüft sind.
- Ein ersetztes Bedienelement bringt auch eigene **Maße** mit. TomSelect rechnet mit Zeilenhöhe
  `1.5`, Bootstrap hier mit `1.4285` — ohne Angleichung springt die Feldhöhe, sobald das Element
  übernommen wird.
- **Ein geprüfter Ruhezustand sagt nichts über den Hover.** Tablers Hover-Füllung `-darken`
  dunkelt seit Tabler 1.6 in beiden Modi ab — eine Füllung mit dunkler Schrift verliert dabei.
  Dazu legt jeder `.btn` im Hover eine Fläche unter (`--tblr-hover-bg`), die einen Textknopf auf
  dunklem Grund unter 4,5:1 drückt. Beide Zustände messen.
- **Manche Töne lassen sich als Text nicht sättigen — das ist keine Nachlässigkeit, sondern die
  Farbe.** Ein Text mit 4,5:1 auf heller Fläche liegt unter einer relativen Leuchtdichte von 0,17,
  und dort ist jedes Gelb ein Braun; umgekehrt ist auf der dunklen Karte jedes Rot unterhalb von
  4,5:1, solange es nicht aufgehellt wird. Aufhellen über Weiß entsättigt. Wo beides zugleich
  gebraucht wird, liefert `oklch(from <token> <L> <zu hohe Buntheit> h)` den Rand des sRGB-Raums —
  das sättigste, was bei dieser Helligkeit möglich ist. Wo auch das nicht reicht, gehört die Farbe
  in die Fläche statt in die Schrift (gelbe Badge: dunkle Schrift auf voller Farbe).
- **Tabler blendet mit Deckkraft aus.** `.legend-off` halbiert die Deckkraft des ganzen Eintrags, und
  die Schrift fällt unter 4,5:1. Diagramm-Legenden sind Tablers Legend (#1293); `salat.css` hält
  einen ausgeblendeten Eintrag in voller Farbe, streicht ihn durch und macht den Punkt zum Ring.
  Ein Punkt in der helleren Stufe für „geplant“ bekommt einen Rand in der vollen Farbe, allein
  erreicht er keine 3:1.
- **Ein Farbwert, den es zweimal gibt, driftet.** Die Füllung einer Badge und die semantische
  Textfarbe desselben Namens sind im hellen Modus **derselbe Ton** (`text-danger` = Füllung von
  `.badge.bg-danger-lt` = `#cb3636`, #1043). Wer einen Anteil ändert, ändert beide; sonst stehen
  zwei Rottöne für dieselbe Aussage nebeneinander im Bild.
- **Eine modusabhängige Textregel gehört in `:where()`.** `[data-bs-theme="dark"] .text-danger`
  wäre spezifischer als `.bg-danger-lt` und holte den Farbton auf die getönte Fläche zurück — genau
  dorthin, wo er nur 4,45:1 erreicht. Mit `:where()` bleibt die Spezifität gleich und die
  Reihenfolge entscheidet weiter, wie #1022 es eingerichtet hat.
- **Bei Buttons entscheidet die Füllung über die Textfarbe.** Tabler nimmt für jede gefüllte
  Variante dasselbe Fastweiß (`--tblr-<farbe>-fg`), unabhängig davon, wie hell die Füllung ist.
  Die Variable ist der richtige Hebel — sie färbt auch Hover, Aktiv und die gefüllte Hover-Fläche
  der Outline-Varianten. Ausnahme `btn-link`: dort überschreibt ein Literal die Variable, `color`
  muss direkt gesetzt werden.

---

## Mails (#1505)

Jede Mail von Salat hat einen Text- und einen HTML-Teil (`multipart/alternative`). Den HTML-Teil
rendert `MailTemplates` aus `templates/mail/`; jede Mail rahmt ihren Inhalt mit `mail/layout`
(Kopfleiste mit Logo, Karte, Fuß mit dem Hinweis auf den automatischen Versand und dem Link, wo man
das ändert).

- **Nur Tabellen und Inline-Stile.** Outlook für Windows rendert mit der Engine von Word: kein
  `flex`, keine Klassen aus `salat.css`, keine `<style>`-Blöcke, auf die Verlass wäre. Eine
  Schaltfläche ist eine Tabellenzelle mit Hintergrund und einem Link darin.
- **Farben aus `MailColors`**, nie als Literal im Template. Das ist die eine Ausnahme von „Farben
  nur aus Tabler-Tokens“: Mailprogramme kennen weder CSS-Variablen noch `oklch()`. Jeder Wert ist
  ein Token, umgerechnet und mit gemessenem Kontrast (≥ 4,5:1), beides steht an `MailColors`.
- **Kein `@{…}` und nichts aus der Anfrage.** Mails werden auch aus Jobs und nach dem Commit
  gerendert; jeder Link kommt als absolute Adresse (`salat.url` + Pfad) in die Variablen.
- **Text aus Eingaben nur mit `th:text`.** Kommentare und Namen erscheinen als Text, nie als Markup.
- **Das Logo ist eingebettet** (`cid:salat-logo`); `MailService` hängt es an jede Mail mit HTML-Teil.
- **Prüfen:** lokal in MailHog (Port 8025), dort sind beide Teile einzeln zu sehen. Ein neues
  Layout zusätzlich in Outlook und Apple Mail ansehen.

## Globally Accessible Objects in `layout/base.html`

`layout/base.html` is the shared layout template rendered for every page. It has no dedicated controller, so Spring MVC model attributes are not available.

**Convention:** Access Spring beans via `${@beanName.property}` — Thymeleaf resolves `@beanName` as a Spring application context lookup.

```html
[[${@buildProperties.version}]]
[[${@salatProperties.docsUrl}]]
```

**Constraint:** `@beanName` is only valid inside `${...}` expressions. It is **not** allowed inside `@{...}` URL expressions. For URLs sourced from a bean, extract the value first with `th:with`, then reference it via `${...}`:

```html
<!-- correct -->
<a th:with="url=${@salatProperties.docsUrl}" th:href="${url}">...</a>

<!-- fails: @beanName prohibited inside @{} -->
<a th:href="@{${@salatProperties.docsUrl}}">...</a>
```

**Dasselbe gilt für die Parameter einer Nachricht.** `#{key(${@bean.wert})}` wertet Thymeleaf im
eingeschränkten Kontext aus und bricht die Seite mit *„Instantiation of new objects and access to
static classes or parameters is forbidden in this context"* ab — ein Fehler, der erst auftritt, wenn
die Stelle überhaupt gerendert wird (die Impersonations-Zeile in `layout/base.html` trug ihn
unbemerkt bis #1033). Der **Schlüssel** einer Nachricht darf die Bohne lesen, der Parameter nicht;
also erst mit `th:with` binden:

```html
<!-- correct -->
<span th:with="actsAs=${@authorizedUser.impersonateLoginSign}"
      th:text="#{main.general.impersonation.actsas.text(${actsAs})}">...</span>

<!-- fails at render time: @beanName as a message parameter -->
<span th:text="#{main.general.impersonation.actsas.text(${@authorizedUser.impersonateLoginSign})}">...</span>
```

---

## TomSelect Dropdowns

All `<select>` elements use [TomSelect](https://tom-select.github.io/) for search-as-you-type behaviour. Initialisation is handled centrally in `layout/base.html` via a `querySelectorAll` on page load and again on `htmx:after:swap` (so OOB-swapped selects are picked up automatically).

### CSS class contract

| Class | `maxItems` | When to use |
|---|---|---|
| `tomselect` | `1` (single) | Any select where only one value is needed |
| `tomselect tomselect-multi` | `null` (unlimited) | Multi-select; always combine with the HTML `multiple` attribute |

### Single-select

```html
<select class="form-select tomselect" th:field="*{orderId}">
  <option value="">-- Select --</option>
  <option th:each="o : ${orders}" th:value="${o.id}"
          th:with="subtext=${@customerorderViewHelper.customerLabel(o)}" th:attr="data-subtext=${subtext}"
          th:text="${@customerorderViewHelper.label(o)}"></option>
</select>
```

### Multi-select

Always add both `tomselect-multi` **and** the native `multiple` attribute. The class sets `maxItems: null` in TomSelect; `multiple` ensures the browser submits all selected values so Spring MVC can bind them to a `List<Long>` (or `List<String>`).

The class also switches on the `remove_button` plugin: every chip carries an × that removes it by tap or click and fires `change` on the `<select>` (#1149). Its translated name comes from `data-select-remove-label` on `<body>` in `layout/base.html`, so a template with a multi-select needs nothing extra.

```html
<select class="form-select tomselect tomselect-multi" th:field="*{contractIds}" multiple>
  <option th:each="ec : ${contracts}" th:value="${ec.id}" th:text="${@employeeLabelViewHelper.contractLabel(ec)}"></option>
</select>
```

### Bezeichnung von Stammdaten (#1266)

Jede Art von Stammdaten heißt in jeder Auswahlliste gleich, und die Bezeichnung entsteht an genau
einer Stelle — einem View-Helper je Art. Ein Template verkettet keine Felder zu einer Bezeichnung.

| Stammdaten | Option | Unterzeile (`data-subtext`) | Helper |
|---|---|---|---|
| Person | `Vorname Nachname \| Kürzel` | – | `@employeeLabelViewHelper.label(e)` |
| Vertrag | wie die Person | Zeitraum, offenes Ende `∞` | `.contractLabel(ec)`, `.contractPeriod(ec)` |
| Kunde | `Kurzname - Name`, ohne eigenen Kurznamen der Name einmal | – | `@customerLabelViewHelper.label(c)` |
| Auftrag | `Kennung - Kurzbeschreibung` | Kunde | `@customerorderViewHelper.label(co)`, `.customerLabel(co)` |
| Unterauftrag | `Auftrag/Unterauftrag - Kurzbeschreibung` | `Auftrag-Kurzbeschreibung · Kunde` | `@suborderLabelViewHelper.label(so)`, `.subtext(so)` |

- Ein Helper-Ergebnis in `data-subtext` erst mit `th:with` binden — `th:attr` erreicht keine Bohne
  (→ „Bestätigungen und modale Dialoge").
- Die Kennzeichnung ausgeblendeter Einträge hängt das Template an die Option
  (→ [domain-rules](domain-rules.md), „The `hide` Flag").
- Wo statt der Entität ein Datensatz mit den Einzelwerten vorliegt, nimmt der Helper die Werte
  (`label(name, sign)`); Java-Code ohne Instanz ruft die statischen `of(...)`.
- **In einer Mehrfachauswahl trägt die Option ihre Kennung als `data-chip`** — Kürzel, Kurzname.
  Der Chip des gewählten Eintrags zeigt nur sie, der volle Text steht als Tooltip daran; sonst
  brechen schon zwei Einträge das Feld um.

### Neue Einträge und Farbpunkte (#847)

- `data-allow-create="true"` (im Dialekt `allowCreate="true"`) lässt einen getippten Wert als neuen
  Eintrag zu.
  - `salat.js` normalisiert ihn so, wie der Server speichert: getrimmt, mehrere Leerzeichen zu
    einem.
  - Unterscheidet er sich von einer vorhandenen Option nur in Groß- und Kleinschreibung, bietet
    `salat.js` ihn nicht neu an.
  - `data-create-label` beschriftet die Zeile, „No results found“ entfällt dann.
- `data-color="<tabler-farbe>"` an einer Option färbt den Eintrag.
  - In der Auswahlliste steht ein Punkt vor dem Namen (`.tag-dot`).
  - Der gewählte Chip ist die Badge der Liste (`badge bg-<farbe>-lt`) mit den Maßen eines Chips.
  - Beim Hover zeigt das × einen Rahmen in der Schriftfarbe, eine Fläche würde den Kontrast der
    Schrift senken.
  - Der aktive Chip bekommt statt der Primärfläche einen Ring.
- `data-tag-colors` am Select nennt die Palette (`TagColor.palette()`). Damit bekommt ein getippter,
  noch nicht gespeicherter Tag schon die Farbe, die der Server ihm gibt. `salat.js` rechnet dafür
  `TagColor.of` nach: Javas `hashCode` des Namens in Kleinbuchstaben modulo Palette. Ändert sich die
  Regel, ändern sich beide Stellen, `TagColorTest` hält die Paare fest.
- Die Unterzeile unter dem Feld (`{field}-subtext`) gibt es nur bei einer Einzelauswahl.
- Ein Tag-Badge in einer Liste ist ein Link (`order/tags :: badges(tags, listUrl, paramName)`).
  - Ein Klick setzt den Tag-Filter der Seite auf genau diesen Tag, `select[data-tag-filter]` per
    `salat.js`, die übrigen Filter bleiben stehen.
  - Ersetzt statt ergänzt wird, weil mehrere Tags ODER bedeuten: Ein Klick, der ergänzt, würde die
    Liste länger machen.
  - Ohne Filter auf der Seite, im neuen Tab oder mit Strg-Klick öffnet der Link die Liste mit dem
    Tag als Filter.

### Free text field with remote suggestions

A field whose value is free text but where the known values should be offered while typing is an
`<input type="text" class="form-control tomselect">` — the central initialisation picks up inputs as
well as selects. What the user types always wins; the list is a convenience.

| Attribute | Purpose |
|---|---|
| `data-remote-url` | endpoint queried on every keystroke; gets `q` plus the context parameter, answers with JSON `[{key, summary}]` |
| `data-remote-context-field` | CSS selector of the field whose value scopes the search. **Point it at the select the user operates and send its id**, not at a hidden field carrying a derived value: the ticket suggestions read `#suborderId` and let the server resolve the order branch from it (#1025). A hidden mirror has to be kept in step by hand — through the change event and through every HTMX swap that re-renders the select — and it hands the endpoint a scope the browser chose |
| `data-remote-context-param` | name of the request parameter that carries that value |
| `data-fill-target` | CSS selector of a field that gets the chosen entry as `key - summary` — **only while it is untouched**: empty, or still holding exactly what an earlier pick wrote there. A text somebody typed is theirs and is never overwritten, not even on the next pick. An entry without a `summary` is text somebody typed rather than one of the offered rows, and fills nothing |
| `data-create-label` | prefix of the "use what I typed" row; pass an `#{...}` message |

Without a context value nothing is loaded, so the field degrades to a plain text input. The endpoint
belongs to the module that owns the data — a browser call crosses no module boundary, whereas a
command event would keep the coupling while hiding the import (`/jira/tickets/suggestions`, #982).
Such an endpoint must not live under `/api` or `/rest`: those are stateless filter chains for machine
clients and do not accept a browser session.

### HTMX + OOB swaps

`htmx:after:swap` re-initialises any `select.tomselect` that does not yet have a `.tomselect` instance, so OOB-replaced selects are picked up without extra work. **Do not** add a `multiple` attribute to single-select OOB replacements — if the original select was single, the OOB replacement must also be single.

---

## Salat Custom Thymeleaf Dialect (`salat:`)

The `SalatDialect` (prefix `sal`, registered via `ThymeleafDialectConfiguration`) provides element processors that replace verbose `th:replace` fragment calls with clean, attribute-based tags.

### Available tags

| Tag | Attributes | Replaces |
|---|---|---|
| `<salat:form>` | `th:action` (expr), `th:object` (expr), `th:id-property` (field) | `<form method="post" class="card">` with `card-body`/`card-footer` divs and hidden id input |
| `<salat:inputs>` | _(body: input tags)_ | `<div class="card-body">` wrapper (used inside `salat:form`) |
| `<salat:buttons>` | _(body: button tags)_ | `<div class="card-footer">` wrapper (used inside `salat:form`) |
| `<salat:textInput />` | `th:field` (field), `th:label` (expr), `required`, `maxlength`, `th:helpText` (optional expr), `type`, `min`/`max` (bounds of a number field) | `<div class="mb-3">` with label, `<input class="form-control">`, optional `form-text` and `invalid-feedback` div (the old fragments are gone, #1247) |
| `<salat:textarea />` | `th:field` (field), `th:label` (expr), `required`, `rows` (default 3), `monospace`, `th:helpText` (optional expr) | `<div class="mb-3">` with label, `<textarea class="form-control">` (monospace via `style`), optional `form-text` and `invalid-feedback` div |
| `<salat:checkboxSwitch />` | `th:field` (field), `th:label` (expr) | `fragments/form-fields :: checkboxSwitch` |
| `<salat:select>` | `th:field` (field), `th:label` (expr), `required` (optional), `allowEmptyOption` (optional, keeps an option with the empty value selectable in TomSelect), `allowCreate` (optional, what is typed becomes a new entry, #847), `th:createLabel` (optional expr, the label of that row); every `data-*`/`th:data-*` attribute goes to the select unchanged | `<div class="mb-3">` with stacked label, `<select class="form-select tomselect">`, auto `<small id="{field}-subtext">` for subtext, and `invalid-feedback` div; body is the `<option>` elements |
| `<salat:formButtons />` | `th:saveLabel` (expr), `th:cancelHref` (expr) | `fragments/form-fields :: formButtons` |

### Usage

1. Declare the namespace on the `<html>` element: `xmlns:salat="http://hbt.de/salat/thymeleaf"`
2. Use `salat:form` as a container; `salat:inputs` and `salat:buttons` wrap field groups:

```html
<salat:form th:action="@{/customers/store}" th:object="${customer}" th:id-property="*{id}">
  <salat:inputs>
    <salat:textInput th:field="*{shortName}" th:label="#{main.customer.shortname.text}" required="true" maxlength="12" />
    <salat:textarea th:field="*{description}" th:label="#{label.description}" required="false" rows="5" />
    <salat:checkboxSwitch th:field="*{active}" th:label="#{label.active}" />
  </salat:inputs>
  <salat:buttons>
    <salat:formButtons th:saveLabel="#{main.button.save}" th:cancelHref="@{/customers}" />
  </salat:buttons>
</salat:form>
```

### Implementation notes

- Processors live in `de.hbt.salat.common.thymeleaf.processor`, extend `AbstractSalatProcessor` → `AbstractElementTagProcessor`.
- `th:label`, `th:saveLabel`, `th:cancelHref`, and `th:helpText` accept any Thymeleaf expression (`#{...}`, `${...}`, `@{...}`, or composite).
- The replacement model is processed (`replaceWith(model, true)`), so `th:field` / `th:errors` in generated output are handled by the standard dialect.
- When adding a new processor: register it in `SalatDialect.getProcessors()`.

---

## UiState: Filter Parameters Carry the Prefix `f` (→ ADR-0022)

`UiStateFilter` remembers a selection across page changes and puts every remembered value **under
the request as a fallback parameter** — `getParameter`, `getParameterValues` and `getParameterMap`
answer with it wherever the request itself brings none. The mapping is global across all modules
and applies to every request, whatever its method or path. A name registered in a
`UiStateKeyContributor` therefore acts in every form of the application; the two namespaces are
kept disjoint:

- **Every registered parameter name is `f` + a capital letter**: `fYear`, `fMonth`,
  `fCustomerOrderId`, `fCustomerFilter`, `fEmployeeOrderShowHidden`. `UiStateKeyRegistry` refuses a
  name without the prefix at startup — the application does not come up.
- **A form field is never called `f…`**. `UiStateParameterNamingTest` checks that no registered
  name is bound via `th:field`, appears in a POST form, or is a field of a `*Form` class.
- The prefix replaces the old module abbreviations: names are spelled out (`cFilter` →
  `fCustomerFilter`, `eoFilter` → `fEmployeeOrderFilter`).
- **Filter forms carry the parameters directly** — `<input name="fCustomerFilter">`, not a bound
  form object. A filter has no `*FilterForm`.
- **A create form may use the filter values as optional input**: `createForm(@RequestParam(required
  = false) Long fCustomerId, …)` prefills the new entry with what the list has selected. The
  **link** to that form passes nothing (see below).
- **Whether a form was submitted or the page merely called is told by a marker of its own**
  (`evaluate`, #1009), never by the presence of an `f…` parameter
  (→ [backend-patterns](backend-patterns.md), Controller Pattern).

---

## Saving Never Changes the Filter (→ ADR-0023)

Creating or editing never changes the filter: a store method never calls `uiState.clearState(...)`.
Where the filter may hide what was just saved, the success message says so:

```java
filterHintViewHelper.addSuccess(redirectAttributes,
    messages.getMessage("form.suborder.message.stored", "Suborder saved successfully"),
    SUBORDER_FILTER, CUSTOMER_ID, CUSTOMER_ORDER_ID);
```

The hint travels as its own flash attribute (`toastSuccessHint`); the toast gives it its own line.

Pass only the filters that can **exclude** an entry — search text and selections, not `showHidden`
or `showInactive`.

**A link to a create form carries no filter parameter either** — the click would rewrite the
filter, and an *empty* value (the usual case: the link renders whatever the list had) is a
parameter that is present, so the fallback stops supplying the remembered one and the form opens
with nothing preselected. `th:href="@{/orders/suborders/create}"`, not `@{/orders/suborders/create(fCustomerId=…)}`:
the fallback prefills the form anyway. Where the form must start with something *other* than the
current selection, the link names the **form field** — `/orders/suborders/create?customerorderId=42`
after creating an order — and the controller falls back to the remembered value without it.
`UiStateParameterNamingTest.noCreateLinkCarriesAFilterParameter` checks this across templates and
Java sources.

---

## List View Filter Toggles
List views that support both validity and visibility filtering expose two independent boolean toggles in the advanced filter section:
- `showInactive` (`Boolean`) — when `true`, includes inactive records; default `null`/`false` leaves them out
- `showHidden` (`Boolean`) — when `true`, includes records whose `hide` flag is set; default `null`/`false` excludes hidden records

**Der Schalter heißt überall `showInactive`** (#950) — im Request-Parameter, im `UiState`-Schlüssel,
im Modellattribut, im Feld des Templates und im Parameter von Service und DAO. Weder `show` noch
`showInvalid` noch `showOnlyValid`: „invalid" klingt nach fehlerhaften Daten, und ein Schalter mit
umgekehrter Bedeutung zwingt jede Schicht, beim Durchreichen nachzudenken. Beide Schalter können
eine Liste nur **erweitern**; das ist der Grund, warum sie nach ADR-0023 nicht in den Filterhinweis
einer Erfolgsmeldung gehören.

**DAO layer**: these are separate `Specification` predicates — never bundle `notHidden` into the
validity predicate. Apply each independently:
```java
if (!TRUE.equals(showInactive)) predicates.add(Validity.<Suborder>notInactive(Suborder_.untilDate).toPredicate(root, query, builder));
if (!TRUE.equals(showHidden))   predicates.add(notHidden().toPredicate(root, query, builder));
```

**Entity without own `hide` field** (e.g. `Employeeorder`): filter on the parent's flag via a join — `notHidden()` checks `suborder.hide` and `suborder.customerorder.hide`, each through `Hiding.notHidden(builder, path)` so that a never-set flag counts as not hidden there too (→ [domain-rules](domain-rules.md), „The `hide` Flag").

**Persistenz des Filters**: über `UiState` (→ ADR-0022), nicht über die Session. Der Parametername
ist `f` + Entität + `ShowInactive` bzw. `ShowHidden` (`fSuborderShowInactive`,
`fEmployeeContractShowHidden`) und in einem `UiStateKeyContributor` registriert. Wer ihn umbenennt,
benennt Request-Parameter, Schlüssel, Modellattribut und Template-Feld gemeinsam um — sonst zeigt
der Schalter den gemerkten Stand nicht mehr an.

**Template**: add both as `form-check form-switch` checkboxes in the advanced filter row, using `name=”fXShowInactive”` / `name=”fXShowHidden”` with `value=”true”` and `th:checked`, each with the hidden `value="false"` twin so switching off sends a value; the auto-submit script picks them up automatically.

---

## Listen mit Obergrenze (#1092, #1380)

Eine Liste, die ohne Filter zu groß würde, zeigt trotzdem sofort etwas — begrenzt, nicht leer.
Buchungsliste, Ticketliste und die drei Auftragslisten teilen dafür eine Mechanik:

- **`ListLimits`** (`common.web`) liest die Stufe aus dem Request-Parameter (50, 100, 500, 1000,
  „Alle“; Vorgabe 500) und deckelt „Alle“ auf `salat.booking-list.all-max-rows`. Die Stufe ist ein
  Filter mit eigenem `f…Limit`-Schlüssel je Liste (ADR-0022).
- **Die Datenbank begrenzt und zählt**: `findAll(Specification, Pageable)` bzw. eine eigene Zähl-
  und Zeilenabfrage, nie alle Treffer laden und in Java abschneiden. Berechtigung und Suchtext
  gehören dafür in die Abfrage. Was jede Zeile zeigt, holt die Zeilenabfrage per `join fetch`;
  die Zählabfrage liest die Filter allein (`findAll(spec, countSpec, pageable)`).
- **`fragments/list-limit.html`**: `select(name)` für die Auswahl, `truncated(text)` für den
  Hinweis oben in der Karte der Tabelle, wenn mehr Treffer da sind als Zeilen.

---

## Grouped List View Pattern
When a list should be partitioned by a categorical field, render one card+table per group instead of a single flat table.

**Ordered groups (3+ values)** — iterate over a literal key list so order is explicit:
```html
<th:block th:each="statusKey : ${ {'bl','pv','bo','ma','restricted','adm'} }"
          th:with="group=${employees.?[status == '__${statusKey}__']}">
  <div th:if="${not #lists.isEmpty(group)}" class="card mb-3">
    <div class="card-header">
      <h3 class="card-title" th:text="#{${'main.employee.status.' + statusKey}}">Status</h3>
    </div>
    <div class="table-responsive">
      <table class="table table-vcenter card-table">...</table>
    </div>
  </div>
</th:block>
```

**Two groups** — use two explicit `th:block` sections (clearer than iterating):
```html
<th:block th:with="group=${contracts.?[freelancer != true]}">
  <div th:if="${not #lists.isEmpty(group)}" class="card mb-3">
    <div class="card-header"><h3 class="card-title" th:text="#{main.employeecontract.group.internal.text}">Internal Staff</h3></div>
    ...
  </div>
</th:block>
<th:block th:with="group=${contracts.?[freelancer == true]}">
  <div th:if="${not #lists.isEmpty(group)}" class="card mb-3">
    <div class="card-header"><h3 class="card-title" th:text="#{main.employeecontract.group.contractors.text}">Contractors</h3></div>
    ...
  </div>
</th:block>
```

Rules:
- The card header conveys the group value — remove the corresponding column from the table header and rows.
- Hide empty groups with `th:if="${not #lists.isEmpty(group)}"`.
- Show the global empty-state card only when the entire list is empty (place it before the group blocks).
- SpEL selection syntax: `list.?[field == value]` or `list.?[field != true]`.
- Where the groups are many and long (#1523, the report list), the heading of each card is a
  `<button>` with `aria-expanded` and `aria-controls` that opens and closes its group, with
  „Alle aufklappen“ / „Alle zuklappen“ above the list. What is closed is remembered in the browser
  (`localStorage`, keyed by the id of the group); while a filter is set every group it leaves is open.
