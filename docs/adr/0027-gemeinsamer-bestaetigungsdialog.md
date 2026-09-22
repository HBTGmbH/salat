# ADR-0027 Ein gemeinsamer Bestätigungsdialog, und er nennt das Geschäftsobjekt

Date: 2026-09-22
Status: Accepted

## Context and Problem Statement

Bestätigungen liefen an 26 Stellen in sieben Modulen über das native `confirm()` des Browsers,
eingebunden als `th:onsubmit="|return confirm('#{…}')|"` oder als `th:onclick` an einem
Submit-Knopf. Zwei davon fragten in hart codiertem Englisch (`'Are you sure?'`).

Das native Popup hat drei Mängel, und der dritte ist der eigentliche:

1. Es folgt nicht dem Design-System — im Dunkelmodus ist es ein Fremdkörper, und über den
   i18n-Text hinaus lässt sich nichts daran gestalten.
2. Es ist nicht prüfbar: ein Playwright-Test muss `page.onDialog` registrieren, und wer das
   vergisst, bekommt stillschweigend ein „Abbrechen" statt eines Fehlers.
3. **Es sagt nicht, worauf sich die Aktion bezieht.** „Soll der Eintrag wirklich gelöscht
   werden?" steht vor einer Liste mit zwanzig gleich aussehenden Zeilen — und der Dialog legt
   sich über genau die Zeile, an der man gegenprüfen würde. Das ist die Stelle, an der das
   falsche Objekt verschwindet.

Modale Dialoge gab es daneben bereits: Benutzerwechsel, Buchung löschen, Favorit löschen, Teilen,
Anonymisieren. Jeder war für seinen Zweck handgeschrieben, mit eigenem `openXyzModal(btn)` in der
Seite. Eine wiederverwendbare Bestätigung gab es nicht — 26 weitere handgeschriebene Dialoge
wären die naheliegende und die falsche Antwort gewesen.

Der Style Guide hielt beides als offene Frage fest (§5.5 sowie die Diskussionspunkte 4 und 18).

## Considered Options

Zur Form des Dialogs:

* **A — Ein Dialog für die ganze Anwendung**, als Fragment in `layout/base.html`, von der
  auslösenden Aktion über `data-`Attribute beschrieben und von einem delegierten Handler in
  `salat.js` gefüllt.
* **B — Ein Dialog je Seite**, handgeschrieben, so wie `#deleteModal` und
  `#deleteFavouriteModal` es in `daily.html` vormachten.
* **C — Beim nativen `confirm()` bleiben** und nur den Text um den Objektnamen erweitern.

Zum Auslösen:

* **D — Ein delegierter Listener auf `submit` in der Capture-Phase**, der das Ereignis dort
  stoppt und nach der Bestätigung mit `requestSubmit()` erneut auslöst.
* **E — HTMX' `hx-confirm` samt `htmx:confirm`-Ereignis** für die über HTMX gesendeten Aktionen,
  ein eigener Weg für die übrigen.

Zur Frage, was der Dialog zeigt:

* **F — Der Dialog nennt die fachlichen Schlüsselinformationen des betroffenen Objekts**, als
  allgemeine Regel für jeden modalen Dialog mit einer Meldung.
* **G — Der Dialog nennt das Objekt dort, wo es gerade passt** — eine Empfehlung, keine Regel.

## Decision Outcome

Chosen: **A + D + F.**

**A** — ein Fragment, `fragments/confirm-dialog.html`, einmal eingebunden in `layout/base.html`.
Die auslösende Aktion beschreibt ihn deklarativ am Formular oder an einem einzelnen Submit-Knopf:
`data-confirm`, `data-confirm-title`, `data-confirm-text`, `data-confirm-detail`,
`data-confirm-detail-secondary`, `data-confirm-detail-input`, `data-confirm-label`,
`data-confirm-variant`. Strukturelle Wiederverwendung ist ein Fragment, kein `salat:`-Dialekt
(→ AGENTS.md, Controller and View Guidelines). Der Handler steht zentral in `static/js/salat.js`;
kein Template bringt für eine Bestätigung eigenes JavaScript mit. B hätte das Muster verdoppelt
statt es aufzulösen, C keinen der drei Mängel behoben.

**D** — ein Listener auf `document` in der **Capture-Phase**, der `preventDefault()` und
`stopPropagation()` aufruft. HTMX 4 registriert seinen Auslöser am Formularelement selbst
(`#initializeTriggers`), also läuft ein Listener in der Bubble-Phase zu spät: das `hx-post` wäre
schon unterwegs. Capture deckt beide Wege mit einer Mechanik ab; E hätte zwei gebraucht, mit dem
Risiko, dass beide greifen und zwei Dialoge hintereinander erscheinen. Ausgelöst wird nach der
Bestätigung mit `requestSubmit()`, nicht mit `submit()` — nur das erste behält den auslösenden
Knopf (Name und Wert) und führt die HTML5-Validierung aus. Ein Merker am Formular sorgt dafür,
dass der Handler sich nicht selbst wieder abfängt. Gehandelt wird auf `hidden.bs.modal`, nicht
auf den Klick: eine HTMX-Aktion tauscht die Seite unter dem Dialog aus, und ein Backdrop, dessen
Modal noch in der Überblendung steckt, bleibt sonst auf dem Schirm stehen.

**F** — die Regel, und sie ist der Grund für den ganzen Umbau:

> Ein modaler Dialog nennt in seiner Meldung die fachlichen Schlüsselinformationen des betroffenen
> Geschäftsobjekts — so viel, dass die Person es ohne Blick auf die Seite dahinter eindeutig
> wiedererkennt. Gibt es kein einzelnes Objekt, benennt der Text stattdessen den Umfang der Aktion
> (Zeitraum, Anzahl, Bereich).

Faustregel: Die Angaben im Dialog müssen zwei benachbarte Zeilen derselben Liste auseinanderhalten
können. Die Datenbank-ID ist keine fachliche Information. Die Regel gilt für **jeden** modalen
Dialog mit einer Meldung, nicht nur für Bestätigungen — deshalb nennt seit #1032 auch der
Teilen-Dialog die Buchung und der Anonymisieren-Dialog die Person, deren Daten überschrieben
werden. G wäre folgenlos geblieben: eine Empfehlung erzeugt keinen Grund, beim nächsten Formular
darüber nachzudenken.

Zwei Dialoge bleiben eigenständig, weil sie keine reinen Bestätigungen sind: der Benutzerwechsel
(die Auswahl findet im Dialog selbst statt), das Teilen-Formular, die Anonymisierung mit ihrer
Doppelbestätigung und die Feldauswahl der JIRA-Replikation. Für sie gilt **F** genauso.

Damit ergeben sich drei Stufen nach Umkehrbarkeit: eine Aktion ohne bleibenden Schaden fragt
nicht; eine löschende oder in die Vergangenheit greifende Aktion geht über den gemeinsamen Dialog,
der benennt, was sie trifft; die Doppelbestätigung bleibt dem einen Fall vorbehalten, der
unwiderruflich Daten überschreibt.

### Consequences

* Good: Eine neue Aktion bekommt ihre Bestätigung durch Attribute am Formular — kein Markup,
  kein Skript, keine Entscheidung über Layout, Fokus oder Tastatur.
* Good: Der Dialog sagt, worauf er sich bezieht. Das war der Anlass; alles andere ist Beiwerk.
* Good: Tastatur und Screenreader sind einmal gelöst statt 26-mal: Fokus beim Öffnen auf der
  Bestätigung, Escape bricht ab (Bootstrap), der Fokus kehrt auf das auslösende Element zurück —
  Letzteres von Hand, weil Bootstrap das nur für über `data-bs-toggle` geöffnete Dialoge tut.
* Good: Prüfbar. `confirmDialog(page)` / `confirmAction(page)` / `cancelAction(page)` in
  `PlaywrightE2ETestBase` ersetzen `page.onDialog`, und ein Test kann jetzt behaupten, *was* der
  Dialog sagt — `FillNotWorkedGuardE2ETest` prüft den Text und den genannten Monat.
* Bad: Die Bestätigung hängt an JavaScript, wo `return confirm(...)` von sich aus blockierte.
  Gegenmaßnahme: `salat.css` blendet jeden bestätigungspflichtigen Submit-Knopf aus, solange
  `salat.js` die Klasse `salat-confirm-ready` nicht gesetzt hat — der Knopf entsteht erst mit dem
  Dialog. `display: none`, nicht `pointer-events: none`: ein nur unklickbarer Knopf bliebe über
  die Tastatur erreichbar.
* Bad: Ein Detailtext, der aus mehreren Feldern zusammengesetzt wird, steht im Template und nicht
  in einem ViewHelper. Für eine Zeile Anzeigetext ist eine eigene Klasse zu viel; wo es unleserlich
  wird, ist der ViewHelper der nächste Schritt.
* Neutral: `th:attr` wertet seine Ausdrücke eingeschränkt aus — eine Bohne (`${@durationUtils…}`)
  ist dort so wenig erreichbar wie in einem Nachrichtenparameter. Wer eine Detailzeile daraus
  baut, bindet den Wert erst mit `th:with`. Steht in AGENTS.md.
* Neutral: Das native Popup war eine Browserfunktion; der Dialog ist jetzt Code, der mitgepflegt
  werden muss. Dafür ist er an einer Stelle.
