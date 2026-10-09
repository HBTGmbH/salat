# Beta-Funktionen

Eine Beta-Funktion ist in Salat eine Änderung, die jede Person in den Einstellungen selbst
einschaltet. Wer nichts tut, behält die gewohnte Oberfläche. Das Muster stammt aus der
Schnelleingabe für Zeit und Dauer (#830), die mit #1248 zum Standard wurde. Dieses Dokument
beschreibt, wann eine Beta passt und wie sie eingeführt, beworben, gemessen und beendet wird. Die
Messung liegt im Modul `beta` (#1447); warum sie so gebaut ist, steht in ADR-0039.

## Wann eine Beta

- **Ja**, wenn eine Änderung einen eingespielten Ablauf auf einer viel benutzten Seite umstellt und
  offen ist, ob alle sie wollen — andere Eingabe, andere Anordnung, etwas fällt weg.
- **Nein** für Fehlerbehebungen, für Rechte und Geschäftsregeln und für neue Seiten ohne alten
  Ablauf. Eine neue Seite bekommt in der Navigation die Marke „New“, keinen Schalter.
- „Beta“ heißt immer: es gibt einen Schalter. Eine Seite ohne Schalter trägt keine Beta-Marke.

Mehrere Tickets, die dieselbe Seite umgestalten, dürfen eine gemeinsame Beta bilden. Ein Ticket
führt sie (Schalter, Bewerbung, Einstellungen), die anderen beschreiben nur, was sich mit
eingeschalteter Beta ändert, und verweisen auf das führende Ticket.

## Vorab festlegen

Ob eine Beta Standard wird, soll sich an Zahlen entscheiden, nicht am Eindruck. Deshalb legt das
führende Ticket **vor dem Start** fest:

- **Hypothese:** was die Beta besser machen soll, in einem Satz („Favoriten werden öfter angewendet,
  weil sie ohne Scrollen sichtbar sind“).
- **Ereignisse:** welche Nutzungen gezählt werden. Sie müssen auch ohne Beta vorkommen können, sonst
  fehlt der Vergleich — „Favorit angewendet“ statt „neue Karte angeklickt“.
- **Erfolgskriterium:** woran die Hypothese als bestätigt gilt, zum Beispiel mehr Anwendungen je
  Person und Woche mit Beta als ohne, und die Differenz größer als zwei Standardfehler; dazu eine
  Obergrenze für die Ausschaltquote und eine Untergrenze für die mittlere Bewertung.
- **Mindestlaufzeit**, in der Regel 4 Wochen, und **Mindestzahl Teilnehmender** je Gruppe — unter
  30 Personen ist ein Unterschied kaum von Zufall zu trennen.
- **Schwelle N** für die Rückfrage: nach wie vielen Nutzungen eine Person gefragt wird.

Was hier nicht steht, wird nicht gezählt und kann später nicht nachgeholt werden.

## Einführen

Eine Beta gehört dem Modul, dessen Seite sie trägt — wie seine UiState-Keys (ADR-0016) und seine
Treffer in der Befehlspalette. Das Modul `beta` schaltet, zählt und wertet aus, kennt aber keine
Beta selbst; die Module liefern ihm ihre Liste über `common.beta` und importieren es nie.

1. **Beitrag im eigenen Modul:** eine Konstante `BetaFeature` mit kurzem, stabilem Schlüssel und
   der Schwelle N, geliefert von einem `@Component`, das `BetaFeatureContributor` implementiert:

   ```java
   @Component
   public class DailyReportBetaFeatureContributor implements BetaFeatureContributor {
     public static final BetaFeature FAVORITES_FIRST = new BetaFeature("favoritesfirst", 20);

     @Override
     public List<BetaFeature> getBetaFeatures() {
       return List.of(FAVORITES_FIRST);
     }
   }
   ```

   Der Schlüssel wird je Person gespeichert; ein Schlüssel, den es nicht mehr gibt, fällt beim Lesen
   still weg, eine Migration braucht es dafür nie. Liefern zwei Module denselben Schlüssel, startet
   die Anwendung nicht.
2. **Texte** `main.settings.beta.<key>.label` und `.help` (`labelKey()`, `helpKey()`) in allen
   Sprachdateien. Die Einstellungen erzeugen den Schalter daraus selbst; der Bereich
   „Beta-Funktionen“ erscheint nur, solange ein Modul eine Beta liefert. `BetaFeaturesTest` prüft
   für jeden Beitrag, dass beide Texte da sind.
3. **Abfragen:** Templates lesen `${@betaViewHelper.isEnabled('favoritesfirst')}`, nie ein
   Model-Attribut — die Seiten werden aus vielen HTMX-Endpunkten gerendert, und ein Model-Attribut
   fehlte im nächsten neuen. Im Java-Code fragt das Modul über das Interface `Betas` aus
   `common.beta`: `betas.isEnabled(FAVORITES_FIRST)`.
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
  - „Aktivieren“ schickt `POST /beta/{key}/enable`. Die Antwort trägt `HX-Refresh`; die
    Seite lädt mit eingeschalteter Beta neu, die Person bleibt, wo sie war.
  - „Später“ und das Schließen merken sich das im `localStorage` (`salat-beta-<key>-hint`), nicht in
    den Einstellungen: Der Hinweis gilt nur für die Dauer der Beta und soll sie nicht in der
    Datenbank überdauern. Er erscheint deshalb höchstens einmal je Gerät.
  - Der Hinweis nennt keinen Rückkanal — wer die Funktion noch nicht kennt, hat nichts zu melden.
- **Einstiegslink** am Bedienelement selbst, klein und rechtsbündig im Kartenkopf
  (`ti ti-bolt`, „… testen“), der zum Abschnitt `/settings#beta` führt. Er bleibt, auch wenn der
  Hinweis weggeklickt ist, und verschwindet, sobald die Beta an ist.
- Texte unter `main.beta.<key>.hint.*` und `main.beta.<key>.link.*`.

## Zählen

Gezählt wird **bei allen**: mit eingeschalteter Beta als Variante `BETA`, sonst als `CLASSIC`, die
Vergleichsgruppe. So vergleicht die Auswertung beide Gruppen in derselben Woche statt vorher und
nachher, und Monatsende, Urlaubszeit oder ein anderes Release treffen beide gleich.

**Die Ereignisse gehören dem Modul, das zählt**, nicht dem Modul `beta`. Es hält ihre Schlüssel
als Konstanten bei sich (`DailyBetaEvents.FAVORITE_APPLIED = "favorite-applied"`), das führende
Ticket nennt sie. `beta` macht keine Annahme über sie: Es zählt jeden formal gültigen Schlüssel
(Kleinbuchstaben, Ziffern, Punkt und Bindestrich, höchstens 64 Zeichen) für eine Beta, die es gibt,
und die Auswertung zeigt, was gezählt wurde.

- **Im Java-Code** im Controller, nachdem die Aktion gelungen ist:
  `betas.count(FAVORITES_FIRST, DailyBetaEvents.FAVORITE_APPLIED)`. Nicht in einem Service
  innerhalb seiner Transaktion — das Lesen des Schalters würde sich ihr anschließen.
- **Im Browser**, für Nutzungen, die der Server sonst nicht sieht: das Attribut
  `data-beta-usage="favoritesfirst:favorite-dialog-opened"` am Element. Ein Klick darauf zählt.
  Was aus dem Browser kommt, nimmt `beta` so, wie es ankommt; eine Person kann ihre eigenen
  Zählungen also verfälschen, die anderer nicht.
- Gezählt wird nur für die angemeldete Person und **nicht während
  einer Vertretung** (Impersonation).
- Das Zählen lässt keine Anfrage scheitern; geht etwas schief, fehlt eine Zählung, sonst nichts.

## Rückfragen

Die Zahlen sagen, *ob* eine Beta genutzt wird, die Rückfragen sagen, *warum*.

- **Nach N Nutzungen:** Die Seite der Beta bindet neben ihrem Hinweis das Fragment
  `~{beta/feedback :: usePrompt('<key>')}` ein. Es erscheint, sobald die Person die Beta N-mal mit
  eingeschalteter Beta genutzt hat und sie seit mindestens 7 Tagen an ist, und fragt nach einer
  Bewertung 1–5 und einem optionalen Freitext. „Später“ fragt nach 7 Tagen erneut, „Nicht mehr
  fragen“ nie wieder.
- **Beim Ausschalten:** Die Einstellungsseite fragt einmal, was nicht gepasst hat. Ein Klick
  überspringt.
- **Anonym:** Eine Antwort wird ohne Person und nur mit der Kalenderwoche gespeichert; die Frage
  sagt das.
- **Slack bleibt:** Ist die Beta an, trägt die Funktion die Marke `badge bg-azure-lt` „Beta“
  (`main.beta.badge`). Ihr Tooltip nennt den Rückkanal (`main.beta.feedback.hint`, Slack-Channel
  #salat); derselbe Hinweis steht im Abschnitt „Beta-Funktionen“ der Einstellungen. Wer etwas
  Konkretes melden will, braucht keine Rückfrage abzuwarten.

## Auswerten

Die Seite **System → Beta-Auswertung** (nur Manager) zeigt je Beta:

- **Teilnahme:** je eingeschaltet, jetzt eingeschaltet, wieder ausgeschaltet, Ausschaltquote, Median
  der Tage bis zum Ausschalten.
- **Nutzung je Ereignis**, die letzten 8 Wochen, mit und ohne Beta: Nutzungen je Person als
  Mittelwert ± Standardfehler, in Klammern die Zahl der Personen.
- **Rückmeldungen:** Zahl der Antworten, Verteilung und Mittelwert der Bewertung, Freitexte.

Was sie nicht zeigt: Personen, und Werte, hinter denen weniger als 3 Personen stehen („–“).
Freitexte erscheinen erst ab 3 Antworten.

Gelesen wird gegen das Erfolgskriterium aus dem Ticket, nicht gegen das Gefühl:

- Ein Unterschied zählt erst, wenn er größer ist als etwa zwei Standardfehler beider Gruppen
  zusammen und die Mindestzahl Teilnehmender erreicht ist.
- Die Mittelwerte stehen über den Personen, die das Ereignis in der Woche genutzt haben. Wer eine
  Funktion gar nicht mehr nutzt, senkt den Mittelwert nicht, sondern die Zahl in Klammern — beides
  lesen.
- Wer die Beta einschaltet, ist oft ohnehin aktiver. Ein Vorsprung von Beginn an, auch in Wochen
  vor dem Einschalten, ist keine Wirkung der Beta.

## Testen

- E2E-Tests prüfen beide Varianten und heißen `…BetaE2ETest`, solange die Beta läuft.
- `BetaFeaturesTest` deckt das Lesen und Schreiben der Schlüssel ab und findet jeden
  `BetaFeatureContributor` auf dem Klassenpfad, um seine Texte zu prüfen; ein Contributor braucht
  dafür einen öffentlichen Konstruktor ohne Argumente.
- Logik, die von der Beta abhängt, wird als Unit-Test am Service bzw. ViewHelper für beide Werte
  des Schalters geprüft.
- Die Services des Moduls `beta` holen die Betas aus `BetaFeatureRegistry`; Unit-Tests setzen dort
  eine Test-Beta ein (`BetaTestData`).
- Im eigenen Modul wird `Betas` gemockt: geschaltet und gezählt wird dort nichts selbst.
- Eine neue Beta prüft im Unit-Test ihres Controllers, dass jede gezählte Aktion `count` mit dem
  richtigen Ereignis aufruft.

## Laufzeit und Ende

Eine Beta hat kein festes Ende. Der Schalter bleibt, bis entschieden ist, ob die Funktion Standard
wird oder zurückgezogen — frühestens nach der Mindestlaufzeit. Das Ende ist ein eigenes Ticket (wie
#1248). Es hält zuerst die Auswertung gesammelt im Ticket fest (Zahlen der Seite, Erfolgskriterium
erfüllt oder nicht, Kernaussagen der Freitexte ohne Zitate, die eine Person erkennen lassen) und
entfernt dann in einem Zug:

- die Konstante und ihren Eintrag im `BetaFeatureContributor` des Moduls, den Contributor selbst,
  wenn er leer wird — der Compiler zeigt danach jede übrige Stelle im Java-Code; im Template sucht
  man den Schlüssel;
- den verworfenen Zweig in Templates, JavaScript und Java;
- Hinweis, Einstiegslink und Beta-Marke samt ihrer Message-Keys in allen Sprachdateien;
- Verweise in der Tastenkürzel-Übersicht und im UI-Style-Guide;
- das Wort „Beta“ im Namen der E2E-Tests, die dann nur noch die verbliebene Variante prüfen;
- `count(...)`-Aufrufe und `data-beta-usage`-Attribute der Beta;
- ihre Zeilen in `beta_usage`, `beta_participation` und `beta_feedback`, per Changeset über den
  Schlüssel. Bis dahin zeigt die Auswertung die beendete Beta unter ihrem Schlüssel weiter.

Einstellungen, die nur in der Beta galten, werden beim Standardwerden für alle sichtbar oder
entfallen; das Ende-Ticket sagt, welches von beiden. Das Modul `beta` bleibt unverändert: Die
nächste Beta kostet ihr Modul eine Konstante, einen Eintrag im Contributor und zwei Texte.

