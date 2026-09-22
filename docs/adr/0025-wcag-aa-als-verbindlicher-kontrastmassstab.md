# ADR-0025 WCAG AA als verbindlicher Kontrastmaßstab, Farben nur aus Tabler-Tokens

Date: 2026-09-16
Status: Accepted

> **Nachtrag 2026-09-22:** Die Entscheidung ist unverändert; ein Token ist hinzugekommen. Tabler
> kennt genau ein Gelb (`#f59f00`), einen Bernstein — als Fläche gelesen ein Ocker, abgedunkelt für
> helle Schrift ein Braun. `--tblr-yellow` und `--tblr-warning` sind deshalb auf `#ffcc00` gesetzt;
> alles Abgeleitete mischt Tabler aus dem Token und folgt. Aus drei benannten Literalen sind damit
> vier geworden. Gelbe Badges tragen dunkle Schrift auf voller Farbe, weil ein Gelb, auf dem helle
> Schrift trägt, kein Gelb mehr ist. Rot im Dunkelmodus wird nicht mehr mit Weiß gemischt, sondern
> über den Rand des sRGB-Raums gesättigt (`oklch(from … )`). Werte und Begründung stehen im Style
> Guide.

## Context and Problem Statement

Zwei Beobachtungen mit demselben Kern führten zu #1022: Farben, die nicht aus den Tabler-Tokens
kommen oder nicht gegen den Hintergrund geprüft sind, auf dem sie landen.

Der Style Guide hielt schon fest: *„ausschließlich Tabler-Tokens (`--tblr-*`); keine eigene
Marken-Palette"*. Im aktiv genutzten Code galt das an fünf Stellen nicht — Textmarkierung,
Diagrammfarben, „Heute"-Markierung, Effektfarben der Matrix, Sponsor-Herz. Zugleich war der
Kontrast nirgends geprüft; Diskussionspunkt 23 des Style Guides hielt genau das als offen fest.

Die Messung zeigte, dass beides zusammenhängt: `.text-muted` — die häufigste Textklasse der
Anwendung, 267× — lag im Dunkelmodus bei 3,04:1, weil Tabler die Klasse auf `--tblr-muted` zeigen
lässt, eine *Themefarbe*, statt auf ein modusabhängiges Token. Und `bg-*-lt` färbt nicht nur die
Fläche, sondern auch den Text: im hellen Modus erreichte **keine** der 18 Tönungen 4,5:1.

Ohne festen Maßstab ist jede Einzelentscheidung verhandelbar, und ohne Token-Bindung wandert jeder
Farbwert früher oder später auseinander. Die Frage ist deshalb nicht „welchen Wert nehmen wir hier",
sondern „woran messen wir, und woher kommt eine Farbe überhaupt".

## Considered Options

Zum Maßstab:

* **A — WCAG AA, 4,5:1 für jeden Text**, unabhängig von seiner Rolle; 3:1 für reine
  Nicht-Text-Elemente
* **B — AA nur für Fließtext**, Kulanz bei 3:1 für Sekundärangaben, Metazeilen und Badges
* **C — AA als Ziel mit gepflegter Ausnahmeliste** für einzelne Stellen

Zur häufigsten Textklasse:

* **D — `.text-muted` an `--tblr-secondary` hängen**, eine Regel in `salat.css`
* **E — Migration auf `text-body-secondary`** in 267 Templates (der von Bootstrap 5.3 vorgesehene
  Weg, → W12)

Zu den getönten Flächen:

* **F — Text auf `--tblr-body-color`**, die Tönung bleibt Bedeutungsträgerin
* **G — nur das aufgefallene `bg-purple-lt` anheben**
* **H — Tablers eigene Abstufungen** `-darken` bzw. `-fg` verwenden

## Decision Outcome

Chosen: **A + D + F**, und Farbwerte kommen **ausschließlich aus Tabler-Tokens**.

**A**, weil eine Kulanzstufe für „nur sekundär" die Grenze dorthin verschiebt, wo sie niemand mehr
prüft. Eine Angabe, die zu unwichtig für lesbaren Kontrast wäre, gehört nicht auf die Seite. B hätte
zudem wenig gespart: `.text-muted` (3,04:1) und die hellen Tönungen ab 1,97:1 fallen auch unter 3:1
durch. C verlagert die Arbeit in eine Liste, die zu pflegen niemandem auffällt, solange nichts weh
tut.

**D**, weil es gemessen das bessere Ergebnis liefert *und* das kleinere Risiko trägt: eine Zeile,
beide Farbmodi, 267 Templatestellen unberührt. E — der auf dem Papier modernere Weg — hätte den
hellen Modus von 4,83:1 auf **3,00:1 verschlechtert**, weil `--tblr-secondary-color` mit 75 % Deckung
gemischt ist. Das ist der Punkt, an dem Messen eine Intuition widerlegt hat.

**F**, weil es die einzige systematische Variante ist, die durchgängig besteht (≥ 8,9:1 in beiden
Modi). H scheidet gemessen aus: `-darken` ist auf hellem Grund *heller* als der Grundton
(1,73:1–3,18:1), `-fg` ist ein Fastweiß für gefüllte Flächen (1,04:1–1,11:1). G hätte den gemeldeten
Einzelfall behoben und das Muster stehen gelassen — `bg-warning-lt` (32×) lag bei 1,97:1.

Die Regel gilt für **jede** getönte Fläche, nicht nur für Badges: dieselbe Klasse trägt die
Wochenend- und Feiertagsspalten der Matrix, die Fehlerzellen, die Dashboard-Kacheln und die Avatare.

Die semantischen `text-*`-Utilities behalten ihren Farbton, gemischt gegen `--tblr-body-color`. Die
Mischung dreht sich mit dem Farbmodus von selbst; der Anteil ist je Farbton der größte, der in beiden
Modi noch 4,8:1 erreicht — so viel Farbe wie möglich bei eingehaltenem Maßstab.

**Werte werden gemessen, nicht geschätzt.** Verfahren, Messtabellen, Mischanteile und die Liste der
verbliebenen Literale stehen in [`docs/ui-style-guide.md` §7.1](../ui-style-guide.md); die Regel
selbst steht in `AGENTS.md`.

### Consequences

* Good: Der Maßstab ist eine Zahl, keine Ermessensfrage — eine Änderung ist prüfbar, nicht
  diskutierbar.
* Good: Farben folgen dem Farbmodus von selbst, weil sie aus Tokens hergeleitet sind. Das gilt auch
  für die Sidebar, die `data-bs-theme="dark"` trägt und deshalb *immer* dunkel ist.
* Good: `.text-muted` und `.text-secondary` sind deckungsgleich — W12 ist durch Angleichung
  aufgelöst statt durch eine Migration an 267 Stellen.
* Bad: `salat.css` korrigiert damit Tabler an mehreren Stellen, statt es unverändert zu übernehmen.
  Ein Tabler-Upgrade muss diese Regeln gegenprüfen; ändert Tabler die Tokens, ändern sich die Werte
  mit.
* Bad: Auf getönten Flächen ist der Text nicht mehr farbig. Wo die Farbe bisher mitgesprochen hat
  (rote Zahl in roter Fehlerzelle), trägt die Aussage nun die Tönung allein.
* Bad: Ein neuer Farbton braucht einen eigenen, gemessenen Eintrag in `salat.css` — ohne ihn gilt
  Tablers Grundton, und der fällt durch.
* Neutral: Es gibt weiterhin keine visuellen Regressionstests. Der Maßstab ist dokumentiert und
  nachmessbar, aber nichts erzwingt ihn automatisch — die Prüfung hängt am Review.
* Neutral: Drei Literale bleiben und sind benannt: der Mischpartner `#fff` in `::selection`, die
  Schattenwerte der Matrix und `1px solid black` im reinen Druck-Stylesheet.
