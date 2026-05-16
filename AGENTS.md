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
- Spring Security roles: `RESTRICTED`, `BACKOFFICE`, `MANAGER`, `ADMIN`; `manager` role includes admins; `backoffice` includes managers and admins

### Thymeleaf Fragment Catalogue
Reusable fragments live in `src/main/resources/templates/fragments/`.

**Fragment mechanics:**
- Fragment-slot parameters (markup passed as `~{::localName}`): use `_` (no-op token) to pass nothing; render inside the fragment with `th:replace="${param}"` or `<th:block th:replace="${param}"/>`.
- Scalar fragments with ≤3 params (`checkboxSwitch`, `dateInput`, `formButtons`, `col*`) use plain named params.
- Fragments with ≥4 scalars use the Typed Parameter Object pattern — a params object replaces all scalars except URL params and slot params (see section below).
- URL params (used in `th:action`, `th:href`) stay as Thymeleaf params (evaluated via `@{...}`) and are NOT in the params object.

**`form-fields.html`**

| Fragment | Parameters | Notes |
|---|---|---|
| `textInput` | `params, label` | `params` = `TextInputParams` via `@formField.*` |
| `textareaInput` | `params, label` | `params` = `TextareaParams` via `@formField.*` |
| `selectInput` | `params, label, placeholder` | `params` = `SelectInputParams` via `@formField.*`; `placeholder=null` for no placeholder option |
| `checkboxSwitch` | `field, label` | — |
| `dateInput` | `field, label, required` | — |
| `formButtons` | `saveLabel, cancelHref` | save + cancel footer buttons |

**`master-table.html`**

| Fragment | Parameters | Notes |
|---|---|---|
| `masterTable` | `params, addHref, addIf, thead, tbody` | `params` = `MasterTableParams` via `@masterTableParams.*`; `thead`/`tbody` are fragment slots |
| `masterTableFilter` | `params, addHref, addIf, filterHref, filterValue, thead, tbody` | table with text-only filter form |
| `colHeaderPrimary` | `label` | `<th>` always visible |
| `colHeader` | `label` | `<th>` hidden on xs (`d-none d-sm-table-cell`) |
| `colHeaderActionIcon` | — | action-icon column header (`w-1`) |
| `colText` | `text, responsive` | `responsive` = `null`/`''` always-visible; `'sm'`/`'md'`/`'lg'` hidden below that breakpoint |
| `colTextAdd` | `text, additional, responsive` | two-line text cell; same `responsive` convention |
| `colYesNo` | `enabled` | yes/no status cell |
| `colCron` | `cronExpr, defCron, desc` | cron expression + description cell |
| `colDate` | `date` | datetime cell formatted `yyyy-MM-dd HH:mm`; always `d-none d-sm-table-cell` |
| `colDateRange` | `fromDate, untilDate, responsive` | from-date with open/until secondary line; `responsive` same convention as `colText` |
| `colEditLink` | `href, ifCondition` | pass `true` for always-visible; expression for conditional |
| `colDeleteForm` | `id, action, ifCondition` | pass `true` for always-visible; includes hidden `filter` preservation |
| `colSlot` | `content, responsive` | wraps a fragment slot in a `<td>` with responsive class; pass `_` for empty slot |

**`filter-card.html`**

| Fragment | Parameters | Notes |
|---|---|---|
| `filterCard` | `params, formAction, primaryFilters, advancedFilters` | `params` = `FilterCardParams` via `@filterCardParams.*` |
| `filterCardJs` | — | companion script: localStorage persistence + checkbox/select auto-submit; include once per page after the card |

- `@filterCardParams.simple('key')` → no HTMX, no advanced toggle; pass `advancedFilters=_`
- `@filterCardParams.basic('key')` → no HTMX, with advanced toggle
- `@filterCardParams.htmx('key', '#target')` → with HTMX partial updates, with advanced toggle

**`danger-zone.html`**

| Fragment | Parameters | Notes |
|---|---|---|
| `dangerZone` | `params` | `params` = `DangerZoneParams` via `@dangerZoneParams.card(...)` |
| `dangerZoneModal` | `params, formAction, modalBody` | `params` = `DangerZoneModalParams` via `@dangerZoneParams.modal(...)`; inner form gets `id="${params.modalId}Form"` |

**`layout/base.html`** — full page shell: navbar, section/subSection active state, toast message rendering.

### Typed Parameter Object for Thymeleaf Fragments
An application of Fowler's *Introduce Parameter Object* — adapted to the Thymeleaf context via a Spring factory bean. All fragment params classes and factories live in `org.tb.common.viewhelper.fragment`.

**Implemented params classes and factories:**

| Params Class | Factory Bean | Key Factory Methods |
|---|---|---|
| `FilterCardParams` | `@filterCardParams` | `basic(key)`, `htmx(key, target)`, `simple(key)` |
| `MasterTableParams` | `@masterTableParams` | `withAdd(label)`, `withAdd(label, icon)` |
| `TextInputParams` | `@formField` | `text(field, maxlen)`, `required(field, maxlen)`, with optional `helpText` overloads |
| `TextareaParams` | `@formField` | `textarea(f, rows)`, `requiredTextarea(f, rows)`, `code(f, rows)`, `requiredCode(f, rows)`, `*WithHelp` variants |
| `SelectInputParams` | `@formField` | `select(f, options, valField, labelField)`, `requiredSelect(...)` |
| `DangerZoneParams` | `@dangerZoneParams` | `card(title, desc, buttonLabel, modalId)` |
| `DangerZoneModalParams` | `@dangerZoneParams` | `modal(modalId, title, warning, submitLabel)` |

Three collaborating pieces:

**1. Params class** — `@Builder @Getter` with `@Builder.Default` for optional fields:
```java
@Builder @Getter
public class FilterCardParams {
    private final String formKey;
    private final String hxTarget;           // null = no HTMX
    @Builder.Default
    private final boolean showAdvancedToggle = true;
}
```

**2. Spring factory bean** — a `@Component` with overloaded factory methods; callable from Thymeleaf as `@beanName`:
```java
@Component("filterCardParams")
public class FilterCardParamsFactory {
    public FilterCardParams basic(String formKey) { ... }
    public FilterCardParams htmx(String formKey, String hxTarget) { ... }
    public FilterCardParams simple(String formKey) { ... } // showAdvancedToggle=false
}
```

Call site in a template — URL param (`formAction`) stays as a Thymeleaf param; scalar config collapses into `params`:
```html
<div th:replace="~{fragments/filter-card :: filterCard(
    params=${@filterCardParams.htmx('co', '#results-container')},
    formAction=@{/orders/customerorders},
    primaryFilters=~{::primaryFilters},
    advancedFilters=~{::advancedFilters}
)}">
```

For i18n keys passed to factory methods (e.g. `dangerZone`), use Thymeleaf preprocessing `__#{key}__`:
```html
<div th:replace="~{fragments/danger-zone :: dangerZone(
    params=${@dangerZoneParams.card(
        __#{form.employee.anonymize.dangerzone.title}__,
        __#{form.employee.anonymize.dangerzone.description}__,
        __#{form.employee.anonymize.button}__,
        'anonymizeModal'
    )}
)}"></div>
```

**3. Single-parameter fragment** — accepts the params object and unpacks inside:
```html
<div th:fragment="filterCard(params, formAction, primaryFilters, advancedFilters)" class="card mb-3">
  <form method="get" th:action="${formAction}"
        th:attr="data-filter-form=${params.formKey},
                 hx-get=${params.hxTarget != null ? formAction : null},
                 hx-target=${params.hxTarget},
                 hx-push-url=${params.hxTarget != null ? 'true' : null}">
    <th:block th:replace="${primaryFilters}"/>
    <th:block th:if="${params.showAdvancedToggle}">
      ...
    </th:block>
  </form>
</div>
```

Rules:
- All params classes and factories live in `org.tb.common.viewhelper.fragment` (shared across modules).
- Factory bean name: camelCase class name without "Factory" suffix (`FilterCardParamsFactory` → `@filterCardParams`).
- URL params stay as Thymeleaf fragment params (never in the params object) — they need `@{...}` URL resolution.
- Fragment-slot params stay as Thymeleaf fragment expressions (`~{::localName}`) — never in the params object.
- Simple fragments with ≤3 scalars keep plain named params; don't force params objects where they add no value.
- Overload the factory for the most common call shapes; defaults cover the rest.

### Flags Column Pattern
List views that expose boolean state flags on rows use a dedicated **Flags** column rather than inline badges or text next to the primary field.

Rules:
- Column header: `th:text="#{main.general.flags.text}"`, class `d-none d-lg-table-cell` (hidden on small screens)
- Each flag is a `<span class="badge bg-<color>-lt" th:title="#{...}">` containing a Bootstrap Icon `<i class="bi bi-..."></i>`
- The **hide** flag always uses `bg-secondary-lt` and `bi-eye-slash`:
  ```html
  <td class="d-none d-lg-table-cell">
    <span th:if="${item.hide}" class="badge bg-secondary-lt" th:title="#{main.general.hide}">
      <i class="bi bi-eye-slash"></i>
    </span>
  </td>
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
  - Both files contain the full set of `main.*` Thymeleaf keys as well as legacy Struts/chicoree keys.
  - `MessageResources.properties` is the German default bundle; it is served for `de_DE` and any locale that has no specific bundle.
  - `MessageResources_en.properties` is served for the `en` locale.
- **Encoding**: both files are **ISO-8859-1**. Never use the Edit/Write tools on these files — they write UTF-8 and corrupt the content. Always use Python with `encoding='iso-8859-1'`.
- **Key order**: keys are sorted alphabetically. The correct workflow: append new key(s) to the file, then sort all lines. Never try to find the insertion point manually.
  ```python
  path = 'src/main/resources/org/tb/web/MessageResources.properties'
  with open(path, encoding='iso-8859-1') as f:
      lines = set(l for l in f.read().splitlines() if l.strip())
  lines.add('new.key=Value')
  with open(path, 'w', encoding='iso-8859-1') as f:
      f.write('\n'.join(sorted(lines)) + '\n')
  ```
- When adding a new feature, add matching keys to **both** bundles — German translation in `MessageResources.properties`, English in `MessageResources_en.properties`.

### Database Migration Convention
- Single Liquibase YAML file: `src/main/resources/db/changelog/db.changelog-master.yaml` — always append; never edit existing changesets
- Always guard with `preConditions: onFail: MARK_RAN` and a `columnExists` / `tableExists` check
- Boolean columns: `type: bit(1)` (Hibernate expects `bit`; `boolean` or `tinyint` will fail schema validation)
- `author` field: use the committer's initials (e.g. `author: kr`)