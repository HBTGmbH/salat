# ADR-0030 Die Befehlspalette liest die Seite, und sie speichert nichts

Date: 2026-09-27
Status: Accepted

> **Nachtrag 2026-09-27 (#1157):** Die Geschäftsobjekte — Aufträge, Unteraufträge, Auftraggeber,
> Personen — kommen vom Server, über einen Anbieter je Modul (ADR-0031). Was hier für Seiten, Tage
> und zuletzt Verwendetes gilt, bleibt: sie kommen ohne Anfrage aus. Ein Objekt wird nicht unter
> „Zuletzt verwendet" gemerkt, weil ein gemerkter Eintrag ohne Anfrage angezeigt würde und damit
> ohne die Prüfung, ob er noch geöffnet werden darf. Die Grundsätze C (die Palette navigiert und
> öffnet vorbelegte Formulare, speichert nichts) und „keine Seite, die mit 403 antwortet" gelten
> für die Objekte unverändert.

> **Nachtrag 2026-10-01 (#1231):** Einstellungsseite und Moduswechsel sind aus der Kopfzeile ins
> Nutzermenü unten links gewandert. Die Palette bietet dessen Einträge an, obwohl das geschlossene
> Menü sie verbirgt: unter `[data-palette-menu]` zählt, ob der Server einen Eintrag **gerendert**
> hat, nicht, ob er angezeigt wird — Grundsatz A bleibt also gewahrt, die Palette liest die Seite
> und fragt nicht nach. Dazu kommen die Funktionen der Anmeldung selbst: Abmelden (ein Link),
> Benutzer wechseln — auch direkt zu einer Person, aus den Formularen des Wechseldialogs — und
> dessen Ende. Die beiden letzten schicken ein Formular ab, und das ist die eine, bewusste Ausnahme
> von Grundsatz C: Sie speichern keine Daten, sondern wechseln die Anmeldung, ausgelöst über das
> Formular der Seite mit genau der Prüfung, die Dialog und Menü schon haben, und sie sind jederzeit
> umkehrbar. Eine Rückfrage brauchen sie deshalb so wenig wie ein Klick im Menü. Was C schützt —
> eine Handlung an Fachdaten bleibt dort, wo sie geprüft wird —, berühren sie nicht. Abmelden,
> Benutzerwechsel und dessen Ende werden nicht unter „Zuletzt verwendet" gemerkt: der oberste
> gemerkte Eintrag ist bei leerer Eingabe vorgewählt, und ein versehentliches Enter meldete sonst ab
> oder wechselte die Identität.

## Context and Problem Statement

#1016 wünschte feste Tastenkürzel je Ansicht: `Ctrl+M` für die Matrixübersicht, `Ctrl+T` für die
Einzelübersicht, `Ctrl+t` für die Ticketauswahl. Zwei davon belegt der Browser oder das System
selbst — `Ctrl+T` behält Chrome sich vor, `Ctrl+M` minimiert je nach System das Fenster —, und
jedes weitere Kürzel ist eines mehr zum Auswendiglernen. Der neue Zuschnitt (#1155, #1157, #1158)
ersetzt sie durch eine Befehlspalette: `Ctrl+K` bzw. `⌘K` von jeder Seite, darunter eine
Eingabezeile, die Seiten, Tage, später Geschäftsobjekte und Befehle mit Parametern findet.

Drei Fragen waren damit zu entscheiden, bevor die Folgetickets darauf aufbauen:

1. Woher weiß die Palette, welche Seiten es gibt — und welche davon die angemeldete Person sehen
   darf?
2. Was darf ein Befehl tun? Eine Palette, die bucht, freigibt oder abnimmt, spart den letzten Klick
   und verliert dabei alles, was die Seiten davor leisten: Bestätigung, Prüfseite, Vorschau.
3. Was darf sie den Server kosten? Sie steht zwischen zwei Tastendrücken und muss da sein, bevor
   der nächste Buchstabe ankommt.

## Considered Options

Zur Quelle der Navigation:

* **A — Die Palette liest die gerenderte Sidebar** (`#sidebar-menu .dropdown-item[href]`),
  Beschriftung, Bereich und Rollenfilter kommen mit.
* **B — Eine eigene Liste der Seiten**, im Template oder in einer Bohne, mit eigener
  Rollenprüfung je Eintrag.

Zur Reichweite der Befehle:

* **C — Die Palette navigiert oder öffnet ein vorbelegtes Formular; sie speichert nichts und
  schickt kein Formular ab.**
* **D — Befehle dürfen handeln**, abgesichert durch eine Rückfrage im gemeinsamen Dialog.

Zur Form:

* **E — Ein natives `<dialog>`** mit `showModal()`, Verhalten in `salat.js`.
* **F — Ein Bootstrap-Modal** wie der Bestätigungsdialog.

## Decision Outcome

Chosen: **A + C + E.**

**A** — die Sidebar ist schon die Antwort auf „was sieht diese Person": ihre `th:if` fragen die
Rollen und die ViewHelper, und genau diese Antwort übernimmt die Palette. B hätte die Rollenfrage
ein zweites Mal gestellt, und zwei Stellen, die dieselbe Berechtigung beantworten, laufen beim
ersten neuen Menüeintrag auseinander — entweder fehlt er in der Palette, oder sie bietet eine Seite
an, die mit 403 antwortet. Mit A erscheint ein neuer Eintrag ohne weiteres Zutun, und „keiner, den
die Person nicht sieht" gilt, weil es nichts anderes zu lesen gibt. Dasselbe Prinzip trägt die
Einstellungen: Moduswechsel und Einstellungsseite sind die Knöpfe der Kopfzeile, das Falten ist
der Knopf der Sidebar, alle markiert mit `data-palette-command`; die Palette löst sie aus und bietet nur an, was
gerade angezeigt ist. Ein Eintrag, dessen Ziel die Seite mit ihrem Zusammenhang kennt, verweist
mit `data-palette-href-from` auf das Element, das es trägt — „Neue Buchung" nimmt so das Ziel des
Knopfs in der Kopfzeile (#1156), samt Tag und Rückweg der Seite.

Zuletzt verwendete Befehle werden im Browser gemerkt und beim Öffnen **gegen die Seite neu
aufgelöst**: eine Seite, die die aktuelle Anmeldung nicht sieht, erscheint nicht, wer auch immer
sie vorher benutzt hat. Ein Tagessprung wird als Ausdruck gemerkt (`fr`), nicht als Datum.

**C** — jede Handlung bleibt dort, wo sie geprüft wird: das Buchungsformular validiert, die
Prüfseite vor Freigabe und Abnahme nennt Person, Zeitraum und Folge (ADR-0027, Nachtrag). Die
Palette führt dorthin, mit so viel vorbelegt, wie sie weiß; gespeichert wird mit dem Knopf der
Seite. Damit braucht kein Befehl eine Rückfrage, und die Palette bekommt keine zweite, schwächere
Form der Absicherung neben den Seiten. D hätte für jeden Befehl die Frage gestellt, was der
Dialog nennen muss, und die Antwort wäre jedes Mal die Seite gewesen, die man übersprungen hat.

**E** — `showModal()` öffnet synchron und ohne Überblendung; der Fokus steht im Feld, bevor der
nächste Buchstabe ankommt. Ein Bootstrap-Modal nimmt die Tastatur erst nach seiner Einblendung
an, und was dazwischen getippt wird, landet im Feld dahinter. Das `<dialog>` liegt in der obersten
Ebene über Sidebar und Kopfzeile und braucht keinen `z-index`. Wie beim Bestätigungsdialog gibt
es das Fragment genau einmal (`fragments/command-palette.html` in `layout/base.html`), und kein
Template bringt eigenes JavaScript mit.

Zum Tempo: Seiten, Tage und zuletzt Verwendetes kommen ohne Anfrage aus. Heute ist der Tag des
**Servers**, damit „gestern" denselben Tag meint wie die Einzelübersicht, auch wenn die Uhr des
Rechners anders geht. Die Seite bringt dafür Zeitpunkt und Zeitzone des Servers mit (`data-now`,
`data-time-zone`); festgehalten wird beim Laden, wie weit die Uhr des Browsers davon abweicht, und
heute ist der Tag in der Zeitzone des Servers zur Uhrzeit des Browsers plus dieser Abweichung. Ein
Tab, der über Mitternacht offen bleibt, rechnet so vom neuen Tag aus. Ein bloßes Datum stünde
still, und ein Abstand in ganzen Tagen stimmte nur, solange Browser und Server in derselben Zone
laufen — sonst wechseln die beiden Tage zu verschiedenen Zeiten.

### Consequences

* Good: Ein neuer Menüeintrag, ein neuer Knopf in der Kopfzeile ist ohne Änderung an der Palette
  auffindbar, und die Rollenfrage bleibt an einer Stelle.
* Good: Kein Befehl kann etwas anrichten, was die Seite nicht auch angerichtet hätte; die
  Folgetickets (#1157, #1158) erben diese Grenze, statt sie je Befehl neu zu ziehen.
* Bad: Die Palette hängt an Markup der Sidebar — an `#sidebar-menu`, `.dropdown-item[href]` und
  `.nav-link-title`. Wer die Sidebar umbaut, muss sie mitdenken;
  `CommandPaletteE2ETest` vergleicht deshalb je Rolle die Einträge der Palette mit denen der
  Sidebar.
* Bad: Eine Seite, die nicht in der Sidebar steht, findet die Palette nicht. Das ist gewollt — was
  die Navigation nicht anbietet, bietet die Palette auch nicht an —, heißt aber, dass ein Ziel für
  die Palette zuerst ein Ziel der Navigation werden muss.
* Neutral: Die Geschäftsobjekte aus #1157 kommen vom Server; dort prüft jeder Anbieter selbst, was
  die Person sehen darf. Die Grundsätze hier gelten für sie unverändert.
