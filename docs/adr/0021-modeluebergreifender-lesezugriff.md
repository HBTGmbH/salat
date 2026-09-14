# ADR-0021 Modulübergreifender Lesezugriff: Join erlaubt, Entities bleiben im Modul

Date: 2026-09-14
Status: Accepted

## Context and Problem Statement

Für modulübergreifende Zusammenarbeit war zweierlei geregelt: **Seiteneffekte** laufen über Spring
Events (ADR-0003), und **Lesen entgegen einer verbotenen Importrichtung** läuft über Command Events
(`common/command/`, in AGENTS.md ausdrücklich als „last resort" markiert).

Ungeregelt war der häufigste Fall: **Lesen in erlaubter Richtung.** Darf ein Modul in seiner eigenen
Abfrage die Entity eines anderen Moduls verbinden, oder muss es den Umweg über dessen Service
nehmen?

Es gab dazu nur zwei Aussagen im Code, beide zu `Timereport`: den Klassen-Javadoc von
`TimereportDTO` („Only `TimereportService` should use `Timereport` directly") und einen Kommentar in
`BudgetQueryService`, der einen Join darauf unter Berufung auf jenen Javadoc verworfen hatte. In den
ADRs stand der Satz einmal, referierend, in ADR-0007 — und der trägt `Status: Superseded by
ADR-0019`. ADR-0019 sagt dazu nichts. Die ArchUnit-Regel `accessEntitiesOnlyInServicesOrDAOs` ist
mit `// TODO @ArchTest` stillgelegt. Es gab also eine Konvention ohne Beschluss und ohne
Durchsetzung.

Was das kostet, zeigte #997. Die Budgetplan-Detailseite las alle Buchungen eines *Kundenauftrags* im
Zeitraum, um daraus in Java die des Plans herauszufiltern und 200 davon anzuzeigen — auf einem Plan
mit tausenden Buchungen 509 Statements für eine Seite. Ein Join von `budget` auf `Timereport` mit
`ORDER BY` und `LIMIT` war der offensichtliche Weg und galt als versperrt.

## Considered Options

* Abfragen enden an der Modulgrenze; über Services des besitzenden Moduls lesen und in Java
  zusammenführen
* Join über die Modulgrenze erlaubt, Entities dürfen sie nicht überschreiten
* Join und Entities frei über Modulgrenzen

## Decision Outcome

Chosen: **Join über die Modulgrenze erlaubt, Entities dürfen sie nicht überschreiten.**

1. Ein Modul darf in seinen eigenen Abfragen die Entities jedes Moduls verbinden, das es importieren
   darf. Die erlaubten Richtungen stehen unverändert in `ArchitectureTest`.
2. Die Entities dürfen die Abfrage nicht verlassen. Zurück kommt ein Record oder ein DTO — eine
   kontrollierte Kopie, die benennt, welche Felder die Grenze überqueren.
3. Die Autorisierung verantwortet der lesende Service. Wo ein zeilenweiser Filter des besitzenden
   Moduls dadurch nicht mehr greift, ist am Aufrufer zu begründen, warum er dort wirkungslos ist
   oder anders abgedeckt wird.

### Begründung

ADR-0001 hält das spätere Herauslösen eines Moduls als eigenen Service ausdrücklich offen. Genau
daran entscheidet sich die Regel, und zwar in beide Richtungen:

Eine **Kopie ist replizierbar.** Wird das Modul herausgelöst, wird aus dem Join eine Replikation
genau der Felder, die die Kopie schon heute benennt — Domain Driven Design nennt das eine
kontrollierte Datenkopie, und der Record ist bereits ihr Vertrag. Eine **geteilte Entity** dagegen
benennt nichts: sie zieht ihren ganzen Assoziationsgraphen mit, und das Herauslösen würde zur
Neumodellierung statt zu einer Replikation.

Die Alternative — Abfragen an der Modulgrenze enden zu lassen — kauft dieselbe Entkopplung teurer
ein: sie verlagert Filter und Sortierung nach Java und lässt Mengen laden, von denen ein Bruchteil
gebraucht wird. Das ist kein Entkopplungsgewinn, sondern ein Antwortzeitverlust.

### Consequences

* Good: Lesepfade laden, was sie zeigen — Filter, Sortierung und Limit liegen dort, wo sie
  ausgeführt werden können
* Good: die Modulgrenze ist an den Records ablesbar; jede Kopie ist eine Liste der Felder, die sie
  überquert
* Good: das Herauslösen eines Moduls bleibt eine Replikationsaufgabe, keine Neumodellierung
* Bad: der zeilenweise Autorisierungsfilter des besitzenden Moduls greift nicht mehr automatisch —
  deshalb Punkt 3
* Neutral: bestehende Lesepfade werden nicht umgestellt; die Regel gilt ab jetzt und ist kein
  Auftrag zum Umbau
* Neutral: ersetzt die referierte Aussage aus ADR-0007 §82, die `Timereport` strenger behandelte als
  jede andere Entity

## Beispiel

`TimereportBudgetAssignmentRepository.findAssignedBookings` (#997) verbindet die Zuordnung des
Budget-Moduls mit `Timereport` aus `dailyreport` und gibt `AssignedBooking` zurück — einen Record mit
acht Feldern. Die Begründung zur Autorisierung steht an
`TimereportBudgetAssignmentService.getAssignedBookings`.
