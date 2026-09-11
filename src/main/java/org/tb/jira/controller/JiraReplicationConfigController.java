package org.tb.jira.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraReplicationConfigData;
import org.tb.jira.service.JiraReplicationConfigService;

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
@PreAuthorize("hasRole('MANAGER')")
public class JiraReplicationConfigController {

  private final JiraReplicationConfigService jiraReplicationConfigService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

  @GetMapping
  public String list(Model model) {
    model.addAttribute("replications", jiraReplicationConfigService.getAll());
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
    addFormModel(model, JiraReplicationConfigForm.of(info));
    model.addAttribute("lastMaxUpdated", info.lastMaxUpdated());
    return "jira/replication-form";
  }

  @PostMapping("/store")
  @PreAuthorize("hasRole('MANAGER')")
  public String store(@ModelAttribute("replicationForm") JiraReplicationConfigForm form,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    var data = new JiraReplicationConfigData(
        form.getName(),
        form.getCustomerorderSign(),
        form.getBaseUrl(),
        form.getApiFlavor(),
        form.getUsername(),
        form.getPassword(),
        form.getJql(),
        form.getParentFieldNames(),
        form.getPageSize(),
        form.isEnabled()
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
  @PreAuthorize("hasRole('MANAGER')")
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
  @PreAuthorize("hasRole('MANAGER')")
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
  @PreAuthorize("hasRole('MANAGER')")
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
   * Starts the replication and waits for it. The button that leads here says so and disables itself
   * while the request is on its way — a replication of a large order fetches page after page.
   */
  @PostMapping("/{id}/run")
  @PreAuthorize("hasRole('MANAGER')")
  public String run(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      var outcome = jiraReplicationConfigService.runNow(id);
      if (outcome.success()) {
        redirectAttributes.addFlashAttribute("toastSuccess",
            messages.getMessage("main.jira.replication.message.executed", new Object[]{outcome.name()}));
      } else {
        redirectAttributes.addFlashAttribute("toastError", messages.getMessage(
            "main.jira.replication.message.failed", new Object[]{outcome.name(), outcome.message()}));
      }
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications";
  }

  private void addFormModel(Model model, JiraReplicationConfigForm form) {
    model.addAttribute("replicationForm", form);
    model.addAttribute("apiFlavors", JiraApiFlavor.values());
    model.addAttribute("isEdit", !form.isNew());
  }

  private List<String> toMessages(ErrorCodeException ex) {
    return errorCodeViewHelper.toViewMessages(ex).stream().map(message -> message.resolved()).toList();
  }

  private String firstMessageOf(ErrorCodeException ex) {
    return toMessages(ex).stream().findFirst().orElse(ex.getMessage());
  }
}
