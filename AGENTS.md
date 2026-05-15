# Architecture and Agents Overview

This document captures the architectural rules and direction for the project to guide day-to-day decisions and long-term evolution.

---

## System Shape
- Architectural style: Modular Monolith
  - The codebase is organized into modules (by domain capability). Modules are packaged and deployed together as a single application.
  - Module boundaries define allowed dependencies and collaboration patterns.

## Technology Positioning
- Legacy UI stack (maintenance mode):
  - Struts 1.2
  - JSP
  - Notes:
    - Existing screens may continue to function but should not receive major new features.
    - Any new UI work should avoid adding to the legacy stack.
  - URL format:
    - Legacy Struts actions use the format `/do/<ActionName>` (e.g., `/do/ShowEmployee`, `/do/CreateDailyReport`)
    - Do NOT use the `.do` suffix format (e.g., `/ShowEmployee.do` is incorrect)
    - When linking to legacy Struts pages from Thymeleaf templates, use: `th:href="@{/do/ActionName}"`

- Target UI and presentation stack (future direction):
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
  - Prefer Thymeleaf fragments to reduce duplication; extract reusable sections (tables, headers, toolbars, forms) into templates/fragments and include them via th:replace/th:include.
  - Shared layout and fragments should live under a common templates/layout and templates/fragments structure.

## Migration Guidance
- Do not expand Struts/JSP usage for new features. New screens/features should use Spring MVC + Thymeleaf.
- When touching legacy screens for significant changes, consider opportunistic migration to the target stack.
- Maintain compatibility during migration; multiple UI technologies may coexist temporarily.

## Testing and Quality
- Preserve unit, integration, and UI tests across module boundaries.
- Prefer testing observable behavior at module boundaries over implementation details.

## Documentation
- Keep this document updated when architectural rules evolve.
- Align feature work and code reviews with the rules above.

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

## Code Structure and Patterns

### Module Overview
Top-level packages under `org.tb`, one module per domain capability:

| Package | Responsibility |
|---|---|
| `auth` | Authentication, authorization beans and annotations |
| `chicoree` | Legacy Struts time-reporting UI (maintenance only) |
| `common` | Shared base classes, exceptions, events, utilities |
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
- Error codes are defined in `ErrorCode` enum (`common/exception/ErrorCode.java`); format: prefix + number per module (e.g. `CU-0001` for customer)
- `ServiceFeedbackMessage` (`common/exception/ServiceFeedbackMessage.java`) wraps an `ErrorCode` + severity + optional arguments; used to accumulate messages when building veto responses

### Security Layers
Two stacked layers provide defence in depth:
- **HTTP boundary** (`@PreAuthorize` on controller): enforced by Spring Security before the method runs
- **Service boundary** (`@Authorized` + runtime guard): enforced inside the service regardless of caller
- `AuthorizedUser` (session-scoped bean, `auth/domain/AuthorizedUser.java`): exposes `isManager()`, `isAdmin()`, `isBackoffice()`, `isRestricted()`, and the current login sign
- Spring Security roles: `RESTRICTED`, `BACKOFFICE`, `MANAGER`, `ADMIN`; `manager` role includes admins; `backoffice` includes managers and admins

### Thymeleaf Fragment Catalogue
Reusable fragments live in `src/main/resources/templates/fragments/`.

**`form-fields.html`**
- `textInput(field, label, required, maxlength)` — text input with inline validation
- `textInputHelp(field, label, required, maxlength, helpText)` — text input with help text
- `textareaInput(field, label, required, rows, monospace)` — textarea
- `textareaInputHelp(field, label, required, rows, helpText, monospace)` — textarea with help text
- `selectInput(field, label, required, placeholder, options, optionValue, optionLabel)` — select dropdown
- `checkboxSwitch(field, label)` — Bootstrap 5 toggle switch
- `dateInput(field, label, required)` — date picker input
- `formButtons(saveLabel, cancelHref)` — save + cancel footer buttons

**`master-table.html`**
- `masterTable(addHref, addLabel, addIf, addIcon, thead, tbody)` — table with optional add button
- `masterTableFilter(addHref, addLabel, addIf, addIcon, filterHref, filterValue, thead, tbody)` — table with a text-only filter form; for advanced filters (checkboxes, extra dropdowns) write the filter form inline (see `customer-order-list.html` or `employee-contract-list.html` as examples)
- Column helpers: `colTextPrimary`, `colText`, `colTextAdd`, `colYesNo`, `colEditLink`, `colEditLinkIf`, `colDeleteForm`, `colDeleteFormIf`

**`layout/base.html`** — full page shell: navbar, section/subSection active state, toast message rendering.

### Database Migration Convention
- Single Liquibase YAML file: `src/main/resources/db/changelog/db.changelog-master.yaml` — always append; never edit existing changesets
- Always guard with `preConditions: onFail: MARK_RAN` and a `columnExists` / `tableExists` check
- Boolean columns: `type: bit(1)` (Hibernate expects `bit`; `boolean` or `tinyint` will fail schema validation)
- `author` field: use the committer's initials (e.g. `author: kr`)