# ADR-0029 „Inaktiv" ist zeitlich definiert und zählt nur das Ende

Date: 2026-09-25
Status: Accepted

## Context and Problem Statement

Zehn Entitäten tragen einen Gültigkeitszeitraum (`fromDate`/`untilDate` bzw.
`validFrom`/`validUntil`). Listen und Auswahllisten blenden die „nicht mehr aktiven" darunter aus,
umschaltbar über einen Filterschalter. Was dabei „aktiv" heißt, war nie entschieden.

ADR-0012 beschreibt Gültigkeitsspannen, aber **widerspricht sich** an genau diesem Punkt:

> Ein Objekt ist *aktuell gültig*, wenn das heutige Datum innerhalb dieser Spanne liegt. Außerhalb
> der Spanne ist das Objekt abgelaufen (oder noch nicht aktiv).

gegen die Prüfbedingung zwei Zeilen darunter:

> `untilDate` ist `null` **oder** `untilDate >= heute`

Der erste Satz zählt den **Beginn** mit, die Prüfbedingung nicht. Beides ist umgesetzt worden, und
die Erhebung zu #950 hat gezeigt, wie weit das auseinandergelaufen ist:

- Vier Namen für denselben Schalter — `show` (Request-Parameter), `showInvalid` (DAOs und Services),
  `showInactive` (Budget-Modul) und `showOnlyValid` mit umgekehrter Bedeutung (Rechnungsmaske).
- Vier wortgleiche `showOnlyValid()`-Prädikate in vier DAOs, die sich nur im Spaltennamen
  unterschieden, plus sechs `getCurrentlyValid()` auf den Entitäten — zehn Kopien derselben Regel.
- An zwei Stellen entscheidet eine **Stichtagsfrage** über Sichtbarkeit: `Suborder.isValidAt(heute)`
  in der Rechnungsmaske (#1095) und `validFrom <= heute` im Sichtbereich einer Teamleitung (#1096).
  Beide prüfen den Beginn mit und lassen damit einen im Voraus angelegten Datensatz verschwinden.
- Ein offenes Ende steht mal als `null` und mal als Sentinel `2999-12-31` in der Datenbank
  (`order_pricing`, `order_budget`, `employee_cost`, `employee_cost_employee`); ob beide gleich
  behandelt werden, war nirgends festgehalten.

Bei #949 (Filter für die Kundenstundensätze) musste die Frage zum ersten Mal ausdrücklich
beantwortet werden. Diese Antwort ist nicht auf Stundensätze beschränkt — sie gilt für jede Entität
mit Gültigkeitszeitraum, und bevor sie an einer weiteren Stelle einzeln nachgebildet wird, gehört
sie festgehalten.

Zu klären sind zwei Dinge: **was** inaktiv heißt, und **wo** die Antwort steht.

## Considered Options

Zur Bedeutung:

* **Option A** — „aktiv = heute liegt im Zeitraum". Beginn und Ende zählen. Das ist die Lesart des
  ersten Satzes in ADR-0012 und das, was `isValidAt` tut.
* **Option B** — „inaktiv = das Ende liegt vor heute". Nur das Ende zählt; ein Beginn in der Zukunft
  macht einen Datensatz nicht inaktiv, sondern noch nicht aktiv.
* **Option C** — Keine übergreifende Regel; jede Liste entscheidet für sich, was ihr Schalter meint.

Zum Ort:

* **Option D** — Die Prädikate bleiben, wo sie sind, und die Regel steht nur als Text in AGENTS.md.
* **Option E** — Die Regel steht einmal im Code, und alle Fundstellen rufen sie auf.

## Decision Outcome

Chosen: **Option B und Option E.**

### Inaktiv heißt: der Zeitraum liegt vollständig in der Vergangenheit

- **inaktiv** = das Ende liegt **vor** dem heutigen Tag.
- Ein Ende **am heutigen Tag** ist noch aktiv — der Vergleich ist einschließend (`>= heute`).
- Ein **offenes Ende** ist nie inaktiv, gleichgültig ob es als `null` oder als Sentinel `2999-12-31`
  abgelegt ist. Der Sentinel braucht keinen eigenen Fall: kein wirklicher Tag liegt danach, also
  kann er nie vor heute liegen.
- Ein **Beginn in der Zukunft** ist nicht inaktiv, sondern noch nicht aktiv. Der Beginn gehört
  deshalb nicht in das Prädikat.

Der letzte Punkt trägt die Entscheidung, und er ist der einzige, bei dem A und B auseinandergehen.
Der Grund ist nicht Symmetrie, sondern ein Fehler, den Option A erzeugt: **wer eine Änderung im
Voraus anlegt, sieht sie danach nicht mehr — und legt sie ein zweites Mal an.** Eine zum Monatsersten
vorbereitete Preiserhöhung, ein Unterauftrag für das kommende Quartal, der Vertrag einer neu
eingestellten Person: alle drei verschwinden unter Option A aus genau der Liste, in der man
nachsehen würde, ob es sie schon gibt. Der Schalter, der sie wieder sichtbar macht, heißt „Inaktive
anzeigen" und holt dann die gesamte Historie mit dazu.

Option A hat dafür kein Gegenargument von gleichem Gewicht. Ihr eigentliches Anliegen — „darf hier
und heute gebucht werden" — bleibt eine berechtigte Frage; sie ist nur eine **andere** (siehe
unten).

Option C wurde verworfen, weil der Zustand vor dieser Entscheidung genau das war. Zehn Kopien einer
Regel, die niemand aufgeschrieben hatte, driften auseinander, und zwei von ihnen waren zum Zeitpunkt
der Erhebung falsch.

### Drei Dinge, die ebenfalls „aktiv" heißen und etwas anderes meinen

Die Abgrenzung ist Teil der Entscheidung, denn jede der drei Verwechslungen ist in der Erhebung
tatsächlich vorgekommen:

1. **`hide`** — die manuelle Entscheidung, einen Datensatz aus Auswahllisten zu nehmen, unabhängig
   von jedem Datum (→ ADR-0012). Zwei Prädikate, zwei Schalter; sie gehören nie in eine Bedingung.
   `CustomerorderRepository.findAllValidAtAndNotHidden` verknüpft sie heute mit `or` und macht damit
   beide wirkungslos (#1094).
2. **Explizite Boolean-Flags** wie `OrderBudget.active`, `JiraReplicationConfig.enabled`,
   `ScheduledReportJob.enabled`. Sie meinen eine gesetzte Entscheidung, kein Datum. Wo beides auf
   derselben Entität existiert — `OrderBudget` hat `active` **und** `validFrom`/`validUntil` —, sind
   es zwei unabhängige Kriterien und sie dürfen nicht in einem Filter vermischt werden.
   `BudgetController.list` meint mit `showInactive` das Flag; das ist richtig so und sagt nichts
   über den Zeitraum.
3. **„Gilt am Tag X"** — die Stichtagsfrage, die den Beginn mitprüft (`Suborder.isValidAt(date)`,
   `Employeeorder.isValidAt(date)`). Für Geschäftsregeln ist sie richtig: an einem Tag, an dem ein
   Auftrag noch nicht begonnen hat, darf nicht auf ihn gebucht werden. Als Aktiv-Filter ist sie
   falsch. Ein `isValidAt` hinter einem Schalter namens `showInactive` ist ein Fehler.

### Die Regel steht einmal im Code: `org.tb.common.Validity`

```java
public static boolean isInactive(LocalDate untilDate)
public static <E> Specification<E> notInactive(SingularAttribute<? super E, LocalDate> untilDate)
```

Die vier `showOnlyValid()` waren strukturgleich und unterschieden sich nur im Spaltennamen
(`untilDate` gegen `validUntil`) und darin, ob sie „heute" selbst holen oder entgegennehmen. Eine
Signatur über die Metamodell-Konstante trägt alle vier, also gab es keinen Grund für die Kopien.

Beide Seiten derselben Frage — die Java-Seite für eine geladene Entität, die Abfrageseite als
`Specification` — liegen **in einer Klasse**. Das ist der eigentliche Gewinn: eine Liste und die
Zeile, die sie rendert, beantworten dieselbe Frage aus derselben Quelle und können nicht mehr
auseinanderlaufen. `ValiditySpecificationTest.selects_exactly_what_the_java_side_calls_active` hält
das fest.

Option D wurde verworfen, weil ein Text neben zehn Kopien die Kopien nicht davon abhält zu driften —
er dokumentiert die Drift nur. Die Erhebung zu #950 ist der Beleg: AGENTS.md beschrieb das Prädikat
bereits richtig, während zwei Fundstellen es falsch umsetzten.

### Der Schalter heißt überall `showInactive`

Über Request-Parameter, `UiStateKey`, Modellattribut, Template-Feld sowie Service- und
DAO-Parameter hinweg. Weder `show` noch `showInvalid` (klingt nach fehlerhaften Daten) noch
`showOnlyValid` (umgekehrte Bedeutung, zwingt jede Schicht beim Durchreichen zum Nachdenken).

Die Request-Parameter sind mit umbenannt worden, obwohl das Lesezeichen kostet. Ein
`fCustomerOrderShowInvalid` in der URL, der auf einen Service-Parameter `showInactive` trifft, wäre
genau die Spaltung, die diese Entscheidung beseitigt. Der Preis bleibt klein: ein Lesezeichen mit
dem alten Parameter verliert seinen Filter und fällt auf den Standard zurück, der gemerkte
`UiState`-Wert wird einmalig verworfen. Es bricht nichts.

### Consequences

* Good: „inaktiv" heißt in der ganzen Anwendung dasselbe, und es steht an einer Stelle — eine elfte
  Kopie des Prädikats ist ab jetzt ein Fehler, kein Versehen.
* Good: Im Voraus angelegte Datensätze bleiben sichtbar. Der Doppelanlage-Fehler kann nicht mehr aus
  der Filterung entstehen.
* Good: Die Abfrageseite und die Java-Seite sind aneinander festgenagelt, statt sich zufällig zu
  gleichen.
* Good: Der Sentinel `2999-12-31` braucht an keiner Fundstelle mehr eine eigene Behandlung.
* Bad: Listen zeigen bei ausgeschaltetem Schalter jetzt auch Datensätze, die noch nicht begonnen
  haben. Ohne optische Kennzeichnung sind sie von den laufenden nicht zu unterscheiden — das ist
  eine offene Frage, siehe unten.
* Bad: Die Umbenennung der Request-Parameter entwertet bestehende Lesezeichen mit gesetztem Filter
  einmalig.
* Neutral: Die Entscheidung erklärt bestehendes Verhalten an zwei Stellen zum Fehler (#1095, #1096).
  Beide sind bewusst nicht mit ihr zusammen behoben worden, weil sie sichtbares Verhalten ändern —
  eine davon ist eine Autorisierungsfrage.
* Neutral: Der Sentinel bleibt in der Datenbank. Ihn durch `null` abzulösen wäre eine
  Datenmigration; `Validity` macht sie nicht nötig, aber auch nicht unmöglich.

## Was offen bleibt

- **Eine optische Kennzeichnung** inaktiver und noch nicht aktiver Zeilen gibt es nicht. Solange sie
  fehlt, sagt eine Liste nicht, welche ihrer Zeilen erst in Zukunft greift.
- **`LocalDateRange` normalisiert den Sentinel über Referenzgleichheit** (`until ==
  FINIT_UNTIL_BOUNDARY`). Ein aus der Datenbank geladenes `2999-12-31` ist eine andere Instanz und
  wird deshalb nicht zu `null`. Auf `contains`, `overlaps`, `intersection` und `minus` wirkt sich
  das nicht aus, wohl aber auf `isInfiniteUntil()`, `contains(LocalDateRange)`, `equals` und
  `compareTo`. Kein Aufrufer verlässt sich heute darauf; es gehört zu einer Ablösung des Sentinels.

## Verwandte Entscheidungen

- ADR-0011: Stammdaten vs. Bewegungsdaten
- ADR-0012: Archivierung von Stammdaten — `hide` und Gültigkeitsspannen. Dessen Abschnitt
  „Gültigkeitsspannen: Semantik und Zweck" wird von diesem ADR präzisiert; die Entscheidung zur
  Dropdown-Filterregel Create vs. Edit bleibt unverändert gültig.
- ADR-0022: Präfix `f` für UiState-Parameter — gilt für die umbenannten Filterparameter
- Issue #949: die Stelle, an der die Frage zuerst beantwortet werden musste
- Issue #950: die Erhebung über alle Module, 298 Fundstellen mit je fünf Prüfantworten
