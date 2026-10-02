package de.hbt.salat.jira.controller;

import static java.util.stream.Collectors.toMap;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraReplicationConfigData;
import de.hbt.salat.jira.domain.JiraReplicationConfigInfo;
import de.hbt.salat.jira.service.JiraReplicationConfigService;
import de.hbt.salat.jira.service.JiraReplicationLauncher;
import de.hbt.salat.jira.service.JiraReplicationRunService;
import de.hbt.salat.jira.viewhelper.JiraReplicationRunViewHelper;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Maintains the JIRA replications (#984) — the rows that used to be edited by hand via SQL.
 *
 * <p>Managers only. Since #982 the replicated tickets are offered to everyone who books, so a
 * misconfigured replication shows up as missing suggestions rather than only in the log; the
 * credentials behind it are still a matter for the management, not for the backoffice.
 */
@Controller
@RequestMapping("/jira/replications")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class JiraReplicationConfigController {

  /** How many runs the run tab shows — about a day of hourly runs over the usual number of configs. */
  static final int RUN_LIMIT = 200;

  /** The fragment that opens the run tab. */
  static final String RUNS_TAB = "#tab-runs";

  private final JiraReplicationConfigService jiraReplicationConfigService;
  private final JiraReplicationLauncher jiraReplicationLauncher;
  private final JiraReplicationRunService jiraReplicationRunService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

  /**
   * The page has two tabs (#1282): the replications to maintain, and the runs they left. The tab
   * comes from the fragment of the address ({@code #tab-runs}), which Tabler opens on its own — the
   * request never sees it, so both lists are always loaded.
   */
  @GetMapping
  public String list(@RequestParam(required = false) Boolean fJiraRunFailedOnly, Model model) {
    var replications = jiraReplicationConfigService.getAll();
    var namesById = replications.stream()
        .collect(toMap(JiraReplicationConfigInfo::id, JiraReplicationConfigInfo::name));
    boolean failedOnly = Boolean.TRUE.equals(fJiraRunFailedOnly);

    model.addAttribute("replications", replications);
    model.addAttribute("runningReplicationIds", jiraReplicationRunService.getRunningReplicationIds());
    model.addAttribute("runs", jiraReplicationRunService.getLatestRuns(RUN_LIMIT, failedOnly).stream()
        .map(run -> JiraReplicationRunViewHelper.from(run, namesById))
        .toList());
    model.addAttribute("fJiraRunFailedOnly", failedOnly);
    return "jira/replication-list";
  }

  @GetMapping("/create")
  public String createForm(Model model) {
    addFormModel(model, new JiraReplicationConfigForm());
    return "jira/replication-form";
  }

  @GetMapping("/{id}/edit")
  public String editForm(@PathVariable long id, Model model) {
    var info = jiraReplicationConfigService.getById(id);
    addFormModel(model, JiraReplicationConfigForm.of(info,
        jiraReplicationConfigService.customerorderSignOf(info.scopeSign())));
    model.addAttribute("lastMaxUpdated", info.lastMaxUpdated());
    return "jira/replication-form";
  }

  /**
   * The field catalogue of one replication, as the body of the picker dialogue (#1013).
   *
   * <p>A read, hence {@code GET}, and only ever for a stored config: the id is the whole input, so
   * nobody can point this at an address of their choosing. Deliberately not under {@code /api} or
   * {@code /rest} — those are stateless filter chains for machine clients and do not accept a
   * browser session.
   */
  @GetMapping("/{id}/fields")
  public String fields(@PathVariable long id, Model model) {
    model.addAttribute("fieldCatalog", jiraReplicationConfigService.getSelectableFields(id));
    return "jira/replication-fields :: fieldPicker";
  }

  @PostMapping("/store")
  @Authorized(requiresManager = true)
  public String store(@ModelAttribute("replicationForm") JiraReplicationConfigForm form,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    var data = new JiraReplicationConfigData(
        form.getName(),
        form.getScopeSign(),
        form.getBaseUrl(),
        form.getApiFlavor(),
        form.getUsername(),
        form.getPassword(),
        form.getJql(),
        form.getParentFieldNames(),
        form.getAdditionalFieldNames(),
        form.getInheritedFieldNames(),
        form.getPageSize(),
        form.isEnabled(),
        form.isWorklogSyncEnabled(),
        form.getWorklogSyncFrom(),
        form.isWorklogSyncInvoiceableOnly()
    );

    try {
      if (form.isNew()) {
        jiraReplicationConfigService.create(data);
        redirectAttributes.addFlashAttribute("toastSuccess",
            messages.getMessage("main.jira.replication.message.created"));
      } else {
        jiraReplicationConfigService.update(form.getId(), data);
        redirectAttributes.addFlashAttribute("toastSuccess",
            messages.getMessage("main.jira.replication.message.updated"));
      }
    } catch (ErrorCodeException ex) {
      // A typed password is not carried back into the re-rendered form: the field starts empty
      // again, which the service reads as "keep the stored one" — never as "clear it".
      form.setPassword(null);
      model.addAttribute("formErrors", toMessages(ex));
      addFormModel(model, form);
      return "jira/replication-form";
    }
    return "redirect:/jira/replications";
  }

  @PostMapping("/{id}/delete")
  @Authorized(requiresManager = true)
  public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationConfigService.delete(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.message.deleted"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications";
  }

  @PostMapping("/{id}/enabled")
  @Authorized(requiresManager = true)
  public String setEnabled(@PathVariable long id, @RequestParam boolean enabled,
                           RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationConfigService.setEnabled(id, enabled);
      redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage(
          enabled ? "main.jira.replication.message.enabled" : "main.jira.replication.message.disabled"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications";
  }

  @PostMapping("/{id}/reset-watermark")
  @Authorized(requiresManager = true)
  public String resetWatermark(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationConfigService.resetWatermark(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.message.watermarkreset"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications";
  }

  /**
   * Starts the replication and returns at once (#1282): the run goes on in the background, like a
   * manually started ETL run, and stands as "running" in the run tab the page lands on.
   */
  @PostMapping("/{id}/run")
  @Authorized(requiresManager = true)
  public String run(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      var name = jiraReplicationConfigService.getById(id).name();
      jiraReplicationLauncher.startManualRun(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.message.started", new Object[]{name}));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications" + RUNS_TAB;
  }

  /** Sets a run that only stands on "running" because of a crash to finished (#1282). */
  @PostMapping("/runs/{runId}/mark-finished")
  @Authorized(requiresManager = true)
  public String markRunFinished(@PathVariable long runId, RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationRunService.markFinished(runId);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.run.message.markedfinished"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications" + RUNS_TAB;
  }

  /**
   * Refills the suborder select when the customer order changes (#1025). Offering the suborders of
   * every order would be a list of several thousand entries, and the scope of a replication is
   * always a place below one order.
   */
  @PostMapping("/suborders")
  @Authorized(requiresManager = true)
  public String suborders(@ModelAttribute("replicationForm") JiraReplicationConfigForm form,
                          Model model, HttpServletRequest request) {
    form.setSuborderSign(null); // the previous pick belongs to the order that was just replaced
    addFormModel(model, form);
    model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
    model.addAttribute("subordersChanged", true);
    return "jira/replication-form";
  }

  private void addFormModel(Model model, JiraReplicationConfigForm form) {
    model.addAttribute("replicationForm", form);
    model.addAttribute("apiFlavors", JiraApiFlavor.values());
    model.addAttribute("isEdit", !form.isNew());
    model.addAttribute("customerorders",
        customerorderService.getSelectableCustomerorders(form.getCustomerorderSign()));
    model.addAttribute("suborders", subordersOf(form));
  }

  /**
   * The suborders of the selected order, at any depth — empty while none is selected. The suborder
   * a replication already points at stays in the list once it is hidden, so that an edit cannot drop
   * the scope and silently write back whatever the browser preselected instead (→ AGENTS.md, #1005).
   */
  private List<Suborder> subordersOf(JiraReplicationConfigForm form) {
    var sign = form.getCustomerorderSign();
    if (sign == null || sign.isBlank()) {
      return List.of();
    }
    var customerorder = customerorderService.getCustomerorderBySign(sign);
    return customerorder == null ? List.of()
        : suborderService.getSelectableSubordersByCustomerorderId(
            customerorder.getId(), form.getSuborderSign());
  }

  private List<String> toMessages(ErrorCodeException ex) {
    return errorCodeViewHelper.toViewMessages(ex).stream().map(message -> message.resolved()).toList();
  }

  private String firstMessageOf(ErrorCodeException ex) {
    return toMessages(ex).stream().findFirst().orElse(ex.getMessage());
  }
}
