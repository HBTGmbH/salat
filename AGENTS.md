# Architecture and Agents Overview

This document captures the architectural rules and direction for the project to guide day-to-day decisions and long-term evolution.
See also README.md

---

## System Shape
- Architectural style: Modular Monolith
  - The codebase is organized into modules (by domain capability). Modules are packaged and deployed together as a single application.
  - Module boundaries define allowed dependencies and collaboration patterns.

## Technology Positioning
- UI and presentation stack:
  - Spring Web MVC (controllers, validation, handler methods)
  - Thymeleaf templates (server-side rendering)
  - Bootstrap 5 for styling and components
  - Tabler (tabler.io) design system layered on top of Bootstrap for consistent look-and-feel

## Dependency and Coupling Rules
- Intra-module coupling is allowed; inter-module coupling must respect declared boundaries.
- Cyclic dependencies between modules are not permitted.
- Required cross-module collaboration that would otherwise introduce cycles must be decoupled via Spring’s event mechanism:
  - Modules publish domain/application events via Spring’s ApplicationEventPublisher (or related facilities).
  - Other modules subscribe to these events using event listeners.
  - Events should carry stable, minimal data contracts to reduce coupling.

## Controller and View Guidelines (target stack)
- Controllers:
  - Use Spring Web MVC controllers to expose application capabilities.
  - Keep controller logic thin; delegate to services within the same module.
- Views:
  - Prefer Thymeleaf templates backed by the module’s controllers.
  - Adopt Bootstrap 5 + Tabler components for UI layout and widgets.
  - **Prefer the `salat:` custom dialect** for reusable form and table components over raw `th:replace` fragment calls. The dialect provides cleaner, attribute-based tags that are easier to read and IDE-friendly.
  - Thymeleaf fragments remain valid for structural/layout reuse (e.g. `master-table`, layout decorators); the `salat:` dialect targets leaf-level components (inputs, selects, buttons).
  - Shared layout and fragments should live under a common templates/layout and templates/fragments structure.

## Legacy URL Redirects

When a URL changes (controller rename, module move, path restructuring), register a permanent redirect in `org.tb.common.configuration.LegacyUrlRedirectConfig` so that bookmarks, history, and external links continue to work.

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

## Testing and Quality
- Preserve unit, integration, and UI tests across module boundaries.
- Prefer testing observable behavior at module boundaries over implementation details.

## Build and Tooling
- Always use `./mvnw` (the Maven wrapper) to build, test, and run Maven goals — never a system-wide `mvn` command.
- On macOS, prefix every `./mvnw` call with `jenv exec` so the correct JDK is on `PATH`: `jenv exec ./mvnw <goal>`.
  - If `jenv` is not installed and `java` is not found, prompt the user to install jenv (`brew install jenv`) and add the required JDK version before continuing.

## GitHub Workflow
- **Branch naming**: `feature/<issue-number>-<short-description>` (e.g. `feature/606-move-fromDBtimeToString-to-DurationUtils`)
- **One issue per branch / PR**: do not bundle unrelated changes.
- **Linking to issues**: add `Closes #NNN` in the PR body — this is the standard GitHub mechanism. `gh issue develop` only creates new branches; it cannot link an existing branch to an issue.
- **Creating a PR**:
  ```
  gh pr create --title "..." --body "$(cat <<'EOF'
  ## Summary
  - bullet points

  ## Test plan
  - [ ] item

  Closes #NNN
  EOF
  )"
  ```
- **PR compliance note**: every PR description must include: _"Reviewed AGENTS.md; changes comply with architecture, view, and security guidelines."_
- **Refactoring issues**: promote one refactoring at a time — open a new issue for the next step rather than bundling multiple cleanups in one PR.
- **Issue type**: every issue must have its type set via the GitHub GraphQL API after creation. Use `Bug` for defects and `Feature` for new capabilities. Available type IDs:
  - `Task`:    `IT_kwDOAYn5ks4AV-6C`
  - `Bug`:     `IT_kwDOAYn5ks4AV-6D`
  - `Feature`: `IT_kwDOAYn5ks4AV-6H`
  ```bash
  gh api graphql -f query='mutation { updateIssue(input: { id: "<node_id>", issueTypeId: "<type_id>" }) { issue { number issueType { name } } } }'
  # get node_id via: gh api repos/HBTGmbH/salat/issues/NNN --jq .node_id
  ```

## Definition of Ready

Before writing any code:

- [ ] `main` is checked out and up-to-date: `git checkout main && git pull`
- [ ] A dedicated branch has been created: name must start with `feature/` (new capability) or `bug/` (defect fix), e.g. `feature/683-multiple-supervisors`

---

## Definition of Done

Before marking any task complete, work through this checklist and report which items apply and whether each is satisfied.

A feature or fix is considered done when **all** of the following are true:

### Code
- [ ] Build passes: `jenv exec ./mvnw verify` completes without errors
- [ ] All existing tests pass; new behaviour should be covered by tests
- [ ] No new cross-module cycles introduced; cross-module side-effects go through Spring events
- [ ] Controllers are thin — business logic lives in a service within the same module
- [ ] Security: `@PreAuthorize` on every controller write method; `@Authorized` + runtime guard in the service

### Views (if UI changed)
- [ ] Uses Spring MVC + Thymeleaf
- [ ] Leaf-level form components use the `salat:` custom dialect; layout/structural reuse uses fragments
- [ ] Bootstrap 5 + Tabler components for layout and widgets
- [ ] CSRF protection relies solely on `th:action="@{...}"` — no explicit `_csrf` hidden input

### Internationalisation (if new keys added)
- [ ] Keys added to both `MessageResources.properties` (German) and `MessageResources_en.properties` (English)
- [ ] Both files sorted after the change
- [ ] Every new `ErrorCode` has a matching `errorcode.*` key in both bundles

### Database (if schema changed)
- [ ] Liquibase changeset appended to `db.changelog-master.yaml` (never edited)
- [ ] Changeset guarded with `preConditions: onFail: MARK_RAN` + a `columnExists`/`tableExists` check
- [ ] Boolean columns use `type: bit(1)`

### Documentation
- [ ] Checked against all ADRs in `docs/adr/` — implementation must not contradict any decision record
- [ ] ADR created in `docs/adr/` for any significant architectural decision
- [ ] `AGENTS.md` updated if an architectural rule changed

### Pull Request
- [ ] Branch named `feature/<issue-number>-<short-description>`
- [ ] PR body contains `Closes #NNN`
- [ ] Issue type set via GitHub GraphQL API
- [ ] PR description includes: *"Reviewed AGENTS.md; changes comply with architecture, view, and security guidelines."*

---

## Documentation
- Keep this document updated when architectural rules evolve.
- Align feature work and code reviews with the rules above.

## Architecture Decision Records
- ADRs live in [`docs/adr/`](docs/adr/README.md) (format: MADR).
- New significant architectural decisions must be captured as an ADR before or alongside implementation.
- This file documents **what** the current rules are; ADRs document **why** a decision was made and which alternatives were considered.

## Spring Boot annotations placement
- Place cross-cutting Spring Boot/Spring Security enabling annotations on the application class (SalatApplication) unless there is a strong, explicit reason to scope them to a specific configuration class.
  - Examples: @EnableMethodSecurity, @EnableScheduling, @EnableAsync. This centralizes enablement, avoids duplicate configuration, and makes project-wide capabilities obvious.

## Agent usage policy (for code generation and refactoring)
- Agents and contributors MUST consult and adhere to this AGENTS.md when generating or modifying code.
- Minimum checklist before committing changes:
  - Verify dependencies and coupling follow the rules above (no new cycles; use events for cross-module collaboration).
  - For views, prefer Thymeleaf fragments and the shared layout structure.
  - Place cross-cutting Spring Boot/Spring Security enabling annotations on SalatApplication.
  - Favor standard Spring Security (@PreAuthorize/roles) over custom aspects, unless explicitly required.
  - Keep controllers thin; push logic to services within the same module.
- Pull Request note: Include a short statement like “Reviewed AGENTS.md; changes comply with architecture, view, and security guidelines.”

---

## Globally Accessible Objects in `layout/base.html`

`layout/base.html` is the shared layout template rendered for every page. It has no dedicated controller, so Spring MVC model attributes are not available.

**Convention:** Access Spring beans via `${@beanName.property}` — Thymeleaf resolves `@beanName` as a Spring application context lookup.

```html
[[${@buildProperties.version}]]
[[${@salatProperties.docsUrl}]]
```

**Constraint:** `@beanName` is only valid inside `${...}` expressions. It is **not** allowed inside `@{...}` URL expressions. For URLs sourced from a bean, extract the value first with `th:with`, then reference it via `${...}`:

```html
<!-- correct -->
<a th:with="url=${@salatProperties.docsUrl}" th:href="${url}">...</a>

<!-- fails: @beanName prohibited inside @{} -->
<a th:href="@{${@salatProperties.docsUrl}}">...</a>
```

## TomSelect Dropdowns

All `<select>` elements use [TomSelect](https://tom-select.github.io/) for search-as-you-type behaviour. Initialisation is handled centrally in `layout/base.html` via a `querySelectorAll` on page load and again on `htmx:afterSettle` (so OOB-swapped selects are picked up automatically).

### CSS class contract

| Class | `maxItems` | When to use |
|---|---|---|
| `tomselect` | `1` (single) | Any select where only one value is needed |
| `tomselect tomselect-multi` | `null` (unlimited) | Multi-select; always combine with the HTML `multiple` attribute |

### Single-select

```html
<select class="form-select tomselect" th:field="*{orderId}">
  <option value="">-- Select --</option>
  <option th:each="o : ${orders}" th:value="${o.id}" th:text="${o.sign}"></option>
</select>
```

### Multi-select

Always add both `tomselect-multi` **and** the native `multiple` attribute. The class sets `maxItems: null` in TomSelect; `multiple` ensures the browser submits all selected values so Spring MVC can bind them to a `List<Long>` (or `List<String>`).

```html
<select class="form-select tomselect tomselect-multi" th:field="*{contractIds}" multiple>
  <option th:each="ec : ${contracts}" th:value="${ec.id}" th:text="${ec.employee.name}"></option>
</select>
```

### HTMX + OOB swaps

`htmx:afterSettle` re-initialises any `select.tomselect` that does not yet have a `.tomselect` instance, so OOB-replaced selects are picked up without extra work. **Do not** add a `multiple` attribute to single-select OOB replacements — if the original select was single, the OOB replacement must also be single.

---

## Salat Custom Thymeleaf Dialect (`salat:`)

The `SalatDialect` (prefix `sal`, registered via `ThymeleafDialectConfiguration`) provides element processors that replace verbose `th:replace` fragment calls with clean, attribute-based tags.

### Available tags

| Tag | Attributes | Replaces |
|---|---|---|
| `<salat:form>` | `th:action` (expr), `th:object` (expr), `th:id-property` (field) | `<form method="post" class="card">` with `card-body`/`card-footer` divs and hidden id input |
| `<salat:inputs>` | _(body: input tags)_ | `<div class="card-body">` wrapper (used inside `salat:form`) |
| `<salat:buttons>` | _(body: button tags)_ | `<div class="card-footer">` wrapper (used inside `salat:form`) |
| `<salat:textInput />` | `th:field` (field), `th:label` (expr), `required`, `maxlength`, `th:helpText` (optional expr) | `fragments/form-fields :: textInput / textInputHelp` |
| `<salat:textarea />` | `th:field` (field), `th:label` (expr), `required`, `rows` (default 3), `monospace`, `th:helpText` (optional expr) | `fragments/form-fields :: textareaInput / textareaInputHelp` |
| `<salat:checkboxSwitch />` | `th:field` (field), `th:label` (expr) | `fragments/form-fields :: checkboxSwitch` |
| `<salat:formButtons />` | `th:saveLabel` (expr), `th:cancelHref` (expr) | `fragments/form-fields :: formButtons` |

### Usage

1. Declare the namespace on the `<html>` element: `xmlns:salat="http://hbt.de/salat/thymeleaf"`
2. Use `salat:form` as a container; `salat:inputs` and `salat:buttons` wrap field groups:

```html
<salat:form th:action="@{/customers/store}" th:object="${customer}" th:id-property="*{id}">
  <salat:inputs>
    <salat:textInput th:field="*{shortName}" th:label="#{main.customer.shortname.text}" required="true" maxlength="12" />
    <salat:textarea th:field="*{description}" th:label="#{label.description}" required="false" rows="5" />
    <salat:checkboxSwitch th:field="*{active}" th:label="#{label.active}" />
  </salat:inputs>
  <salat:buttons>
    <salat:formButtons th:saveLabel="#{main.button.save}" th:cancelHref="@{/customers}" />
  </salat:buttons>
</salat:form>
```

### Implementation notes

- Processors live in `org.tb.common.thymeleaf.processor`, extend `AbstractSalatProcessor` → `AbstractElementTagProcessor`.
- `th:label`, `th:saveLabel`, `th:cancelHref`, and `th:helpText` accept any Thymeleaf expression (`#{...}`, `${...}`, `@{...}`, or composite).
- The replacement model is processed (`replaceWith(model, true)`), so `th:field` / `th:errors` in generated output are handled by the standard dialect.
- When adding a new processor: register it in `SalatDialect.getProcessors()`.

---

## Code Structure and Patterns

### Module Overview
Top-level packages under `org.tb`, one module per domain capability:

| Package | Responsibility |
|---|---|
| `auth` | Authentication, authorization beans and annotations |
| `common` | Shared base classes, exceptions, events, utilities |
| `error` | Custom error page (cross-cutting; may depend on auth, employee, common) |
| `customer` | Customer management |
| `dailyreport` | Time reports and working days |
| `employee` | Employee and contract management |
| `etl` | Data integration / extract-transform-load |
| `favorites` | User favorites for quick access |
| `invoice` | Invoice generation and settings |
| `jira` | Jira integration and replication |
| `management` | Admin and account management screens |
| `order` | Customer orders, employee orders, suborders |
| `reporting` | Report definitions and scheduling |
| `settings` | User preference store: entity, converter, repository, service — generic map-based API, no UI |
| `settingseditor` | User preferences editing UI — aggregator; may import from any module's `*.preferences` package; other modules must not import from `settingseditor` |
| `statistic` | Aggregations and statistics |

### Module Layer Conventions
Each module uses a consistent sub-package structure:

| Sub-package | Purpose |
|---|---|
| `domain` | JPA entities and DTOs |
| `persistence` | Spring Data repositories and DAO components |
| `service` | Business logic, transactions, authorization checks |
| `controller` | Spring MVC controllers (HTTP boundary) |
| `event` | Domain event classes |
| `listener` | `@EventListener` subscribers |
| `rest` | REST endpoints |
| `viewhelper` | View decorators and model-prep helpers |

**`viewhelper` package rule:** View helper classes (model-prep helpers, view decorators) **must stay in the `viewhelper` sub-package** of their module. They must not be placed in `domain`, `service`, or any other sub-package. Rules:
- View helpers are used by **controllers**, by **other view helpers**, and **directly in Thymeleaf templates** — never by services.
- Services must not import or return view helper types; they return domain objects or DTOs.
- View helpers may not use `HttpSession` directly; they receive domain data as constructor arguments and provide formatted/computed values for templates.
- The typical pattern: service returns `List<SomeDomainDTO>`, controller maps to `List<SomeViewHelper>` via a static `from(contract, dto)` factory, then puts the view helpers in the model.

### Entity Classification: Stammdaten vs. Bewegungsdaten
Entities are divided into two categories (→ ADR-0011):

**Stammdaten (Master Data)** — reference/configuration entities with long lifetimes; rarely changed; referenced by transactional data. Deactivation via `hide`-flag, `enabled`-flag, or validity range rather than deletion.

**Bewegungsdaten (Transactional Data)** — business-event records; append-only or near-immutable after creation; reference master data; may use soft-delete.

| Entity | Type | `hide` / deactivation |
|---|---|---|
| `Customer` | Stammdaten | `hide` |
| `Employee` | Stammdaten | `hide` |
| `Employeecontract` | Stammdaten | `hide` + `validFrom`/`validUntil` |
| `Customerorder` | Stammdaten | `hide` + `fromDate`/`untilDate` |
| `Suborder` | Stammdaten | `hide` + `fromDate`/`untilDate` |
| `Employeeorder` | Stammdaten | inherits `hide` from parent |
| `Publicholiday` | Stammdaten | — (calendar fact) |
| `Referenceday` | Stammdaten | — (calendar reference) |
| `SalatUser` | Stammdaten | `Employee.hide` |
| `AuthorizationRule` | Stammdaten | `validFrom`/`validUntil` |
| `ETLDefinition` | Stammdaten | — |
| `JiraReplicationConfig` | Stammdaten | `enabled` |
| `ReportDefinition` | Stammdaten | `hide` (proposed — not yet implemented) |
| `ScheduledReportJob` | Stammdaten | `enabled` |
| `OrderRevenueExcelMapping` | Stammdaten | — |
| `Timereport` | Bewegungsdaten | soft-delete (`deleted` + `@SQLRestriction`) |
| `Workingday` | Bewegungsdaten | — |
| `Overtime` | Bewegungsdaten | — |
| `Vacation` | Bewegungsdaten | — |
| `OrderRevenue` | Bewegungsdaten | — |
| `ETLExecutionHistory` | Bewegungsdaten | — |
| `ScheduledReportExecutionHistory` | Bewegungsdaten | — |
| `StatisticValue` | Bewegungsdaten | — |
| `JiraTicket` | Bewegungsdaten | — |
| `Favorite` | Bewegungsdaten | — |

### Entity Pattern
- All JPA entities extend `AuditedEntity` (`common/domain/AuditedEntity.java`)
- `AuditedEntity` provides: `@Id @GeneratedValue(IDENTITY)`, Spring Data audit fields (`created`, `lastupdate`, `createdby`, `lastupdatedby`), optimistic locking via `@Version updatecounter`, `equals`/`hashCode` by ID
- Standard entity annotations: `@Entity`, `@Getter @Setter` (Lombok), `@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)`
- Boolean columns in the database must be `bit(1)` — Hibernate maps `Boolean` to `bit`, not `tinyint`. In Liquibase migrations always use `type: bit(1)`.

### Repository + DAO Hybrid Pattern
- Spring Data repositories extend `PagingAndSortingRepository<E, Long>` and `CrudRepository<E, Long>`; custom queries use `@Query` with multiline JPQL text blocks
- DAO components (`@Component`, `@RequiredArgsConstructor`) wrap the repository and expose domain-meaningful method names (e.g. `getCustomers()`, `getCustomersOrderedByShortName()`)
- Controllers and services call the DAO, not the repository directly — unless no DAO exists for the module
- Sort fields are referenced via the JPA metamodel class (e.g. `Customer_.NAME`) to avoid string literals

### Service Pattern
- Class annotations: `@Service @RequiredArgsConstructor @Transactional @Authorized`
- Read-only queries: annotate the method with `@Transactional(readOnly = true)`
- Privileged operations use `@Authorized(requiresManager = true)` on the method plus an explicit runtime guard:
  ```java
  if (!authorizedUser.isManager()) throw new AuthorizationException(AA_NEEDS_MANAGER);
  ```
- Services throw typed `ErrorCodeException` subclasses (`InvalidDataException`, `BusinessRuleException`, `AuthorizationException`) — never a raw `RuntimeException` for business errors
- Before destructive DB operations, publish a domain event via `ApplicationEventPublisher`; catch `VetoedException` and re-throw with added context (see Event / Veto Pattern)

### Controller Pattern
- Class annotations: `@Controller @RequestMapping(“/path”) @RequiredArgsConstructor @PreAuthorize(“not hasRole('RESTRICTED')”)`
- Write operations get `@PreAuthorize(“hasRole('MANAGER')”)` on the method
- Session filter persistence: check `request.getParameterMap().containsKey(“filter”)` — store to session on explicit submit, read from session otherwise
- Redirect-After-Post: successful writes return `”redirect:/...”` with `redirectAttributes.addFlashAttribute(“toastSuccess”, ...)`
- Form validation errors: call the service inside `try/catch(ErrorCodeException)`, convert via `ErrorCodeViewHelper.toViewMessages(ex)`, add to model, and re-render the form view (do not redirect)
- HTMX partial updates: use `th:hx-post`, `hx-swap=”none”`, `hx-include=”closest form”`, `hx-trigger=”change”` on select/input elements; detect `HX-Request` header in the controller and return `”view :: fragmentName”` for partial responses
- CSRF tokens: **never** add an explicit `<input type=”hidden” th:name=”${_csrf.parameterName}” th:value=”${_csrf.token}” />`. Spring Security 6 uses deferred tokens — `_csrf` is null when accessed directly in templates. Using `th:action=”@{...}”` is sufficient; Thymeleaf's `CsrfRequestDataValueProcessor` injects the token automatically into every POST form.
- **No direct `HttpSession` access** (→ ADR-0013): new Spring MVC controllers must not read or write `HttpSession` for UI state. Pass state via URL parameters, path variables, or form fields. User selection state (e.g. currently selected employee contract) must be expressed in the URL or stored in a cookie — not the session. Exceptions (`AuthorizedUser`, `AuthorizedEmployee`, impersonation) must be documented with a comment `// ADR-0013: Ausnahme — [Begründung]` at the point of use.

### The `hide` Flag (UX Declutter)
The `hide` boolean flag is a UX feature: it removes an entity from all dropdown select inputs in forms, keeping the app compact when a customer, order, or suborder is no longer actively used but must not be deleted (e.g. historical records still referenced by time reports). Hidden records remain in the database and in list management views, but are suppressed everywhere a user picks from a list.

Rules:
- Entities with a `hide` flag: `Customer`, `Customerorder`, `Suborder`, `Employee`, `Employeecontract`.
- `Employeeorder` has no own `hide` — it inherits visibility from its parent `Suborder` and `Customerorder`.
- All service/DAO methods that populate dropdowns must exclude hidden records by default (apply `notHidden()` spec or equivalent).
- The list management view exposes a “Show hidden” toggle so managers can still see and edit hidden records.

### Validity Ranges (Time-Bounded Entities)
Many entities carry a validity range (`fromDate` / `untilDate`) that defines the period during which they are usable. An entity is *currently valid* when today's date falls within its range. Outside that range it is *expired* (or not yet active), and it is often unusable — e.g. you cannot book time on a customer order whose validity ended last month.

This is a separate UX concern from `hide`:
- `hide` is an explicit, manual decision to remove an entry from dropdowns regardless of its dates.
- Validity is automatic and time-driven: an order expires when its `untilDate` passes.

Default behaviour in dropdowns and service methods: show only currently valid entities (`untilDate` is null or ≥ today). The `show` filter toggle in list views lets managers override this to also see expired or future records for auditing and management purposes.

Entities with validity ranges (non-exhaustive): `Customerorder`, `Suborder`, `Employeeorder`, `Employeecontract`.

The `showOnlyValid()` JPA `Specification` (present in the DAO of each such entity) encodes the date check:
```java
builder.or(
    builder.isNull(root.get(Entity_.untilDate)),
    builder.greaterThanOrEqualTo(root.get(Entity_.untilDate), today())
)
```
Keep this predicate separate from `notHidden()` — they are independent concerns.

### List View Filter Toggles
List views that support both validity and visibility filtering expose two independent boolean toggles in the advanced filter section:
- `show` (`Boolean`) — when `true`, includes expired/invalid records; default `null`/`false` shows only currently valid
- `showHidden` (`Boolean`) — when `true`, includes records whose `hide` flag is set; default `null`/`false` excludes hidden records

**DAO layer**: these are separate `Specification` predicates — never bundle `notHidden` inside `showOnlyValid()`. Apply each independently:
```java
if (!TRUE.equals(showInvalid)) predicates.add(showOnlyValid().toPredicate(root, query, builder));
if (!TRUE.equals(showHidden))  predicates.add(notHidden().toPredicate(root, query, builder));
```

**Entity without own `hide` field** (e.g. `Employeeorder`): filter on the parent's flag via a join — `notHidden()` checks `suborder.hide` and `suborder.customerorder.hide`.

**Session keys**: store both flags in session alongside other filter state; session key names follow the pattern `<module>.<entity>.show` and `<module>.<entity>.showHidden`.

**Template**: add both as `form-check form-switch` checkboxes in the advanced filter row, using `name=”show”` / `name=”showHidden”` with `value=”true”` and `th:checked`; the auto-submit script picks them up automatically.

### DTO Pattern
- Class annotations: `@Builder @Data @Jacksonized @AllArgsConstructor`
- Static factory method `from(Entity)` maps entity → DTO (include all audit fields)
- Mutator `copyTo(Entity)` applies mutable fields back to the entity; never overwrite `id` or audit fields
- `isNew()` helper returns `id == null`
- Modern modules use Java `record` for simple, immutable DTOs
- When writing a `Boolean` from DTO to entity, guard against null: `Boolean.TRUE.equals(value)`

### Event / Veto Pattern
- Event hierarchy: `VetoableEvent` → `DomainObjectDeleteEvent` / `DomainObjectUpdateEvent` (in `common/event/`)
- Module-specific events (e.g. `CustomerDeleteEvent`) extend the appropriate base, passing the entity ID to the constructor
- Service publishes the event before the destructive DB call, wrapped in `try/catch(VetoedException)`; on veto it prepends its own context message and re-throws
- Listeners are annotated with `@EventListener` and throw `VetoedException` to block the operation
- Cross-module side-effects and integrity checks must flow through events — never direct service-to-service calls across module boundaries

### Exception Hierarchy
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
  - Module prefixes in use: `AA` (auth), `CO` (customer order), `CU` (customer), `EC` (employee contract), `EM` (employee), `EO` (employee order), `SO` (suborder), `TR` (time report), `RL` (release), `WD` (working day), `ETL`, `XX` (generic).
  - When adding a new error code, append it to the enum; never reuse or renumber existing codes.
- `ServiceFeedbackMessage` (`common/exception/ServiceFeedbackMessage.java`) wraps an `ErrorCode` + severity + optional positional arguments (`{0}`, `{1}`, …); used to accumulate messages when building veto responses.
  - Factory methods: `ServiceFeedbackMessage.error(errorCode, args…)` / `.warning(…)`.

### Error Reporting to the User
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

### Security Layers
Two stacked layers provide defence in depth:
- **HTTP boundary** (`@PreAuthorize` on controller): enforced by Spring Security before the method runs
- **Service boundary** (`@Authorized` + runtime guard): enforced inside the service regardless of caller
- `AuthorizedUser` (session-scoped bean, `auth/domain/AuthorizedUser.java`): exposes `isManager()`, `isAdmin()`, `isBackoffice()`, `isRestricted()`, and the current login sign
- Spring Security roles: `USER`, `RESTRICTED`, `BACKOFFICE`, `PEOPLE_LEAD`, `MANAGER`, `ADMIN`; `manager` role includes admins; `backoffice` includes managers and admins; `people_lead` includes managers and admins
- Role semantics (derived from `SalatUser.status` at login):
  - `USER` — base role granted to every authenticated user = every employee
  - `RESTRICTED` — external employees (contractors) and interns (`status=restricted`); heavily limited access, cannot access most list/management views
  - `BACKOFFICE` — Backoffice (`status=bo`) plus everyone with `MANAGER`; can create invoices and upload financial data from books and records
  - `PEOPLE_LEAD` — people leads/supervisors (`status=pv`) plus everyone with `MANAGER`; can read time reports and contracts of their team members, run reports
  - `MANAGER` — general manager (`status=bl`) plus `ADMIN`; can manage contracts, orders, and time reports for everyone
  - `ADMIN` — system administrators (`status=adm`); full access; only role that is NOT an employee
  - A "regular employee" is someone with `BACKOFFICE` but not `PEOPLE_LEAD` or `MANAGER` — they can only view their own data.

### Flags Column Pattern
List views that expose boolean state flags on rows use a dedicated **Flags** column rather than inline badges or text next to the primary field.

Rules:
- Column header: `th:text="#{main.general.flags.text}"`, class `d-none d-lg-table-cell` (hidden on small screens)
- Each flag is a `<span class="badge bg-<color>-lt" th:title="#{...}">` containing a Bootstrap Icon `<i class="bi bi-..."></i>`
- The **hide** flag always uses `bg-danger-lt` and `bi-eye-slash`. In list views the flag cell is produced by the `fragments/hide-toggle.html` fragments which also render a clickable toggle for managers:
  ```html
  <td class="d-none d-lg-table-cell" th:replace="~{fragments/hide-toggle :: customerHideFlag}"></td>
  ```
- Other flag icons used in the project (suborder list as reference): `bi-cash-stack` (invoiceable), `bi-bookmark-star-fill` (standard), `bi-chat-square-text` (comment required), `bi-tag-fill` (fixed price), `bi-mortarboard` (training)
- Do not put flag badges inline in the primary/name column — use the flags column instead

### Grouped List View Pattern
When a list should be partitioned by a categorical field, render one card+table per group instead of a single flat table.

**Ordered groups (3+ values)** — iterate over a literal key list so order is explicit:
```html
<th:block th:each="statusKey : ${ {'bl','pv','bo','ma','restricted','adm'} }"
          th:with="group=${employees.?[status == '__${statusKey}__']}">
  <div th:if="${not #lists.isEmpty(group)}" class="card mb-3">
    <div class="card-header">
      <h3 class="card-title" th:text="#{${'main.employee.status.' + statusKey}}">Status</h3>
    </div>
    <div class="table-responsive">
      <table class="table table-vcenter card-table">...</table>
    </div>
  </div>
</th:block>
```

**Two groups** — use two explicit `th:block` sections (clearer than iterating):
```html
<th:block th:with="group=${contracts.?[freelancer != true]}">
  <div th:if="${not #lists.isEmpty(group)}" class="card mb-3">
    <div class="card-header"><h3 class="card-title" th:text="#{main.employeecontract.group.internal.text}">Internal Staff</h3></div>
    ...
  </div>
</th:block>
<th:block th:with="group=${contracts.?[freelancer == true]}">
  <div th:if="${not #lists.isEmpty(group)}" class="card mb-3">
    <div class="card-header"><h3 class="card-title" th:text="#{main.employeecontract.group.contractors.text}">Contractors</h3></div>
    ...
  </div>
</th:block>
```

Rules:
- The card header conveys the group value — remove the corresponding column from the table header and rows.
- Hide empty groups with `th:if="${not #lists.isEmpty(group)}"`.
- Show the global empty-state card only when the entire list is empty (place it before the group blocks).
- SpEL selection syntax: `list.?[field == value]` or `list.?[field != true]`.

### i18n Message Bundles
- **The application is German-first.** German is the primary/default language.
- Files: `src/main/resources/org/tb/web/MessageResources.properties` (German, default) and `MessageResources_en.properties` (English)
  - Both files contain the full set of `main.*` Thymeleaf keys.
  - `MessageResources.properties` is the German default bundle; it is served for `de_DE` and any locale that has no specific bundle.
  - `MessageResources_en.properties` is served for the `en` locale.
- **Encoding**: both files are **UTF-8**.
- **Key order**: keys are sorted alphabetically. The correct workflow: append new key(s) to the file, then sort all lines. Never try to find the insertion point manually.
- When adding a new feature, add matching keys to **both** bundles — German translation in `MessageResources.properties`, English in `MessageResources_en.properties`.

### Database Migration Convention
- Single Liquibase YAML file: `src/main/resources/db/changelog/db.changelog-master.yaml` — always append; never edit existing changesets
- Always guard with `preConditions: onFail: MARK_RAN` and a `columnExists` / `tableExists` check
- Boolean columns: `type: bit(1)` (Hibernate expects `bit`; `boolean` or `tinyint` will fail schema validation)
- `author` field: use the committer's initials (e.g. `author: kr`)