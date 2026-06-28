# ADR-0018 CSRF-Schutz mit CookieCsrfTokenRepository und CsrfTokenRequestAttributeHandler

Date: 2026-06-28
Status: Accepted

## Context and Problem Statement

CSRF-Schutz war in allen Spring Security Filter Chains deaktiviert. Die Anwendung verwendet eine stateless Session-Policy (`STATELESS`), daher ist ein `HttpSession`-basiertes Token-Speichermodell nicht praktikabel. Gleichzeitig werden Thymeleaf-Formulare (reguläre POST-Submissions) und HTMX 2.0 (partielle Updates via `hx-post`) für die UI verwendet.

Ziel: CSRF-Schutz für alle Production-/Staging-/LocalEasyAuth- und LocalDev-Filter-Chains aktivieren, ohne HttpSession-Abhängigkeit einzuführen, und transparent für Thymeleaf-Formulare sowie HTMX-Anfragen.

## Considered Options

* **Option A:** `CookieCsrfTokenRepository` + `CsrfTokenRequestAttributeHandler` (raw token)
* **Option B:** `CookieCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler` (XOR-masked, Spring Security 6 Standard)
* **Option C:** CSRF-Schutz deaktiviert lassen

## Decision Outcome

Chosen: **Option A**, weil `CsrfTokenRequestAttributeHandler` cookie-Wert, Formularfeld-Wert und HTMX-Header denselben raw Token-Wert tragen — JavaScript kann nach jedem HTMX-Response den `_csrf` Input direkt aus dem Cookie aktualisieren.

### Consequences

* Good: Stateless — kein HttpSession erforderlich; passt zur bestehenden `STATELESS` Session-Policy.
* Good: Thymeleaf-Formulare funktionieren ohne Template-Änderungen — `CsrfRequestDataValueProcessor` injiziert das Hidden-Feld automatisch über `th:action`.
* Good: HTMX-Anfragen senden den Token als `X-XSRF-TOKEN`-Header (via `htmx:configRequest`-Listener in `base.html`).
* Good: Nach einem HTMX-Response werden veraltete `_csrf` Hidden-Felder per `htmx:afterSettle`-Handler in `salat.js` aktualisiert.
* Bad (BREACH-Trade-off): `XorCsrfTokenRequestAttributeHandler` würde pro Render einen anderen XOR-maskierten Wert liefern und damit BREACH-Angriffe erschweren. `CsrfTokenRequestAttributeHandler` omitiert dieses Masking. Für diese Anwendung akzeptabel, weil: (a) ein Angreifer, der komprimierte Antwortinhalte kontrolliert, ein unrealistisches Bedrohungsmodell für eine interne Zeiterfassungsanwendung darstellt; (b) HTMX-Anfragen senden den Raw-Token ohnehin als Header; (c) die Cookie-Refresh-Anforderung macht XOR-Masking ohne server-seitige Koordination nicht praktikabel.
* Neutral: REST-API-Chains (`/api/**`, `/rest/**`) und statische Ressourcen (Order 0) behalten `csrf(disable)` — sie verwenden Token-basierte Authentifizierung bzw. haben keine State-modifizierenden Formulare.

## Token-Speicher und Verarbeitung

- **Repository:** `CookieCsrfTokenRepository.withHttpOnlyFalse()` — speichert den Token im Cookie `XSRF-TOKEN`; `httpOnly=false` erlaubt JavaScript den Zugriff.
- **Handler:** `CsrfTokenRequestAttributeHandler` — Token im Formularfeld = Token im Header = Token im Cookie (raw, kein XOR).
- **HTMX:** `htmx:configRequest`-Listener in `layout/base.html` liest `XSRF-TOKEN`-Cookie und setzt `X-XSRF-TOKEN`-Header auf jeder HTMX-Anfrage.
- **Stale Forms:** `htmx:afterSettle`-Handler in `salat.js` refresht alle `input[name="_csrf"]` aus dem aktuellen Cookie-Wert nach jedem HTMX-Response.
