package de.hbt.salat.jira.controller;

import static java.util.Comparator.comparing;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.jira.domain.JiraImportTarget;
import de.hbt.salat.jira.domain.JiraManualTicketData;
import de.hbt.salat.jira.domain.JiraTicketListFilter;
import de.hbt.salat.jira.domain.JiraTicketSort;
import de.hbt.salat.jira.service.JiraTicketAuthorization;
import de.hbt.salat.jira.service.JiraTicketMaintenanceService;
import de.hbt.salat.order.domain.CustomerorderOption;
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
  private final AuthorizedUser authorizedUser;
  private final SalatProperties salatProperties;
  private final SuborderService suborderService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

  /** The limit steps of the list, as in the booking list; 0 is "all", capped like there (#1153). */
  static final List<Integer> LIMITS = List.of(50, 100, 500, 1000, 0);
  static final int DEFAULT_LIMIT = 500;

  /**
   * The order filters, the suborder narrows to its branch; key, title and type narrow further, and
   * keys bring their children along unless switched off, as in the booking list. Everything is
   * remembered (UiState). A remembered suborder of another order, or an order the user may not see,
   * is dropped rather than refused — the selection may be older than the user's responsibilities.
   */
  @GetMapping
  public String list(@RequestParam(required = false) Long fJiraTicketCustomerorderId,
                     @RequestParam(required = false) Long fJiraTicketSuborderId,
                     @RequestParam(required = false) String fJiraTicketKeys,
                     @RequestParam(required = false) String fJiraTicketChildren,
                     @RequestParam(required = false) String fJiraTicketTitle,
                     @RequestParam(required = false) String fJiraTicketTypes,
                     @RequestParam(required = false) String fJiraTicketLimit,
                     @RequestParam(required = false) String fJiraTicketSort,
                     Model model, HttpServletRequest request) {
    var customerorderId = fJiraTicketCustomerorderId != null && authorization.mayMaintain(fJiraTicketCustomerorderId)
        ? fJiraTicketCustomerorderId : null;
    var suborders = suborderOptions(customerorderId, fJiraTicketSuborderId);
    var suborderId = suborders.stream().anyMatch(so -> Objects.equals(so.id(), fJiraTicketSuborderId))
        ? fJiraTicketSuborderId : null;
    var keys = csv(fJiraTicketKeys);
    var types = csv(fJiraTicketTypes);
    // Checked, the switch sends "true" next to the hidden "false", which arrive as "true,false".
    boolean withChildren = fJiraTicketChildren == null || fJiraTicketChildren.isBlank()
        || fJiraTicketChildren.contains("true");
    int limit = limit(fJiraTicketLimit);
    var sort = JiraTicketSort.parse(fJiraTicketSort);
    boolean descending = JiraTicketSort.descending(fJiraTicketSort);

    model.addAttribute("customerorders", ticketOrders(customerorderId));
    // The import chooses its own scope among every order the user may maintain, tickets or not (#1386).
    model.addAttribute("importOrders", authorization.selectableCustomerorders(customerorderId));
    model.addAttribute("importSuborders", visibleSuborders(customerorderId, suborderId));
    model.addAttribute("suborders", suborders);
    model.addAttribute("fJiraTicketCustomerorderId", customerorderId);
    model.addAttribute("fJiraTicketSuborderId", suborderId);
    model.addAttribute("fJiraTicketKeys", String.join(", ", keys));
    model.addAttribute("fJiraTicketChildren", withChildren);
    model.addAttribute("fJiraTicketTitle", fJiraTicketTitle == null ? "" : fJiraTicketTitle.trim());
    model.addAttribute("selectedTypes", types);
    model.addAttribute("limits", LIMITS);
    model.addAttribute("limit", limit);
    model.addAttribute("sort", sort.name());
    model.addAttribute("sortDescending", descending);
    model.addAttribute("isManager", authorizedUser.isManager());
    // Like the booking list, the page lists tickets right away: without an order, those of every order
    // the user may see (#1386).
    int maxResults = limit == 0 ? salatProperties.getBookingList().getAllMaxRows() : limit;
    model.addAttribute("result", maintenanceService.search(new JiraTicketListFilter(customerorderId, suborderId,
        keys, withChildren, fJiraTicketTitle, types, maxResults, sort, descending)));
    model.addAttribute("coveringReplications", customerorderId == null ? List.of()
        : maintenanceService.getCoveringReplications(customerorderId, suborderId));
    // Like the booking list: a change of the filter swaps the results, not the page.
    return "true".equals(request.getHeader("HX-Request")) ? "jira/ticket-list :: results" : "jira/ticket-list";
  }

  /**
   * The details of a ticket, as the body of the dialog the list opens (#1386). With {@code related},
   * the ticket a parent, top-level or inherited key names instead — in the ticket's own scope first,
   * then in the order; where there is none, the dialog stays on the ticket and says so.
   */
  @GetMapping("/{id}/details")
  public String details(@PathVariable long id, @RequestParam(required = false) String related, Model model) {
    long shown = id;
    if (related != null && !related.isBlank()) {
      var found = maintenanceService.findRelated(id, related.trim());
      if (found.isPresent()) {
        shown = found.get();
      } else {
        model.addAttribute("relatedMissing", related.trim());
      }
    }
    model.addAttribute("detail", maintenanceService.getDetail(shown));
    model.addAttribute("isManager", authorizedUser.isManager());
    return "jira/ticket-detail :: detailBody";
  }

  /** The scope of the filter is a start; the form chooses its own (#1386). */
  @GetMapping("/create")
  public String create(@RequestParam(required = false) Long customerorderId,
                       @RequestParam(required = false) Long suborderId, Model model) {
    if (customerorderId != null && !authorization.mayMaintain(customerorderId)) {
      customerorderId = null;
      suborderId = null;
    }
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
        if (form.getCustomerorderId() == null) {
          throw new InvalidDataException(ErrorCode.JI_TICKET_SCOPE_REQUIRED);
        }
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

  /** The suborders the import dialog and the form of a new ticket offer once their order changes (#1386). */
  @GetMapping("/scope/suborders")
  public String scopeSuborders(@RequestParam(required = false) Long customerorderId, Model model) {
    if (customerorderId != null) authorization.checkMayMaintain(customerorderId);
    model.addAttribute("importSuborders", visibleSuborders(customerorderId, null));
    return "jira/ticket-list :: scopeSuborderSwap";
  }

  /**
   * The preview of a ticket file (#1386): its headings and first rows, each column with the reading
   * its heading suggests, for the user to confirm or change. Nothing is kept — the import sends the
   * file again together with the assignment.
   */
  @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String importPreview(@ModelAttribute("importForm") JiraTicketImportForm form, Model model) throws IOException {
    try {
      var preview = maintenanceService.preview(form.getFile() == null ? null : form.getFile().getBytes(),
          form.getCustomerorderId(), form.getSuborderId());
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
          form.getFile() == null ? null : form.getFile().getOriginalFilename(),
          form.getFile() == null ? null : form.getFile().getBytes(), form.readMapping());
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
    model.addAttribute("scopeOrders", authorization.selectableCustomerorders(form.getCustomerorderId()));
    model.addAttribute("scopeSuborders", visibleSuborders(form.getCustomerorderId(), form.getSuborderId()));
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

  /**
   * The orders the filter offers (#1386): those the user may see that have tickets — the list shows
   * nothing else — and the one already chosen.
   */
  private List<CustomerorderOption> ticketOrders(Long selectedId) {
    var withTickets = maintenanceService.getCustomerorderIdsWithTickets();
    return authorization.selectableCustomerorders(selectedId).stream()
        .filter(order -> withTickets.contains(order.id()) || Objects.equals(order.id(), selectedId))
        .toList();
  }

  /** Every visible suborder of the order, and the one already chosen, by complete order sign. */
  private List<SuborderReadModel> visibleSuborders(Long customerorderId, Long selectedId) {
    if (customerorderId == null) return List.of();
    return suborderService.getAllSuborderReadModelsByCustomerorderId(customerorderId).stream()
        .filter(so -> !so.hide() || Objects.equals(so.id(), selectedId))
        .sorted(comparing(SuborderReadModel::completeOrderSign))
        .toList();
  }

  /**
   * The suborders of the order the filter offers (#1386): visible ones with tickets of their own or
   * below them — the filter narrows to a branch — and the one already chosen, by complete order sign.
   */
  private List<SuborderReadModel> suborderOptions(Long customerorderId, Long selectedId) {
    if (customerorderId == null) return List.of();
    var withTickets = maintenanceService.getSuborderIdsWithTickets(customerorderId);
    var readModels = suborderService.getAllSuborderReadModelsByCustomerorderId(customerorderId);
    var offered = new HashSet<Long>();
    readModels.stream().filter(so -> withTickets.contains(so.id())).forEach(so -> offered.addAll(so.path()));
    return readModels.stream()
        .filter(so -> (!so.hide() && offered.contains(so.id())) || Objects.equals(so.id(), selectedId))
        .sorted(comparing(SuborderReadModel::completeOrderSign))
        .toList();
  }

  private static List<String> csv(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).distinct().toList();
  }

  private static int limit(String value) {
    try {
      int parsed = Integer.parseInt(value == null ? "" : value.trim());
      return LIMITS.contains(parsed) ? parsed : DEFAULT_LIMIT;
    } catch (NumberFormatException ex) {
      return DEFAULT_LIMIT;
    }
  }

  private List<String> toMessages(ErrorCodeException ex) {
    return errorCodeViewHelper.toViewMessages(ex).stream().map(message -> message.resolved()).toList();
  }
}
