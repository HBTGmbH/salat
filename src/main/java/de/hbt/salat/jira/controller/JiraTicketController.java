package de.hbt.salat.jira.controller;

import static java.util.Comparator.comparing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.MediaType;
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
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraManualTicketData;
import de.hbt.salat.jira.service.JiraTicketAuthorization;
import de.hbt.salat.jira.service.JiraTicketMaintenanceService;
import de.hbt.salat.order.domain.SuborderReadModel;
import de.hbt.salat.order.service.SuborderService;

/**
 * The ticket page (#1386): the tickets of a customer order, replicated and maintained by hand, and
 * the maintenance of the latter where no replication covers the scope — one by one or by CSV import.
 *
 * <p>Managers and the people responsible for an order ({@link JiraTicketAuthorization}); the service
 * checks every order again, the class-level guard only keeps restricted users out.
 */
@Controller
@RequestMapping("/jira/tickets")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class JiraTicketController {

  private final JiraTicketMaintenanceService maintenanceService;
  private final JiraTicketAuthorization authorization;
  private final SuborderService suborderService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

  /**
   * The order filters, the suborder narrows to its branch. Both are remembered (UiState); a
   * remembered suborder of another order, or an order the user may not see, is dropped rather than
   * refused — the selection may be older than the user's responsibilities.
   */
  @GetMapping
  public String list(@RequestParam(required = false) Long fJiraTicketCustomerorderId,
                     @RequestParam(required = false) Long fJiraTicketSuborderId,
                     Model model) {
    var customerorderId = fJiraTicketCustomerorderId != null && authorization.mayMaintain(fJiraTicketCustomerorderId)
        ? fJiraTicketCustomerorderId : null;
    var suborders = suborderOptions(customerorderId, fJiraTicketSuborderId);
    var suborderId = suborders.stream().anyMatch(so -> Objects.equals(so.id(), fJiraTicketSuborderId))
        ? fJiraTicketSuborderId : null;

    model.addAttribute("customerorders", authorization.selectableCustomerorders(customerorderId));
    model.addAttribute("suborders", suborders);
    model.addAttribute("fJiraTicketCustomerorderId", customerorderId);
    model.addAttribute("fJiraTicketSuborderId", suborderId);
    if (customerorderId != null) {
      model.addAttribute("tickets", maintenanceService.getTickets(customerorderId, suborderId));
      model.addAttribute("coveringReplications",
          maintenanceService.getCoveringReplications(customerorderId, suborderId));
    }
    return "jira/ticket-list";
  }

  @GetMapping("/create")
  public String create(@RequestParam long customerorderId, @RequestParam(required = false) Long suborderId,
                       Model model) {
    authorization.checkMayMaintain(customerorderId);
    var form = new JiraTicketForm();
    form.setCustomerorderId(customerorderId);
    form.setSuborderId(suborderId);
    return showForm(form, model);
  }

  @GetMapping("/{id}/edit")
  public String edit(@PathVariable long id, Model model) {
    return showForm(JiraTicketForm.of(maintenanceService.getTicket(id)), model);
  }

  @PostMapping("/store")
  public String store(@ModelAttribute("ticketForm") JiraTicketForm form, Model model,
                      RedirectAttributes redirectAttributes) {
    var data = new JiraManualTicketData(form.getKey(), form.getSummary(), form.getIssueType(), form.getParentKey());
    try {
      if (form.isNew()) {
        maintenanceService.create(form.getCustomerorderId(), form.getSuborderId(), data);
        redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.jira.ticket.message.created"));
      } else {
        maintenanceService.update(form.getId(), data);
        redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.jira.ticket.message.updated"));
      }
    } catch (ErrorCodeException ex) {
      model.addAttribute("formErrors", toMessages(ex));
      return showForm(form, model);
    }
    return "redirect:/jira/tickets";
  }

  @PostMapping("/{id}/delete")
  public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      maintenanceService.delete(id);
      redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.jira.ticket.message.deleted"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", String.join(" ", toMessages(ex)));
    }
    return "redirect:/jira/tickets";
  }

  /**
   * The preview of a ticket file (#1386): its headings and first rows, each column with the reading
   * its heading suggests, for the user to confirm or change. Nothing is kept — the import sends the
   * file again together with the assignment.
   */
  @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String importPreview(@ModelAttribute("importForm") JiraTicketImportForm form, Model model) throws IOException {
    try {
      var preview = maintenanceService.preview(form.getFile() == null ? null : form.getFile().getBytes());
      var columns = new ArrayList<JiraTicketImportForm.Column>();
      for (int column = 0; column < preview.headings().size(); column++) {
        columns.add(JiraTicketImportForm.Column.of(preview.suggested().get(column), preview.headings().get(column)));
      }
      form.setColumns(columns);
      model.addAttribute("preview", preview);
      model.addAttribute("importTargets", JiraImportTarget.values());
    } catch (ErrorCodeException ex) {
      model.addAttribute("previewErrors", toMessages(ex));
    }
    return "jira/ticket-list :: importPreview";
  }

  /**
   * Imports into the scope the filter shows, reading the columns as assigned in the preview. The
   * findings of a refused file can be one per row, so they come back as a list above the table
   * rather than as a toast.
   */
  @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String importTickets(@ModelAttribute("importForm") JiraTicketImportForm form,
                              RedirectAttributes redirectAttributes) throws IOException {
    if (form.getCustomerorderId() == null) {
      return "redirect:/jira/tickets";
    }
    try {
      int count = maintenanceService.importTickets(form.getCustomerorderId(), form.getSuborderId(),
          form.getFile() == null ? null : form.getFile().getBytes(), form.mapping());
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.ticket.message.imported", new Object[] {count}));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("importErrors", toMessages(ex));
    }
    return "redirect:/jira/tickets";
  }

  private String showForm(JiraTicketForm form, Model model) {
    model.addAttribute("ticketForm", form);
    model.addAttribute("isEdit", !form.isNew());
    model.addAttribute("scopeSign", form.getScopeSign() != null ? form.getScopeSign()
        : scopeSignOf(form.getCustomerorderId(), form.getSuborderId()));
    return "jira/ticket-form";
  }

  /** The scope a new ticket is created in, as the filter chose it. */
  private String scopeSignOf(Long customerorderId, Long suborderId) {
    if (customerorderId == null) return null;
    var orders = authorization.selectableCustomerorders(customerorderId);
    var orderSign = orders.stream().filter(order -> order.id() == customerorderId).findFirst()
        .map(order -> order.sign()).orElse(null);
    if (suborderId == null) return orderSign;
    return suborderOptions(customerorderId, suborderId).stream().filter(so -> so.id() == suborderId).findFirst()
        .map(SuborderReadModel::completeOrderSign).orElse(orderSign);
  }

  /** The suborders of the order, the visible ones and the selected one, by complete order sign. */
  private List<SuborderReadModel> suborderOptions(Long customerorderId, Long selectedId) {
    if (customerorderId == null) return List.of();
    return suborderService.getAllSuborderReadModelsByCustomerorderId(customerorderId).stream()
        .filter(so -> !so.hide() || Objects.equals(so.id(), selectedId))
        .sorted(comparing(SuborderReadModel::completeOrderSign))
        .toList();
  }

  private List<String> toMessages(ErrorCodeException ex) {
    return errorCodeViewHelper.toViewMessages(ex).stream().map(message -> message.resolved()).toList();
  }
}
