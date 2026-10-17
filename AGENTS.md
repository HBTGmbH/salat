# Architecture and Agents Overview

This document captures the architectural rules and direction for the project to guide day-to-day decisions and long-term evolution.
See also README.md

## Wo welche Regel steht

Diese Datei trägt die Regeln, die bei jeder Aufgabe gelten, und wird ganz gelesen. Das
Nachschlagewissen zu einzelnen Themen steht unter `docs/guides/` und gilt ebenso verbindlich: vor einer
Änderung das Dokument lesen, dessen Thema sie berührt (#1484).

| Dokument | Inhalt | Lesen, wenn … |
|---|---|---|
| [`docs/guides/ui.md`](docs/guides/ui.md) | View-Regeln, Bestätigungen und modale Dialoge, Befehlspalette, Tastenkürzel, sortierbare Listen, Farben und Kontrast, Objekte in `layout/base.html`, TomSelect, `salat:`-Dialekt, UiState und Filter, Filter-Umschalter, Listen mit Obergrenze, gruppierte Listen | ein Template, `salat.js`, `salat.css` oder ein Controller mit Liste oder Filter geändert wird |
| [`docs/guides/backend-patterns.md`](docs/guides/backend-patterns.md) | Entity, Criteria, Repository, Service, Controller, DTO, Events, Exceptions, Fehlermeldungen, Security, Listener, gleichzeitige Änderungen, Secrets, Flags, JSON, i18n, Migrationen, CSV, Dauer und Uhrzeit, REST-Parameter, Legacy-Redirects | Java-Code in `domain`, `persistence`, `service`, `controller`, `rest` oder `listener`, ein Changeset oder ein Message-Bundle geändert wird |
| [`docs/guides/domain-rules.md`](docs/guides/domain-rules.md) | Stammdaten und Bewegungsdaten, das `hide`-Flag, Gültigkeitszeiträume, Budget | eine Änderung Stammdaten, das Ausblenden, Gültigkeitszeiträume oder das Budget berührt |
| [`docs/performance-tips.md`](docs/performance-tips.md) | Gemessene Performance-Regeln | JPA-Mappings, `AttributeConverter`, Repository-Queries oder View-Helper hinzukommen |
| [`docs/beta-funktionen.md`](docs/beta-funktionen.md) | Beta-Funktionen: Einführung, Messung, Ende | eine Änderung als Beta eingeführt wird |
| [`docs/adr/`](docs/adr/README.md) | Warum eine Regel gilt und welche Alternativen verworfen wurden | eine Regel in Frage steht oder sich ändern soll |

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
- **Reading across a module boundary that the import direction allows** (→ ADR-0021): a module may
  join another module's entities in its own query, but those entities must not leave the query —
  the result is a record or DTO of plain values and ids, a controlled copy. Four constraints come
  with it:
  - Every module the query **traverses** must be import-legal too, not just the one it names — a
    path over `Timereport.employeeorder.employeecontract.employee` reads `order` and `employee`.
  - No entity as a record component, no interface projection that navigates in the caller, no
    `Object[]`/`Tuple`.
  - Read only: no `@Modifying`, no DML, no cascade across the boundary. Writing stays with events.
  - JPQL, never `nativeQuery = true`.

  Authorization is then the reading service's responsibility; where a per-row filter of the owning
  module no longer applies, say at the call site why it is moot or covered otherwise, and name the
  premises that argument rests on. `ArchitectureTest` enforces the Java side of it
  (`noEntityCrossesAModuleBoundaryThroughAService`, #1244): no call from another module into a
  non-private `@Service` method with a JPA entity in its signature. The violations that existed when
  the rule came in are frozen in `src/test/resources/archunit_store`; a new one fails the build —
  return a record instead of adding it to the store. The contents of a `@Query` are a string, and
  remain a matter of review.
- **Read model of the owning module** (→ ADR-0021, Nachtrag #1338): where a value is logic of the
  entity rather than a column — the path in the order tree, the effective order type — the owning
  module offers a record through its service, computed with its own rules (`SuborderReadModel`,
  `CustomerorderOption`, `PublicholidayService.getPublicHolidayDatesBetween`). The entity does not
  change for it: no new field, `@Formula` or fetch strategy for another module's needs. The join
  stays the right choice where the reading module needs columns only and bounds the set in its own
  query (#997); a read model must not load per row.
- **References across a module boundary** (→ ADR-0036): a reference to **master data** of another
  module — customer, customer order, suborder, employee order, employee, employee contract, login
  (`SalatUser`) — may be a real `@ManyToOne`, in the import direction `ArchitectureTest` allows.
  **Transaction data stays an id** (`TimereportBudgetAssignment.timereportId`: a booking is
  soft-deleted and sensitive). Within a module the reference is the rule, transaction data included.
  A reference across the boundary is always `LAZY`, read only — no cascade, no setter called on the
  other module's entity, writing stays with events (ADR-0003) — and navigation stays within master
  data. `ArchitectureTest` enforces read only (#1371): `noSetterIsCalledOnAnEntityOfAnotherModule`
  (the calls that existed when it came in are frozen in `src/test/resources/archunit_store`; a new
  one fails the build), `noAssociationCascadesIntoAnotherModule` and
  `collectionsReachableFromAnotherModuleAreHandedOutReadOnly`: an entity another module reaches hands
  out its collections only as unmodifiable views, and its own module changes them through setters or
  explicit methods of the entity. Lists fetch it with `join fetch` or `@BatchSize`, never per
  row. Entities in service signatures across the boundary stay forbidden (ADR-0021, #1244). A
  reference is written from an id through `EntityManager.getReference` in a small component of the
  writing module (`MasterDataReferences`, `OrderReferences`), never through a service of the owning
  module.
- **A repository asks about its own aggregate** (#1369): every query of `XRepository` starts at its
  own entity or yields values, and no method returns an entity of another module — not even inside an
  `Optional` or a list (`ArchitectureTest.noRepositoryReturnsAnEntityOfAnotherModule`). A join along a
  reference into another module stays allowed; a query rooted in the other module's entity does not
  belong there.
- **A question about another module's master data alone is answered by the owning module, as a value**:
  does it exist, whose is it, is it valid — `EmployeeorderService.getEmployeeIdOfEmployeeorder`, not a
  join rebuilt in the asking module's repository. Where the asking module needs the entity as a
  reference, it takes it with `getReference` (see above).

## Testing and Quality
- Preserve unit, integration, and UI tests across module boundaries.
- Prefer testing observable behavior at module boundaries over implementation details.

### E2E tests: one browser per run, in separate runs

The E2E suite shares a single H2 database across all test classes and never cleans up the
bookings a test creates (see the class comment on `PlaywrightE2ETestBase`). A
`@ParameterizedTest` over `browsers()` therefore creates them **once per browser**, and the
second browser sees what the first one left behind.

**Always pass exactly one browser and run the browsers in separate runs:**

```
jenv exec ./mvnw test -Pe2e -De2e.browsers=chrome
jenv exec ./mvnw test -Pe2e -De2e.browsers=firefox
```

- Do **not** run `jenv exec ./mvnw test -Pe2e` without `-De2e.browsers`. That drives both
  browsers from one JVM against one database and produces failures that have nothing to do with
  the change under test — a test asserting on a per-day total reads the sum of both runs.
- CI works the same way: `.github/workflows/e2e-tests.yml` runs a `[chrome, firefox]` matrix, one
  runner and one database per browser. A green CI therefore says nothing about the two-browser
  constellation, and a failure that only appears locally is usually this and not a regression.
- When a single-browser run is green and a combined run is not, the test is at fault, not the
  code: it asserts on shared state instead of on the data it created itself (#846).

## Build and Tooling
- Always use `./mvnw` (the Maven wrapper) to build, test, and run Maven goals — never a system-wide `mvn` command.
- On macOS, prefix every `./mvnw` call with `jenv exec` so the correct JDK is on `PATH`: `jenv exec ./mvnw <goal>`.
  - If `jenv` is not installed and `java` is not found, prompt the user to install jenv (`brew install jenv`) and add the required JDK version before continuing.

## Spring Profiles

| Profile | Purpose |
|---|---|
| `local` | Daily local development: dev login via `?login-name=<sign>`, local datasource, devtools active |
| `local-qa` | Local response-time measurement; pulls in `local` via profile group and overrides only performance-relevant settings with their production values (→ ADR-0020) |
| `sqltrace` | Diagnostic overlay on top of `local-qa`: logs every SQL statement with its execution time |
| `localeasyauth` | Local run against Azure Easy Auth |
| `staging`, `production` | Deployed environments |
| `unittest` | Test execution (set by the Surefire/Failsafe `argLine`) |

**Parity rule for `local-qa`:** every entry in `application-local-qa.yaml` under `server`,
`spring` and `salat` mirrors `application-production.yaml` exactly. When a PR changes a
performance-relevant setting in `application-production.yaml`, it must change
`application-local-qa.yaml` in the same PR. Deviations require a comment in the profile
stating why (currently: actuator `metrics` exposure, Azure auth).

## GitHub Workflow
- **Repository**: `HBTGmbH/salat` on GitHub (`git@github.com:HBTGmbH/salat.git`). Issues, pull
  requests, Actions runs and releases live there. Links take the form
  `https://github.com/HBTGmbH/salat/issues/NNN` (`/pull/NNN` for pull requests); `gh` resolves the
  repository from the `origin` remote, outside a checkout pass `-R HBTGmbH/salat`. Never derive the
  owner from the company name or the mail domain — take it from `git remote get-url origin`.
- **No operating environment details in GitHub artifacts**: nothing that describes the concrete
  runtime environment may end up on GitHub — not in issues, PRs, commit messages, code, comments,
  documentation or screenshots. This covers resource, subscription, tenant, host and account names,
  deployment and management URLs, connection strings and secrets, monitoring or log queries against
  live systems, and the CLI invocations used to reach them. Describe it neutrally instead: „the
  deployed environment", „the production database", `<host>`, `<resource>`. Such details stay in the
  local working session. Reason: the repository is visible to a wider circle than the environment
  itself. A later correction does not help — GitHub keeps the edit history and git keeps the commit;
  delete and recreate the issue, and coordinate before rewriting already pushed history.
- **Branch naming**: `feature/<issue-number>-<short-description>` (e.g. `feature/606-move-fromDBtimeToString-to-DurationUtils`)
- **One issue per branch / PR**: do not bundle unrelated changes.
- **Commit messages**: every commit message starts with the issue ID, then ` - `, then a short
  description in the imperative or nominal style used so far, e.g.
  `#930 - Anfangsueberstunden mit dem Vertragsbeginn als Stichtag anlegen`. This applies to every
  commit on the branch, not just the first one. If there is no issue, use `#noissue` in its place,
  e.g. `#noissue - Tippfehler in der Anleitung korrigieren`.
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
- **Issue structure**: every issue states the current behaviour (*Ist-Zustand*, with references into
  the code), the desired behaviour (*Wunsch*), and **acceptance criteria** as a checklist. An
  implementation proposal and notes on pitfalls are optional but usual. Acceptance criteria are
  mandatory — they are what "done" is measured against.
- **Issue type**: every issue must have its type set via the GitHub GraphQL API after creation. Use `Bug` for defects and `Feature` for new capabilities. Available type IDs:
  - `Task`:    `IT_kwDOAYn5ks4AV-6C`
  - `Bug`:     `IT_kwDOAYn5ks4AV-6D`
  - `Feature`: `IT_kwDOAYn5ks4AV-6H`
  ```bash
  gh api graphql -f query='mutation { updateIssue(input: { id: "<node_id>", issueTypeId: "<type_id>" }) { issue { number issueType { name } } } }'
  # get node_id via: gh api repos/HBTGmbH/salat/issues/NNN --jq .node_id
  ```

## Releases
- Releases run through the **Release** workflow (`.github/workflows/release.yml`), started manually
  from the Actions tab on `main` with the version bump as input (`patch`, `minor`, `major`).
- `patch` releases the version `main` already carries as SNAPSHOT (`5.0.10-SNAPSHOT` → `5.0.10`),
  `minor` and `major` raise it accordingly. The next development version is always the released
  version with the patch level raised, e.g. `5.1.0` → `5.1.1-SNAPSHOT`.
- The workflow refuses to run if a check run on the commit to release has failed, or if `main` is not
  on a SNAPSHOT version.
- It then pushes the two commits `Release version X` and `Prepare next development version Y` plus
  the tag `vX` to `main`, creates the GitHub release with generated notes, and builds and publishes
  the image to GHCR.
- Release notes are grouped by the label categories in `.github/release.yml`, so labelling the merged
  PRs is what shapes the notes.
- Nothing has to be done by hand: no `pom.xml` edit, no tag, no release. A tag pushed by hand still
  triggers the image build on its own.

## Definition of Ready

Before writing any code:

- [ ] An issue exists and its type is set (`Bug`, `Feature`, `Task`) — the type belongs to the issue, not to the pull request, and is set before the branch is created
- [ ] The issue describes the current behaviour (with references into the code), the desired behaviour, and acceptance criteria as a checklist
- [ ] An issue that changes something visible carries a mockup of the desired state in its description — an ASCII or Markdown sketch in a code block, or an image without real data
- [ ] Where the code references in the issue are easier to follow with a picture, it has a simple UML class diagram, UML sequence diagram or ER diagram as a ` ```mermaid ` block (GitHub renders it) — small, only the classes, calls or tables the issue touches
- [ ] The acceptance criteria are reviewed: each one can be checked, and together they say when the issue is done. Gaps are closed in the issue before coding starts
- [ ] `main` is checked out and up-to-date: `git checkout main && git pull`
- [ ] A dedicated branch has been created: name must start with `feature/` (new capability) or `bug/` (defect fix), e.g. `feature/683-multiple-supervisors`

### Mockups and diagrams in issues

How to meet the two checklist items above.

**Choosing the form**
- ASCII or Markdown sketch in a code block: enough for a single field, a column, a changed label or
  a small dialog.
- Rendered image: for a new card, a changed table or anything whose layout matters. Build it as
  HTML and render it to PNG (below).
- Show every state the issue distinguishes, not just the full one: collapsed and expanded, with and
  without data, before and after a selection.

**Building an HTML mockup**
- Use the application's real styles: `tabler.min.css` and `bootstrap-icons.min.css` from the
  webjars (versions from `pom.xml`, the jars are in the local Maven repository) and
  `src/main/resources/static/css/salat.css`. Start from the markup of the Thymeleaf template the
  issue changes, so the mockup looks like the page it describes.
- Mark everything new or changed the same way in every mockup: a dashed magenta outline
  (`outline: 2px dashed #d63384`) and a small „neu“ badge. A caption line above each picture names
  the issue and the state shown.
- Invented data only — `Person A`, `ABC-12`, `Wartung und Betrieb`. The repository is public: no
  customer, person, order or ticket title from the local database dump.
- Numbers agree across all mockups of an issue: subtotals add up to the total, and the same booking
  carries the same duration in every picture.

**Rendering**
- Headless Chrome, full-page screenshot, 1290 px wide, device scale factor 2, light colour scheme.
- Look at every PNG before uploading it. Tabler's own padding easily swallows an indentation, and
  a cut-off last row is easy to miss.

**Storing and embedding**
- GitHub strips inline `<svg>` and `data:` URLs from issue text, and the CLI cannot attach files to
  an issue. Images therefore live on the orphan branch `issue-assets`, one folder per issue:
  `issue-assets/<issue>/<name>.png`.
- Commit them as an ordinary commit on that branch (`#<issue> - Mockup …`) from a separate
  worktree, so the working checkout stays untouched, and push:
  `git fetch origin issue-assets && git worktree add ../salat-issue-assets issue-assets`.
  The branch is never merged into `main` and never deleted — issues link to it.
- Embed with `![caption](https://github.com/HBTGmbH/salat/blob/issue-assets/<issue>/<name>.png?raw=true)`.
- To change a mockup, overwrite the same file in a new commit; the URL in the issue stays valid.
- The HTML sources stay local; only the PNG goes to GitHub.

**Diagrams**
- A ` ```mermaid ` block directly in the issue text: `classDiagram` for a new or changed model,
  `sequenceDiagram` for a flow across several classes or modules, `erDiagram` for schema changes.
- Only what the issue touches, roughly ten nodes at most. Mark new elements with „(neu)“ and new
  module dependencies on the edge, e.g. `InvoiceService ..> JiraTicketService : neue Kante invoice → jira`.

---

## Definition of Done

Before marking any task complete, work through this checklist and report which items apply and whether each is satisfied.

The checklist is applied **at the end of each task, to the sources that task changed** — it is not a
periodic audit of the whole codebase.

A feature or fix is considered done when **all** of the following are true:

### Code
- [ ] Build passes: `jenv exec ./mvnw verify` completes without errors
- [ ] All existing tests pass; new behaviour should be covered by tests
- [ ] No new cross-module cycles introduced; cross-module side-effects go through Spring events
- [ ] Controllers are thin — business logic lives in a service within the same module
- [ ] Security: `@Authorized(requires…)` on every controller write method and on the service (method or class). A runtime guard only where the role alone does not answer the question (→ [backend-patterns](docs/guides/backend-patterns.md), Service Pattern). No `@PreAuthorize` (#926)
- [ ] No unused imports in the changed files. Removing code tends to leave its imports behind, and
  the compiler does not complain. Check the files the task touched — not the whole codebase.
- [ ] Gestapelte Umgehungen sind **benannt statt fertiggebaut**: wo eine Lösung erst über die
  zweite oder dritte Umgehung gegen dasselbe Framework trägt, stehen Aufwand, Nebenwirkungen und
  Nutzen nebeneinander, und das Verwerfen ist ausdrücklich vorgeschlagen — beim zweiten Workaround,
  nicht erst nach dem grünen Testlauf. Der Nutzen von UI-Politur ist dabei leicht zu überschätzen
  (#829: die fixierte Auftragsspalte der Matrixübersicht kostete vier Umgehungen gegen Bootstrap,
  nahm im schmalen Fenster genau den Platz, den sie retten sollte, und wurde verworfen).

### Views (if UI changed)
- [ ] Uses Spring MVC + Thymeleaf
- [ ] Leaf-level form components use the `salat:` custom dialect; layout/structural reuse uses fragments
- [ ] Bootstrap 5 + Tabler components for layout and widgets
- [ ] CSRF protection relies solely on `th:action="@{...}"` — no explicit `_csrf` hidden input
- [ ] Comments stating a rationale use the parser form `<!--/* … */-->`; only section markers stay `<!-- … -->` (→ [ui](docs/guides/ui.md), Controller and View Guidelines)
- [ ] Kein positiver `tabindex`; die Bedienelemente stehen im Markup in der Reihenfolge, in der sie bedient werden (→ [ui](docs/guides/ui.md), Controller and View Guidelines)
- [ ] No hard-coded colour value — every colour derives from a `--tblr-*` token (→ [ui](docs/guides/ui.md), Farben und Kontrast)
- [ ] New text colour measured in both colour modes and at or above 4,5:1 (→ [ui](docs/guides/ui.md), Farben und Kontrast)

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
- [ ] `AGENTS.md` or the guide under `docs/guides/` that carries the rule updated if an architectural rule changed

### Pull Request
- [ ] Branch named `feature/<issue-number>-<short-description>`
- [ ] Every commit message starts with the issue ID (or `#noissue`), e.g. `#930 - <short description>`
- [ ] PR body contains `Closes #NNN`
- [ ] Issue type set via GitHub GraphQL API
- [ ] The fulfilled acceptance criteria are ticked off in the issue, wherever their checklist stands (body or comment). A criterion that is not fulfilled stays open, and the PR says why
- [ ] PR description includes: *"Reviewed AGENTS.md; changes comply with architecture, view, and security guidelines."*

---

## Onboarding
- New developers are onboarded in a dialogue with the agent, not by reading: the skill
  `.claude/skills/onboarding/SKILL.md` (`/onboarding`) drives it.
- [`docs/onboarding.md`](docs/onboarding.md) describes the process, names the lead developer to
  escalate to, and maps every core concept to the document that owns it.
- This file and the guides under `docs/guides/` stay the contract for both humans and agents — the
  onboarding points here rather than restating rules, so keep the rules here, not there.

## Documentation
- Keep this document and the guides under `docs/guides/` updated when architectural rules evolve. A rule
  that applies to every task belongs here; one that applies to a topic belongs in its guide.
- Align feature work and code reviews with the rules above.
- [`docs/performance-tips.md`](docs/performance-tips.md) collects measured performance rules —
  consult it when adding JPA mappings, `AttributeConverter`s, repository queries or view helpers.
  Most notably: **a class mapped via `AttributeConverter` must implement `equals`/`hashCode`**,
  otherwise Hibernate treats the entity as dirty on every flush and emits UPDATEs on read-only
  requests.
- [`docs/beta-funktionen.md`](docs/beta-funktionen.md) describes when a change becomes an opt-in
  beta and how it is introduced, promoted in context, measured (#1447, ADR-0039) and ended.

## Architecture Decision Records
- ADRs live in [`docs/adr/`](docs/adr/README.md) (format: MADR).
- New significant architectural decisions must be captured as an ADR before or alongside implementation.
- This file documents **what** the current rules are; ADRs document **why** a decision was made and which alternatives were considered.

## Spring Boot annotations placement
- Place cross-cutting Spring Boot/Spring Security enabling annotations on the application class (SalatApplication) unless there is a strong, explicit reason to scope them to a specific configuration class.
  - Examples: @EnableMethodSecurity, @EnableScheduling, @EnableAsync. This centralizes enablement, avoids duplicate configuration, and makes project-wide capabilities obvious.

## Agent usage policy (for code generation and refactoring)
- Agents and contributors MUST consult and adhere to this AGENTS.md, and to the guides under `docs/guides/` the
  change touches, when generating or modifying code.
- Minimum checklist before committing changes:
  - Verify dependencies and coupling follow the rules above (no new cycles; use events for cross-module collaboration).
  - For views, prefer Thymeleaf fragments and the shared layout structure.
  - Place cross-cutting Spring Boot/Spring Security enabling annotations on SalatApplication.
  - Require a permission with `@Authorized(requires…)` — on the controller and on the service alike. `@PreAuthorize` is not used here (#926, → ADR-0006).
  - Keep controllers thin; push logic to services within the same module.
  - Start the commit message with the issue ID, or `#noissue` if there is none
    (`#NNN - <short description>`).
- Pull Request note: Include a short statement like “Reviewed AGENTS.md; changes comply with architecture, view, and security guidelines.”

### Human review of agent proposals — where to spend the attention
An agent produces proposals; the responsibility stays with the person who merges them. That person
reads the diff, not just the running application. Review depth is deliberately **not** uniform:

- **Critical — everything from the service layer inwards**: `service`, `persistence`, `domain`,
  authorization (`@Authorized`, runtime guards), `event`/`listener`, and Liquibase
  changesets. Mistakes here produce wrong results, wrong numbers, data loss or unauthorized access,
  and they are typically invisible in the UI. Read these diffs line by line and demand evidence:
  which test covers it, which test run was green, what the numbers were before and after.
- **Not critical in that sense — UI code**: Thymeleaf templates, layout, styling, `viewhelper`
  formatting. Mistakes here affect UX — comfort and acceptance — and they surface immediately when
  clicking through the change. Review it, but with a different question: does it look and behave
  the way it should? It rarely produces a wrong result.

The rule of thumb: spend the review time where wrong results come from, not where wrong pixels do.

---

## Code Structure and Patterns

The modules and their layers. The patterns for entities, services, controllers and the rest are in
[`docs/guides/backend-patterns.md`](docs/guides/backend-patterns.md).

### Module Overview
Top-level packages under `de.hbt.salat`, one module per domain capability:

| Package | Responsibility |
|---|---|
| `auth` | Authentication, authorization beans and annotations |
| `beta` | Opt-in betas (#1447, ADR-0039): the switches, counting their use with a comparison group, asking about them without storing the person, and the evaluation for management; imports only `common`, `auth`, `settings` and `employee`. It knows no beta itself: each module declares its own and contributes the list through `BetaFeatureContributor` in `common.beta` (the pattern of `UiStateKeyContributor`), asking and counting through `Betas` there; no module imports `beta` except `settingseditor` |
| `budget` | Budget planning and cost controlling |
| `common` | Shared base classes, exceptions, events, utilities |
| `error` | Custom error page (cross-cutting; may depend on auth, employee, common) |
| `customer` | Customer management |
| `dailyreport` | Time reports and working days |
| `employee` | Employee and contract management |
| `etl` | Data integration / extract-transform-load |
| `favorites` | User favorites for quick access |
| `invoice` | Invoice generation and settings |
| `jira` | Jira integration and replication, and tickets maintained by hand or imported next to it (#1386, ADR-0037); may import `order` and `secret` (its credentials, #1432) — a replication is scoped to a place in the order tree (#1025). Since #1007 it writes booked hours back as worklogs, but it must **not** import `dailyreport`: the sums come through a command event in `jira.command` that `dailyreport` answers, and `dailyreport` may import `jira` for exactly that |
| `notification` | Notifications |
| `palette` | Object search of the command palette (#1157, ADR-0031): collects every module's `PaletteProvider`; imports only `common` and `auth`, and no module imports it |
| `order` | Customer orders, employee orders, suborders |
| `reporting` | Report definitions and scheduling |
| `secret` | Secrets of foreign systems, encrypted (#1432, ADR-0038): `SecretService` stores and reads them by id; imports only `common` and `auth` and knows none of the owners that import it |
| `settings` | User preference store: entity, converter, repository, service — generic map-based API, no UI |
| `settingseditor` | User preferences editing UI — aggregator; may import from **any** module; other modules must not import from `settingseditor` |
| `statistic` | Aggregations and statistics |

### Module Layer Conventions
Each module uses a consistent sub-package structure:

| Sub-package | Purpose |
|---|---|
| `domain` | JPA entities and DTOs |
| `persistence` | Spring Data repositories and DAO components |
| `service` | Business logic, transactions, authorization checks |
| `controller` | Spring MVC controllers (HTTP boundary) |
| `auth` | Module-internal authorization logic (who may see which records), used by the module's services |
| `command` | Command event classes the module publishes to read data owned by another module |
| `event` | Domain event classes |
| `listener` | `@EventListener` subscribers |
| `rest` | REST endpoints |
| `viewhelper` | View decorators and model-prep helpers |

**`viewhelper` package rule:** View helper classes (model-prep helpers, view decorators) **must stay in the `viewhelper` sub-package** of their module. They must not be placed in `domain`, `service`, or any other sub-package. Rules:
- View helpers are used by **controllers**, by **other view helpers**, and **directly in Thymeleaf templates** — never by services.
- Services must not import or return view helper types; they return domain objects or DTOs.
- View helpers may not use `HttpSession` directly; they receive domain data as constructor arguments and provide formatted/computed values for templates.
- The typical pattern: service returns `List<SomeDomainDTO>`, controller maps to `List<SomeViewHelper>` via a static `from(contract, dto)` factory, then puts the view helpers in the model.
