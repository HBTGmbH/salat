# Architecture Decision Records

This directory contains ADRs (Architecture Decision Records) for the Salat project.
Format: [MADR](https://adr.github.io/madr/) — Markdown Any Decision Records.

## How to add a new ADR

1. Copy `template.md` to `NNNN-short-title.md` (next sequential number, kebab-case)
2. Fill in all sections
3. Add a row to the index below
4. Set status to `Proposed`; change to `Accepted` once the team agrees

## Index

| # | Title | Status | Date |
|---|---|---|---|
| [0001](0001-modular-monolith.md) | Modular Monolith als Architekturstil | Accepted | 2026-05-24 |
| [0002](0002-thymeleaf-spring-mvc-als-ui-stack.md) | Thymeleaf + Spring MVC als Ziel-UI-Stack | Accepted | 2026-05-24 |
| [0003](0003-spring-events-fuer-cross-module-kommunikation.md) | Spring Events für Cross-Module-Kommunikation | Accepted | 2026-05-24 |
| [0004](0004-salat-thymeleaf-dialect.md) | Salat Custom Thymeleaf Dialect | Superseded by ADR-0005 | 2026-05-24 |
| [0005](0005-alle-fragmente-durch-salat-dialect-ersetzen.md) | Alle Thymeleaf-Fragmente durch Salat-Dialect ersetzen | Accepted | 2026-05-24 |
| [0006](0006-rollenbasierte-autorisierung.md) | Rollenbasierte Autorisierung mit zwei Durchsetzungsebenen | Accepted | 2026-05-24 |
| [0007](0007-repository-dao-hybrid.md) | Repository + DAO Hybrid Pattern | Superseded by ADR-0019 | 2026-05-24 |
| [0008](0008-exception-hierarchie-mit-errorcode.md) | Exception-Hierarchie mit ErrorCode | Accepted | 2026-05-24 |
| [0009](0009-liquibase-datenbankmigrationen.md) | Liquibase für Datenbankmigrationen | Accepted | 2026-05-24 |
| [0010](0010-deutsch-first-i18n.md) | Deutsch als Primärsprache und UTF-8-Encoding | Accepted | 2026-05-24 |
| [0011](0011-stammdaten-vs-bewegungsdaten.md) | Stammdaten vs. Bewegungsdaten | Accepted | 2026-05-30 |
| [0012](0012-archivierung-von-stammdaten.md) | Archivierung von Stammdaten: `hide`-Flag und Gültigkeitsspannen | Accepted | 2026-06-04 |
| [0013](0013-kein-httpsession-in-neuen-controllern.md) | Kein direktes HttpSession-Zugriff in neuen Spring-MVC-Controllern | Accepted | 2026-06-10 |
| [0014](0014-uistate-fuer-benutzerbezogenen-selektionszustand.md) | UiState für benutzerbezogenen Selektionszustand | Accepted | 2026-06-18 |
| [0015](0015-error-modul-fuer-fehlerseite.md) | Eigenes `error`-Modul für die Fehlerseite | Accepted | 2026-06-21 |
| [0016](0016-uistate-key-ownership-per-modul.md) | UiState-Key-Ownership pro Modul | Accepted | 2026-06-21 |
| [0017](0017-viewhelper-fuer-darstellungslogik.md) | ViewHelper-Klassen für darstellungsspezifische Aufbereitung | Accepted | 2026-06-22 |
| [0018](0018-csrf-schutz-mit-cookie-tokenrepository.md) | CSRF-Schutz mit CookieCsrfTokenRepository und CsrfTokenRequestAttributeHandler | Accepted | 2026-06-28 |
| [0019](0019-direkter-repository-zugriff-in-services.md) | Direkter Repository-Zugriff in Services (kein DAO für neue Module) | Accepted | 2026-07-05 |
