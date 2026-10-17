package de.hbt.salat.invoice.service;

import static java.time.Duration.ZERO;
import static java.util.Comparator.comparing;
import static de.hbt.salat.common.GlobalConstants.YESNO_YES;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.service.BudgetQueryService;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.invoice.domain.InvoiceData;
import de.hbt.salat.invoice.domain.InvoiceGroup;
import de.hbt.salat.invoice.domain.InvoiceGrouping;
import de.hbt.salat.invoice.domain.InvoiceLine;
import de.hbt.salat.invoice.domain.InvoiceProjectKey;
import de.hbt.salat.invoice.domain.InvoiceSuborder;
import de.hbt.salat.invoice.domain.InvoiceTicketDisplay;
import de.hbt.salat.invoice.domain.InvoiceTicketOption;
import de.hbt.salat.invoice.domain.InvoiceTimereport;
import de.hbt.salat.jira.domain.JiraTicketInfo;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.comparator.SubOrderComparator;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresBackoffice = true)
public class InvoiceService {

  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final TimereportService timereportService;
  private final BudgetQueryService budgetQueryService;
  private final JiraTicketService jiraTicketService;

  private static final Comparator<TimereportDTO> BOOKING_ORDER =
      comparing(TimereportDTO::getReferenceday).thenComparing(TimereportDTO::getEmployeeSign);

  /** {@link #BOOKING_ORDER} once the bookings of several suborders come together under one ticket. */
  private static final Comparator<InvoiceTimereport> INVOICE_TIMEREPORT_ORDER =
      comparing(InvoiceTimereport::getReferenceDay).thenComparing(InvoiceTimereport::getEmployeeSign);

  public InvoiceData generateInvoiceData(long customerorderId, Optional<Long> suborderId, LocalDateRange invoiceDateRange, InvoiceOptions options) {
    return generateInvoiceData(customerorderId, suborderId, Optional.empty(), invoiceDateRange, options);
  }

  /**
   * The invoice over one customer order, narrowed either to one suborder or to one budget plan
   * (#915) — never both, which the form refuses before it gets here.
   *
   * <p>In the budget case the bookings come from the assignment: billed is what was booked onto that
   * budget, however many suborders it spreads over. The period still applies on top, so a plan
   * running longer than the billing month yields only that month's bookings. Suborders without an
   * assigned booking in the period drop out — they contribute nothing to this invoice.
   *
   * <p>The groups follow {@link InvoiceOptions#getGrouping()} (#1390): by suborder as before, by
   * ticket within each suborder, or by ticket across them; how the bookings fall to the tickets is
   * described at {@link InvoiceGroups}. Without any ticket reference among the bookings there is
   * nothing to group by and the invoice is grouped by suborder whatever was asked for.
   */
  public InvoiceData generateInvoiceData(long customerorderId, Optional<Long> suborderId,
      Optional<Long> orderBudgetId, LocalDateRange invoiceDateRange, InvoiceOptions options) {
    var customerorder = customerorderService.getCustomerorderById(customerorderId);

    var dateFirst = invoiceDateRange.getFrom();
    var dateLast = invoiceDateRange.getUntil();
    // Null means "no budget narrowing at all", which is not the same as "a plan with no bookings".
    var assignedIds = orderBudgetId
        .map(id -> Set.copyOf(budgetQueryService.getAssignedTimereportIds(id)))
        .orElse(null);

    // For every suborder the suborders of its branch: the scopes its tickets may be kept under (#1323).
    var scopesBySuborder = new HashMap<Long, Set<Long>>();
    var invoiceSuborders = suborderId
        .map(sid -> List.of(suborderService.getSuborderById(sid)))
        .orElseGet(() -> suborderService.getSubordersByCustomerorderId(customerorderId))
        .stream()
        .filter(suborder -> suborder.getValidity().overlaps(invoiceDateRange))
        .filter(suborder -> options.isShowNonInvoicableSuborders() || suborder.getInvoice() == YESNO_YES)
        .sorted(SubOrderComparator.INSTANCE)
        .map(suborder -> {
          scopesBySuborder.put(suborder.getId(),
              suborder.withParents().stream().map(Suborder::getId).collect(Collectors.toSet()));
          var timereports = timereportService.getTimereportsByDatesAndSuborderId(dateFirst, dateLast, suborder.getId()).stream()
              .filter(timereport -> assignedIds == null || assignedIds.contains(timereport.getId()))
              .sorted(BOOKING_ORDER)
              .map(timereport -> new InvoiceTimereport(timereport))
              .toList();
          return new InvoiceSuborder(suborder, timereports, options);
        })
        .filter(invoiceSuborder -> assignedIds == null || !invoiceSuborder.getTimereports().isEmpty())
        .toList();

    var totalDuration = invoiceSuborders.stream()
        .map(InvoiceSuborder::getTotalDuration)
        .reduce(Duration::plus)
        .orElse(ZERO);

    var customerOrderSign = options.isUseCustomerDescriptions() && isCustomerDescriptionAvailable(customerorder) ?
        customerorder.getOrder_customer() :
        customerorder.getSignAndDescription();

    var bookings = invoiceSuborders.stream().flatMap(s -> s.getTimereports().stream()).toList();
    if (bookings.stream().allMatch(booking -> booking.getTicketReferences().isEmpty())) {
      return new InvoiceData(options, invoiceDateRange, customerOrderSign, customerorder.getCustomer(), totalDuration,
          invoiceSuborders, InvoiceGrouping.SUBORDER, numbered(bySuborder(invoiceSuborders)), List.of(), List.of());
    }

    var ticketsByKey = InvoiceTicketTree.byKey(ticketsOf(customerorderId, invoiceSuborders, scopesBySuborder));
    var tree = new InvoiceTicketTree(bookings, ticketsByKey, scopesBySuborder);
    var selected = selectedTickets(options.getTicketSelection(), tree);
    var suborderById = invoiceSuborders.stream().collect(Collectors.toMap(InvoiceSuborder::getId, s -> s));

    var groups = switch (options.getGrouping()) {
      case SUBORDER -> bySuborder(invoiceSuborders);
      case SUBORDER_TICKET -> invoiceSuborders.stream().map(invoiceSuborder -> {
        var group = InvoiceGroup.ofSuborder(invoiceSuborder);
        // within a suborder the tickets are resolved in its branch alone
        var ownTree = new InvoiceTicketTree(invoiceSuborder.getTimereports(), ticketsByKey, scopesBySuborder);
        group.getChildren().addAll(InvoiceGroups.byTicket(invoiceSuborder.getTimereports(), ownTree, selected, suborderById::get));
        return group;
      }).toList();
      case TICKET -> InvoiceGroups.byTicket(bookings.stream().sorted(INVOICE_TIMEREPORT_ORDER).toList(), tree, selected,
          suborderById::get);
    };

    var ticketOptions = ticketOptions(tree, bookings, selected);
    return new InvoiceData(options, invoiceDateRange, customerOrderSign, customerorder.getCustomer(), totalDuration,
        invoiceSuborders, options.getGrouping(), numbered(groups), ticketOptions, projectKeys(ticketOptions));
  }

  /**
   * The tickets of every scope a booked suborder's branch reaches: the order-wide ones and those of
   * each suborder on the paths. Read in one go — the list of scopes is that of one order.
   */
  private List<JiraTicketInfo> ticketsOf(long customerorderId, List<InvoiceSuborder> suborders,
      Map<Long, Set<Long>> scopesBySuborder) {
    var scopes = new LinkedHashSet<Long>();
    suborders.stream()
        .filter(suborder -> suborder.getTimereports().stream().anyMatch(booking -> !booking.getTicketReferences().isEmpty()))
        .forEach(suborder -> scopes.addAll(scopesBySuborder.get(suborder.getId())));
    // JiraTicketService filters nothing per row; it leaves that to whoever reads the bookings. Here
    // that is moot: this service requires backoffice, which bills every order, and the scopes are
    // those of the bookings of the one order being billed — the tickets show what the invoice shows.
    return jiraTicketService.getTickets(List.of(customerorderId), scopes);
  }

  /** The submitted selection, or — never sent — every booked reference and none of the tickets above them. */
  private static Set<String> selectedTickets(Set<String> submitted, InvoiceTicketTree tree) {
    if (submitted == null) {
      return tree.keys().stream().filter(tree::isBooked).collect(Collectors.toSet());
    }
    return submitted.stream().filter(key -> key != null && !key.isBlank())
        .map(TicketReferences::comparable).collect(Collectors.toSet());
  }

  private static List<InvoiceGroup> bySuborder(List<InvoiceSuborder> suborders) {
    return suborders.stream().map(invoiceSuborder -> {
      var group = InvoiceGroup.ofSuborder(invoiceSuborder);
      invoiceSuborder.getTimereports().forEach(booking -> group.getLines().add(InvoiceLine.whole(booking, invoiceSuborder)));
      return group;
    }).toList();
  }

  /** Gives every group an id and its depth, every line the ids of the groups it stands in. */
  private static List<InvoiceGroup> numbered(List<InvoiceGroup> groups) {
    number(groups, "", 0, new int[] {0});
    return groups;
  }

  private static void number(List<InvoiceGroup> groups, String path, int depth, int[] counter) {
    for (var group : groups) {
      group.setId("g" + ++counter[0]);
      group.setDepth(depth);
      var ownPath = path.isEmpty() ? group.getId() : path + " " + group.getId();
      group.getLines().forEach(line -> line.setGroupPath(ownPath));
      number(group.getChildren(), ownPath, depth + 1, counter);
    }
  }

  /**
   * The ticket selection as a tree along the parents (#1393). A booked ticket shows what was booked
   * on it — a booking with several references with its share, divided as the invoice divides it
   * without any filter.
   */
  private static List<InvoiceTicketOption> ticketOptions(InvoiceTicketTree tree, List<InvoiceTimereport> bookings,
      Set<String> selected) {
    var booked = new HashMap<String, Duration>();
    for (var booking : bookings) {
      var references = booking.getTicketReferences();
      if (references.isEmpty()) continue;
      var shares = InvoiceGroups.split(booking.getDuration(), references.size());
      for (int i = 0; i < references.size(); i++) {
        booked.merge(TicketReferences.comparable(references.get(i)), shares.get(i), Duration::plus);
      }
    }
    var children = new HashMap<String, List<String>>();
    var roots = new ArrayList<String>();
    tree.keys().forEach(key -> {
      var parent = tree.parent(key);
      if (parent == null) {
        roots.add(key);
      } else {
        children.computeIfAbsent(parent, k -> new ArrayList<>()).add(key);
      }
    });
    var options = new ArrayList<InvoiceTicketOption>();
    roots.stream().sorted(InvoiceTicketTree.KEY_ORDER)
        .forEach(root -> appendOption(options, root, 0, tree, children, booked, selected));
    return options;
  }

  private static void appendOption(List<InvoiceTicketOption> options, String key, int level, InvoiceTicketTree tree,
      Map<String, List<String>> children, Map<String, Duration> booked, Set<String> selected) {
    var ticket = tree.ticket(key);
    options.add(new InvoiceTicketOption(tree.spelling(key), ticket.map(JiraTicketInfo::summary).orElse(null),
        ticket.map(JiraTicketInfo::issueType).orElse(null), TicketReferences.projectKeyOf(key), level,
        booked.get(key), selected.contains(key)));
    children.getOrDefault(key, List.of()).stream().sorted(InvoiceTicketTree.KEY_ORDER)
        .forEach(child -> appendOption(options, child, level + 1, tree, children, booked, selected));
  }

  private static List<InvoiceProjectKey> projectKeys(List<InvoiceTicketOption> options) {
    var counts = new TreeMap<String, Integer>();
    options.stream().filter(option -> !option.isFreeText())
        .forEach(option -> counts.merge(option.projectKey(), 1, Integer::sum));
    return counts.entrySet().stream().map(entry -> new InvoiceProjectKey(entry.getKey(), entry.getValue())).toList();
  }

  private static boolean isCustomerDescriptionAvailable(Customerorder customerorder) {
    return StringUtils.hasText(customerorder.getOrder_customer());
  }

  @Builder
  @Data
  @AllArgsConstructor
  public static class InvoiceOptions {
    private final boolean showNonInvoicableSuborders;
    private final boolean showTimereports;
    private final boolean showTaskdescriptions;
    private final boolean showEmployee;
    private final boolean shortDescriptions;
    private final boolean useCustomerDescriptions;
    private final boolean showBudget;
    @Builder.Default
    private final InvoiceGrouping grouping = InvoiceGrouping.SUBORDER;
    /**
     * Comparable keys of the tickets that get a group of their own (#1390); {@code null} when the
     * selection was never sent, which is not the same as an empty one: then every booked reference
     * is selected.
     */
    private final Set<String> ticketSelection;
    /** Where a line names its tickets; without it, nowhere, as before (#1390). */
    @Builder.Default
    private final InvoiceTicketDisplay ticketDisplay = InvoiceTicketDisplay.NONE;
    /** Whether a grouping by ticket lists the bookings under each ticket or only the subtotal. */
    @Builder.Default
    private final boolean showTicketLines = true;
  }

}
