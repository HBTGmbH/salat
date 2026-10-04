# ADR-0035 Sonderaufträge per Konfiguration über das Kürzel, ihre Kürzel gesperrt

Date: 2026-10-04
Status: Accepted

## Context and Problem Statement

Einige Aufträge haben eine Rolle, an der Regeln der Anwendung hängen: der Urlaubsauftrag mit seinen
Jahres-Unteraufträgen, deren Mitarbeiteraufträge einen berechneten Urlaubsanspruch tragen, der
Sonderurlaub ohne Anspruch und die reguläre Fortbildung, die das Fortbildungskonto von der
projektbezogenen trennt. Bis #1341 erkannte die Anwendung sie an Kürzeln, die fest in
`GlobalConstants` standen, und las das Jahr eines Jahres-Unterauftrags aus seinem Kürzel.

Welche Aufträge diese Rollen haben, ist eine Entscheidung im Betrieb, kein Teil der Software. Seit
ADR-0034 sind Kürzel zudem ausdrücklich änderbar: Wer den Urlaubsauftrag umbenannte, ließ das
Urlaubskonto aller Personen lautlos verschwinden. Und ein Unterauftrag des Urlaubsauftrags, dessen
Kürzel kein Jahr ist, ließ jede Vertragsänderung der Personen darauf scheitern.

Zu entscheiden war, wie die Anwendung diese Aufträge erkennt.

## Considered Options

* **A — Kennzeichen am Auftrag:** eine Spalte an Auftrag bzw. Unterauftrag, gepflegt im Formular.
* **B — Konfiguration über die id:** `application.yaml` nennt die Aufträge über ihre id.
* **C — Konfiguration über das Kürzel, Kürzel gesperrt:** `application.yaml` nennt sie über ihr
  Kürzel; die Anwendung löst es beim Start auf und lässt diese Kürzel nicht mehr ändern.

## Decision Outcome

Chosen: **C**. Die Rolle eines Auftrags ist Konfiguration des Betriebs und gehört neben die übrigen
Einstellungen, nicht in Stammdaten, die jeder Manager im Formular ändern kann (gegen A). Das Kürzel
ist dort lesbar und in jeder Umgebung dasselbe, auch in einer, deren Daten nicht aus demselben
Bestand stammen (gegen B). Damit eine Umbenennung die Konfiguration nicht unterläuft, sind die
Kürzel der genannten Aufträge gesperrt.

```yaml
salat:
  vacation:
    customerorder-sign: URLAUB
    do-not-calculate-signs:
      - URLAUB/Sonderurlaub
  training:
    regular-suborder-signs:
      - i976/FORTBILDUNG
```

Umgesetzt in `SpecialOrders` (Modul `order`):

- **Auflösung beim Start.** Die Kürzel werden einmal in ids aufgelöst und protokolliert; danach
  fragt die Anwendung nur noch über die id. Ein Kürzel, das keinen Auftrag nennt, und ein Eintrag von
  `do-not-calculate-signs`, der nicht unter dem Urlaubsauftrag liegt, halten den Start an. Ohne
  Eintrag ist die Rolle aus.
- **Zwei Gruppen unter dem Urlaubsauftrag.** Die Unteraufträge in `do-not-calculate-signs` haben
  keinen berechneten Anspruch; alle übrigen sind Jahres-Unteraufträge. Das Jahr eines
  Jahres-Unterauftrags ist das Jahr seines Beginns, nicht sein Kürzel.
- **Sperre.** `CustomerorderService` und `SuborderService` lehnen ab, das Kürzel eines genannten
  Auftrags oder Unterauftrags zu ändern, ebenso das jedes Auftrags und Unterauftrags darüber — sonst
  änderte sich der vollständige Schlüssel des genannten. Ein genannter Unterauftrag lässt sich nicht
  umhängen, und keiner von ihnen lässt sich löschen (CO-0007, SO-0007). Das Formular zeigt das
  Kürzelfeld gesperrt.

Die eine Regel, die am Urlaubsauftrag hängt, ist der Umfang des Mitarbeiterauftrags
(`EmployeeorderService.applyVacationEntitlement`): der wirksame Urlaubsanspruch des Vertrags im Jahr
des Unterauftrags, beim automatischen Anlegen, beim Anlegen von Hand ohne Soll und bei jeder
Vertragsänderung. Ein Manager darf das Soll im Formular überschreiben; die nächste Vertragsänderung
berechnet es neu.

### Consequences

* Good: Welche Aufträge Sonderaufträge sind, entscheidet der Betrieb, ohne dass Code sich ändert.
* Good: Eine Umbenennung kann die Konfiguration nicht mehr lautlos ins Leere laufen lassen, und
  ein falsches Kürzel fällt beim Start auf statt im Urlaubskonto.
* Good: Das Kürzel eines Jahres-Unterauftrags ist frei; das Jahr kommt aus dem Beginn.
* Bad: Einen Sonderauftrag umzubenennen ist ein Vorgang im Betrieb: Kürzel in der Datenbank und
  Konfiguration im selben Deployment ändern, sonst hält der Start an.
* Bad: Ein Unterauftrag unter dem Urlaubsauftrag, der weder Jahr noch Sonderurlaub ist, bekommt einen
  Anspruch, solange er nicht in `do-not-calculate-signs` steht. Die Anwendung warnt beim Start vor
  einem, dessen Kürzel nicht das Jahr seines Beginns nennt — außer er endet vor dem laufenden Jahr:
  ein abgelaufenes Relikt bekommt keinen neuen Mitarbeiterauftrag, und eine Warnung bei jedem Start
  wäre nur Rauschen.
* Neutral: Reports, ETL-Definitionen und Views, die den Urlaubsauftrag am Kürzel erkennen, schützt die
  Sperre mit; sie lesen die Konfiguration nicht.
* Neutral: Ausnahme von ADR-0034 („Kürzel bleiben änderbar") für genau diese Aufträge.
