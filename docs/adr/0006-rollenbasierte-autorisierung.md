# ADR-0006 Rollenbasierte Autorisierung mit zwei Durchsetzungsebenen

Date: 2026-05-24
Status: Accepted

## Context and Problem Statement

Die Anwendung verwaltet sensible Daten (Zeitberichte, Verträge, Rechnungen) und muss sicherstellen, dass Benutzer nur auf die für ihre Rolle erlaubten Operationen zugreifen können. Es gibt klar unterscheidbare Rollen mit hierarchischen Rechten. Die Herausforderung: Wie werden diese Rechte zuverlässig durchgesetzt — auch wenn ein Controller-Aufruf umgangen wird oder ein Service direkt aus einem Job oder einem anderen Service aufgerufen wird?

## Considered Options

* Nur HTTP-Boundary-Checks (Spring Security `@PreAuthorize` auf Controllern)
* Nur Service-Layer-Checks (manuelle `if`-Guards in jedem Service)
* Zwei gestapelte Ebenen: HTTP-Boundary + Service-Boundary via AOP

## Decision Outcome

Chosen: **Zwei gestapelte Ebenen** (Defense in Depth), weil ein einzelner Check an der HTTP-Grenze nicht greift, wenn Services direkt aufgerufen werden (Scheduled Jobs, interne Aufrufe), und manuelle Guards in jedem Service fehleranfällig und inkonsistent wären.

### Consequences

* Good: ein vergessener `@PreAuthorize` auf einem Controller führt nicht automatisch zu einer Sicherheitslücke — der Service-Layer fängt es auf
* Good: Scheduled Jobs und interne Service-Aufrufe unterliegen denselben Prüfungen wie HTTP-Requests
* Bad: jede Operation wird zweimal geprüft (Performance-Overhead ist vernachlässigbar, aber Komplexität steigt)
* Bad: neue Entwickler müssen beide Ebenen kennen, um Berechtigungen korrekt zu modellieren

---

## Rollenhierarchie

Rollen werden aus dem Mitarbeiterstatus (`loginStatus`) abgeleitet und sind kumulativ:

```
adm  ──→ ADMIN + MANAGER + PEOPLE_LEAD + BACKOFFICE + USER   (Administration)
bl   ──→ MANAGER + PEOPLE_LEAD + BACKOFFICE + USER           (Geschäftsführung)
pv   ──→ PEOPLE_LEAD + USER                                  (People Lead)
bo   ──→ BACKOFFICE + USER                                   (Backoffice)
ma   ──→ USER                                                (Mitarbeitender)
restricted → RESTRICTED                                      (Extern/Praktikum)
```

Scheduled Jobs laufen mit synthetischen Rechten: `manager = true`, `backoffice = true`, `loginSign = "SYSTEM"`.

---

## Ebene 1: HTTP-Boundary (`@PreAuthorize` auf Controllern)

Spring Security wertet `@PreAuthorize`-Ausdrücke aus, bevor die Controller-Methode ausgeführt wird. `@EnableMethodSecurity` ist auf `SalatApplication` aktiviert.

Typische Muster:

```java
// Klasse: alle Methoden sperren RESTRICTED-Nutzer aus
@PreAuthorize("not hasRole('RESTRICTED')")
public class SuborderController { ... }

// Methode: schreibende Operationen erfordern MANAGER
@PreAuthorize("hasRole('MANAGER')")
public String store(...) { ... }

// Gesamter Controller: nur BACKOFFICE+
@PreAuthorize("hasRole('BACKOFFICE')")
public class InvoiceController { ... }
```

Spring Authorities: `ROLE_USER`, `ROLE_RESTRICTED`, `ROLE_BACKOFFICE`, `ROLE_MANAGER`, `ROLE_ADMIN`.

---

## Ebene 2: Service-Boundary (`@Authorized` + AOP)

Die Annotation `@Authorized` (`auth/domain/Authorized.java`) markiert Services oder einzelne Methoden. `AuthorizationAspect` (`auth/service/AuthorizationAspect.java`) interceptiert alle so markierten Methoden via AOP und prüft den `AuthorizedUser`-Bean (Session-scoped).

```java
@Authorized                          // Klasse: Authentifizierung erforderlich
public class EmployeeorderService {

    @Authorized(requiresManager = true)   // Methode: überschreibt Klassenannotation
    public void create(...) { ... }

    @Authorized(permitAll = true)         // Jobs dürfen ohne User-Session aufrufen
    public void runJob(...) { ... }
}
```

Prüfreihenfolge im Aspect:

```
permitAll?           → sofort durchlassen
requiresAuthentication && !authenticated → AA-0001
requireUnrestricted  && restricted       → AA-0002
requiresBackoffice   && !backoffice      → AA-0003
requiresManager      && !manager         → AA-0004
requiresAdmin        && !admin           → AA-0005
```

`AuthorizedUser` (`auth/domain/AuthorizedUser.java`) ist ein session-scoped Bean, das nach dem Login befüllt wird und `isManager()`, `isAdmin()`, `isBackoffice()`, `isRestricted()` sowie den effektiven Login-Sign (inkl. Impersonation) bereitstellt.

---

## Ebene 3: Feingranulare Runtime-Guards (business-kontextsensitiv)

Für Operationen, bei denen Rolle allein nicht ausreicht (z. B. „Mitarbeiter darf nur eigene Zeitberichte bearbeiten"), enthält die Service-Methode explizite Guards:

```java
if (!authorizedUser.isManager()
        && !employee.equals(authorizedUser.getEffectiveLoginSign())
        && !authService.isAuthorizedAnyObject(grantorSign, CATEGORY, today(), WRITE)) {
    throw new AuthorizationException(WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER);
}
```

Datenbankgestützte Regeln (`AuthorizationRule`-Entity) erlauben granulare Vergabe von Zugriff auf bestimmte Kategorien, Objekte und Zeiträume mit hierarchischen `AccessLevel`-Werten (`DELETE ⊇ WRITE ⊇ READ ⊇ EXECUTE`).

---

## Fehlerbehandlung

Alle Autorisierungsfehler werfen `AuthorizationException` (Subklasse von `ErrorCodeException`) mit einem `AA-*`-Fehlercode:

| Code    | Bedeutung |
|---------|-----------|
| AA-0001 | Nicht authentifiziert |
| AA-0002 | Unrestricted-Zugriff erforderlich |
| AA-0003 | Backoffice-Rolle erforderlich |
| AA-0004 | Manager-Rolle erforderlich |
| AA-0005 | Admin-Rolle erforderlich |
| AA-9999 | Generisch nicht autorisiert |

---

## Sicherheitskonfiguration

Zwei Profile mit identischer Rollenlogik, aber unterschiedlicher Authentifizierung:

* **`local`** (`LocalDevSecurityConfiguration`): Pre-Authenticated Filter mit `login-name`-Parameter
* **`production` / `staging`** (`AzureEasyAuthSecurityConfiguration`): JWT/OAuth2 via Azure EasyAuth

Beide Konfigurationen bestehen aus drei `SecurityFilterChain`-Beans (statische Ressourcen / REST-API stateless / Web-UI session-basiert).

---

## Beteiligte Klassen

| Klasse | Paket | Rolle |
|--------|-------|-------|
| `AuthorizedUser` | `auth/domain` | Session-scoped Bean, zentrale Berechtigungsquelle |
| `Authorized` | `auth/domain` | Service-Layer-Annotation |
| `AuthorizationAspect` | `auth/service` | AOP-Enforcement für `@Authorized` |
| `AuthorizationRule` | `auth/domain` | JPA-Entity für datenbankgestützte Feinregeln |
| `AuthService` | `auth/service` | Verwaltung und Cache der Autorisierungsregeln |
| `AccessLevel` | `auth/domain` | Hierarchische Zugriffslevels |
| `LocalDevSecurityConfiguration` | `auth/configuration` | Spring Security (lokal) |
| `AzureEasyAuthSecurityConfiguration` | `auth/configuration` | Spring Security (Produktion) |
