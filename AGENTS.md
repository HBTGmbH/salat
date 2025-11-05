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