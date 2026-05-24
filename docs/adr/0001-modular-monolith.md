# ADR-0001 Modular Monolith als Architekturstil

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Salat ist eine interne Zeiterfassungs- und Projektmanagement-Anwendung mit klar abgrenzbaren Fachdomänen (Kunden, Mitarbeiter, Aufträge, Reporting, …). Es stellte sich die Frage, wie diese Domänen strukturiert und deployed werden sollen — als Microservices, klassischer Monolith oder als Modular Monolith.

## Considered Options

* Microservices
* Klassischer Monolith (keine Modul-Grenzen)
* Modular Monolith

## Decision Outcome

Chosen: **Modular Monolith**, weil der Umfang der Anwendung und die Teamgröße keinen Mehraufwand für Netzwerkkommunikation, verteilte Transaktionen und separates Deployment rechtfertigen, die Domänengrenzen aber dennoch explizit durchgesetzt werden sollen.

### Consequences

* Good: ein einziges Deployment-Artefakt, keine Netzwerk-Latenz zwischen Domänen, volle ACID-Transaktionen über Modulgrenzen hinweg möglich
* Good: Modulgrenzen sind per Paketstruktur und Coupling-Regeln sichtbar und erzwungen
* Bad: vertikales Skalieren einzelner Module nicht möglich; alle Module skalieren gemeinsam
* Neutral: ein späteres Herauslösen einzelner Module zu Microservices bleibt möglich, wenn sich die Anforderungen ändern

## Module

Top-level-Pakete unter `org.tb`: `auth`, `chicoree`, `common`, `customer`, `dailyreport`, `employee`, `etl`, `favorites`, `invoice`, `jira`, `management`, `order`, `reporting`, `statistic`.

Coupling-Regeln:
- Intra-Modul: freie Abhängigkeiten erlaubt
- Inter-Modul: nur über deklarierte Grenzen; keine Zyklen
- Cross-Modul-Seiteneffekte: ausschließlich über Spring Application Events (→ ADR-0003)
