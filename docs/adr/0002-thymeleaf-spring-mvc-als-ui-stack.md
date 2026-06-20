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
* Bad: kein reaktives/SPA-Frontend ohne zusätzlichen Aufwand
* Bad: HTMX für partielle Updates nötig, wenn clientseitige Interaktivität gefordert ist

## Migration

**Migration abgeschlossen (2026-06-21).** Die Struts-Schicht (Actions, JSPs, Viewhelper, struts-config.xml) wurde vollständig entfernt. Die URL-Konvention `/do/<ActionName>` existiert nicht mehr; alle Endpunkte folgen REST-ähnlichen Pfaden.
