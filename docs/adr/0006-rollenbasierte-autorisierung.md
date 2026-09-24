# ADR-0006 Rollenbasierte Autorisierung mit zwei Durchsetzungsebenen

Date: 2026-05-24
Status: Accepted

> **Nachtrag 2026-09-18:** Die beschreibenden Abschnitte wurden an den Code angeglichen. Geändert
> hat sich seit 2026-05-24: `AuthorizedUser` ist `@RequestScope` statt session-scoped, es gibt die
> Rolle `PEOPLE_LEAD` mit eigenem Fehlercode `AA-0006`, und alle Filter-Chains sind `STATELESS`.
> Die Entscheidung selbst — zwei gestapelte Durchsetzungsebenen — steht unverändert.
>
> **Nachtrag 2026-09-22 (#926):** Ebene 1 wird nicht mehr mit `@PreAuthorize` ausgedrückt, sondern
> mit demselben `@Authorized(requires…)` wie Ebene 2. Die Entscheidung — zwei gestapelte Ebenen —
> steht weiterhin; was fällt, ist die zweite Ausdrucksform für dieselbe Aussage. Siehe „Ebene 1"
> unten für die Begründung und für die eine Verhaltensänderung, die daraus folgt.

## Context and Problem Statement

Die Anwendung verwaltet sensible Daten (Zeitberichte, Verträge, Rechnungen) und muss sicherstellen, dass Benutzer nur auf die für ihre Rolle erlaubten Operationen zugreifen können. Es gibt klar unterscheidbare Rollen mit hierarchischen Rechten. Die Herausforderung: Wie werden diese Rechte zuverlässig durchgesetzt — auch wenn ein Controller-Aufruf umgangen wird oder ein Service direkt aus einem Job oder einem anderen Service aufgerufen wird?

## Considered Options

* Nur HTTP-Boundary-Checks (Annotation auf Controllern)
* Nur Service-Layer-Checks (manuelle `if`-Guards in jedem Service)
* Zwei gestapelte Ebenen: HTTP-Boundary + Service-Boundary via AOP

## Decision Outcome

Chosen: **Zwei gestapelte Ebenen** (Defense in Depth), weil ein einzelner Check an der HTTP-Grenze nicht greift, wenn Services direkt aufgerufen werden (Scheduled Jobs, interne Aufrufe), und manuelle Guards in jedem Service fehleranfällig und inkonsistent wären.

### Consequences

* Good: ein vergessener Guard auf einem Controller führt nicht automatisch zu einer Sicherheitslücke — der Service-Layer fängt es auf
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

Scheduled Jobs laufen mit synthetischen Rechten: `manager = true`, `peopleLead = true`,
`backoffice = true`, `admin = false`, `loginSign = "SYSTEM"`. Diese Rechte entstehen nicht von
selbst — der Job muss `authorizedUser.initForJob()` aufrufen. Ohne diesen Aufruf gibt es in einem
asynchronen Kontext keinen `SecurityContext` und damit keine Authentifizierung
(`ScheduledReportJobScheduler`, `BudgetAlertScheduler` zeigen das Muster).

---

## Ebene 1: HTTP-Boundary (`@Authorized` auf Controllern)

Der Controller verlangt seine Berechtigung mit derselben Annotation wie der Service (#926):

```java
// Klasse: alle Methoden sperren RESTRICTED-Nutzer aus
@Authorized(requireUnrestricted = true)
public class SuborderController { ... }

// Methode: schreibende Operationen erfordern die Geschäftsführung
@Authorized(requiresManager = true)
public String store(...) { ... }

// Gesamter Controller: nur Backoffice und darüber
@Authorized(requiresBackoffice = true)
public class InvoiceController { ... }
```

Bis #926 stand hier `@PreAuthorize("hasRole(…)")`. Beide Formen prüfen vor derselben Methode und
beide sind Proxy-basiert; **dieselbe Frage beantworten sie aber nicht**:

- `hasRole` liest die Authorities aus dem `SecurityContext`. Die entstehen bei der Anmeldung aus
  dem Mitarbeiterstatus (`EmployeeStatusAuthorities.from(status)`) und ändern sich innerhalb einer
  Anmeldung nicht.
- `@Authorized` fragt `AuthorizedUser`, und der schlägt die **übernommene** Anmeldung im `UiState`
  nach.

Während einer Impersonation antworteten die beiden Ebenen deshalb verschieden: der Controller ließ
durch, was der Service danach ablehnte. Wer eine fremde Anmeldung übernimmt, sieht die Anwendung
seitdem so, wie die übernommene Person sie sieht — die gewollte Bedeutung von Impersonation, und
die einzige Verhaltensänderung aus #926. Wer eine Impersonation nach `AuthorizationRule` mit
`AccessLevel.LOGIN` vergibt, vergibt damit auch deren Rechte an der HTTP-Grenze; an Ebene 2 war das
schon immer so.

Was aus der Umstellung sonst noch folgt:

- **Die Antwort auf eine fehlende Berechtigung entsteht jetzt an einer Stelle.** Die
  `AuthorizationException` des Aspekts ist keine `AccessDeniedException`; ohne Behandlung kommt sie
  als `500` heraus. Das beantwortet `AuthorizationExceptionHandler` (`common/web`) mit `403` —
  bzw. `401` bei `AA-0001` — und für `/api` und `/rest` als `ProblemDetail`.
- **Es gibt keine Rollenausdrücke mehr im Java-Code.** `ArchitectureTest` weist `@PreAuthorize` an
  Klassen und Methoden zurück. Die Schalter sind `requiresAuthentication`, `requireUnrestricted`,
  `requiresBackoffice`, `requiresPeopleLead`, `requiresManager`, `requiresAdmin`, `permitAll`.
- **Eine Oder-Verknüpfung gibt es nicht** — und sie fehlte auch nicht: `hasAnyRole('MANAGER',
  'PEOPLE_LEAD')` war `requiresPeopleLead`, weil die Rollen kumulativ sind (siehe Hierarchie oben).
  Wo eine echte Oder-Bedingung entsteht, gehört sie als Runtime-Guard in die Ebene 3 und nicht in
  einen Ausdruck.
- `@EnableMethodSecurity` bleibt auf `SalatApplication` aktiviert. Es setzt nichts mehr durch,
  fängt aber ein versehentlich stehengelassenes `@PreAuthorize` weiter ab, solange
  `ArchitectureTest` es noch nicht gesehen hat.

Die Spring Authorities (`ROLE_USER`, `ROLE_RESTRICTED`, `ROLE_BACKOFFICE`, `ROLE_PEOPLE_LEAD`,
`ROLE_MANAGER`, `ROLE_ADMIN`) bleiben, was sie sind: die Sicherheitsketten arbeiten damit, und die
Templates fragen sie über `#authorization.expression(…)`. **Die Templates sind damit weiterhin
blind für die Impersonation** — ein Menüeintrag kann sichtbar sein, obwohl der Controller dahinter
abweist. Das war vor #926 genauso und ist als eigener Schritt zu beheben.

---

## Ebene 2: Service-Boundary (`@Authorized` + AOP)

Die Annotation `@Authorized` (`auth/domain/Authorized.java`) markiert Services oder einzelne Methoden. `AuthorizationAspect` (`auth/service/AuthorizationAspect.java`) interceptiert alle so markierten Methoden via AOP und prüft den `AuthorizedUser`-Bean (request-scoped).

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
requiresPeopleLead   && !peopleLead      → AA-0006
requiresManager      && !manager         → AA-0004
requiresAdmin        && !admin           → AA-0005
```

Methodenannotation ersetzt Klassenannotation vollständig, sie ergänzt sie nicht: steht an der
Methode ein `@Authorized`, wird die Klassenannotation gar nicht mehr gelesen.

`AuthorizedUser` (`auth/domain/AuthorizedUser.java`) ist ein **request-scoped** Bean. Es hält
keinen eigenen Zustand, sondern liest pro Request aus dem `SecurityContext` und stellt
`isManager()`, `isAdmin()`, `isPeopleLead()`, `isBackoffice()`, `isRestricted()` sowie den
effektiven Login-Sign bereit. Die Impersonation ist kein Bean-Zustand: der vertretene Login-Sign
steht im `UiState` (→ ADR-0014, Keys in `AuthUiStateKeyContributor`) und wird bei jeder Abfrage
dort nachgeschlagen. Der einzige Fall mit eigenem Zustand ist der Job-Modus (`initForJob()`), weil
es dort keinen `SecurityContext` gibt.

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

### Eine Regel nennt zwei Dinge: wer, und woran (#1089)

Eine Regel besteht aus Kategorie, **Berechtigtem** (`grantee_id`), **Objekt** (`object_id`),
Zugriffsstufen und Gültigkeit. Ein drittes Feld, den Gewährenden (`grantor_id`), gab es bis #1089.
In vier von fünf Kategorien, die ihn füllten, war er genau das, worauf Zugriff gewährt wird — das
Objektfeld blieb dort leer, und zwei Felder standen für dieselbe Rolle. Er ist im Objekt
aufgegangen; `EMPLOYEE`, `RELEASE_TIMEREPORTS`, `ACCEPT_TIMEREPORTS` und `WORKINGDAY` tragen das
Kürzel der betroffenen Person seitdem dort.

### Der Platzhalter `*` (#1087)

Beide Felder kennen `*` als „alle". Eine Auswertung wird damit für alle freigegeben, ohne eine
Kürzelliste zu pflegen: `REPORT_DEFINITION` / `grantee_id = '*'` / `object_id = <id>`.

Die **leere** Menge bedeutet dagegen nicht bei beiden dasselbe, und das ist Absicht:

| Feld | `*` | leer |
|---|---|---|
| `grantee_id` | jeder angemeldete Benutzer | **niemand** — die Regel greift nie |
| `object_id` | jedes Objekt der Kategorie | jedes Objekt der Kategorie |

Beim Berechtigten darf das Weglassen nicht alle berechtigen — sonst wäre ein vergessenes Feld eine
Freigabe. Der Platzhalter wird hingeschrieben, sonst gilt er nicht. Und `*` schließt `RESTRICTED`
ein: Externe und Praktikanten sind angemeldete Benutzer. Wer das nicht will, vergibt weiter
einzelne Kürzel.

Bis #1087 wertete `AuthService` den Platzhalter beim Berechtigten nur in einer von vier Prüfungen
aus — eine Regel mit `grantee_id = '*'` war für Auswertungen und ETL-Definitionen wirkungslos.
Seitdem steht der Vergleich einmal in `matchesGrantee`, und jede Prüfung geht dort hindurch.

### Zwei Achsen in einem Objektwert: `TIMEREPORT` (#1089)

`TIMEREPORT` ist die einzige Kategorie, die zwei Dinge zugleich einschränkt: **wessen** Buchungen
und **auf welchem Auftrag**. Beides steht im einen Objekt, getrennt durch einen Doppelpunkt:

| Objektwert | Bedeutung |
|---|---|
| `1453`, `1453/01` | Buchungen aller Personen auf diesem Auftrag |
| `xx:1453`, `xx:1453/01` | dort nur die Buchungen von `xx` |
| `xx:*` | die Buchungen von `xx`, auf jedem Auftrag |

**`AuthService` zerlegt dabei nichts.** Es vergleicht weiterhin ganze Werte auf Gleichheit; die
Formen baut der Aufrufer, und `TimereportAuthorization` fragt mit allen fünf zugleich. Dadurch
bleiben beide Achsen samt Platzhaltern erhalten, ohne dass die Regelauswertung Muster auswerten oder
ein Format kennen müsste — der Preis dafür ist, dass dieses Format nur in dieser einen Kategorie
etwas bedeutet.

Zwei Grenzen, die dabei zu kennen sind:

- **Gelesen wird am ersten Doppelpunkt.** Auftragszeichen dürfen einen enthalten und tun es
  vereinzelt, Kürzel nicht — erzwungen wird das von keinem Constraint.
- **`xx:*` wirkt nur, weil der Aufrufer es mitfragt.** In einer anderen Kategorie hingeschrieben
  ergibt die Schreibweise eine Regel, die nie greift.

Die Prüf-API besteht seitdem aus drei Methoden: `isAuthorized`, `isAuthorizedAnyObject` und
`isAuthorizedForOwnLogin`. Die letzte fragt die **echte** Anmeldung statt der übernommenen und ist
allein für `LOGIN` da: wer eine fremde Anmeldung übernommen hat, soll sich damit nicht die nächste
Übernahme genehmigen.

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
| AA-0006 | People-Lead-Rolle erforderlich |
| AA-9999 | Generisch nicht autorisiert |

---

## Sicherheitskonfiguration

Zwei Profile mit identischer Rollenlogik, aber unterschiedlicher Authentifizierung:

* **`local`** (`LocalDevSecurityConfiguration`): Pre-Authenticated Filter mit `login-name`-Parameter
* **`production` / `staging` / `localeasyauth`** (`AzureEasyAuthSecurityConfiguration`): JWT/OAuth2
  via Azure EasyAuth — warum die Authentifizierung dort und nicht in der Anwendung liegt, steht in
  ADR-0026

Beide Konfigurationen bestehen aus drei `SecurityFilterChain`-Beans (statische Ressourcen /
REST-API / Web-UI). **Alle drei sind `STATELESS`**, auch die der Web-UI; es gibt keine
sitzungsbasierte Chain. Die Sitzung hält in den deployten Umgebungen EasyAuth, lokal ersetzt der
`login-name`-Parameter sie.

---

## Beteiligte Klassen

| Klasse | Paket | Rolle |
|--------|-------|-------|
| `AuthorizedUser` | `auth/domain` | Request-scoped Bean, zentrale Berechtigungsquelle |
| `EmployeeStatusAuthorities` | `auth/domain` | Abbildung `loginStatus` → Spring-Authorities |
| `Authorized` | `auth/domain` | Annotation für beide Durchsetzungsebenen |
| `AuthorizationAspect` | `auth/service` | AOP-Enforcement für `@Authorized` |
| `AuthorizationExceptionHandler` | `common/web` | `AuthorizationException` → 403 bzw. `ProblemDetail` |
| `AuthorizationRule` | `auth/domain` | JPA-Entity für datenbankgestützte Feinregeln |
| `AuthService` | `auth/service` | Verwaltung und Cache der Autorisierungsregeln |
| `AccessLevel` | `auth/domain` | Hierarchische Zugriffslevels |
| `LocalDevSecurityConfiguration` | `auth/configuration` | Spring Security (lokal) |
| `AzureEasyAuthSecurityConfiguration` | `auth/configuration` | Spring Security (Produktion) |
