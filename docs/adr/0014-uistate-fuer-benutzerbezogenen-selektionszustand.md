# ADR-0014 UiState für benutzerbezogenen Selektionszustand

Date: 2026-06-18
Status: Accepted

## Context and Problem Statement

ADR-0013 legt fest, dass neue Spring-MVC-Controller keinen direkten `HttpSession`-Zugriff für UI-Zustand verwenden dürfen. Als konkreten Mechanismus für benutzerbezogenen Selektionszustand (z. B. "welcher Mitarbeitervertrag ist gerade ausgewählt") benennt ADR-0013 Cookie- oder URL-Parameter-basierte Lösungen — ohne die Implementierung festzulegen.

Mit dem Wachstum der neuen UI-Schicht taucht der gleiche Bedarf in mehreren Controllern auf: der aktuell gewählte Mitarbeitervertrag (`employeeContractId`), Kunde, Auftrag und Unterauftrag sollen seitenübergreifend erinnerbar sein, ohne Session-State. Eine ad-hoc-Lösung in jedem Controller würde zu Duplikation und inkonsistentem Cookie-Handling führen.

## Considered Options

* **Option A** — Jeder Controller liest/schreibt Cookies selbst über `HttpServletRequest`/`HttpServletResponse`.
* **Option B** — Zentraler `UiState`-Bean im Request-Scope, befüllt durch einen Servlet-Filter aus Cookies und URL-Parametern.
* **Option C** — Selektionszustand ausschließlich als URL-Parameter; keine Cookie-Persistenz.

## Decision Outcome

Chosen: **Option B**, weil sie ADR-0013 umsetzt ohne Boilerplate in jedem Controller, und weil Cookie-Persistenz den Nutzungskomfort deutlich verbessert (der zuletzt gewählte Vertrag bleibt nach Navigation erhalten).

### Implementierung

**`UiState`** (`common/web/UiState.java`)
- `@RequestScope`-Bean (Proxy-Mode `TARGET_CLASS`).
- Generisches `getLong(key)` / `setLong(key, value)` plus typisierte Convenience-Methoden (`getSelectedContractId()`).
- Wird per Dependency Injection in Controller eingebunden — kein direkter `HttpServletRequest`/`HttpServletResponse`-Zugriff nötig.

**`UiStateKey`** (`common/web/UiStateKey.java`)
- Konstanten für alle bekannten Schlüssel: `SELECTED_CONTRACT`, `SELECTED_CUSTOMER`, `SELECTED_ORDER`, `SELECTED_SUBORDER`.
- Mapping `PARAM_TO_KEY`: HTTP-Request-Parametername → interner Schlüssel (z. B. `"employeeContractId"` → `SELECTED_CONTRACT`).

**`UiStateFilter`** (`common/filter/UiStateFilter.java`)
- `OncePerRequestFilter`, registriert in `HttpFilterConfiguration`.
- Phase 1: Explizite URL-/Form-Parameter (nach `PARAM_TO_KEY`) setzen den UiState-Wert und schreiben gleichzeitig ein Cookie (`salat_<key>=<value>; Path=/; HttpOnly; SameSite=Strict`).
- Phase 2: Noch nicht gesetzte Slots werden aus vorhandenen `salat_`-Cookies befüllt.
- Explizite Parameter haben immer Vorrang vor Cookies.

### Nutzung in Controllern

```java
@RequiredArgsConstructor
public class SomeController {
    private final UiState uiState;

    private long effectiveContractId() {
        Long fromCookie = uiState.getSelectedContractId();
        if (fromCookie != null && fromCookie > 0) return fromCookie;
        // Fallback auf den aktuellen Vertrag des eingeloggten Mitarbeiters
        return employeecontractService.getCurrentContract(loginEmployee.getId())
            .map(EmployeeContract::getId).orElse(-1L);
    }
}
```

Die Selektion wird im HTML per verstecktem Feld oder als Query-Parameter übergeben; der Filter erkennt sie automatisch und aktualisiert Cookie + UiState.

### Weiterentwicklung

Die Key-Konstanten und HTTP-Param-Mappings wurden in ADR-0016 in das jeweilige Fachmodul verschoben (`EmployeeUiStateKeyContributor`, `OrderUiStateKeyContributor`). `UiStateKey` ist seitdem ein `@Component` mit Contributor-Muster. `UiState` hat keine domänenspezifischen Methoden mehr.

### Consequences

* Good: kein Cookie-Boilerplate in Controllern
* Good: explizite Parameter schlagen Cookie immer — deterministisches Verhalten, leicht testbar
* Good: Cookie-Persistenz über Seitennavigation hinweg ohne Session-Abhängigkeit
* Bad: Zustand lebt im Browser-Cookie, nicht im Server — Ablaufdatum und Invalidierung liegen in der Verantwortung des Clients
* Neutral: neue Selektionsdimensionen erfordern einen Eintrag in `UiStateKey.PARAM_TO_KEY`
