# ADR-0017 ViewHelper-Klassen für darstellungsspezifische Aufbereitung

Date: 2026-06-22
Status: Accepted

## Context and Problem Statement

Die Präsentationsschicht benötigt häufig aufbereitete Werte, die aus Domänenobjekten berechnet werden: formatierte Dauern mit Tagesumrechnung, prozentuale Auslastungen, Farbklassen für Überstundenanzeigen. Die Frage ist, wo diese Darstellungslogik implementiert wird — im Service, direkt im Template oder in einer dedizierten Klasse.

Konkreter Auslöser: `OvertimeViewHelper` und `VacationViewHelper` verwendeten `HttpSession` als temporären Datenpuffer zwischen Serviceaufruf und Modellfüllung (→ ADR-0013). Bei der Migration musste entschieden werden, wohin die Darstellungslogik (Formatierung, Prozentwerte, Farb-Klassen) stattdessen gehört.

## Considered Options

* **Option A** — Formatierungslogik in Services: Services geben fertig formatierte Strings zurück (z. B. `"8:30 (1,06 Tage)"`).
* **Option B** — Formatierungslogik inline in Thymeleaf-Templates: SpEL-Ausdrücke und `th:with`-Variablen erledigen Berechnung und Formatierung direkt im Template.
* **Option C** — Dedizierte ViewHelper-Klassen im `viewhelper`-Subpaket: Java-Klassen, die Domänedaten als Konstruktorargumente übernehmen und aufbereitete Werte als Methoden anbieten. Controller und Templates rufen diese Klassen auf; Services kennen sie nicht.

## Decision Outcome

Chosen: **Option C**, weil Services keine Präsentationslogik enthalten sollen (Option A verletzt Separation of Concerns) und komplexe Formatierungen in Templates schwer lesbar und nicht testbar sind (Option B).

### Regeln

**Paketort:**
- ViewHelper-Klassen gehören ausschließlich in das `viewhelper`-Subpaket ihres Moduls (z. B. `org.tb.dailyreport.viewhelper`).
- Sie dürfen nicht in `domain`, `service` oder anderen Subpaketen abgelegt werden.

**Abhängigkeitsregeln:**
- Services dürfen ViewHelper-Klassen **nicht** importieren oder zurückgeben. Services geben Domänenobjekte oder DTOs zurück.
- ViewHelper dürfen von **Controllern**, von **anderen ViewHelpern** und direkt in **Thymeleaf-Templates** verwendet werden.
- ViewHelper dürfen kein `HttpSession` direkt verwenden (→ ADR-0013). Domänedaten werden als Konstruktorargumente übergeben.

**Typisches Muster:**
1. Service gibt `List<SomeDTO>` zurück (Domäneschicht, kein ViewHelper-Bezug).
2. Controller bildet DTOs auf ViewHelper ab — typischerweise über eine statische Fabrikmethode `SomeViewHelper.from(contract, dto)`.
3. Controller legt die ViewHelper-Liste als Model-Attribut ab.
4. Template ruft Methoden der ViewHelper-Objekte auf.

```java
// Service (keine ViewHelper-Abhängigkeit)
List<VacationInfo> getVacations(Employeecontract employeecontract);

// Controller
var vacations = vacationService.getVacations(contract).stream()
    .map(info -> VacationViewHelper.from(contract, info))
    .toList();
model.addAttribute("vacations", vacations);

// Template
<span th:text="${v.usedVacationString}">...</span>
```

### Consequences

* Good: Präsentationslogik ist in normalen Java-Klassen implementiert und damit unit-testbar.
* Good: Services bleiben frei von Formatierungscode; Domäneobjekte/DTOs sind sprach- und renderingneutral.
* Good: Templates bleiben lesbar — komplexe Berechnungen landen nicht als verschachtelter SpEL-Ausdruck im HTML.
* Good: ViewHelper können andere ViewHelper nutzen und so Darstellungslogik wiederverwenden.
* Bad: Zusätzlicher Mapping-Schritt im Controller (Service-DTO → ViewHelper).
* Neutral: Für jeden Darstellungskontext entsteht eine eigene Klasse; bei einfachen Fällen kann das überdimensioniert wirken.
