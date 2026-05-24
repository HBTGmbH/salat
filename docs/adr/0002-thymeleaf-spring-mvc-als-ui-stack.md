# ADR-0002 Thymeleaf + Spring MVC als Ziel-UI-Stack

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Die bestehende UI basiert auf Struts 1.2 und JSP — ein veralteter Stack, der keine aktive Weiterentwicklung mehr erfährt und schlecht mit modernen Spring-Boot-Features integriert. Neue Screens und Features brauchen eine zukunftsfähige Alternative, die mit dem bestehenden Spring-Boot-Backend harmoniert.

## Considered Options

* Struts 1.2 + JSP (Status quo beibehalten)
* React SPA + REST-API
* Thymeleaf + Spring MVC (server-side rendering)
* JSF

## Decision Outcome

Chosen: **Thymeleaf + Spring MVC**, weil es natives Server-Side-Rendering beibehält (kein separates Build-System), direkt in Spring Boot integriert, die bestehende Session-basierte Security (Spring Security) ohne API-Layer wiederverwendet und Thymeleaf-Templates als valides HTML auch ohne laufenden Server lesbar und testbar sind.

Für Styling und Komponenten: Bootstrap 5 mit Tabler-Design-System als Overlay.

### Consequences

* Good: kein separater Frontend-Build-Prozess (kein npm/webpack im CI)
* Good: Spring Security und Session-Management funktionieren unverändert
* Good: schrittweise Migration möglich — Struts/JSP und Thymeleaf koexistieren während der Übergangsphase
* Bad: kein reaktives/SPA-Frontend ohne zusätzlichen Aufwand
* Bad: HTMX für partielle Updates nötig, wenn clientseitige Interaktivität gefordert ist
* Neutral: Legacy-Struts-Screens bleiben funktional, erhalten aber keine neuen Features mehr

## Migration

- Neue Screens: immer Spring MVC + Thymeleaf
- Legacy-Screens: opportunistische Migration bei signifikanten Änderungen
- URL-Konvention Legacy: `/do/<ActionName>`; neue Controller: sprechende REST-ähnliche Pfade
