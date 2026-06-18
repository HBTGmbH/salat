# ADR-0013 Kein direktes HttpSession-Zugriff in neuen Spring-MVC-Controllern

Date: 2026-06-10
Status: Accepted

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

**Erlaubte Ausnahmen (müssen am Ort der Ausnahme dokumentiert werden):**

| Bean | Warum erlaubt |
|---|---|
| `AuthorizedUser` (`auth/domain/AuthorizedUser.java`) | Identitäts- und Rollendaten des eingeloggten Nutzers. Session-Scope ist hier korrekt — es handelt sich um Sicherheits-State, nicht UI-State. Die Impersonation (`impersonate(SalatUser)`) ist ebenfalls erlaubt: sie ist ein Identitätswechsel für Support-Zwecke, kein UI-Selektionszustand. |
| `AuthorizedEmployee` (`employee/domain/AuthorizedEmployee.java`) | Analoges Sicherheitsobjekt für den eingeloggten Mitarbeiter (Name, ID). Session-Scope aus dem gleichen Grund wie `AuthorizedUser`. |

Weitere Ausnahmen sind grundsätzlich möglich, müssen aber mit einem Kommentar `// ADR-0013: Ausnahme — [Begründung]` am Ort der `HttpSession`-Nutzung dokumentiert werden.

### Transition-Problem: neue UI → Legacy-Struts-Actions

Wenn eine neue Thymeleaf-Seite auf eine Legacy-Struts-Action verlinkt, die ihren Zustand aus der `HttpSession` liest, muss der Zielzustand im URL mitgegeben werden. Die Struts-Action kann den URL-Parameter prüfen und damit die Session befüllen (wie `ShowDailyReportAction` bereits für `day`/`month`/`year` tut).

Solange nicht migrierte Struts-Actions erreichbar sind, ist diese Übergabe per URL-Parameter der einzig korrekte Weg — Session-Voraussetzungen in Legacy-Actions dürfen nicht in neuen Controllern erfüllt werden.

### Consequences

* Good: neue Controller sind einfach unit-testbar (kein `MockHttpSession` notwendig)
* Good: Requests sind idempotent und bookmarkbar
* Good: tab-safe — mehrere Browser-Tabs können unterschiedliche Filtereinstellungen haben
* Bad: mehr URL-Parameter, längere URLs bei komplexen Filtern
* Bad: Übergangsaufwand beim Verlinken neue UI → Legacy (URL-Param muss explizit gesetzt werden)
* Neutral: Legacy-Struts-Screens sind von dieser Regel nicht betroffen
