# Beta-Funktionen

Eine Beta-Funktion ist in Salat eine Änderung, die jede Person in den Einstellungen selbst
einschaltet. Wer nichts tut, behält die gewohnte Oberfläche. Das Muster stammt aus der
Schnelleingabe für Zeit und Dauer (#830), die mit #1248 zum Standard wurde. Dieses Dokument
beschreibt, wann eine Beta passt und wie sie eingeführt, beworben und beendet wird.

## Wann eine Beta

- **Ja**, wenn eine Änderung einen eingespielten Ablauf auf einer viel benutzten Seite umstellt und
  offen ist, ob alle sie wollen — andere Eingabe, andere Anordnung, etwas fällt weg.
- **Nein** für Fehlerbehebungen, für Rechte und Geschäftsregeln und für neue Seiten ohne alten
  Ablauf. Eine neue Seite bekommt in der Navigation die Marke „New“, keinen Schalter.
- „Beta“ heißt immer: es gibt einen Schalter. Eine Seite ohne Schalter trägt keine Beta-Marke.

Mehrere Tickets, die dieselbe Seite umgestalten, dürfen eine gemeinsame Beta bilden. Ein Ticket
führt sie (Schalter, Bewerbung, Einstellungen), die anderen beschreiben nur, was sich mit
eingeschalteter Beta ändert, und verweisen auf das führende Ticket.

## Einführen

1. **Konstante** in `BetaFeature` mit einem kurzen, stabilen Schlüssel. Er wird je Person
   gespeichert; ein Schlüssel, den es nicht mehr gibt, fällt beim Lesen still weg, eine Migration
   braucht es dafür nie.
2. **Texte** `main.settings.beta.<key>.label` und `.help` (`labelKey()`, `helpKey()`) in allen
   Sprachdateien. Die Einstellungen erzeugen den Schalter daraus selbst; der Bereich
   „Beta-Funktionen“ erscheint nur, solange es eine Konstante gibt.
3. **Getter** in `BetaViewHelper`, der an `isEnabled` delegiert. Templates lesen
   `${@betaViewHelper.<name>}`, nie ein Model-Attribut — die Seiten werden aus vielen
   HTMX-Endpunkten gerendert, und ein Model-Attribut fehlte im nächsten neuen. Im Java-Code fragt
   `BetaFeatureService.isEnabledForCurrentUser`.
4. **Verzweigung klein halten.** Am besten unterscheiden sich beide Varianten in einem Attribut
   oder einem Fragment, nicht in einem zweiten Codepfad. Was beim Beenden gelöscht wird, soll man
   finden: jede Stelle liest die Konstante, im Template mit Kommentar und Ticketnummer.
5. **Einstellungen, die nur in der Beta gelten**, stehen in den Einstellungen unter ihrem Schalter
   und sind nur sichtbar und wirksam, solange die Beta an ist. Gespeicherte Werte bleiben beim
   Ausschalten erhalten.

## Bewerben

Eine Beta, die niemand findet, liefert keine Rückmeldung. Sie wird deshalb dort beworben, wo die
Funktion wirkt, nicht nur in den Einstellungen:

- **Hinweis** oben auf der betroffenen Seite, solange die Beta aus ist: `alert alert-info` mit
  Titel „Neu zum Ausprobieren: …“, einem Satz, was sich ändert, und den Knöpfen **Aktivieren** und
  **Später**.
  - „Aktivieren“ schickt `POST /settings/beta/{key}/enable`. Die Antwort trägt `HX-Refresh`; die
    Seite lädt mit eingeschalteter Beta neu, die Person bleibt, wo sie war.
  - „Später“ und das Schließen merken sich das im `localStorage` (`salat-beta-<key>-hint`), nicht in
    den Einstellungen: Der Hinweis gilt nur für die Dauer der Beta und soll sie nicht in der
    Datenbank überdauern. Er erscheint deshalb höchstens einmal je Gerät.
  - Der Hinweis nennt keinen Rückkanal — wer die Funktion noch nicht kennt, hat nichts zu melden.
- **Einstiegslink** am Bedienelement selbst, klein und rechtsbündig im Kartenkopf
  (`ti ti-bolt`, „… testen“), der zum Abschnitt `/settings#beta` führt. Er bleibt, auch wenn der
  Hinweis weggeklickt ist, und verschwindet, sobald die Beta an ist.
- Texte unter `main.beta.<key>.hint.*` und `main.beta.<key>.link.*`.

## Rückmeldung

- Ist die Beta an, trägt die Funktion die Marke `badge bg-azure-lt` „Beta“ (`main.beta.badge`).
  Ihr Tooltip nennt den Rückkanal (`main.beta.feedback.hint`, Slack-Channel #salat).
- Derselbe Hinweis steht dauerhaft im Abschnitt „Beta-Funktionen“ der Einstellungen.

## Testen

- E2E-Tests prüfen beide Varianten und heißen `…BetaE2ETest`, solange die Beta läuft.
- `BetaFeaturesTest` deckt das Lesen und Schreiben der Schlüssel ab; eine neue Konstante braucht
  dort nur dann einen Fall, wenn sie sich anders verhält.
- Logik, die von der Beta abhängt, wird als Unit-Test am Service bzw. ViewHelper für beide Werte
  des Schalters geprüft.

## Laufzeit und Ende

Eine Beta hat kein festes Ende. Der Schalter bleibt, bis entschieden ist, ob die Funktion Standard
wird oder zurückgezogen. Das Ende ist ein eigenes Ticket (wie #1248) und entfernt in einem Zug:

- die Konstante in `BetaFeature` und den Getter in `BetaViewHelper` — der Compiler zeigt danach
  jede übrige Stelle;
- den verworfenen Zweig in Templates, JavaScript und Java;
- Hinweis, Einstiegslink und Beta-Marke samt ihrer Message-Keys in allen Sprachdateien;
- Verweise in der Tastenkürzel-Übersicht und im UI-Style-Guide;
- das Wort „Beta“ im Namen der E2E-Tests, die dann nur noch die verbliebene Variante prüfen.

Einstellungen, die nur in der Beta galten, werden beim Standardwerden für alle sichtbar oder
entfallen; das Ende-Ticket sagt, welches von beiden. `BetaFeature` bleibt als leeres Enum stehen,
damit die nächste Beta nur eine Konstante, zwei Texte und einen Getter kostet.
