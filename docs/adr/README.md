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
