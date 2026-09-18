# ADR-0013 Kein direktes HttpSession-Zugriff in neuen Spring-MVC-Controllern

Date: 2026-06-10
Status: Accepted

> **Nachtrag 2026-09-18:** Die Regel gilt inzwischen ausnahmslos. Die beiden 2026-06-10 noch
> notierten Ausnahmen sind entfallen — `AuthorizedUser` und `AuthorizedEmployee` sind
> `@RequestScope`, und im gesamten `src/main/java` kommt `HttpSession` nicht mehr vor. Die
> Entscheidung selbst ist unverändert; nur die Ausnahmetabelle wurde ersetzt.

## Context and Problem Statement

Die Legacy-Struts-Schicht nutzt `HttpSession` ausgiebig als zentralen Zustandsspeicher: selektierter Mitarbeitervertrag, aktuelle Filter, Formulardaten, Navigationszustand. Dieses Pattern ist schwer testbar, verhindert horizontales Skalieren und macht den Kontrollfluss undurchsichtig.

Mit der Migration zu Spring MVC + Thymeleaf (ADR-0002) stellt sich die Frage: Soll das Session-basierte Statemanagement übernommen werden, oder gelten andere Regeln für neue Controller?

Auslöser war konkret der Wunsch, aus der neuen Matrixübersicht (`/dailyreport/matrix`) auf die Legacy-Einzelbuchungsansicht (`/do/ShowDailyReport`) zu verlinken. Der Struts-Action liest den aktuell ausgewählten Mitarbeitervertrag aus `HttpSession` — die neue UI will diesen aber explizit per URL-Parameter übergeben.

## Considered Options

* **Option A** — Session-State wie in Legacy fortführen: neue Controller lesen/schreiben `HttpSession` direkt.
* **Option B** — Vollständiges Verbot: kein `HttpSession`-Zugriff in neuen Controllern; Zustand immer via URL-Parameter oder Hidden-Form-Fields.
* **Option C** — Differenziertes Verbot mit explizit dokumentierten Ausnahmen: neue Controller ohne direkten `HttpSession`-Zugriff; benutzerbezogener Selektionszustand über Cookies/Browser-Storage; Sicherheits-/Identitätszustand als typisierte Session-Beans erlaubt.

## Decision Outcome

Chosen: **Option C**, weil ein vollständiges Verbot die schrittweise Migration der Legacy-Screens erschwert, aber unkontrollierter Session-State in neuen Screens alle Nachteile aus der Legacy-Welt erbt.

### Regeln

**Neue Spring-MVC-Controller:**
- Kein direktes `HttpSession`-Schreiben oder -Lesen für UI-Zustand.
- Zustand, der über einen einzelnen Request hinaus gebraucht wird (z. B. Filtereinstellungen, aktuell ausgewählter Mitarbeitervertrag), gehört in den URL (Query-Parameter, Path-Variable) oder in ein Cookie.
- Formulardaten werden als `@ModelAttribute`-Objekte gebunden, nicht über `HttpSession` geteilt.

**Benutzerbezogener Selektionszustand (z. B. "welcher Vertrag ist gerade ausgewählt"):**
- Muss über URL-Parameter (bevorzugt) oder ein Browser-Cookie persistiert werden.
- Kein Ablegen in `HttpSession` — der Zustand überlebt keinen Server-Neustart und ist nicht tab-safe.
- Die konkrete Implementierung dieses Mechanismus ist in ADR-0014 beschrieben (`UiState`-Bean + `UiStateFilter`).
- Konsequenz für Verlinkung von neuen auf Legacy-Screens: der Zielzustand muss als URL-Parameter mitgeliefert werden, auch wenn die Legacy-Action ihn danach in die Session schreibt.

**Sicherheits- und Identitätszustand (ursprünglich als Ausnahme geführt):**

Die ADR erlaubte 2026-06-10 zwei session-scoped Beans, weil Identität kein UI-Zustand ist. Diese
Ausnahme wird nicht mehr gebraucht — beide Beans sind inzwischen **request-scoped**:

| Bean | Heutiger Scope | Woher der Zustand kommt |
|---|---|---|
| `AuthorizedUser` (`auth/domain/AuthorizedUser.java`) | `@RequestScope` | liest pro Request aus dem `SecurityContext`; der vertretene Login-Sign bei Impersonation steht im `UiState` (→ ADR-0014), nicht im Bean. Einzige Ausnahme ist der Job-Modus (`initForJob()`), weil ein Scheduler keinen `SecurityContext` hat. |
| `AuthorizedEmployee` (`employee/domain/AuthorizedEmployee.java`) | `@Scope(SCOPE_REQUEST)` | wird pro Request über `login(Employee)` befüllt |

Damit gilt die Regel im Anwendungscode ausnahmslos: `HttpSession` kommt in `src/main/java` nicht
vor. Sollte je eine Ausnahme nötig werden, ist sie mit dem Kommentar
`// ADR-0013: Ausnahme — [Begründung]` am Ort der `HttpSession`-Nutzung zu dokumentieren.

### Consequences

* Good: neue Controller sind einfach unit-testbar (kein `MockHttpSession` notwendig)
* Good: Requests sind idempotent und bookmarkbar
* Good: tab-safe — mehrere Browser-Tabs können unterschiedliche Filtereinstellungen haben
* Good: die Anwendungsinstanz ist austauschbar — in den deployten Umgebungen hält EasyAuth die
  Sitzung (→ ADR-0026), sodass Skalieren keine Sitzungsaffinität braucht
* Bad: mehr URL-Parameter, längere URLs bei komplexen Filtern
