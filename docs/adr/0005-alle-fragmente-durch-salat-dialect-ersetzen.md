# ADR-0005 Alle Thymeleaf-Fragmente durch Salat-Dialect ersetzen

Date: 2026-05-24
Status: Accepted

Supersedes: ADR-0004

## Context and Problem Statement

ADR-0004 führte den `salat:`-Dialect für Leaf-Formular-Komponenten ein, ließ aber strukturelle Fragmente (z. B. `master-table`, Layout-Dekoratoren) weiterhin als `th:replace`-Calls zu. Das erzeugt zwei parallele Abstraktionsebenen für wiederverwendete Komponenten, die Orientierung in Templates erschweren und inkonsequent wirken.

## Considered Options

* Status quo: Dialect für Leaf-Komponenten, `th:replace`-Fragments für Struktur
* Alle Fragmente durch Salat-Dialect-Tags ersetzen (einheitliche Abstraktionsebene)

## Decision Outcome

Chosen: **Alle Fragmente durch Salat-Dialect-Tags ersetzen**, einschließlich `master-table` und weiterer struktureller Layout-Komponenten.

### Consequences

* Good: eine einzige Abstraktionsebene für alle wiederverwendeten Template-Komponenten
* Good: Templates werden konsistenter und einfacher zu lesen
* Bad: Migration aller bestehenden `th:replace`-Calls in vorhandenen Templates erforderlich
* Bad: Der Dialect wächst — mehr Prozessoren zu implementieren und zu warten
* Neutral: Layout-Dekoratoren (Thymeleaf Layout Dialect) sind davon nicht betroffen — diese Entscheidung betrifft nur Fragment-basierte Komponenten
