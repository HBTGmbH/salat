# ADR-0033 Eine gleichzeitige Änderung wird am Service zum fachlichen Befund

Date: 2026-10-01
Status: Accepted

> **Nachtrag 2026-10-03 (#1208):** Fachliche Schlüssel haben jetzt einen Unique-Schlüssel in der
> Datenbank. Dessen Verletzung kommt wie der Versionskonflikt erst beim Flush, deshalb übersetzt
> derselbe Aspekt auch sie: Kürzel des Mitarbeiters `EM-0005`, Auftragsnummer `CO-0006`,
> Zeitscheibe einer Kostenkategorie `BU-0007` (Überschneidung), jeder andere Unique-Schlüssel
> `XX-0004`. Andere Integritätsverletzungen bleiben unverändert. Wer einen Zusammenstoß auflösen
> kann, fängt ihn weiter selbst (#1111).

## Context and Problem Statement

Jede Entität trägt eine Versionsnummer (`@Version updatecounter` in `AuditedEntity`). Lesen zwei
Anfragen denselben Stand und schreiben beide, scheitert die zweite: Hibernate findet beim `UPDATE …
where updatecounter = ?` keine Zeile mehr, und Spring meldet eine
`ObjectOptimisticLockingFailureException`. Gespeichert hat die erste.

Der häufigste Auslöser ist ein doppelter Klick. Die Freigabe braucht ein, zwei Sekunden, und in
dieser Zeit schickt ein zweiter Klick das Formular noch einmal ab (#1237). Die Ausnahme fing
niemand, die Person landete auf der Fehlerseite (500), obwohl ihre Freigabe gespeichert war.

Zu entscheiden war, wo die Ausnahme zu einer Meldung wird. Zwei Bedingungen grenzen das ein:

1. **Der Controller soll die Persistenz nicht kennen.** Er fängt `BusinessRuleException` und
   `ErrorCodeException`, keine Ausnahmen von Spring Data oder Hibernate.
2. **Die Ausnahme entsteht meist erst beim Commit.** Ein Service ändert eine geladene Entität,
   ohne `save()` (`EmployeecontractService.updateReportReleaseData` setzt nur zwei Felder). Hibernate
   schreibt beim Flush, und der kommt erst, wenn der Transaktions-Proxy nach dem Methodenrumpf
   festschreibt. Ein `catch` im Service sieht die Ausnahme dann nicht. Ein DAO auch nicht, es
   schreibt hier gar nicht.

## Considered Options

* **A — Im Controller fangen:** `catch (OptimisticLockingFailureException)` an jeder Stelle, die
  es braucht.
* **B — Im Service flushen und fangen:** an der Stelle, an der alles geschrieben ist, ausdrücklich
  flushen, den Konflikt fangen und als `BusinessRuleException` werfen.
* **C — Ein Aspekt um alle Services:** außerhalb der Transaktion übersetzt er
  `OptimisticLockingFailureException` in eine `BusinessRuleException` `XX-0003`.

## Decision Outcome

Chosen: **C**, umgesetzt in `de.hbt.salat.common.service.ConcurrentModificationAspect`.

A verletzt die erste Bedingung, und zwar an jeder Stelle neu. B erfüllt beide Bedingungen, aber nur
dort, wo jemand an den Flush denkt. Es verschiebt außerdem den Zeitpunkt des Schreibens in einen
Service, der fachlich nichts damit zu tun hat. C erfüllt beide Bedingungen an einer Stelle und für
jeden Service. Der Aspekt liegt außen um den `TransactionInterceptor` und sieht deshalb auch den
Fehler aus dem Commit. Für den Aufrufer ist der Konflikt danach ein fachlicher Befund wie jeder
andere.

Die Reihenfolge ist der Kern der Entscheidung:

* Die Transaktion hat die Standardreihenfolge `LOWEST_PRECEDENCE`, der Aspekt liegt mit `0` davor
  und damit außen.
* `HIGHEST_PRECEDENCE` geht nicht. Dort liefe der Aspekt noch vor dem `ExposeInvocationInterceptor`
  (`HIGHEST_PRECEDENCE + 1`), und ein AspectJ-Advice lässt sich dann nicht ausführen.

`ConcurrentModificationIntegrationTest` stellt den Fall im echten Anwendungskontext nach: Hibernate,
H2, Konflikt beim Commit.

Der Aspekt greift auf jede Klasse mit `@Service` unter `de.hbt.salat`. Ruft ein Service einen
anderen, übersetzt der innere, falls der Flush schon in ihm geschieht. Die `BusinessRuleException`
rollt die gemeinsame Transaktion dann ebenso zurück.

Im Browser verhindert `data-submit-once` (→ `docs/ui-style-guide.md` §5.5) den doppelten Klick
gleich ganz. Der Aspekt bleibt für alles, was der Browser nicht verhindert: Neuladen, zwei
Registerkarten, zwei Personen.

### Consequences

* Good: Ein Konflikt endet nirgends mehr auf der Fehlerseite, wo der Controller
  `BusinessRuleException` fängt. Der Controller weiß von der Persistenz nichts.
* Good: Neue Services sind ohne weiteres Zutun abgedeckt.
* Bad: `XX-0003` ist eine allgemeine Meldung. Sie sagt, dass gleichzeitig geändert wurde, nicht
  was. Wo das Was zählt, nennt die Seite den aktuellen Stand (die Prüfseiten zeigen nach der
  Rückkehr, was inzwischen freigegeben ist).
* Bad: Was innerhalb der Transaktion schon nach außen gegangen ist, nimmt das Zurückrollen nicht
  zurück. Die Freigabe verschickt ihre Mail vor dem Commit, bei einem Konflikt also doppelt. So war
  es schon vorher.
* Neutral: Fängt ein Controller nur `BusinessRuleException | InvalidDataException` nicht, endet
  der Konflikt weiter auf der Fehlerseite, jetzt aber mit einer `ErrorCodeException` statt einer
  Persistenz-Ausnahme.
* Neutral: Einen Zusammenstoß, der sich auflösen lässt, löst weiter der Service auf, in dem er
  entsteht. #1111 fängt so den doppelt angelegten Arbeitstag (Unique Key, nicht Versionsnummer) und
  schreibt ihn fort. Der Aspekt greift nur, wo niemand vorher fängt.
