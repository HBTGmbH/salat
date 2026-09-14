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
`BudgetQueryService`, der einen Join darauf unter Berufung auf eben diese Konvention verworfen hatte
— dort mit #908 belegt statt mit dem Javadoc. In den ADRs stand der Satz einmal, referierend, in
ADR-0007 — und der trägt `Status: Superseded by ADR-0019`. ADR-0019 sagt dazu nichts. Die
ArchUnit-Regeln `accessEntitiesOnlyInServicesOrDAOs` und `noEntityInPublicInterfaceOfService`, die
der Sache am nächsten kommen, sind beide mit `// TODO @ArchTest` stillgelegt. Es gab also eine
Konvention ohne Beschluss und ohne Durchsetzung.

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
   darf. Das gilt auch für die Module, die die Abfrage über Assoziationen nur **durchläuft**: wer
   `Timereport.employeecontract.employee` navigiert, liest `employee` und muss auch dieses Modul
   importieren dürfen. Die erlaubten Richtungen stehen unverändert in `ArchitectureTest` — dort
   geprüft werden allerdings nur Importe, nicht Joins (→ Durchsetzung).
2. Die Entities dürfen die Abfrage nicht verlassen. Zurück kommt ein Record oder ein DTO aus reinen
   Werten und Ids — eine kontrollierte Kopie, die benennt, welche Felder die Grenze überqueren.
   Keine Entity als Record-Komponente, keine Interface-Projektion, die im Aufrufer nachnavigiert,
   kein `Object[]` oder `Tuple`: alle drei geben den Assoziationsgraphen doch wieder heraus.
3. Nur lesend. Kein `@Modifying`, kein DML und kein Cascade über die Grenze — Schreiben bleibt bei
   ADR-0003 und den Events.
4. JPQL, nicht natives SQL. So hängt die Abfrage am Entity-Modell des fremden Moduls und nicht an
   dessen Tabellen; ein `nativeQuery = true` fiele nicht einmal beim Anwendungsstart auf.
5. Die Autorisierung verantwortet der lesende Service. Der zeilenweise Filter des besitzenden Moduls
   — die zweite Durchsetzungsebene aus ADR-0006 — greift für einen gejointen Lesepfad nicht mehr
   automatisch. Wo er dadurch ausfällt, ist am Aufrufer zu begründen, warum er dort wirkungslos ist
   oder anders abgedeckt wird, samt der Prämissen, auf denen die Begründung ruht. Eine Begründung,
   die ihre Prämissen nicht nennt, lässt sich nach einer Änderung an einer der beteiligten
   `*Authorization`-Klassen nicht nachrechnen.

### Begründung

ADR-0001 hält das spätere Herauslösen eines Moduls als eigenen Service offen — als neutrale Folge,
nicht als Ziel. Genau daran entscheidet sich die Regel, und zwar in beide Richtungen:

Eine **Kopie ist replizierbar.** Wird das Modul herausgelöst, wird aus dem Join eine Replikation
genau der Felder, die die Kopie schon heute benennt, und der Record ist bereits ihr Vertrag — in
DDD-Begriffen die *Published Language* an der Kontextgrenze, aus der später ein abgeleitetes Read
Model des lesenden Kontexts wird. Eine **geteilte Entity** dagegen benennt nichts: sie zieht ihren
ganzen Assoziationsgraphen mit, und das Herauslösen würde zur Neumodellierung statt zu einer
Replikation.

Billig wird die Replikation damit nicht. Sie bringt mit, was ein gemeinsames Schema heute
verschenkt: Eventual Consistency, einen Erstbefüllungslauf, die Weitergabe von Löschungen — beim
`Timereport` von *Soft*-Löschungen — und eine Aussage darüber, wie alt eine Zahl sein darf. Die
Regel macht das Herauslösen abschätzbar, nicht billig.

Die Alternative — Abfragen an der Modulgrenze enden zu lassen — kauft dieselbe Entkopplung teurer
ein: sie verlagert Filter und Sortierung nach Java und lässt Mengen laden, von denen ein Bruchteil
gebraucht wird. Das ist kein Entkopplungsgewinn, sondern ein Antwortzeitverlust.

### Was die Regel nicht leistet

Die Entity bleibt im Modul, das Wissen über ihren Aufbau nicht. Die Abfrage nennt
`t.referenceday.refdate`, `t.durationhours`, `t.employeecontract.employee.firstname` — Spalten und
Assoziationen, die `dailyreport` als Interna hält und nicht als Schnittstelle veröffentlicht. Ein
Anticorruption Layer wäre das Gegenteil davon. Diese Entscheidung nimmt die Kopplung in Kauf und
begrenzt sie auf die eine Stelle, an der die Abfrage steht.

Umbenennungen fallen dabei beim Anwendungsstart auf, weil Hibernate jedes `@Query` übersetzt.
Bedeutungsänderungen fallen nicht auf: `findAssignedBookings` verlässt sich darauf, dass
`Timereport` ein `@SQLRestriction("deleted = false")` trägt, und führt deshalb keine eigene
Löschbedingung. Ändert `dailyreport` seine Löschstrategie, ändert sich das Ergebnis in `budget`
stumm. Wer über die Grenze joint, übernimmt solche Invarianten des fremden Moduls in die eigenen
Zeilen — und hat sie dort zu benennen.

### Durchsetzung

Die Regel wird von keinem Test durchgesetzt. `ArchitectureTest` prüft Klassenabhängigkeiten; der
Inhalt eines `@Query` ist eine Zeichenkette und taucht dort nicht auf. Ein Join in ein Modul, das
nicht importiert werden darf, fiele durch kein Netz — auch `beFreeOfCycles` nicht, denn der Zyklus
entstünde allein im SQL. Die beiden Regeln, die der Sache am nächsten kommen, sind stillgelegt und
griffen ohnehin nur auf Java-Ebene. Diese Entscheidung hängt am Review.

### Consequences

* Good: Lesepfade laden, was sie zeigen — Filter, Sortierung und Limit liegen dort, wo sie
  ausgeführt werden können
* Good: die Modulgrenze ist an den Records ablesbar; jede Kopie ist eine Liste der Felder, die sie
  überquert
* Good: das Herauslösen eines Moduls bleibt eine Replikationsaufgabe, keine Neumodellierung
* Bad: der Join koppelt an das Persistenzmodell des fremden Moduls statt an dessen veröffentlichte
  Schnittstelle; Bedeutungsänderungen dort wirken stumm hierher
* Bad: der zeilenweise Autorisierungsfilter des besitzenden Moduls greift nicht mehr automatisch —
  deshalb Punkt 5
* Bad: nichts erzwingt die Regel automatisch, sie hängt am Review
* Neutral: sie setzt eine gemeinsame Persistence Unit voraus und gilt, solange der Monolith eine
  ist; für ein herausgelöstes Modul tritt die Replikation an ihre Stelle
* Neutral: bestehende Lesepfade werden nicht umgestellt; die Regel gilt ab jetzt und ist kein
  Auftrag zum Umbau
* Neutral: ersetzt die referierte Aussage aus ADR-0007 §82, die `Timereport` strenger behandelte als
  jede andere Entity

## Beispiel

`TimereportBudgetAssignmentRepository.findAssignedBookings` (#997) verbindet die Zuordnung des
Budget-Moduls mit `Timereport` aus `dailyreport` und gibt `AssignedBooking` zurück. Sieben Felder
überqueren dort die Grenze, und sie zeigen, wie weit ein Join reicht: vier stammen aus `dailyreport`
selbst, zwei über dessen Assoziationen aus `employee`, eines aus `order`. Alle drei Module darf
`budget` importieren, sonst wäre die Abfrage nach Punkt 1 nicht erlaubt. Das achte Feld des Records,
`suborderSign`, füllt der Service nach — `Suborder#getCompleteOrderSign()` läuft die Elternkette ab
und lässt sich in JPQL nicht ausdrücken.

Die Begründung zur Autorisierung steht an `TimereportBudgetAssignmentService.getAssignedBookings`.
