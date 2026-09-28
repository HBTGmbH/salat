# ADR-0032 Befehle mit Parametern: die Sidebar bietet sie an, der Browser liest, die Seite vervollständigt

Date: 2026-09-28
Status: Accepted

## Context and Problem Statement

Mit #1155 und #1157 springt die Befehlspalette zu Seiten und Objekten. Was danach einzugeben ist —
Tag, Unterauftrag, Dauer, Monat, Person —, wird im Formular noch einmal eingegeben. #1158 gibt der
Palette Befehle mit Parametern: `buchen gestern wart 1,5 Review` öffnet das Buchungsformular
vollständig belegt, `abnahme ppp` die Prüfseite vor der Abnahme.

Vier Fragen waren zu entscheiden:

1. Woher weiß die Palette, welche Befehle eine Person hat? `abnahme` gibt es nur für People Leads.
2. Wer liest die Parameter — und wie schnell muss das gehen? Die Vorschau soll mit jedem Tastendruck
   sagen, was Enter tut.
3. Wer schlägt die Werte vor, die nur der Server kennt: die Unteraufträge, die ein Vertrag an einem
   Tag buchen darf, die Personen, die jemand abnehmen darf, den nächsten offenen Monat?
4. Wie weit darf Enter gehen? ADR-0030 sagt: die Palette speichert nichts.

## Considered Options

Zur Herkunft der Befehle:

* **A — Ein Sidebar-Eintrag trägt den Befehl seiner Seite** (`data-palette-verb`); was die Sidebar
  nicht zeigt, bietet die Palette nicht an.
* **B — Der Server liefert die Befehle** der Person, mit eigener Rollenprüfung je Befehl.

Zum Lesen der Parameter:

* **C — Tag, Monat und Dauer liest der Browser;** Objekte, der letzte Arbeitstag und die Monate von
  Freigabe und Abnahme kommen vom Server.
* **D — Der Server liest die ganze Eingabe** und antwortet mit Chips und Vorschau.

Zu den Vorschlägen des Servers:

* **E — `PaletteProvider#suggest`:** je Befehl und Parameter antwortet das Modul, dem die Zielseite
  gehört, aus derselben Quelle wie diese Seite.
* **F — Das Modul `palette` fragt die Services** der Fachmodule direkt.

## Decision Outcome

Chosen: **A + C + E**, und Enter öffnet immer ein vorbelegtes Formular oder eine Prüfseite.

**A** — dasselbe Argument wie in ADR-0030 für die Seiten: die Sidebar ist schon die Antwort auf
„was darf diese Person". `abnahme` hängt an „Abnahme", das nur eine People Lead sieht, `controlling`
an „Soll-Ist-Controlling", das nur sieht, wer ein Budget sehen darf. B hätte die Rollenfrage ein
zweites Mal gestellt. Was ein Befehl mit seinen Werten tut — welche Parameter, welches Ziel —, steht
in `salat.js`, die Wörter und Texte stehen am Dialog.

**C** — Tag, Monat und Dauer sind Rechnen mit dem Kalender und mit denselben Schreibweisen wie im
Zeitfeld (`parseDurationValue`); der Browser kann es ohne Anfrage, und die Vorschau läuft beim
Tippen mit. D hätte jeden Tastendruck zu einer Anfrage gemacht. Den letzten Arbeitstag kann der
Browser nicht wissen — Feiertage —, er kommt einmal je Öffnen vom Server. Was ein vollständiges Wort
genau einem Wert zuordnet, wird Chip; was nicht passt, bleibt offen und wird vorgeschlagen. Ein
optionaler Parameter, zu dem das Wort nicht passt, bleibt leer (`buchen wart` bucht heute).

**E** — `PaletteProvider` bekommt `suggest(PaletteSuggestionRequest)`; das Modul `palette` sammelt
die Antworten wie bei der Suche, in einer immer zurückgerollten Transaktion, und ein Anbieter, der
mit einer `AuthorizationException` endet, trägt nichts bei (ADR-0031). Geantwortet wird vom Modul,
dem die Zielseite gehört — wer die Seite schützt, weiß, welche Werte sie annimmt:

* `dailyreport` für `buchen`, `tag`, `matrix`, `freigabe`, `abnahme`. Die Unteraufträge sind die
  Liste des Buchungsformulars (`SuborderOption.bookable`), die Monate die der Seiten
  (`ReviewMonths`), die Personen der Abnahme die der Abnahmeseite, jede nur mit
  `ReleaseService#isAcceptAllowed`.
* `budget` für `controlling`: die Auftragssuche der Palette, eingeschränkt mit
  `BudgetAuthorization` wie das Controlling-Ziel aus #1157.

F hätte `palette` zum Knoten aller Module gemacht, das hat ADR-0031 schon verworfen.

**Enter** öffnet das Ziel mit den Werten, die dastehen, und nimmt dabei den gewählten Vorschlag für
den offenen Parameter mit, wenn etwas dafür getippt ist oder der Befehl ihn braucht; die Vorschau
zeigt genau das. Gespeichert, freigegeben, abgenommen wird auf der Seite (ADR-0030, Grundsatz C). Das
Buchungsformular setzt den Fokus dorthin, wo die Palette aufgehört hat: aufs erste Feld, das sie
nicht belegt hat, sind alle belegt, auf „Speichern" (`focus=`).

### Consequences

* Good: Ein neuer Befehl braucht einen Sidebar-Eintrag mit `data-palette-verb`, seinen Eintrag in
  `PALETTE_VERBS` und, wo Werte vom Server kommen, einen Zweig in `suggest` des Moduls seiner Seite;
  die Rollenfrage beantwortet weiter die Sidebar.
* Good: Die Palette schlägt nichts vor, was die Seite nicht annähme — ein Unterauftrag, den das
  Formular am Tag nicht zeigt, steht ausgegraut mit dem Grund, statt zu fehlen.
* Bad: Der Server weiß, welcher Befehl fragt (`PaletteCommand`), der Browser, was ein Befehl mit
  seinen Werten tut — ein Befehl steht an zwei Stellen. Beide sind kurz, und sie ändern sich
  zusammen.
* Bad: Die Personen- und Auftragssuche aus #1157 verlangt ein Wort; ohne Eingabe listet die Palette
  dort nichts. Eine Liste aller Aufträge hülfe niemandem.
* Neutral: Die Ticketnummer ist ein eigener, optionaler Parameter von `buchen`, nur mit ihrer
  ganzen Nummer — jedes andere Wort beginnt den Kommentar. Ohne Kommentar wird er mit Nummer und
  Titel des Tickets vorbelegt, wie beim Auswählen im Formular.
