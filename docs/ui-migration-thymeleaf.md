### UI Migration Proposal: Struts 1.2 + JSP to Thymeleaf + Bootstrap 5

#### Goals
- Modernize the UI stack to Thymeleaf templates rendered by Spring MVC controllers.
- Preserve functionality and URLs during migration where feasible.
- Adopt Bootstrap 5 for consistent, responsive styling.
- Migrate incrementally with Struts and Thymeleaf coexisting until completion.

---

### High-level Strategy
1. Coexistence: Add Thymeleaf to the application while keeping Struts/JSP running. Use parallel routes (e.g., /reporting/jobs2) for migrated screens during the pilot phase.
2. Pilot Feature: Migrate “Scheduled Report Jobs” (list + create/edit) end-to-end. This exercises tables, forms, validation, i18n, navigation, and security.
3. Shared Layout: Create a Bootstrap 5 base layout and fragments to replace common JSP includes (head-includes.jsp, menu.jsp, message rendering).
4. Incremental Rollout: Migrate feature-by-feature, validate with stakeholders, then flip the default route and retire the Struts view.
5. Decommission: After all features are migrated, remove Struts, JSP taglibs, and legacy resources.

---

### Architectural Changes
- Controllers: Replace Struts Actions with Spring MVC @Controller methods returning String view names.
- Views: Thymeleaf templates in src/main/resources/templates using Bootstrap 5.
- Forms & Validation: Spring MVC binding with @ModelAttribute + Jakarta Validation annotations and BindingResult for errors (replacing Struts form beans).
- I18n: Use Spring MessageSource with Thymeleaf’s #{key} syntax (replacing <bean:message/> tag usage).
- Security: Keep the existing @Authorized approach; expose authorization flags to views via model attributes where needed or adopt Spring method security annotations.
- Session: Continue using session-scoped AuthorizedUser; usable in controllers and templates (e.g., ${authorizedUser.loginSign}).

---

### Dependencies (pom.xml)
Add the following dependencies to enable Thymeleaf UI rendering. Keep JSP/Struts deps during migration.

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
<dependency>
  <groupId>nz.net.ultraq.thymeleaf</groupId>
  <artifactId>thymeleaf-layout-dialect</artifactId>
  <version>3.3.0</version>
</dependency>
<!-- Optional if using Spring Security dialect for templating -->
<dependency>
  <groupId>org.thymeleaf.extras</groupId>
  <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

No explicit view resolver is necessary in Spring Boot; it auto-configures Thymeleaf for templates under classpath:/templates/.

---

### Structure and Templates
- Base layout: templates/layout/base.html
  - Contains <head> with Bootstrap 5 CSS/JS (via WebJars or CDN), navbar, container, footer.
  - Define fragments for head, navbar, messages, and content blocks.
- Common fragments: templates/fragments/
  - head.html, navbar.html, messages.html
- Feature templates: templates/reporting/
  - scheduled-jobs-list.html
  - scheduled-job-form.html

Example base layout snippet:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
  <meta charset="UTF-8"/>
  <title th:text="${pageTitle}">Salat</title>
  <link rel="stylesheet" href="/webjars/bootstrap/5.3.8/css/bootstrap.min.css"/>
  <link rel="stylesheet" href="/webjars/bootstrap-icons/1.13.1/font/bootstrap-icons.min.css"/>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-dark bg-dark">
  <div class="container-fluid">
    <a class="navbar-brand" th:href="@{/}">Salat</a>
    <!-- add menu items here -->
  </div>
</nav>
<main class="container my-4" layout:fragment="content">
  <!-- page-specific content -->
</main>
<script src="/webjars/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
</body>
</html>
```

Example list page using the layout:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout" layout:decorate="~{layout/base}">
<main layout:fragment="content">
  <h1>Scheduled Report Jobs</h1>
  <div class="mb-3">
    <a class="btn btn-primary" th:href="@{/reporting/jobs2/new}"><i class="bi bi-plus-lg"></i> Create Job</a>
  </div>
  <table class="table table-striped align-middle">
    <thead>
      <tr>
        <th>Name</th><th>Report</th><th>Recipients</th><th>Enabled</th><th>Schedule</th><th>Last Updated</th><th></th>
      </tr>
    </thead>
    <tbody>
      <tr th:each="job: ${jobs}">
        <td th:text="${job.name}"></td>
        <td th:text="${job.reportDefinition.name}"></td>
        <td th:text="${job.recipientEmails}"></td>
        <td>
          <span th:if="${job.enabled}" class="text-success">✓ Yes</span>
          <span th:if="${!job.enabled}" class="text-danger">✗ No</span>
        </td>
        <td>
          <div th:text="${#strings.isEmpty(job.cronExpression) ? defaultCron : job.cronExpression}"></div>
          <small class="text-muted" th:text="${cronDescriptions[job.id]}"></small>
        </td>
        <td th:text="${#temporals.format(job.lastupdate, 'dd.MM.yyyy HH:mm')}"></td>
        <td class="text-end">
          <a class="btn btn-sm btn-outline-secondary" th:href="@{'/reporting/jobs2/' + ${job.id} + '/edit'}"><i class="bi bi-pencil"></i></a>
          <form th:action="@{'/reporting/jobs2/' + ${job.id} + '/delete'}" method="post" class="d-inline">
            <button class="btn btn-sm btn-outline-danger" onclick="return confirm('Delete?')"><i class="bi bi-trash"></i></button>
          </form>
        </td>
      </tr>
    </tbody>
  </table>
</main>
</html>
```

---

### Controller Design (Pilot)
```java
@Controller
@RequestMapping("/reporting/jobs2")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class ScheduledReportJobController {
  private final ScheduledReportJobService jobService;
  private final ReportingService reportingService; // to fetch report definitions

  @GetMapping
  public String list(Model model) {
    var jobs = jobService.getAllJobs();
    model.addAttribute("jobs", jobs);
    // compute defaultCron and cronDescriptions similar to ShowScheduledReportJobsAction
    return "reporting/scheduled-jobs-list";
  }

  @GetMapping("/new")
  public String createForm(Model model) { /* ... */ return "reporting/scheduled-job-form"; }

  @PostMapping
  public String create(@Valid @ModelAttribute("job") ScheduledReportJobForm form, BindingResult br, RedirectAttributes ra) { /* ... */ }

  @GetMapping("/{id}/edit")
  public String editForm(@PathVariable Long id, Model model) { /* ... */ return "reporting/scheduled-job-form"; }

  @PostMapping("/{id}")
  public String update(@PathVariable Long id, @Valid @ModelAttribute("job") ScheduledReportJobForm form, BindingResult br, RedirectAttributes ra) { /* ... */ }

  @PostMapping("/{id}/delete")
  public String delete(@PathVariable Long id, RedirectAttributes ra) { /* ... */ return "redirect:/reporting/jobs2"; }
}
```

Form DTO example:
```java
public record ScheduledReportJobForm(
  @NotBlank String name,
  @NotNull Long reportDefinitionId,
  String reportParameters,
  @NotBlank String recipientEmails,
  boolean enabled,
  String cronExpression,
  String description
) {}
```

---

### Tag/Feature Mapping Cheat Sheet
- <bean:message key="k"/> → [[#{k}]] in Thymeleaf or th:text="#{k}"
- JSTL c:forEach, c:if, c:out → th:each, th:if, th:text
- Struts html:form, html:text, html:textarea, html:checkbox → Regular HTML + th:object/th:field
- Custom java8 date tag → #temporals.format(object, pattern)
- Include head/menu jsp → layout dialect fragments

---

### Coexistence and URLs
- Keep legacy routes (e.g., /ShowScheduledReportJobs) for now.
- Add new routes under a parallel path (/reporting/jobs2). After validation, redirect legacy to new.
- Ensure CSRF tokens are enabled for POST forms (Spring Security) or explicit configuration if not using security.

---

### Testing
- MockMvc tests for controller endpoints (GET list, GET create/edit, POST create/update/delete).
- Thymeleaf template rendering smoke tests (assert status and selected text appear).

---

### Rollout Plan
- Milestone 1 (Pilot): Implement baseline + Scheduled Jobs list/form; user demo; collect feedback.
- Milestone 2 (Core Screens): Migrate Reports listing/execution pages.
- Milestone 3 (Daily Report, Employee, Order screens).
- Milestone 4: Replace remaining legacy, flip routes, and remove Struts.

---

### Decommission Steps
- Remove Struts dependencies from pom.xml and custom taglibs.
- Delete legacy JSPs and webapp directory structure gradually.
- Clean up web.xml / struts-config (if present) and Struts Actions.

---

### Risks & Mitigations
- Risk: Inconsistent look & feel → Adopt base layout early and reuse fragments.
- Risk: Hidden coupling to Struts form beans → Introduce DTOs and mapping layer; migrate validators.
- Risk: Mixed navigation → Temporary duplication acceptable; use clear banner or URL prefix for new UI.
- Risk: Time overrun → Migrate per feature; prioritize high-impact screens first.
