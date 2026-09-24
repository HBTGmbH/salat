# ADR-0028 Ein ETL-Lauf zur Zeit: die RUNNING-Zeile ist die Sperre

Date: 2026-09-24
Status: Accepted

## Context and Problem Statement

Mit #1071 lässt sich ein ETL-Lauf aus der Oberfläche anstoßen. Damit gibt es drei Wege in einen
Lauf statt bisher zwei: die Liste unter „System → ETL-Läufe", die REST-Schnittstelle
(`/api/etl/execute-all`, `/api/etl/{etl-name}/execute`) und den nächtlichen Lauf um 02:00.

**Zwei gleichzeitige Läufe vertragen sich nicht.** Die Definitionen schreiben in dieselben
Zieltabellen, und `init`/`cleanup` einer Definition legen Objekte an und wieder weg. MySQL committet
DDL implizit — ein zweiter Lauf zieht dem ersten die Tabelle unter den Händen weg, mitten in dessen
`execute`. Das Ergebnis wäre kein Fehler, den man zuordnen kann, sondern eine abgeleitete Tabelle mit
Zahlen, die niemand nachrechnen kann. Solange ein Lauf läuft, darf also kein zweiter starten, und
zwar unabhängig davon, auf welchem der drei Wege er angestoßen wird.

**Der Lauf läuft nebenläufig, nicht synchron.** Ein Lauf über drei Monate und alle Definitionen
setzt pro Definition und Referenzperiode mehrere Anweisungen ab und läuft in die Minuten. Der Knopf
„jetzt ausführen" an den JIRA-Replikationen wartet auf sein Ergebnis und sperrt sich währenddessen;
für den ETL wäre das der falsche Schnitt — die Anfrage liefe in den Timeout des Proxys, und ein
Neuladen der Seite während des Wartens ließe offen, ob der Lauf noch läuft. Seit #573 ist das
Warten auch nicht mehr nötig: die Zeile in `etl_run_history` entsteht **beim Start**, nicht am Ende.
Ein nebenläufig gestarteter Lauf steht damit sofort in derselben Liste, auf die umgeleitet wird —
mit Status „Läuft" und ohne Endzeitpunkt.

**Der Lauf bekommt dafür einen eigenen Executor**, nicht den gemeinsamen Async-Pool. Der hat genau
einen Thread (`SalatApplication#getAsyncExecutor`, `corePoolSize = maxPoolSize = 1`) und wird von
`StatisticService` für die Fortschreibung der Ist-Aufwände mitbenutzt. Ein minutenlanger ETL-Lauf
legte diese Fortschreibung so lange still, und zwar unsichtbar: die Aufgaben gehen bei einer
Warteschlange von 10.000 nicht verloren, sie kommen nur später. Ein eigener Executor mit einem
Thread und **Warteschlange der Länge null** trennt die beiden Lasten und weist eine zweite
Einreichung ab, statt sie anzustellen.

Nebenläufig heißt außerdem: keine HTTP-Anfrage und damit kein `@RequestScope`. Der Hintergrundthread
braucht denselben Vorlauf wie ein geplanter Job — eigene `SchedulerRequestAttributes`,
`AuthorizedUser.initForJob()`, am Ende abräumen (→ ADR-0006).

Drei Fragen waren zu entscheiden, und alle drei hängen daran, dass der Lauf jetzt nebenläufig läuft:
**wo die Sperre sitzt**, **wie ein hängengebliebener Lauf wieder aufgelöst wird** und **wie die
Berechtigung greift, wenn eine einzelne Definition ihre Abhängigkeiten mitzieht**.

## Considered Options

Zur Sperre:

* **A — Die `RUNNING`-Zeile selbst ist die Sperre.** Prüfen und Anlegen in einem
  `synchronized`-Abschnitt, durch den jeder Weg in einen Lauf geht.
* **B — Eine Sperre in der Datenbank.** In MySQL 8 eine generierte Spalte auf `etl_run_history`, die
  genau für `status = 'RUNNING'` einen konstanten Wert trägt, mit eindeutigem Index darauf; der
  zweite Einfügeversuch scheitert dann an der Datenbank.
* **C — Der einthreadige Executor ist die Sperre.** Keine eigene Prüfung; wer keinen Thread bekommt,
  bekommt keinen Lauf.

Zum hängengebliebenen Lauf:

* **D — Ein Knopf „als beendet markieren" an der Zeile**, der sie auf `FAILED` setzt.
* **E — Eine Altersschwelle**: ein `RUNNING`-Eintrag zählt ab einer bestimmten Dauer nicht mehr als
  laufend.
* **F — Beim Hochfahren der Anwendung jeden `RUNNING`-Eintrag auf `FAILED` setzen.**

Zur Berechtigung bei mitgezogenen Abhängigkeiten:

* **G — Geprüft wird die gewählte Definition**; die Abhängigkeiten laufen ohne eigene Prüfung.
* **H — Geprüft wird jede Definition der transitiven Hülle**, so wie `executeETL` es bisher pro
  Definition tat.

## Decision Outcome

Chosen: **A + D + G.**

### A — die `RUNNING`-Zeile ist die Sperre

`ETLService.startRun(dateRange, trigger)` prüft den Zeitraum, weist einen zweiten Lauf ab und legt
die `RUNNING`-Zeile an — alles in einem `synchronized`-Abschnitt, denn Prüfen und Anlegen bilden
zusammen einen Abschnitt; zwei gleichzeitige Anfragen kämen sonst beide an der Prüfung vorbei. Es
gibt kein Lock, kein Sperrobjekt und keine zweite Tabelle: **der Zustand, der ohnehin geführt wird,
ist die Sperre.** Ein Lauf ohne Zeile in der Historie wäre ein Lauf, den niemand sieht; eine Sperre
ohne Lauf wäre eine Sperre, die niemand erklären kann.

Entscheidend ist, dass jeder Weg durch diese eine Tür geht — Oberfläche, REST-Schnittstelle,
nächtlicher Lauf. Die Methode läuft im Thread des Aufrufers, nicht im Hintergrund: nur so sieht ein
zweiter Anstoß die Zeile, und nur so steht der Lauf schon in der Liste, auf die umgeleitet wird.

Ein abgewiesener Anstoß aus der Oberfläche oder über die REST-Schnittstelle ist eine Fehlermeldung an
die Person, die ihn ausgelöst hat (`ETL-0002`, mit dem Startzeitpunkt des laufenden Laufs und dem
Hinweis auf den Ausweg). Ein abgewiesener **nächtlicher** Lauf hat niemanden, dem er es sagen könnte,
und hinterlässt deshalb eine Zeile mit dem neuen Status `SKIPPED`.

**B — Datenbanksperre** ist verworfen, aber die Voraussetzung dahinter gehört benannt: die Anwendung
läuft in **einer** Instanz, und die Sperre wirkt innerhalb einer JVM. Die generierte Spalte mit
eindeutigem Index wäre der Weg, das auf mehrere Instanzen auszudehnen — im Projekt gibt es dafür
bisher kein Vorbild, und dieselbe Voraussetzung tragen die `@Scheduled`-Jobs der Anwendung ohnehin
schon: bei zwei Instanzen liefe auch der nächtliche Lauf zweimal. Eine Sperre in der Datenbank hier
löste ein Problem, das die Anwendung an anderer Stelle unverändert hätte.

**C — der Executor als alleinige Sperre** ist verworfen, weil er nicht alle Wege erfasst: der
nächtliche Lauf läuft auf dem Scheduler-Thread und käme an ihm vorbei. Und selbst dort, wo er greift,
wäre die Bedeutung falsch — ein Executor mit Warteschlange macht aus dem Abweisen ein Anstellen, und
angestellt heißt: der zweite Lauf startet doch, nur später und ohne dass jemand ihn noch will. Der
Executor bleibt trotzdem einthreadig und mit Warteschlange der Länge null, aber als **Rückfalllinie**
hinter der Sperre, nicht als Sperre: was an ihr vorbeikäme, wird abgewiesen und nicht heimlich für
später vorgemerkt. Eine Ablehnung dort setzt die eben eröffnete Zeile auf `FAILED` — ein Lauf, der nie
begann, darf nicht wie einer aussehen, der noch läuft — und wird als `ETL-0007` gemeldet. Die
technische `TaskRejectedException` weitergereicht wäre eine Fehlerseite: sie ist keine
`ErrorCodeException`, und niemand fängt sie.

### D — hängengebliebene Läufe werden von Hand aufgelöst

Stirbt der Prozess mitten im Lauf, bleibt dessen Zeile für immer auf `RUNNING` stehen. Das ist der
Sinn der Spalte (#573) — und es blockiert jeden weiteren Start. Aufgelöst wird das über den Knopf
**„als beendet markieren"** an der Zeile, der sie auf `FAILED` setzt und die Meldung um den Vermerk
ergänzt. Der Knopf fragt vorher nach, über den gemeinsamen Bestätigungsdialog (→ ADR-0027).

**Der Knopf hält den Lauf nicht an. Er markiert nur die Zeile.** Die Anwendung kann einen Thread, der
in einer SQL-Anweisung steht, nicht abbrechen; läuft der Lauf in Wahrheit noch, ist die Zeile
anschließend eine Lüge und ein zweiter Lauf kann starten. Deshalb sagt der Bestätigungstext genau
das, und deshalb ist es ein Knopf mit Rückfrage und kein Automatismus: die Person, die ihn drückt,
trifft eine Aussage über die Welt, die die Anwendung nicht treffen kann.

**E — Altersschwelle** ist verworfen, weil jede Zahl geraten wäre. Ein Lauf über drei Monate und alle
Definitionen dauert Minuten, ein erster Lauf nach einer geänderten Definition womöglich länger — und
die Schwelle griffe von selbst genau dann, wenn ein Lauf ungewöhnlich lange braucht, also im
ungünstigsten Moment. Sie ersetzte ein sichtbares Hindernis durch eine stille Fehlentscheidung.

**F — Aufräumen beim Hochfahren** ist verworfen, weil es die Voraussetzung aus A stillschweigend
verschärfte: mit zwei Instanzen setzte der Start der zweiten den laufenden Lauf der ersten mit
zurück. Und der Fall, für den es gedacht ist — die Anwendung stirbt mitsamt dem Lauf —, ist nicht der
einzige: ein abgebrochener Hintergrundthread bei laufender Anwendung bliebe weiterhin stehen, der
Knopf wäre also ohnehin nötig.

**Der neue Status `SKIPPED`** gehört hierher, weil er dieselbe Unterscheidung führt: `FAILED` ist
etwas, das lief und schiefging; `SKIPPED` ist etwas, das gar nicht erst begann. Beide als `FAILED` zu
führen machte die Liste unbrauchbar für die Frage, die man an sie stellt — „ist heute Nacht etwas
schiefgegangen?" —, denn ein übersprungener nächtlicher Lauf ist kein Schaden, sondern die korrekte
Folge eines von Hand angestoßenen Laufs. Umgekehrt wäre „gar keine Zeile" auch keine Antwort: die
Liste zeigte eine Lücke, die von einer abgeschalteten Anwendung nicht zu unterscheiden ist.

### G — geprüft wird die gewählte Definition, nicht die Hülle

Wird eine einzelne Definition gewählt, laufen ihre Abhängigkeiten vorher mit — sonst wertet sie
Zwischenergebnisse von gestern aus, und das Ergebnis sieht richtig aus, ohne es zu sein. Die
Berechtigung wird dabei für den **Einstieg** geprüft, nicht für die transitive Hülle.

**H** ist verworfen, weil es die Regel selbst entwertete: Wer eine Regel für `X` hat, aber keine für
dessen Abhängigkeit `Y`, bekäme einen Lauf, der an der ersten Abhängigkeit abbricht. Eine Regel für
`X` ohne das, was `X` braucht, ist keine Berechtigung, sondern eine Einladung zu einem Fehler. Die
Wahl steht damit zwischen „`X` ausführbar, samt dem, was `X` braucht" und „`X` gar nicht ausführbar";
ein Mittelweg existiert nicht.

Die Berechtigung fällt dabei **vor** der Eingabeprüfung und **vor** der Zeile: wer gar nicht anstoßen
darf, soll das erfahren und nicht erst eine Rückmeldung zu seiner Eingabe bekommen — und er darf
keine `RUNNING`-Zeile hinterlassen, denn die ist die Sperre. Andernfalls könnte über die
REST-Schnittstelle, die nur nach Authentifizierung fragt, jede beliebige Anmeldung reihenweise
Sperrzeilen erzeugen und damit berechtigte Läufe und den nächtlichen Lauf abweisen lassen. Die
Prüfung je einzelner Definition bleibt daneben im Lauf: ein Recht, das für eine bestimmte Definition
fehlt, gehört in die Zeile des Laufs, nicht vor ihn.

Geprüft wird die Berechtigung dort, wo es die anfragende Person noch gibt: in `resolveManualRun`, im
Thread der Anfrage. Im Hintergrundthread arbeitet der Lauf als `SYSTEM` (`AuthorizedUser.initForJob`)
und käme durch jede Prüfung — eine Prüfung dort sähe aus wie eine und wäre keine. `executeETL` prüft
deshalb gar nicht mehr; die Begründung steht als Kommentar an der Stelle, an der sie greift.

Für den Sammeleintrag „alle Definitionen" heißt dasselbe: „alle, die ich ausführen darf" — die
Auswahlliste zeigt nichts anderes, und `getExecutableDefinitions` liefert genau diese Menge.

### Consequences

* Good: Die Sperre hat keinen eigenen Zustand. Was sie prüft, steht in der Liste, die ohnehin
  angezeigt wird — wer wissen will, warum ein Start abgewiesen wurde, sieht es dort, ohne ins Log zu
  schauen.
* Good: Ein abgewiesener nächtlicher Lauf ist als `SKIPPED` sichtbar, statt nur im Log zu stehen.
  Die Liste unterscheidet „lief und ging schief", „begann gar nicht erst" und „Anwendung war aus"
  (die Lücke zwischen zwei Zeilen).
* Good: Die Oberfläche kann `isRunInProgress()` für den Hinweis und den Zustand des Knopfes nutzen,
  ohne dass diese Frage etwas entscheidet — die Entscheidung fällt allein in `startRun`. Zwischen
  Anzeige und Klick darf sich der Zustand ändern, ohne dass daraus ein zweiter Lauf wird.
* Bad: **Die Sperre wirkt nur innerhalb einer JVM.** Bei mehreren Instanzen ist sie wirkungslos —
  genau wie die übrigen `@Scheduled`-Jobs der Anwendung. Eine Instanz ist damit eine benannte
  Betriebsvoraussetzung, keine Lücke. Wird sie aufgegeben, ist Option B der nächste Schritt, und
  zwar für die Jobs gemeinsam, nicht für den ETL allein.
* Bad: Der Knopf „als beendet markieren" kann falsch gedrückt werden. Läuft der Lauf noch, erlaubt
  die Markierung einen zweiten — also genau das, was A verhindern soll. Gegenmaßnahmen: die
  Rückfrage sagt ausdrücklich, dass der Lauf dadurch nicht angehalten wird; und der Executor mit
  Warteschlange null weist den zweiten Lauf ab, solange der erste seinen Thread noch belegt.
* Bad: Eine Regel für eine Definition macht damit **mittelbar auch deren Abhängigkeiten
  ausführbar**. Wer `X` ausführen darf, stößt über `X` auch `Y` und `Z` an, ohne eine Regel dafür zu
  haben. Das ist bewusst so: die Alternative ist keine feinere Berechtigung, sondern eine, die nicht
  funktioniert. Wer das nicht will, gibt die Regel für `X` nicht.
* Neutral: Der ETL bekommt einen eigenen Executor und damit einen zweiten Hintergrund-Thread-Pool
  neben dem gemeinsamen. Das ist der Preis dafür, dass die Fortschreibung der Ist-Aufwände nicht
  hinter einem minutenlangen Lauf wartet.
* Neutral: Die REST-Schnittstelle ist mit unter die Sperre gefallen, ohne dass ihre Signatur sich
  geändert hat; ein abgewiesener Aufruf antwortet jetzt mit 400 statt zu laufen. Ob
  `GET /api/etl/{etl-name}/execute` auch die Abhängigkeiten mitziehen soll, ist in #1071
  ausdrücklich **nicht** entschieden — sie hat Aufrufer außerhalb der Anwendung.
* Neutral: Ein laufender Lauf lässt sich nicht abbrechen. Das bleibt ein eigenes Thema; der Knopf
  „als beendet markieren" ist ausdrücklich nicht die kleine Lösung dafür.

## Beziehungen zu anderen ADRs

| ADR | Beziehung |
|---|---|
| [ADR-0006](0006-rollenbasierte-autorisierung.md) | Grundlage für beides: der Job-Modus von `AuthorizedUser`, den der Hintergrundthread braucht, und die Laufzeitprüfung im Service statt einer Annotation, weil die Berechtigung ein Entweder-oder ist (Geschäftsführung, Administration oder eine Regel `ETL`/`EXECUTE`). |
| [ADR-0027](0027-gemeinsamer-bestaetigungsdialog.md) | Gibt die Abstufung vor, nach der hier zweierlei entschieden ist: das Anstoßen eines Laufs fragt **nicht** nach — es baut abgeleitete Tabellen neu auf, die ohnehin jede Nacht entstehen. Das Markieren als beendet fragt nach, über den gemeinsamen Dialog. |
| [ADR-0001](0001-modular-monolith.md) | Die Betriebsvoraussetzung „eine Instanz" hängt am Monolithen. Würde der ETL herausgelöst, wäre die Sperre neu zu stellen — dann als Option B. |
