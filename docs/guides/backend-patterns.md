# Code-Muster

Teil der Entwicklungsregeln: Kern und Verweistabelle stehen in [AGENTS.md](../../AGENTS.md), was hier
steht, gilt ebenso verbindlich. Lesen, bevor Java-Code in `domain`, `persistence`, `service`,
`controller`, `rest` oder `listener`, ein Changeset oder ein Message-Bundle geändert wird.

---

## Entity Pattern
- All JPA entities extend `AuditedEntity` (`common/domain/AuditedEntity.java`)
- `AuditedEntity` provides: `@Id @GeneratedValue(IDENTITY)`, Spring Data audit fields (`created`, `lastupdate`, `createdby`, `lastupdatedby`), optimistic locking via `@Version updatecounter`, `equals`/`hashCode` by ID
- Standard entity annotations: `@Entity`, `@Getter @Setter` (Lombok), `@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)`
- Boolean columns in the database must be `bit(1)` — Hibernate maps `Boolean` to `bit`, not `tinyint`. In Liquibase migrations always use `type: bit(1)`.
- **A person is referenced by `employee_id`, never by their sign** (#968). The sign changes — a
  correction, an anonymization — and nothing makes it unique. No sign column stands next to the id:
  views, ETL definitions and reports join on the id as well (#1202).
- **The same holds for customer orders and suborders** (#1205): a record refers to them by
  `customerorder_id` / `suborder_id` with a foreign key, never by sign — signs are renamed, and a
  suborder can be moved to another parent. No sign column stands next to the ids, in the budget
  module (#1321, #1359) as little as in the JIRA tables (#1372). A subtree is decided by the path of
  suborder ids (`OrderPosition`), not by a sign prefix. The exception is a `LIKE` pattern over
  complete order signs (`OrderPricing.suborderSign`, → `OrderPricingLookup`): that is a pattern,
  not a reference.
- **A cost category is referenced by id** (#1209): rate periods (`EmployeeCost`) and assignments
  (`EmployeeCostAssignment`) point at `CostCategory` by foreign key, and a rename changes that one
  row. The name lives on the category alone (#1345); `getName()` and `getEmployeeCostName()` read it
  from there.
- **Signs stay changeable** (#1206, → ADR-0034). Saving a renamed order, or a renamed or moved
  suborder, publishes `SignsRenamedEvent` with the old and the new complete sign. Whatever still
  names the order tree by sign listens to it and follows — or adds a notice that the success
  message shows (`NoticeViewHelper`). A new place that stores an order or suborder sign as more than
  a mirror has to do the same.
  **Exception — special orders** (#1341, → ADR-0035): the vacation order, its suborders without a
  calculated entitlement and the suborders of the regular training are named by sign in
  `application.yaml` (`salat.vacation`, `salat.training`). `SpecialOrders` resolves them into ids at
  start and answers by id; their signs, and those of every order and suborder above them, are locked
  (CO-0007, SO-0007). No code recognizes a role by comparing a sign — ask `SpecialOrders`.
- **The complete order sign is stored** (#1342): `suborder.complete_order_sign`
  (`Suborder#getCompleteOrderSign()`, `ORDER/01/02`, without a trailing slash) is the one source of the
  complete sign — for Java, JPQL, views, reports and ETL definitions alike. Nothing rebuilds it from the
  parent chain. Only the order module writes it (`Suborder#deriveCompleteOrderSign`): on creating a
  suborder, on renaming or moving one for its whole branch, and on renaming the order, in the same
  transaction. A `LIKE` pattern that binds with a trailing slash compares against
  `completeOrderSign + "/"` or `CONCAT(complete_order_sign, '/')` — the slash belongs to the comparison,
  not to the data.

---

## Criteria-Abfragen über den EntityManager (#1092)

Eine einzige Stelle baut ihre Abfragen über den `EntityManager` statt über ein Spring-Data-Repository:
`TimereportListDAO`. Dieselbe zur Laufzeit gebaute Bedingung aus **Filter und Sichtbarkeit** muss dort
drei Dinge beantworten, und nur das erste davon kann ein Repository:

| | über ein Repository? |
|---|---|
| Die Zeilen | **ja** — `JpaSpecificationExecutor.findAll(Specification, Pageable)` kann dynamisches Prädikat, Sortierung und Obergrenze |
| Die Summen über alle Treffer (`count`, `sum`, `sum(case …)`, zweimal `count(distinct)`) | **nein** — `Specification` selektiert immer die Entität; `JpaSpecificationExecutor` kennt nur `count(Specification)`, keine eigenen Aggregate |
| Die Werte der Filter (eine `distinct`-Projektion unter derselben Sichtbarkeit, über `employeeorder` statt `timereport`, #1127) | **nein** — die Fluent-API von `findBy` projiziert auf Entitäten, nicht auf einzelne Spalten, und kennt kein `distinct` |

Die Zeilenabfrage bleibt trotzdem hier, statt als einzige ins Repository zu wandern: sonst stünde
dieselbe Bedingung zweimal im Code, einmal als `Specification` und einmal als `Predicate`. Ein
Sicherheitsprädikat an zwei Stellen zu pflegen ist ein Fehler, der beim ersten Auseinanderdriften
Daten freigibt.

Wer eine zweite solche Stelle anlegt, begründet sie genauso — für alles andere bleibt es bei
Repository und `Specification`.

---

## Repository-Zugriff in Services (→ ADR-0019)
- Spring Data repositories extend `PagingAndSortingRepository<E, Long>` and `CrudRepository<E, Long>`; custom queries use `@Query` with multiline JPQL text blocks
- **Neue Module**: Services verwenden das Repository direkt — kein DAO-Wrapper. (→ ADR-0019)
- **Legacy-Module** (Struts-Ära): DAOs (`@Component`, `@RequiredArgsConstructor`) bleiben bestehen und werden nicht aktiv migriert. Neue Features in Legacy-Modulen können den bestehenden DAO erweitern, müssen es aber nicht.
- Sort fields are referenced via the JPA metamodel class (e.g. `Customer_.NAME`) to avoid string literals

---

## Service Pattern
- Class annotations: `@Service @RequiredArgsConstructor @Transactional @Authorized`
- Read-only queries: annotate the method with `@Transactional(readOnly = true)`
- **No repository writes under `@Transactional(propagation = NOT_SUPPORTED)` inside a request** (#1282).
  With Open Session in View the empty scope splits the work across EntityManagers. Once a read
  without its own transaction has run, reads come from the request's cached EntityManager, while
  every `save` opens a fresh one. An entity written and then read and written again fails the
  version check. Work that waits for a foreign system and writes entities on the way runs as a job on
  an executor of its own (`JiraReplicationLauncher`, `ETLRunLauncher`).
- **The role is checked by `@Authorized` alone** (#1256, → ADR-0006): `@Authorized(requiresManager = true)`
  on the method, or on the class when most of the service is management only — then every method
  open to any login says so with a plain `@Authorized` (`EmployeecontractService`: the reads, and the
  release data a person writes when releasing their own bookings). A method-level annotation
  replaces the class-level one.
- **No `if (!authorizedUser.isManager()) throw …` that merely repeats the annotation.** A check in the
  method body belongs where the role alone does not answer the question — the person's own data, a
  supervisor, an `AuthorizationRule` (ADR-0006, level 3) — or where a method does something
  *additional* depending on the role.
- **A public service method does not call another public method of the same service.** An internal
  call (`this.x()`) bypasses the aspect, so the callee's `@Authorized` is not checked there. Shared
  logic goes into a private method or straight to the repository/DAO. Where one public method does
  call another, the caller's `@Authorized` demands at least what the callee's demands — the
  roles are cumulative, so `requiresManager` covers `requiresBackoffice`, `requiresPeopleLead`,
  `requireUnrestricted` and a plain `@Authorized`, and `requiresAdmin` covers `requiresManager`.
  A manager-only method may read through a method open to every login; the reverse is the hole.
  That includes `@EventListener` methods, which the aspect intercepts as well.
- Services throw typed `ErrorCodeException` subclasses (`InvalidDataException`, `BusinessRuleException`, `AuthorizationException`) — never a raw `RuntimeException` for business errors
- Before destructive DB operations, publish a domain event via `ApplicationEventPublisher`; catch `VetoedException` and re-throw with added context (see Event / Veto Pattern)

---

## Controller Pattern
- Class annotations: `@Controller @RequestMapping(“/path”) @RequiredArgsConstructor @Authorized(requireUnrestricted = true)`
  - **`@Authorized` without arguments only means *authenticated*** — it does **not** exclude
    `RESTRICTED`. Leaving out `requireUnrestricted = true` opens the exact hole that #919 had to
    close: every authenticated user, including externals and interns, could reach the views by
    knowing the URL. Spell the requirement out on every controller.
  - The available switches are `requiresAuthentication` (default `true`), `requireUnrestricted`,
    `requiresBackoffice`, `requiresPeopleLead`, `requiresManager`, `requiresAdmin`, `permitAll`.
- Write operations get `@Authorized(requiresManager = true)` on the method
- **`@PreAuthorize` does not appear in this codebase** (#926, → ADR-0006). `ArchitectureTest`
  rejects `@PreAuthorize` on a class or a method.
  - There is no *or*: `hasAnyRole('MANAGER','PEOPLE_LEAD')` was `requiresPeopleLead` all along,
    because the roles are cumulative. A genuine either/or is a runtime guard in the service, not an
    expression.
  - A method-level `@Authorized` **replaces** the class-level one, it does not add to it. That is
    safe for `requiresManager` on a `requireUnrestricted` class — a status is one value, so a
    manager is never restricted — but it is not a general licence to weaken.
- Filter persistence goes through `UiState` (→ ADR-0022), not through the session and not through a
  `containsKey` check on the request: the filter remembers a registered `f…` parameter and supplies
  it again as a fallback, so a controller only declares `@RequestParam(required = false) Long
  fBudgetCustomerOrderId`. Where a submit must be told apart from a mere page call — because it triggers
  something expensive — that hangs on a hidden field of the form (`evaluate`), never on whether the
  filter parameter is present (#1009)
- Redirect-After-Post: successful writes return `”redirect:/...”` with `redirectAttributes.addFlashAttribute(“toastSuccess”, ...)`
- Form validation errors: call the service inside `try/catch(ErrorCodeException)`, convert via `ErrorCodeViewHelper.toViewMessages(ex)`, add to model, and re-render the form view (do not redirect)
- HTMX partial updates: use `th:hx-post`, `hx-swap=”none”`, `hx-include=”closest form”`, `hx-trigger=”change”` on select/input elements; detect `HX-Request` header in the controller and return `”view :: fragmentName”` for partial responses
- CSRF tokens: **never** add an explicit `<input type=”hidden” th:name=”${_csrf.parameterName}” th:value=”${_csrf.token}” />`. Spring Security 6 uses deferred tokens — `_csrf` is null when accessed directly in templates. Using `th:action=”@{...}”` is sufficient; Thymeleaf's `CsrfRequestDataValueProcessor` injects the token automatically into every POST form.
- **UiState parameters are named `f…`** (→ ADR-0022): see [ui](ui.md), „UiState: Filter Parameters Carry the Prefix `f`“, before registering a key.
- **No direct `HttpSession` access** (→ ADR-0013): new Spring MVC controllers must not read or write `HttpSession` for UI state. Pass state via URL parameters, path variables, or form fields. User selection state (e.g. currently selected employee contract) must be expressed in the URL or stored in a cookie — not the session. The rule has no exceptions left (ADR-0013, Nachtrag 2026-09-18); a new one must be documented with a comment `// ADR-0013: Ausnahme — [Begründung]` at the point of use.

---

## DTO Pattern
- Class annotations: `@Builder @Data @Jacksonized @AllArgsConstructor`
- Static factory method `from(Entity)` maps entity → DTO (include all audit fields)
- Mutator `copyTo(Entity)` applies mutable fields back to the entity; never overwrite `id` or audit fields
- `isNew()` helper returns `id == null`
- Modern modules use Java `record` for simple, immutable DTOs
- When writing a `Boolean` from DTO to entity, guard against null: `Boolean.TRUE.equals(value)`

---

## Event / Veto / Command Pattern
- Event hierarchy: `VetoableEvent` → `DomainObjectDeleteEvent` / `DomainObjectUpdateEvent` (in `common/event/`)
- Module-specific events (e.g. `CustomerDeleteEvent`) extend the appropriate base, passing the entity ID to the constructor
- Service publishes the event before the destructive DB call, wrapped in `try/catch(VetoedException)`; on veto it prepends its own context message and re-throws
- Listeners are annotated with `@EventListener` and throw `VetoedException` to block the operation
- Cross-module side-effects and integrity checks must flow through events — never direct service-to-service calls across module boundaries

**Command events — reading data across a forbidden boundary (last resort)**

A plain event carries no answer back. When a module needs *data* owned by another module and the
import direction is forbidden, a `CommandEvent<T>` (`common/command/`) carries the result:

```java
var command = GetTimereportMinutesCommandEvent.builder().orderType(SUB).orderIds(suborderIds).build();
commandPublisher.publish(command);
return command.getResult().values().stream().reduce(Duration::plus).orElse(Duration.ZERO);
```

- The event class belongs to the **asking** module, in its `command` sub-package; the owning module
  answers it in an `@EventListener` that calls `setResult(...)`.
- **Use this only as a last resort.** First check whether the dependency may simply run in the
  allowed direction (`dailyreport` imports `order` freely — only the reverse is forbidden), or
  whether the logic belongs in the other module altogether. A command event is a synchronous call
  in disguise; it removes the import, not the coupling.

---

## Exception Hierarchy
```
RuntimeException
├── TechnicalException                — unexpected system errors
└── ErrorCodeException (abstract)     — all business/auth errors carry an ErrorCode
    ├── AuthorizationException        — AA-* codes
    ├── BusinessRuleException         — domain rule violations
    ├── InvalidDataException          — entity not found, bad input
    └── VetoedException               — carries the VetoableEvent with listener messages
```
- All business and authorization errors **must** use a typed `ErrorCodeException` subclass — never throw a raw `RuntimeException` for business errors.
- Choose the subclass by intent:
  - `InvalidDataException` — bad input, entity not found, precondition on data not met
  - `BusinessRuleException` — domain rule violated (e.g. overlapping dates, budget exceeded)
  - `AuthorizationException` — caller lacks the required role or ownership
  - `VetoedException` — raised by an event listener to block a destructive operation
- Error codes are defined in `ErrorCode` enum (`common/exception/ErrorCode.java`).
  - Format: two-letter module prefix + four-digit number, e.g. `CU-0001` for customer.
  - Module prefixes in use: `AA` (auth), `CO` (customer order), `CU` (customer), `EC` (employee contract), `EM` (employee), `EO` (employee order), `SO` (suborder), `TR` (time report), `RL` (release), `WD` (working day), `FA` (favorites), `ETL`, `SE` (secret), `XX` (generic).
  - When adding a new error code, append it to the enum; never reuse or renumber existing codes.
- `ServiceFeedbackMessage` (`common/exception/ServiceFeedbackMessage.java`) wraps an `ErrorCode` + severity + optional positional arguments (`{0}`, `{1}`, …); used to accumulate messages when building veto responses.
  - Factory methods: `ServiceFeedbackMessage.error(errorCode, args…)` / `.warning(…)`.

---

## Error Reporting to the User
Errors surface through two distinct UI patterns depending on whether the operation redirects or re-renders:

**Redirect flows (delete, anonymize, and other non-form POSTs)**
```java
// controller
try {
    service.doSomething(id);
    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("form.xyz.message.done", "Done"));
    return "redirect:/list";
} catch (ErrorCodeException ex) {
    redirectAttributes.addFlashAttribute("toastError",
        errorCodeViewHelper.toViewMessages(ex).stream()
            .map(Object::toString).findFirst().orElse("Error"));
    return "redirect:/back";
}
```
- `toastSuccess` / `toastError` flash attributes are rendered as dismissible toast notifications by `layout/base.html`.
- `ErrorCodeViewHelper.toViewMessages(ex)` translates each `ServiceFeedbackMessage` inside the exception into a `ViewMessage` by looking up the i18n key `errorcode.<prefix>.<number>` (e.g. `EM-0002` → `errorcode.em.0002`) and formatting it with the stored arguments.
- **Every `ErrorCode` must therefore have a matching `errorcode.*` key in both message bundles.**

**Form re-render flows (create/edit forms with validation)**
```java
// controller
try {
    service.save(entity);
} catch (ErrorCodeException ex) {
    model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
    // fall through to re-render the form
}
```
- The `errors` model attribute is a `List<ErrorCodeViewHelper.ViewMessage>`; templates iterate over it and display `viewMessage.resolved()`.
- Spring MVC `BindingResult` validation errors (field-level) are separate: they use `bindingResult.rejectValue(…)` and are rendered via `th:errors` on the form fields.

---

## Security Layers
Two stacked layers provide defence in depth — **both spelled `@Authorized`** (#926, → ADR-0006):
- **HTTP boundary** (`@Authorized` on the controller class, and on a method where it differs):
  enforced by `AuthorizationAspect` before the handler method runs
- **Service boundary** (`@Authorized` on the service class or method): enforced by the same aspect on
  every call through the Spring proxy, regardless of the caller. A runtime guard in the method body
  only for what the role alone does not answer (see Service Pattern)
- **The answer to a denial comes from one place**: `AuthorizationException` is a plain
  `RuntimeException`, so nothing in Spring Security sees it — unhandled it leaves the
  `DispatcherServlet` as a **500**. `AuthorizationExceptionHandler` (`common/web`) answers it with
  `403` (`401` for `AA-0001`), as a `ProblemDetail` under `/api` and `/rest` and otherwise through
  `sendError` so the application's own error page renders. Whoever adds a second place where an
  authorization decision is made has to answer this question again.
- `AuthorizedUser` (**request-scoped** bean, `auth/domain/AuthorizedUser.java`): exposes `isManager()`, `isAdmin()`, `isPeopleLead()`, `isBackoffice()`, `isRestricted()`, and the current login sign. It holds no state of its own — it reads the `SecurityContext` per request. A scheduled job has no `SecurityContext`, so it must call `authorizedUser.initForJob()` first (→ ADR-0006).
  In job mode the user is authenticated **and** manager, people lead and backoffice — not admin, not
  restricted. A job path therefore never needs a weaker `@Authorized` than the manual way into the
  same method; a method-level annotation that lowers the class level for a job is a mistake (#1282).
  A test that mocks `AuthorizedUser` for a job path takes its roles from `initForJob`.
- Spring Security roles: `USER`, `RESTRICTED`, `BACKOFFICE`, `PEOPLE_LEAD`, `MANAGER`, `ADMIN`; `manager` role includes admins; `backoffice` includes managers and admins; `people_lead` includes managers and admins
- Role semantics (derived from `SalatUser.status` at login):
  - `USER` — base role granted to every authenticated user = every employee
  - `RESTRICTED` — external employees (contractors) and interns (`status=restricted`); heavily limited access, cannot access most list/management views
  - `BACKOFFICE` — Backoffice (`status=bo`) plus everyone with `MANAGER`; can create invoices and upload financial data from books and records
  - `PEOPLE_LEAD` — people leads/supervisors (`status=pv`) plus everyone with `MANAGER`; can read time reports and contracts of their team members, run reports
  - `MANAGER` — general manager (`status=bl`) plus `ADMIN`; can manage contracts, orders, and time reports for everyone
  - `ADMIN` — system administrators (`status=adm`); full access; only role that is NOT an employee
  - A "regular employee" is someone with `BACKOFFICE` but not `PEOPLE_LEAD` or `MANAGER` — they can only view their own data.
- **Writing follows the period the day lies in** (#1164), for bookings and for the working day
  (start, break, „Nicht gearbeitet") alike: open — the person and `MANAGER`; released — `MANAGER`
  and the supervising `PEOPLE_LEAD`, never the person themselves; accepted — only `ADMIN`, everyone
  else reopens the period first. The rule lives in `TimereportAuthorization` with `ReportPeriod`;
  a view offers creating or editing only where that rule lets the save through, decided per day.

---

## Ein Ereignis-Listener entscheidet über keinen HTTP-Status (#1054)

Die Filterketten sind zustandslos (`SessionCreationPolicy.STATELESS`), die Authentifizierung gelingt
also bei **jeder** Anfrage neu — und damit auch in der Weiterleitung des Servlet-Containers auf
`/error`. Ein `@EventListener` auf `AuthorizedUserChangedEvent` läuft deshalb zweimal je Fehlerfall.
Wer dort eine `ResponseStatusException` wirft, nimmt die Fehlerseite mit: Tomcat bricht die
Bearbeitung der Fehlerseite ab (`Exception Processing [ErrorPage[…]]`) und antwortet mit seiner
eigenen 500-Seite. Der Statuscode, den der Code setzt, erreicht den Aufrufer nie.

Eine Bedingung, die vor jedem Controller feststeht, wird deshalb **festgehalten und getrennt davon
beantwortet**:

- Der Listener schreibt sie in eine anfragebezogene Bohne (`EmployeeAccessDenial`) — eine
  `ServiceFeedbackMessage` mit `ErrorCode`, wie jeder andere fachliche Fehler auch.
- Ein Filter hinter der Sicherheitskette (`EmployeeAccessFilter`, `@Order(104)`) gibt die Antwort:
  `sendError(403, …)` für eine Seite, damit der Container auf `/error` weiterleitet und die
  Fehlerseite der Anwendung den Grund nennt; ein `ProblemDetail` als `application/problem+json` für
  `/api/**` und `/rest/**`.
- Der Filter greift in der Weiterleitung auf `/error` **nicht** — `OncePerRequestFilter` lässt den
  Fehler-Dispatch von sich aus aus, und genau das ist hier die Zusage.
- Ausgenommen bleiben statische Dateien (die Fehlerseite braucht ihr Stylesheet) und
  `/auth/exit-impersonation` (sonst ist der Weg zurück aus einer übernommenen Anmeldung gesperrt).
  Die Pfadlisten stehen einmal in `de.hbt.salat.common.filter.RequestPaths`.

---

## Gleichzeitige Änderungen (→ ADR-0033)

- **Kein Controller fängt eine Ausnahme der Persistenz.** Einen Konflikt der Versionsnummer
  (`OptimisticLockingFailureException`) übersetzt `ConcurrentModificationAspect` an jedem `@Service`
  in eine `BusinessRuleException` `XX-0003`. Der Konflikt entsteht meist erst beim Commit, ein
  `catch` im Service sähe ihn nicht.
  Ebenso übersetzt er die Verletzung eines Unique-Schlüssels, `XX-0004` oder den eigenen Befund
  des Schlüssels (#1208). Ein neuer Unique-Schlüssel heißt in Changeset und Entität gleich
  (`uk_<tabelle>_<spalten>`). Braucht er eine eigene Meldung, kommt er in die Tabelle des Aspekts.
- **Ein Formular, dessen Antwort spürbar dauert und dessen zweites Absenden kollidiert oder etwas
  doppelt anlegt, trägt `data-submit-once`** (heute die Prüfseiten von Freigabe und Abnahme, das
  Öffnen auf der Seite der Abnahme und das Buchungsformular). Einzelheiten in
  [`docs/ui-style-guide.md` §5.5](../ui-style-guide.md).

---

## Secrets (→ ADR-0038)

**Kein Secret im Klartext in der Datenbank. Secrets — Passwörter, Tokens, Schlüssel fremder
Systeme — speichert und liest allein das Modul `secret`** (#1432).

- `SecretService` legt an, ersetzt, liest und löscht; für ein Formular gibt `getSummary` alles außer
  dem Secret heraus. Die Entität `Secret` und ihr Repository verlassen das Modul nicht
  (`ArchitectureTest.nothingOutsideSecretTouchesTheStoredSecret`).
- **Der Eigentümer merkt sich die id mit Fremdschlüssel**, nicht als `@ManyToOne` (bewusste
  Abweichung von ADR-0036), und **löscht das Secret mit sich, in derselben Transaktion**: erst sich,
  dann das Secret, denn der Fremdschlüssel zeigt darauf.
- Jede Art von Secret ist ein Typ (`UsernamePassword`, `Token`), dessen `toString()` das Secret
  auslässt. Kein Secret in Logs, Fehlermeldungen oder Laufhistorie: was ein fremder Client in seine
  Meldung schreibt, geht durch eine Redaktion (`JiraCredentialRedaction`), bevor es angezeigt oder
  gespeichert wird.
- **Der Schlüssel kommt nur aus der Umgebung** (`SALAT_SECRET_ACTIVEKEYID`,
  `SALAT_SECRET_KEYS_<ID>`), nie aus einem `application*.yaml` in `src/main/resources`
  (`SecretKeyConfigurationTest`); der Schlüssel der Tests steht in `application-unittest.yaml`.
  Ohne Schlüssel startet die Anwendung und speichert kein Secret; ein fehlerhafter verhindert den
  Start. Lokal braucht jede Person einen eigenen (README).

---

## Flags Column Pattern
List views that expose boolean state flags on rows use a dedicated **Flags** column rather than inline badges or text next to the primary field.

Rules:
- Column header: `th:text="#{main.general.flags.text}"`, class `d-none d-lg-table-cell` (hidden on small screens)
- Each flag is a `<span class="badge bg-<color>-lt" th:title="#{...}">` containing a Bootstrap Icon `<i class="bi bi-..."></i>`
- The **hide** flag always uses `bg-danger-lt` and `bi-eye-slash`. In list views the flag cell is produced by the `fragments/hide-toggle.html` fragments which also render a clickable toggle for managers:
  ```html
  <td class="d-none d-lg-table-cell" th:replace="~{fragments/hide-toggle :: customerHideFlag}"></td>
  ```
- Other flag icons used in the project (suborder list as reference): `bi-cash-stack` (invoiceable), `bi-bookmark-star-fill` (standard), `bi-chat-square-text` (comment required), `bi-mortarboard` (training), `bi-percent` on `bg-teal-lt` (time surcharge, #1469)
- Do not put flag badges inline in the primary/name column — use the flags column instead
- **Exception: „Inaktiv"** (#1220). An inactive entry gets no row tint (`table-*`); it carries
  `fragments/inactive-badge :: badge(${…})` behind its name or sign, in the first column that is
  always visible. The condition stays the list's own — `active` for a budget plan, `currentlyValid`
  otherwise (→ ADR-0029). Reason and measurements in [`docs/ui-style-guide.md`
  §5.4](../ui-style-guide.md).

---

## JSON Columns (→ ADR-0024)

A column whose content is a set of values only the configuration knows is mapped as native `json`
via `@JdbcTypeCode(SqlTypes.JSON)` on a `Map` or `List` attribute: `JiraTicket.customFields` /
`customFieldsEffective` (#881) and the column reading of an import, `JiraTicketImport.columnMapping`
(#1386).

- **Reading a value out of the JSON is MySQL-specific and belongs in ETL/report SQL or in a view** —
  never in a JPQL query or a repository method. The persistence model itself stays portable, and the
  round trip is covered against H2 (`JiraTicketCustomFieldsTest`).
- **The empty state is `NULL`, never `{}` and never an empty string.** `JSON_EXTRACT(NULL, …)`
  answers `NULL`.
- Where the stored document depends on a configured list, keep a hash of that list next to it and
  rewrite the row when the hash differs. Build the hash from the **configured value** (trimmed,
  sorted), not from the stored JSON.

---

## i18n Message Bundles
- **The application is German-first.** German is the primary/default language.
- Files: `src/main/resources/de/hbt/salat/web/MessageResources.properties` (German, default) and `MessageResources_en.properties` (English)
  - Both files contain the full set of `main.*` Thymeleaf keys.
  - `MessageResources.properties` is the German default bundle; it is served for `de_DE` and any locale that has no specific bundle.
  - `MessageResources_en.properties` is served for the `en` locale.
- **Encoding**: both files are **UTF-8**.
- **Key order**: keys are sorted alphabetically. The correct workflow: append new key(s) to the file, then sort all lines. Never try to find the insertion point manually.
- When adding a new feature, add matching keys to **both** bundles — German translation in `MessageResources.properties`, English in `MessageResources_en.properties`.

---

## Database Migration Convention
- Single Liquibase YAML file: `src/main/resources/db/changelog/db.changelog-master.yaml` — always append; never edit existing changesets
- Always guard with `preConditions: onFail: MARK_RAN` and a `columnExists` / `tableExists` check
- Boolean columns: `type: bit(1)` (Hibernate expects `bit`; `boolean` or `tinyint` will fail schema validation)
- `author` field: use the committer's initials (e.g. `author: kr`)

---

## CSV über einen `HttpMessageConverter`

Eine Schnittstelle, die dieselben Daten als JSON **und** als CSV anbietet, überlässt die Wahl dem
`Accept`-Header und stellt das Format nicht über einen Parameter ein. Die Methode nennt beides in
`produces`, **JSON zuerst** — diese Reihenfolge entscheidet, was ein Aufrufer mit `*/*` bekommt.
Eigene Konverter-Bohnen stellt Spring Boot vor die mitgelieferten, ohne die Angabe in `produces`
gewinnt also CSV.

Zwei Stellen, die ein direkt umgesetztes `HttpMessageConverter` nicht geschenkt bekommt — beide
stecken in `AbstractHttpMessageConverter`, das die Konverter hier nicht erweitern:

- **`canWrite` muss die Klasse mitprüfen**, nicht nur den Medientyp. Sonst kollidieren zwei
  CSV-Konverter, und genau dafür tragen die Konverter im Modul `dailyreport` die Suffixe
  `text/csv+dailyreport` und `text/csv+dailyworkingreport`. Wer die Klasse prüft
  (`ReportDataCsvConverter`), darf `text/csv` führen — der bessere Typ für einen fremden Aufrufer.
- **`write` muss den Content-Type selbst setzen**, und zwar **vor** `getBody()`: eine
  Servlet-Antwort schreibt ihre Kopfzeilen, sobald der Strom offen ist. Ohne das trägt die Antwort
  keinen Content-Type — im `dailyreport`-Modul setzt ihn deshalb jeder Aufrufer von Hand.

Für eine bean-basierte `MappingStrategy` von opencsv braucht es eine Klasse mit festen Feldern. Wo
die Spalten erst zur Laufzeit feststehen (Reportergebnisse), wird über die Spaltenliste geschrieben.

---

## Dauer und Uhrzeit an einer Schnittstelle (#1398)

Eine Dauer steht in JSON, CSV und jedem anderen Import oder Export als **ein** Wert `HH:mm`,
auch ab 24 Stunden (`25:30`); im Java-Code ist sie ein `Duration`, nie ein `LocalTime` und
nie zwei Felder für Stunden und Minuten. JSON nimmt dafür `@HoursMinutes` (ein Saldo mit Vorzeichen
`@HoursMinutes.Signed`), CSV den `DurationConverter` von `DailyWorkingReportCsvConverter`, Excel eine
Zahl in Tagen mit dem Zellformat `[hh]:mm`. Ein Beginn ist ein `LocalTime` mit `HH:mm`, in JSON über
`@TimeOfDay` und streng gelesen — `@JsonFormat(pattern = "HH:mm")` nähme `24:00` als Mitternacht. Ein Wert,
den das Format nicht liest — Minuten ab 60, ein Vorzeichen, ein Beginn außerhalb von 00:00–23:59 —,
wird mit 400 abgelehnt. Ein abgelöstes Feld bleibt übergangsweise lesbar und schreibbar, steht im
OpenAPI-Schema als `deprecated` und nennt das Feld, das es ersetzt; das neue hat Vorrang.

---

## Reserviert in den Parametern einer REST-Schnittstelle

Eine Methode, die freie Anfrageparameter durchreicht (`@RequestParam Map<String, String>`), reicht
nur das durch, was der Aufrufer geschickt hat: **`UiStateFilter` lässt `/api/**` und `/rest/**`
aus** (`shouldNotFilter`). Der gemerkte Zustand gehört der Oberfläche; in einer zustandslosen Kette
für maschinelle Aufrufer hätte ein Wert aus dem Cookie dieselbe Anfrage aus einem Browser anders
beantwortet als aus einem Skript. Die Ausnahme gilt für alle REST-Endpunkte, nicht nur für die, die
freie Parameter annehmen — deshalb steht sie im Filter und nicht in einem Endpunkt.

Zu reservieren bleiben nur die **eigenen Parameter der Methode**: trägt die Anfrage den Namen der
Ressource (`?report=Stundenliste`), darf dieser Name nicht zusätzlich als fachlicher Parameter
gelten. Er wird ausgefiltert, statt sich auf Nichtkollision zu verlassen — ein stiller falscher Wert
ist schlimmer als ein fehlender, den die Antwort benennt.

---

## Legacy URL Redirects

When a URL changes (controller rename, module move, path restructuring), register a permanent redirect in `de.hbt.salat.common.configuration.LegacyUrlRedirectConfig` so that bookmarks, history, and external links continue to work.

```java
// in LegacyUrlRedirectConfig.addViewControllers():
redirect(registry, "/old-path", "/new-path");
```

- Every entry is a **301 Moved Permanently** — browsers and crawlers update their records.
- Add one line per changed URL; do not remove old entries (they are the permanent record of URL history).

**Known redirects**

| Old URL | New URL | Since |
|---|---|---|
| `/welcome` | `/dailyreport/dashboard` | #724 |
