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
- **Reading across a module boundary that the import direction allows** (→ ADR-0021): a module may
  join another module's entities in its own query, but those entities must not leave the query —
  the result is a record or DTO of plain values and ids, a controlled copy. That copy is what makes
  extracting a module later a replication task instead of a remodelling. Four constraints come with
  it:
  - Every module the query **traverses** must be import-legal too, not just the one it names — a
    path over `Timereport.employeecontract.employee` reads `employee`.
  - No entity as a record component, no interface projection that navigates in the caller, no
    `Object[]`/`Tuple` — each hands the association graph back out.
  - Read only: no `@Modifying`, no DML, no cascade across the boundary. Writing stays with events.
  - JPQL, never `nativeQuery = true`: a JPQL query at least breaks at application start when the
    other module renames something.

  Authorization is then the reading service's responsibility; where a per-row filter of the owning
  module no longer applies, say at the call site why it is moot or covered otherwise, and name the
  premises that argument rests on. Nothing enforces any of this automatically — `ArchitectureTest`
  sees class dependencies, and the contents of a `@Query` are a string.

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
  - **Die Tab-Reihenfolge ist die Reihenfolge des Dokuments** (#1064). Kein positiver `tabindex`,
    im Markup so wenig wie aus JavaScript; erlaubt ist allein `-1` für ein Ziel, das nur gezielt
    angesprungen wird. Wo der Einstieg liegt, entscheidet nicht die Reihenfolge, sondern
    `focusEntryField()` in `salat.js`: beim Laden das erste Feld des ersten Formulars in
    `.page-body` — bei einer Liste das erste Feld des Filters. An der Navigation vorbei führt der
    Sprunglink in `layout/base.html`. Einzelheiten in [`docs/ui-style-guide.md`
    §3.1](docs/ui-style-guide.md).
  - **A rationale belongs in a parser comment `<!--/* … */-->`** (#1056). Thymeleaf removes that form
    while parsing, so it never reaches the browser; a plain `<!-- … -->` is passed through unchanged
    and stands in the source of the delivered page. A comment that explains *why* something is built
    the way it is — with or without an issue number — addresses the development team and has no place
    there. A pure section marker (`<!-- DAILY MODE -->`, `<!-- Row 1: Date + Duration -->`) may stay
    an HTML comment. Nothing in the build checks this: telling a rationale from a section marker is a
    judgement, and a rule triggering on every `#NNN` would misfire on the next section marker that
    carries one.

## Bestätigungen und modale Dialoge (→ ADR-0027)

**Es gibt kein `confirm()`, `alert()` oder `prompt()`.** Jede Rückfrage läuft über den gemeinsamen
Bestätigungsdialog: einmal als Fragment (`fragments/confirm-dialog.html`) in `layout/base.html`,
ausgelöst über `data-confirm`-Attribute am Formular oder an einem einzelnen Submit-Knopf, gefüllt
vom delegierten Handler in `static/js/salat.js`. Kein Template bringt dafür eigenes JavaScript mit,
und kein zweiter handgeschriebener Bestätigungsdialog kommt daneben.

```html
<form th:action="@{/customers/delete}" method="post"
      data-confirm
      th:data-confirm-title="#{main.customer.delete.confirm.title}"
      th:data-confirm-detail="${cu.shortName}"
      th:data-confirm-detail-secondary="${cu.name}"
      th:data-confirm-label="#{main.general.button.delete.text}"
      data-confirm-variant="danger">
```

**Ein modaler Dialog nennt in seiner Meldung die fachlichen Schlüsselinformationen des betroffenen
Geschäftsobjekts** — so viel, dass zwei benachbarte Zeilen derselben Liste auseinanderzuhalten
sind, denn der Dialog verdeckt genau die Zeile, an der man gegenprüfen würde. Das gilt für **jeden**
Dialog mit einer Meldung, nicht nur für Bestätigungen. Die Datenbank-ID ist keine fachliche
Information. Wo es kein einzelnes Objekt gibt, benennt der Text den **Umfang** der Aktion (Zeitraum,
Anzahl, Bereich); wo der Umfang erst eingegeben wird, holt `data-confirm-detail-input` ihn aus dem
Feld. Die Attributliste steht in [`docs/ui-style-guide.md` §5.5](docs/ui-style-guide.md).

Zwei Fallstricke, die beide daher kommen, dass der Dialog zwischen Klick und Aktion steht:

- **`th:attr` wertet seine Ausdrücke eingeschränkt aus** — eine Bohne (`${@durationUtils…}`) ist
  dort so wenig erreichbar wie in einem Nachrichtenparameter (siehe „Globally Accessible Objects").
  Erst mit `th:with` binden, dann im Attribut verwenden.
- **Der Handler hängt am `submit`-Ereignis in der Capture-Phase** und stoppt es dort. HTMX
  registriert seinen Auslöser am Formular selbst; alles andere als Capture ließe ein `hx-post`
  abgehen, bevor die Frage beantwortet ist. Ausgelöst wird danach mit `requestSubmit()`, nicht mit
  `submit()`: nur das erste behält den auslösenden Knopf und die HTML5-Validierung.

## Farben und Kontrast (→ ADR-0025)

Farbwerte kommen **ausschließlich aus Tabler-Tokens** (`--tblr-*`); es gibt keine eigene
Marken-Palette. Ein Literal im Stylesheet oder in einem Template ist ein Fehler, solange es nicht
in der Ausnahmeliste von [`docs/ui-style-guide.md` §7.1](docs/ui-style-guide.md) steht — dort sind
die vier verbliebenen benannt und begründet. Eines davon ist das Gelb selbst: Tabler kennt nur
`#f59f00`, einen Bernstein, deshalb setzt `salat.css` `--tblr-yellow` und `--tblr-warning` auf
`#ffcc00`. Alles Abgeleitete mischt Tabler aus dem Token und folgt von selbst.

Wo ein Wert zur Laufzeit gebraucht wird (Diagramme), wird das Token gelesen statt abgeschrieben:
`--tblr-<name>` vom `body`, wie es `tabler.tabler.getColor` tut. **Modusabhängige Tokens taugen
dafür nicht** — `--tblr-body-color` löst zu `light-dark(#374151, #e5e7eb)` auf, was keine
Diagrammbibliothek parst; der aufgelöste Wert steht in der berechneten Textfarbe des `body`.

**Der verbindliche Kontrastmaßstab ist WCAG AA: 4,5:1 für jeden Text**, unabhängig von seiner
Rolle, in **beiden** Farbmodi; 3:1 für reine Nicht-Text-Elemente. Es gibt keine Kulanzstufe für
Sekundärtext. Die Sidebar ist immer dunkel (`data-bs-theme="dark"`) — Textfarben darin sind auch im
hellen Modus gegen `#1f2937` zu prüfen.

Werte werden **gemessen, nicht geschätzt**; Verfahren, Messtabellen und die Korrekturen in
`salat.css` stehen im Style Guide. Fallstricke, die eine naive Prüfung verfehlt:

- Chrome gibt `color-mix()` als `color(srgb …)` zurück — ohne Auflösung über ein Canvas liefert die
  Auswertung Unsinn.
- **Wer den Farbmodus zur Laufzeit umschaltet, misst zu früh.** Tabler animiert die Farbe eines
  `.btn`; direkt nach dem Setzen von `data-bs-theme` trägt es noch die Farbe des alten Modus
  (`btn-link` dunkel: sofort 2,24:1, ausgeklungen 4,9:1). Chrome gibt einen interpolierenden Wert
  als `oklab(…)` zurück statt als `color(srgb …)`. Nach dem Moduswechsel warten und Sofort- gegen
  Nachmessung vergleichen.
- `bg-*-lt` setzt nicht nur den Hintergrund, sondern auch die **Textfarbe**. Eine getönte Fläche
  ohne eigenes `text-*` ist deshalb trotzdem eingefärbt.
- **In Tabellen steckt die Tönung der Zeile im Innenschatten, nicht im Hintergrund.** Streifung und
  `table-active` setzen `box-shadow: inset 0 0 0 9999px …`; die Zelle meldet `background-color:
  rgba(0, 0, 0, 0)`. Wer die Schichten nur über `background-color` zusammensetzt, misst gegen den
  falschen Untergrund — und dort sitzt regelmäßig die ungünstigste Probe.
- **Eingebundene Fremd-Stylesheets bringen eigene Paletten mit** und codieren sie hart. Sie liegen
  in WebJars und fallen bei einer Suche über `src/` nicht auf. TomSelect ist der bekannte Fall
  (`salat.css` fängt es ab); ein neu eingebundenes Stylesheet ist erst fertig eingebunden, wenn
  seine Farben gegen **beide** Farbmodi geprüft sind.
- Ein ersetztes Bedienelement bringt auch eigene **Maße** mit. TomSelect rechnet mit Zeilenhöhe
  `1.5`, Bootstrap hier mit `1.4285` — ohne Angleichung springt die Feldhöhe, sobald das Element
  übernommen wird.
- **Ein geprüfter Ruhezustand sagt nichts über den Hover.** Tablers Hover-Füllung heißt `-darken`,
  mischt aber mit 20 % Transparenz gegen den Untergrund: auf hellem Grund hellt sie auf, auf
  dunklem dunkelt sie ab — jeweils in die Richtung, in der der Text verliert. Beide Zustände
  messen.
- **Manche Töne lassen sich als Text nicht sättigen — das ist keine Nachlässigkeit, sondern die
  Farbe.** Ein Text mit 4,5:1 auf heller Fläche liegt unter einer relativen Leuchtdichte von 0,17,
  und dort ist jedes Gelb ein Braun; umgekehrt ist auf der dunklen Karte jedes Rot unterhalb von
  4,5:1, solange es nicht aufgehellt wird. Aufhellen über Weiß entsättigt. Wo beides zugleich
  gebraucht wird, liefert `oklch(from <token> <L> <zu hohe Buntheit> h)` den Rand des sRGB-Raums —
  das sättigste, was bei dieser Helligkeit möglich ist. Wo auch das nicht reicht, gehört die Farbe
  in die Fläche statt in die Schrift (gelbe Badge: dunkle Schrift auf voller Farbe).
- **Ein Farbwert, den es zweimal gibt, driftet.** Die Füllung einer Badge und die semantische
  Textfarbe desselben Namens sind im hellen Modus **derselbe Ton** (`text-danger` = Füllung von
  `.badge.bg-danger-lt` = `#cb3636`, #1043). Wer einen Anteil ändert, ändert beide; sonst stehen
  zwei Rottöne für dieselbe Aussage nebeneinander im Bild.
- **Eine modusabhängige Textregel gehört in `:where()`.** `[data-bs-theme="dark"] .text-danger`
  wäre spezifischer als `.bg-danger-lt` und holte den Farbton auf die getönte Fläche zurück — genau
  dorthin, wo er nur 4,45:1 erreicht. Mit `:where()` bleibt die Spezifität gleich und die
  Reihenfolge entscheidet weiter, wie #1022 es eingerichtet hat.
- **Bei Buttons entscheidet die Füllung über die Textfarbe.** Tabler nimmt für jede gefüllte
  Variante dasselbe Fastweiß (`--tblr-<farbe>-fg`), unabhängig davon, wie hell die Füllung ist.
  Die Variable ist der richtige Hebel — sie färbt auch Hover, Aktiv und die gefüllte Hover-Fläche
  der Outline-Varianten. Ausnahme `btn-link`: dort überschreibt ein Literal die Variable, `color`
  muss direkt gesetzt werden.

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
- [ ] `main` is checked out and up-to-date: `git checkout main && git pull`
- [ ] A dedicated branch has been created: name must start with `feature/` (new capability) or `bug/` (defect fix), e.g. `feature/683-multiple-supervisors`

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
- [ ] Security: `@Authorized(requires…)` on every controller write method; `@Authorized` + runtime guard in the service. No `@PreAuthorize` (#926)
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
- [ ] Comments stating a rationale use the parser form `<!--/* … */-->`; only section markers stay `<!-- … -->` (→ Controller and View Guidelines)
- [ ] Kein positiver `tabindex`; die Bedienelemente stehen im Markup in der Reihenfolge, in der sie bedient werden (→ Controller and View Guidelines)
- [ ] No hard-coded colour value — every colour derives from a `--tblr-*` token (→ Farben und Kontrast)
- [ ] New text colour measured in both colour modes and at or above 4,5:1 (→ Farben und Kontrast)

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
- [ ] Every commit message starts with the issue ID (or `#noissue`), e.g. `#930 - <short description>`
- [ ] PR body contains `Closes #NNN`
- [ ] Issue type set via GitHub GraphQL API
- [ ] PR description includes: *"Reviewed AGENTS.md; changes comply with architecture, view, and security guidelines."*

---

## Onboarding
- New developers are onboarded in a dialogue with the agent, not by reading: the skill
  `.claude/skills/onboarding/SKILL.md` (`/onboarding`) drives it.
- [`docs/onboarding.md`](docs/onboarding.md) describes the process, names the lead developer to
  escalate to, and maps every core concept to the document that owns it.
- This file stays the contract for both humans and agents — the onboarding points here rather than
  restating rules, so keep the rules here, not there.

## Documentation
- Keep this document updated when architectural rules evolve.
- Align feature work and code reviews with the rules above.
- [`docs/performance-tips.md`](docs/performance-tips.md) collects measured performance rules —
  consult it when adding JPA mappings, `AttributeConverter`s, repository queries or view helpers.
  Most notably: **a class mapped via `AttributeConverter` must implement `equals`/`hashCode`**,
  otherwise Hibernate treats the entity as dirty on every flush and emits UPDATEs on read-only
  requests.

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

**Dasselbe gilt für die Parameter einer Nachricht.** `#{key(${@bean.wert})}` wertet Thymeleaf im
eingeschränkten Kontext aus und bricht die Seite mit *„Instantiation of new objects and access to
static classes or parameters is forbidden in this context"* ab — ein Fehler, der erst auftritt, wenn
die Stelle überhaupt gerendert wird (die Impersonations-Zeile in `layout/base.html` trug ihn
unbemerkt bis #1033). Der **Schlüssel** einer Nachricht darf die Bohne lesen, der Parameter nicht;
also erst mit `th:with` binden:

```html
<!-- correct -->
<span th:with="actsAs=${@authorizedUser.impersonateLoginSign}"
      th:text="#{main.general.impersonation.actsas.text(${actsAs})}">...</span>

<!-- fails at render time: @beanName as a message parameter -->
<span th:text="#{main.general.impersonation.actsas.text(${@authorizedUser.impersonateLoginSign})}">...</span>
```

## TomSelect Dropdowns

All `<select>` elements use [TomSelect](https://tom-select.github.io/) for search-as-you-type behaviour. Initialisation is handled centrally in `layout/base.html` via a `querySelectorAll` on page load and again on `htmx:after:swap` (so OOB-swapped selects are picked up automatically).

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

### Free text field with remote suggestions

A field whose value is free text but where the known values should be offered while typing is an
`<input type="text" class="form-control tomselect">` — the central initialisation picks up inputs as
well as selects. What the user types always wins; the list is a convenience.

| Attribute | Purpose |
|---|---|
| `data-remote-url` | endpoint queried on every keystroke; gets `q` plus the context parameter, answers with JSON `[{key, summary}]` |
| `data-remote-context-field` | CSS selector of the field whose value scopes the search. **Point it at the select the user operates and send its id**, not at a hidden field carrying a derived value: the ticket suggestions read `#suborderId` and let the server resolve the order branch from it (#1025). A hidden mirror has to be kept in step by hand — through the change event and through every HTMX swap that re-renders the select — and it hands the endpoint a scope the browser chose |
| `data-remote-context-param` | name of the request parameter that carries that value |
| `data-fill-target` | CSS selector of a field that gets the chosen entry as `key - summary` — **only while it is untouched**: empty, or still holding exactly what an earlier pick wrote there. A text somebody typed is theirs and is never overwritten, not even on the next pick. An entry without a `summary` is text somebody typed rather than one of the offered rows, and fills nothing |
| `data-create-label` | prefix of the "use what I typed" row; pass an `#{...}` message |

Without a context value nothing is loaded, so the field degrades to a plain text input. The endpoint
belongs to the module that owns the data — a browser call crosses no module boundary, whereas a
command event would keep the coupling while hiding the import (`/jira/tickets/suggestions`, #982).
Such an endpoint must not live under `/api` or `/rest`: those are stateless filter chains for machine
clients and do not accept a browser session.

### HTMX + OOB swaps

`htmx:after:swap` re-initialises any `select.tomselect` that does not yet have a `.tomselect` instance, so OOB-replaced selects are picked up without extra work. **Do not** add a `multiple` attribute to single-select OOB replacements — if the original select was single, the OOB replacement must also be single.

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
| `<salat:select>` | `th:field` (field), `th:label` (expr), `required` (optional) | `<div class="mb-3">` with stacked label, `<select class="form-select tomselect">`, auto `<small id="{field}-subtext">` for subtext, and `invalid-feedback` div; body is the `<option>` elements |
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
| `budget` | Budget planning and cost controlling |
| `common` | Shared base classes, exceptions, events, utilities |
| `error` | Custom error page (cross-cutting; may depend on auth, employee, common) |
| `customer` | Customer management |
| `dailyreport` | Time reports and working days |
| `employee` | Employee and contract management |
| `etl` | Data integration / extract-transform-load |
| `favorites` | User favorites for quick access |
| `invoice` | Invoice generation and settings |
| `jira` | Jira integration and replication; may import `order` — a replication is scoped to a place in the order tree (#1025). Since #1007 it writes booked hours back as worklogs, but it must **not** import `dailyreport`: the sums come through a command event in `jira.command` that `dailyreport` answers, and `dailyreport` may import `jira` for exactly that |
| `notification` | Notifications |
| `order` | Customer orders, employee orders, suborders |
| `reporting` | Report definitions and scheduling |
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
| `OrderFlatRate` | Stammdaten | `validFrom`/`validUntil` |
| `OrderFlatRateInstalment` | Stammdaten | inherits the validity of its `OrderFlatRate` |
| `Timereport` | Bewegungsdaten | soft-delete (`deleted` + `@SQLRestriction`) |
| `TimereportBudgetAssignment` | Bewegungsdaten | — (gelöst oder gelöscht) |
| `Workingday` | Bewegungsdaten | — |
| `Overtime` | Bewegungsdaten | — |
| `OrderRevenue` | Bewegungsdaten | — |
| `ETLExecutionHistory` | Bewegungsdaten | — |
| `ScheduledReportExecutionHistory` | Bewegungsdaten | — |
| `StatisticValue` | Bewegungsdaten | — |
| `JiraTicket` | Bewegungsdaten | — |
| `JiraWorklogSync` | Bewegungsdaten | — (gelöscht, sobald das Worklog in JIRA gelöscht wird) |
| `Favorite` | Bewegungsdaten | — |

### Entity Pattern
- All JPA entities extend `AuditedEntity` (`common/domain/AuditedEntity.java`)
- `AuditedEntity` provides: `@Id @GeneratedValue(IDENTITY)`, Spring Data audit fields (`created`, `lastupdate`, `createdby`, `lastupdatedby`), optimistic locking via `@Version updatecounter`, `equals`/`hashCode` by ID
- Standard entity annotations: `@Entity`, `@Getter @Setter` (Lombok), `@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)`
- Boolean columns in the database must be `bit(1)` — Hibernate maps `Boolean` to `bit`, not `tinyint`. In Liquibase migrations always use `type: bit(1)`.

### Criteria-Abfragen über den EntityManager (#1092)

Eine einzige Stelle baut ihre Abfragen über den `EntityManager` statt über ein Spring-Data-Repository:
`TimereportListDAO`. Dieselbe zur Laufzeit gebaute Bedingung aus **Filter und Sichtbarkeit** muss dort
drei Dinge beantworten, und nur das erste davon kann ein Repository:

| | über ein Repository? |
|---|---|
| Die Zeilen | **ja** — `JpaSpecificationExecutor.findAll(Specification, Pageable)` kann dynamisches Prädikat, Sortierung und Obergrenze |
| Die Summen über alle Treffer (`count`, `sum`, `sum(case …)`, zweimal `count(distinct)`) | **nein** — `Specification` selektiert immer die Entität; `JpaSpecificationExecutor` kennt nur `count(Specification)`, keine eigenen Aggregate |
| Die Werte der Filter (fünf `distinct`-Projektionen unter demselben Prädikat) | **nein** — die Fluent-API von `findBy` projiziert auf Entitäten, nicht auf einzelne Spalten, und kennt kein `distinct` |

Die Zeilenabfrage bleibt trotzdem hier, statt als einzige ins Repository zu wandern: sonst stünde
dieselbe Bedingung zweimal im Code, einmal als `Specification` und einmal als `Predicate`. Ein
Sicherheitsprädikat an zwei Stellen zu pflegen ist ein Fehler, der beim ersten Auseinanderdriften
Daten freigibt.

Wer eine zweite solche Stelle anlegt, begründet sie genauso — für alles andere bleibt es bei
Repository und `Specification`.

### Repository-Zugriff in Services (→ ADR-0019)
- Spring Data repositories extend `PagingAndSortingRepository<E, Long>` and `CrudRepository<E, Long>`; custom queries use `@Query` with multiline JPQL text blocks
- **Neue Module**: Services verwenden das Repository direkt — kein DAO-Wrapper. (→ ADR-0019)
- **Legacy-Module** (Struts-Ära): DAOs (`@Component`, `@RequiredArgsConstructor`) bleiben bestehen und werden nicht aktiv migriert. Neue Features in Legacy-Modulen können den bestehenden DAO erweitern, müssen es aber nicht.
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
- Class annotations: `@Controller @RequestMapping(“/path”) @RequiredArgsConstructor @Authorized(requireUnrestricted = true)`
  - **`@Authorized` without arguments only means *authenticated*** — it does **not** exclude
    `RESTRICTED`. Leaving out `requireUnrestricted = true` opens the exact hole that #919 had to
    close: every authenticated user, including externals and interns, could reach the views by
    knowing the URL. Spell the requirement out on every controller.
  - The available switches are `requiresAuthentication` (default `true`), `requireUnrestricted`,
    `requiresBackoffice`, `requiresPeopleLead`, `requiresManager`, `requiresAdmin`, `permitAll`.
- Write operations get `@Authorized(requiresManager = true)` on the method
- **`@PreAuthorize` does not appear in this codebase** (#926, → ADR-0006). Both forms guard the same
  method, but they do not ask the same question: `hasRole` reads the authorities of the
  `SecurityContext`, `@Authorized` asks `AuthorizedUser` — and that one knows the impersonated
  login. With both in use it depended on the annotation whether a page answered the real or the
  impersonated person, and controller and service could contradict each other. `ArchitectureTest`
  rejects `@PreAuthorize` on a class or a method.
  - There is no *or*: `hasAnyRole('MANAGER','PEOPLE_LEAD')` was `requiresPeopleLead` all along,
    because the roles are cumulative. A genuine either/or is a runtime guard in the service, not an
    expression.
  - A method-level `@Authorized` **replaces** the class-level one, it does not add to it. That is
    safe for `requiresManager` on a `requireUnrestricted` class — a status is one value, so a
    manager is never restricted — but it is not a general licence to weaken.
- Filter persistence goes through `UiState` (→ ADR-0022), not through the session and not through a
  `containsKey` check on the request: the filter remembers a registered `f…` parameter and supplies
  it again as a fallback, so a controller only declares `@RequestParam(required = false) String
  fCustomerOrderSign`. Where a submit must be told apart from a mere page call — because it triggers
  something expensive — that hangs on a hidden field of the form (`evaluate`), never on whether the
  filter parameter is present: the fallback makes it present on every request (#1009)
- Redirect-After-Post: successful writes return `”redirect:/...”` with `redirectAttributes.addFlashAttribute(“toastSuccess”, ...)`
- Form validation errors: call the service inside `try/catch(ErrorCodeException)`, convert via `ErrorCodeViewHelper.toViewMessages(ex)`, add to model, and re-render the form view (do not redirect)
- HTMX partial updates: use `th:hx-post`, `hx-swap=”none”`, `hx-include=”closest form”`, `hx-trigger=”change”` on select/input elements; detect `HX-Request` header in the controller and return `”view :: fragmentName”` for partial responses
- CSRF tokens: **never** add an explicit `<input type=”hidden” th:name=”${_csrf.parameterName}” th:value=”${_csrf.token}” />`. Spring Security 6 uses deferred tokens — `_csrf` is null when accessed directly in templates. Using `th:action=”@{...}”` is sufficient; Thymeleaf's `CsrfRequestDataValueProcessor` injects the token automatically into every POST form.
- **UiState parameters are named `f…`** (→ ADR-0022): see the section below before registering a key.
- **No direct `HttpSession` access** (→ ADR-0013): new Spring MVC controllers must not read or write `HttpSession` for UI state. Pass state via URL parameters, path variables, or form fields. User selection state (e.g. currently selected employee contract) must be expressed in the URL or stored in a cookie — not the session. Exceptions (`AuthorizedUser`, `AuthorizedEmployee`, impersonation) must be documented with a comment `// ADR-0013: Ausnahme — [Begründung]` at the point of use.

### UiState: Filter Parameters Carry the Prefix `f` (→ ADR-0022)

`UiStateFilter` remembers a selection across page changes and puts every remembered value **under
the request as a fallback parameter** — `getParameter`, `getParameterValues` and `getParameterMap`
answer with it wherever the request itself brings none. The mapping is global across all modules
and applies to every request, whatever its method or path.

A name registered in a `UiStateKeyContributor` therefore acts in every form of the application.
That is why the two namespaces are kept disjoint:

- **Every registered parameter name is `f` + a capital letter**: `fYear`, `fMonth`,
  `fCustomerOrderId`, `fCustomerFilter`, `fEmployeeOrderShowHidden`. `UiStateKeyRegistry` refuses a
  name without the prefix at startup — the application does not come up.
- **A form field is never called `f…`**. `UiStateParameterNamingTest` checks that no registered
  name is bound via `th:field`, appears in a POST form, or is a field of a `*Form` class.
- The prefix replaces the old module abbreviations: names are spelled out (`cFilter` →
  `fCustomerFilter`, `eoFilter` → `fEmployeeOrderFilter`).
- **Filter forms carry the parameters directly** — `<input name="fCustomerFilter">`, not a bound
  form object. A filter has no `*FilterForm`.
- **A create form may use the filter values as optional input**: `createForm(@RequestParam(required
  = false) Long fCustomerId, …)` prefills the new entry with what the list has selected. That is
  the wanted half of the mechanism — but the **link** to that form passes nothing (see below).

### Saving Never Changes the Filter (→ ADR-0023)

A filter is the user's setting; adding or editing an entry is no reason to change it. A store
method therefore never calls `uiState.clearState(...)`. Where the filter may hide what was just
saved, the success message says so:

```java
filterHintViewHelper.addSuccess(redirectAttributes,
    messages.getMessage("form.suborder.message.stored", "Suborder saved successfully"),
    SUBORDER_FILTER, CUSTOMER_ID, CUSTOMER_ORDER_ID);
```

The hint travels as its own flash attribute (`toastSuccessHint`) and the toast gives it a line of
its own — it is about the list, not about the saving.

Pass only the filters that can **exclude** an entry — search text and selections. `showHidden` and
`showInactive` only ever widen a list and can never be the reason something is missing.

**A link to a create form carries no filter parameter either** — the click would rewrite the
filter, and an *empty* value (the usual case: the link renders whatever the list had) is a
parameter that is present, so the fallback stops supplying the remembered one and the form opens
with nothing preselected. `th:href="@{/orders/suborders/create}"`, not `@{/orders/suborders/create(fCustomerId=…)}`:
the fallback prefills the form anyway. Where the form must start with something *other* than the
current selection, the link names the **form field** — `/orders/suborders/create?customerorderId=42`
after creating an order — and the controller falls back to the remembered value without it.

### The `hide` Flag (UX Declutter)
The `hide` boolean flag is a UX feature: it removes an entity from all dropdown select inputs in forms, keeping the app compact when a customer, order, or suborder is no longer actively used but must not be deleted (e.g. historical records still referenced by time reports). Hidden records remain in the database and in list management views, but are suppressed everywhere a user picks from a list.

Rules:
- Entities with a `hide` flag: `Customer`, `Customerorder`, `Suborder`, `Employee`, `Employeecontract`.
- `Employeeorder` has no own `hide` — it inherits visibility from its parent `Suborder` and `Customerorder`.
- All service/DAO methods that populate dropdowns must exclude hidden records by default (apply `notHidden()` spec or equivalent).
- The list management view exposes a “Show hidden” toggle so managers can still see and edit hidden records.

**The one exception: the record a select already stores** (→ #1005). A select box that carries an
already stored reference must offer the stored record even when it is hidden. Hiding declutters the
choice of something *new*; it must never make an existing record uneditable, and it must never
silently rewrite one. A select whose stored value is missing from its options cannot mark anything,
so the browser preselects the first option — and that value is what a save writes back. That is how a
hidden customer came to be displayed as a different customer, and how a hidden parent suborder moved
its whole subtree to the top level.

- Build such a list from a `getSelectable…(keep…)` method of the **owning** service —
  `CustomerService.getSelectableCustomers(keepId)`,
  `CustomerorderService.getSelectableCustomerorders(keepSign)`,
  `SuborderService.getSelectableSubordersByCustomerorderId(id, keep…)`,
  `EmployeeService.getSelectableEmployees(keepSign)`. Not an ad-hoc “add it back if absent” block in
  the controller: the rule then lives in as many places as there are forms.
- Where a stored value can be filtered out for reasons **other** than `hide` — expired validity,
  authorization — a `getSelectable…` method is not enough, because it only knows `hide`. Those places
  keep their own fallback (`CustomerorderController.addFormModel`,
  `EmployeeorderController.addFormModel`); do not "unify" them away.
- Mark such an entry with `${@hiddenMarkerViewHelper.suffix(x.hide)}` appended to the option text
  (`common/viewhelper/HiddenMarkerViewHelper`, message key `main.general.hidden.suffix`). An entry
  that is only in the list because the record stores it has to say so, otherwise the form claims it is
  available for picking.

### Gültigkeitszeiträume: aktiv und inaktiv (→ ADR-0029)

Viele Entitäten tragen einen Gültigkeitszeitraum (`fromDate`/`untilDate` bzw.
`validFrom`/`validUntil`). Für sie alle ist „aktiv" und „inaktiv" **zeitlich** definiert, und zwar
allein über das **Ende**:

- **inaktiv** = der Gültigkeitszeitraum liegt **vollständig in der Vergangenheit**, das Ende liegt
  also vor dem heutigen Tag.
- Ein Ende **am heutigen Tag** ist noch aktiv — der Vergleich ist **einschließend** (`>= heute`).
- Ein **offenes Ende** ist nie inaktiv, gleichgültig ob es als `null` oder als Sentinel `2999-12-31`
  (`LocalDateRange.FINIT_UNTIL_BOUNDARY`) abgelegt ist. Der Sentinel braucht keinen eigenen Fall:
  kein wirklicher Tag liegt danach, also kann er nie vor heute liegen.
- Ein **Beginn in der Zukunft** ist **nicht** inaktiv, sondern noch nicht aktiv. Der Beginn gehört
  deshalb nicht in das Prädikat. Solche Datensätze bleiben sichtbar: eine im Voraus angelegte
  Änderung darf nicht aus der Liste verschwinden, sonst wird sie ein zweites Mal angelegt.

Warum nur das Ende zählt, steht in [ADR-0029](docs/adr/0029-inaktiv-ist-zeitlich-und-zaehlt-nur-das-ende.md)
samt der verworfenen Alternative: wer eine Änderung im Voraus anlegt und sie danach nicht mehr
sieht, legt sie ein zweites Mal an.

**Die Regel steht genau einmal im Code: `org.tb.common.Validity`.** Beide Seiten derselben Frage
liegen dort nebeneinander, damit eine Liste und die Zeile, die sie rendert, nicht auseinanderlaufen
können:

```java
// in Java, auf der Entität
public boolean getCurrentlyValid() {
    return !Validity.isInactive(untilDate);
}

// in der Abfrage, im DAO
if (!TRUE.equals(showInactive)) {
    predicates.add(Validity.<Suborder>notInactive(Suborder_.untilDate).toPredicate(root, query, builder));
}
```

Eine fünfte Kopie des Prädikats ist damit ein Fehler. Wer eine weitere Entität mit
Gültigkeitszeitraum anlegt, ruft `Validity`, statt `untilDate >= today` noch einmal zu schreiben.

**Davon zu unterscheiden, und ausdrücklich nicht dasselbe:**

- **`hide`** — die manuelle Entscheidung, einen Datensatz aus Auswahllisten zu nehmen, unabhängig
  von jedem Datum (siehe „The `hide` Flag", → ADR-0012). `notHidden()` und `Validity.notInactive(...)` sind zwei
  Prädikate und werden über zwei Schalter zugeschaltet; sie gehören nie in eine Bedingung. Ein `or`
  zwischen ihnen macht beide wirkungslos.
- **Explizite Boolean-Flags** wie `OrderBudget.active`, `JiraReplicationConfig.enabled`,
  `ScheduledReportJob.enabled`. Sie heißen ebenfalls „aktiv", meinen aber eine gesetzte Entscheidung,
  kein Datum. Wo beides auf derselben Entität existiert — `OrderBudget` hat `active` **und**
  `validFrom`/`validUntil` —, sind es zwei unabhängige Kriterien und sie dürfen nicht in einem Filter
  vermischt werden. `BudgetController.list` meint mit `showInactive` heute das **Flag**; das ist
  richtig so und sagt nichts über den Zeitraum.
- **„Gilt am Tag X"** — eine Stichtagsfrage, die den Beginn **mitprüft**
  (`Suborder.isValidAt(date)`, `Employeeorder.isValidAt(date)`,
  `Validity.isInactiveOn(untilDate, date)` prüft ihn gerade nicht). Sie ist für
  Geschäftsregeln richtig („darf an diesem Tag gebucht werden") und als Aktiv-Filter falsch: sie
  blendet den im Voraus angelegten Datensatz aus. Ein `isValidAt` hinter einem Schalter namens
  `showInactive` ist ein Fehler.

Entitäten mit Gültigkeitszeitraum: `Customerorder`, `Suborder`, `Employeeorder`,
`Employeecontract`, `OrderBudget`, `OrderPricing`, `OrderFlatRate` (+ `OrderFlatRateInstalment`),
`EmployeeCost`, `EmployeeCostAssignment`, `AuthorizationRule`.

Zwei Eigenheiten, die die Erhebung zu #950 zutage gefördert hat und die man kennen muss:

- **Wo das offene Ende als `null` steht und wo als Sentinel, ist nicht einheitlich.** `null` im
  Auftrags- und Vertragsbereich, Sentinel `2999-12-31` bei `order_pricing`, `order_budget`,
  `employee_cost` und `employee_cost_employee` (Spaltenvorgabe im Liquibase-Changelog, `NOT NULL`).
  `Validity` behandelt beide gleich, sonst nichts — eine Ablösung des Sentinels wäre eine
  Datenmigration.
- **Die Pauschale kennt kein offenes Ende.** Bei `OrderFlatRate` heißt ein fehlendes „bis" „noch
  nicht eingegeben", nicht „läuft weiter" (→ Konditionen an einem Budgetplan).

### Revenue in the Budget Module
An order earns from two sources, and they add up (#972):

- **Hourly** — a time report priced with the `OrderPricing` rate that matches its suborder, employee, budget plan and date. No booking, no revenue.
- **Flat rate** — an `OrderFlatRate` amount falling due on a date, regardless of any booking: a maintenance retainer, an initial fee, the instalments of a fixed price order. Several definitions per order are normal and add up; there is deliberately no overlap rule.

Rules that follow from this:
- `BudgetControllingRow.revenueEuro` is the **hourly** part only. Every figure derived from revenue — budget utilization, overrun, gross profit, margin — must read `totalRevenueEuro()`, which is the sum of both. Reading `revenueEuro` for those would silently drop the flat rates.
- A flat rate schedule is derived in exactly one place, `OrderFlatRate.dueAmountsWithin`. The form preview and the controlling both call it, so a rate cannot be previewed as one calendar and evaluated as another.
- Which plan a flat rate amount counts against follows `FlatRateAllocation.uniquePlanFor`: the plan the flat rate **names**, or — where it names none — the one active plan whose period contains the due date and whose scope covers it. Where several plans qualify and none is named, none is chosen; the amount is reported as being without a budget, exactly as an ambiguous booking is. Never guess a plan; double counting and silent reassignment are both worse than an explicit "without budget". A named plan is not weighed against period and scope again — the saving did that (→ Konditionen an einem Budgetplan) — but it only counts while it is **active**, so a deactivated plan drops its amounts to "without budget" just as it does its bookings.
- The unit of allocation is a single due amount, not the definition: a monthly rate spanning two plans has each month counted against the plan it falls into.
- **Dashboard and alerts end their window today**, never at the plan's own end (`BudgetControllingService.evaluatedUntil`). They answer "where does this plan stand", which is a question about the present; reading a plan to its end counted what has not happened yet — a monthly flat rate running to December contributed all twelve months in June. The cut applies to the budget as well: an adjustment taking effect in November has not been granted yet. With both ends cut, a dashboard row says exactly what a controlling evaluation up to today says, and the row links to that window rather than to a wider one. The controlling view itself keeps its explicit `from`/`until` filter and is not capped.

### Konditionen an einem Budgetplan (#1065)

Kundenstundensatz und Pauschale können **zusätzlich** einem Budgetplan zugeordnet werden. Das Feld
ist optional, alle Bestandsdatensätze sind planlos, und ohne Plan verhält sich alles wie zuvor. Die
Wirkung ist auf beiden Seiten eine andere, und das ist beabsichtigt:

- **Beim Stundensatz ist der Plan eine Rangstufe, kein Schalter.** `OrderPricingLookup` prüft ihn an
  *beiden* Stellen: `Candidate.covers` lässt einen plangebundenen Satz nur für seinen Plan zu,
  `bySpecificity` ordnet ihn über den planlosen. Nur als Filter gebaut ließe er fremde Pläne
  gewinnen; nur als Rang gebaut bepreiste er jede Buchung. Die Reihenfolge lautet **Kürzel, dann
  Plan, dann Musterlänge, dann id** — die Stufe sitzt unter dem Mitarbeitendenbezug und über dem
  Unterauftrag, und das ist die einzige Einfügung, die bestehende Zahlen nicht verändert.
- **Bei der Pauschale nagelt der Plan eine hergeleitete Zuordnung fest** (siehe oben).
- **Die Plan-id der Buchung kommt aus der gespeicherten Zuordnung**, nie aus einer Herleitung:
  `BudgetControllingService.scoreReports` reicht `planOfBooking` durch, `BudgetEmployeeService` die
  id des Plans, dessen Seite es rendert. Es gibt keine zusätzliche Abfrage je Buchung — die Plan-id
  wandert in den `MemoKey` des Lookups.
- **`OrderPricing.isOrderWide` verlangt zusätzlich, dass kein Plan gesetzt ist.** Ein plangebundener
  Satz bepreist nur die Buchungen seines Plans, deckt den Auftragszeitraum also nicht ab und darf
  die Lückenprüfung `hasUncoveredPeriod` nicht befriedigen.
- **`findOverlapping` führt den Plan im Schlüssel.** Zwei Sätze mit gleichem Auftrag, Muster und
  Kürzel, aber verschiedenen Plänen sind keine Überlappung — ein plangebundener Satz neben dem
  planlosen, den er verengt, ist der Zweck der Stufe.
- **Welche Pläne zur Auswahl stehen, entscheidet `OrderBudgetBinding`**, und zwar für Auswahl *und*
  Speichern: gleicher Auftrag, sich schneidende Geltungsbereiche, sich überschneidende
  Gültigkeiten. Eine nachweislich tote Kombination wird abgewiesen, nicht nur ausgeblendet
  (`BU-0028`, `BU-0029`). Zwei Fälle antworten ohne einen einzigen Unterauftrag: ein auftragsweiter
  Satz trifft jeden Plan seines Auftrags, ein auftragsweiter Plan jeden Satz — sonst scheiterte die
  Prüfung an einem Auftrag ohne (sichtbare) Unteraufträge.
- **Eine Bedingung der Auswahl greift erst, wenn ihr Feld gefüllt ist.** Beim Anlegen steht die
  Gültigkeit **unter** dem Plan im Formular; wer sie vorab verlangt, liefert genau dann eine leere
  Auswahl, wenn sie bedient wird — und die Leermeldung behauptet dann, kein Plan passe, obwohl
  keiner geprüft wurde. Beim Bearbeiten fällt das nicht auf, weil das Datum vorbelegt ist. Verloren
  geht dadurch nichts: die Gültigkeit ist Pflichtfeld, das Speichern prüft ohnehin alle drei
  Bedingungen, und die Auswahl verengt sich, sobald das Feld gefüllt ist. Bei der Pauschale zählt
  dafür der **ganze** Zeitraum — sie kennt kein offenes Ende, ein fehlendes „bis" heißt dort „noch
  nicht eingegeben" und nicht „läuft weiter".
- **Aktiv ist ein Auswahlkriterium, kein Speicherkriterium.** Inaktive Pläne stehen nicht zur
  Auswahl, ein bereits gespeicherter bleibt aber in der Liste und bleibt speicherbar — sonst würde
  das Deaktivieren eines Plans den Satz, der an ihm hängt, unbearbeitbar machen.

### Budget Assignments Follow a Changed Plan
A booking is assigned to a budget plan explicitly (#913), and that assignment is what every
evaluation reads. Editing the plan therefore has to bring its assignments back in line (#974):

- `OrderBudgetService.update` compares what the plan covers — validity period and scope — before and
  after the edit, and only then calls `TimereportBudgetAssignmentService.revalidateAssignmentsOf`. A
  renamed plan or a moved alert threshold cannot invalidate an assignment.
- Revalidation looks only at the bookings **of that plan**. A booking the change newly brings into
  the plan's reach belongs to another plan or to none; pulling it in would take it away from a
  decision somebody else made. Bulk assignment (#911) and the backfill (#910) serve that direction.
- An assignment that survives the change is left alone, including a deliberate manual one. One the
  change invalidated moves to the single other active plan covering the booking, or is dropped when
  none or several do — the booking then shows up under "without budget".
- Deactivating a plan is deliberately **not** part of its coverage: an inactive plan keeps its
  assignments, and the controlling reports its bookings as unplanned.
- `TimereportBudgetAssignmentService` loads and authorizes a plan through `OrderBudgetRepository` +
  `BudgetAuthorization` rather than through `OrderBudgetService`. That service has to be able to call
  this one, so going the other way would close a bean cycle.

### List View Filter Toggles
List views that support both validity and visibility filtering expose two independent boolean toggles in the advanced filter section:
- `showInactive` (`Boolean`) — when `true`, includes inactive records; default `null`/`false` leaves them out
- `showHidden` (`Boolean`) — when `true`, includes records whose `hide` flag is set; default `null`/`false` excludes hidden records

**Der Schalter heißt überall `showInactive`** (#950) — im Request-Parameter, im `UiState`-Schlüssel,
im Modellattribut, im Feld des Templates und im Parameter von Service und DAO. Weder `show` noch
`showInvalid` noch `showOnlyValid`: „invalid" klingt nach fehlerhaften Daten, und ein Schalter mit
umgekehrter Bedeutung zwingt jede Schicht, beim Durchreichen nachzudenken. Beide Schalter können
eine Liste nur **erweitern**; das ist der Grund, warum sie nach ADR-0023 nicht in den Filterhinweis
einer Erfolgsmeldung gehören.

**DAO layer**: these are separate `Specification` predicates — never bundle `notHidden` into the
validity predicate. Apply each independently:
```java
if (!TRUE.equals(showInactive)) predicates.add(Validity.<Suborder>notInactive(Suborder_.untilDate).toPredicate(root, query, builder));
if (!TRUE.equals(showHidden))   predicates.add(notHidden().toPredicate(root, query, builder));
```

**Entity without own `hide` field** (e.g. `Employeeorder`): filter on the parent's flag via a join — `notHidden()` checks `suborder.hide` and `suborder.customerorder.hide`.

**Persistenz des Filters**: über `UiState` (→ ADR-0022), nicht über die Session. Der Parametername
ist `f` + Entität + `ShowInactive` bzw. `ShowHidden` (`fSuborderShowInactive`,
`fEmployeeContractShowHidden`) und in einem `UiStateKeyContributor` registriert. Wer ihn umbenennt,
benennt Request-Parameter, Schlüssel, Modellattribut und Template-Feld gemeinsam um — sonst zeigt
der Schalter den gemerkten Stand nicht mehr an.

**Template**: add both as `form-check form-switch` checkboxes in the advanced filter row, using `name=”fXShowInactive”` / `name=”fXShowHidden”` with `value=”true”` and `th:checked`, each with the hidden `value="false"` twin so switching off sends a value; the auto-submit script picks them up automatically.

### DTO Pattern
- Class annotations: `@Builder @Data @Jacksonized @AllArgsConstructor`
- Static factory method `from(Entity)` maps entity → DTO (include all audit fields)
- Mutator `copyTo(Entity)` applies mutable fields back to the entity; never overwrite `id` or audit fields
- `isNew()` helper returns `id == null`
- Modern modules use Java `record` for simple, immutable DTOs
- When writing a `Boolean` from DTO to entity, guard against null: `Boolean.TRUE.equals(value)`

### Event / Veto / Command Pattern
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
Two stacked layers provide defence in depth — **both spelled `@Authorized`** (#926, → ADR-0006):
- **HTTP boundary** (`@Authorized` on the controller class, and on a method where it differs):
  enforced by `AuthorizationAspect` before the handler method runs
- **Service boundary** (`@Authorized` + runtime guard): enforced inside the service regardless of caller
- **The answer to a denial comes from one place**: `AuthorizationException` is a plain
  `RuntimeException`, so nothing in Spring Security sees it — unhandled it leaves the
  `DispatcherServlet` as a **500**. `AuthorizationExceptionHandler` (`common/web`) answers it with
  `403` (`401` for `AA-0001`), as a `ProblemDetail` under `/api` and `/rest` and otherwise through
  `sendError` so the application's own error page renders. Whoever adds a second place where an
  authorization decision is made has to answer this question again.
- `AuthorizedUser` (**request-scoped** bean, `auth/domain/AuthorizedUser.java`): exposes `isManager()`, `isAdmin()`, `isPeopleLead()`, `isBackoffice()`, `isRestricted()`, and the current login sign. It holds no state of its own — it reads the `SecurityContext` per request. A scheduled job has no `SecurityContext`, so it must call `authorizedUser.initForJob()` first (→ ADR-0006).
- Spring Security roles: `USER`, `RESTRICTED`, `BACKOFFICE`, `PEOPLE_LEAD`, `MANAGER`, `ADMIN`; `manager` role includes admins; `backoffice` includes managers and admins; `people_lead` includes managers and admins
- Role semantics (derived from `SalatUser.status` at login):
  - `USER` — base role granted to every authenticated user = every employee
  - `RESTRICTED` — external employees (contractors) and interns (`status=restricted`); heavily limited access, cannot access most list/management views
  - `BACKOFFICE` — Backoffice (`status=bo`) plus everyone with `MANAGER`; can create invoices and upload financial data from books and records
  - `PEOPLE_LEAD` — people leads/supervisors (`status=pv`) plus everyone with `MANAGER`; can read time reports and contracts of their team members, run reports
  - `MANAGER` — general manager (`status=bl`) plus `ADMIN`; can manage contracts, orders, and time reports for everyone
  - `ADMIN` — system administrators (`status=adm`); full access; only role that is NOT an employee
  - A "regular employee" is someone with `BACKOFFICE` but not `PEOPLE_LEAD` or `MANAGER` — they can only view their own data.

### Ein Ereignis-Listener entscheidet über keinen HTTP-Status (#1054)

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
  Die Pfadlisten stehen einmal in `org.tb.common.filter.RequestPaths`.

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

### JSON Columns (→ ADR-0024)

A column whose content is a set of values only the configuration knows is mapped as native `json`
via `@JdbcTypeCode(SqlTypes.JSON)` on a `Map` attribute. So far exactly one place does this:
`JiraTicket.customFields` / `customFieldsEffective` (#881).

- **Reading a value out of the JSON is MySQL-specific and belongs in ETL/report SQL or in a view** —
  never in a JPQL query or a repository method. H2 2.4 knows neither `JSON_VALUE` nor `JSON_EXTRACT`,
  so such a query cannot be tested. The persistence model itself stays portable, and the round trip
  is covered against H2 (`JiraTicketCustomFieldsTest`).
- **The empty state is `NULL`, never `{}` and never an empty string.** A native `json` column cannot
  hold an empty string at all, and `JSON_EXTRACT` on an empty document aborts with `ERROR 3141` —
  taking the whole ETL statement with it. `JSON_EXTRACT(NULL, …)` answers `NULL`.
- Where the stored document depends on a configured list, keep a hash of that list next to it and
  rewrite the row when the hash differs. Build the hash from the **configured value** (trimmed,
  sorted), not from the stored JSON: MySQL normalises JSON on write.

### CSV über einen `HttpMessageConverter`

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

### Reserviert in den Parametern einer REST-Schnittstelle

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
