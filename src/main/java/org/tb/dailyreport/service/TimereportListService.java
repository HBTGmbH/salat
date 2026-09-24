package org.tb.dailyreport.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.customer.domain.Customer;
import org.tb.customer.service.CustomerService;
import org.tb.dailyreport.auth.TimereportVisibility;
import org.tb.dailyreport.auth.TimereportVisibilityService;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportFilterOptions;
import org.tb.dailyreport.domain.TimereportFilterOptions.CustomerOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.EmployeeOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.OrderOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.SuborderOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.TicketOption;
import org.tb.dailyreport.domain.TimereportListFilter;
import org.tb.dailyreport.domain.TimereportListResult;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportListDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.jira.service.JiraTicketService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * The booking list (#1092): which bookings a filter hits, and which values that filter may offer.
 *
 * <p>Both answers rest on the same {@link TimereportVisibility}, built once per call. That is what keeps the list off
 * the two roads that lead nowhere: checking every row in Java, and reading the filter values from master data the user
 * has nothing to do with.
 *
 * <p>What the user picked is expanded before anything is asked: a suborder stands for its whole branch, a ticket for
 * its whole descendant chain. Without that both would find nothing — bookings hang on the leaf of the order tree, and
 * on the ticket somebody actually typed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized(requireUnrestricted = true)
public class TimereportListService {

  private final TimereportListDAO timereportListDAO;
  private final TimereportDAO timereportDAO;
  private final TimereportVisibilityService visibilityService;
  private final EmployeeService employeeService;
  private final CustomerService customerService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final JiraTicketService jiraTicketService;

  /** The hits of this filter — the rows cut to its maximum, the numbers over all of them. */
  public TimereportListResult search(TimereportListFilter filter) {
    var visibility = visibilityService.forPeriod(filter.period());
    if (visibility.isEmpty()) {
      return TimereportListResult.empty();
    }

    var expanded = expand(filter);
    var totals = timereportListDAO.findTotals(expanded, visibility);
    if (totals.count() == 0) {
      return TimereportListResult.empty();
    }
    var rows = timereportDAO.getDtosOf(timereportListDAO.findRows(expanded, visibility));
    return new TimereportListResult(rows, totals.count(), totals.duration(), totals.billableDuration(),
        totals.employees(), totals.orders());
  }

  /** Every hit, regardless of the display maximum — what the export writes (#1092). */
  public List<TimereportDTO> searchAll(TimereportListFilter filter) {
    var visibility = visibilityService.forPeriod(filter.period());
    if (visibility.isEmpty()) {
      return List.of();
    }
    var unlimited = new TimereportListFilter(filter.employeeIds(), filter.customerIds(), filter.customerOrderIds(),
        filter.suborderIds(), filter.ticketKeys(), filter.ticketDescendants(), filter.from(), filter.until(),
        filter.billable(), filter.sort(), filter.descending(), TimereportListFilter.UNLIMITED);
    return timereportDAO.getDtosOf(timereportListDAO.findRows(expand(unlimited), visibility));
  }

  /**
   * What the page itself needs: the two select boxes, and enough about the current selection to label it.
   *
   * <p>Deliberately <em>not</em> the orders and tickets — in production those are seven thousand suborders and some
   * thirty-six thousand tickets. Rendered into the page they make a document nobody can load and a dialog nobody can
   * scroll; they are searched for in {@link #searchOrders} and {@link #searchTickets} when their dialog opens.
   *
   * <p>Two roads to the employees and customers, and which is cheaper depends on who asks. A manager reads
   * everything, so the answer is the master data and the {@code distinct} over the bookings is not needed —
   * unrestricted it costs about a second on half a million rows. Everybody else carries a condition the index on the
   * employee contract serves, and there the exact answer costs milliseconds. Exactness matters: without it, whoever is
   * responsible for a single order would be offered the name of every employee in the house.
   *
   * <p>The lists do not depend on the period being shown — they would empty themselves while somebody pages through
   * the months. A value offered here can therefore have no hit in the period currently displayed.
   */
  public TimereportFilterOptions getFilterOptions(List<Long> selectedOrderIds, List<Long> selectedSuborderIds,
      List<String> selectedTicketKeys) {

    var visibility = visibilityService.anyTime();
    if (visibility.isEmpty()) return TimereportFilterOptions.empty();

    // Versteckte Stammdaten stehen in keiner Auswahlliste - das ist der Zweck des hide-Flags. Ihre
    // Buchungen bleiben in der Liste sichtbar, nur anwaehlen laesst sich der Eintrag nicht mehr.
    List<Employee> employees;
    List<Customer> customers;
    if (visibility.unrestricted()) {
      employees = employeeService.getAllEmployees();
      customers = customerService.getSelectableCustomers(null);
    } else {
      var values = timereportListDAO.findFilterValues(visibility);
      employees = notHidden(employeeService.getEmployeesByIds(values.employeeIds()),
          employee -> Boolean.TRUE.equals(employee.getHide()));
      var customerIds = new HashSet<>(values.customerIds());
      customers = customerService.getSelectableCustomers(null).stream()
          .filter(customer -> customerIds.contains(customer.getId()))
          .toList();
    }

    var selectedOrders = customerorderService.getCustomerordersByIds(selectedOrderIds);
    var selectedSuborders = suborderService.getSubordersByIds(selectedSuborderIds);

    return new TimereportFilterOptions(
        employees.stream()
            .sorted(Comparator.comparing(Employee::getName))
            .map(employee -> new EmployeeOption(employee.getId(), employee.getName(), employee.getSign()))
            .toList(),
        customers.stream()
            .sorted(Comparator.comparing(Customer::getShortname))
            .map(customer -> new CustomerOption(customer.getId(), customer.getShortname(), customer.getName()))
            .toList(),
        selectedOrders.stream().map(this::toOption).toList(),
        selectedSuborders.stream().map(TimereportListService::toOption).toList(),
        List.copyOf(selectedTicketKeys),
        includedSuborderCount(selectedOrders, selectedSuborders));
  }

  /**
   * The orders and suborders a dialog shows: what matches the search, capped. Without a search term the orders alone,
   * because a flat list of every suborder helps nobody — the term is what narrows it down.
   */
  public OrderSearchResult searchOrders(String term, boolean includeOrders, boolean includeSuborders, int limit) {
    var visibility = visibilityService.anyTime();
    if (visibility.isEmpty()) return new OrderSearchResult(List.of(), List.of(), 0);

    List<Customerorder> orders;
    List<Suborder> suborders;
    if (visibility.unrestricted()) {
      orders = customerorderService.getVisibleCustomerorders();
      suborders = suborderService.getAllVisibleSuborders();
    } else {
      var values = timereportListDAO.findFilterValues(visibility);
      orders = notHidden(customerorderService.getCustomerordersByIds(values.customerOrderIds()),
          Customerorder::getHide);
      suborders = notHidden(suborderService.getSubordersByIds(values.suborderIds()), Suborder::isHide);
    }

    var search = term == null ? "" : term.trim().toLowerCase(java.util.Locale.ROOT);
    var matchedOrders = includeOrders
        ? orders.stream().filter(order -> matches(search, order.getSign(), order.getShortdescription(),
              order.getCustomer().getShortname())).toList()
        : List.<Customerorder>of();
    var matchedSuborders = !includeSuborders || search.isEmpty()
        ? List.<Suborder>of()
        : suborders.stream()
            .filter(suborder -> matches(search, suborder.getCompleteOrderSign(), suborder.getShortdescription()))
            .toList();

    int total = matchedOrders.size() + matchedSuborders.size();
    var subordersByOrder = matchedSuborders.stream()
        .collect(Collectors.groupingBy(suborder -> suborder.getCustomerorder().getId()));
    return new OrderSearchResult(
        matchedOrders.stream().limit(limit).map(this::toOption).toList(),
        matchedSuborders.stream().limit(limit).map(TimereportListService::toOption).toList(),
        total);
  }

  /** The tickets a dialog shows: those of the scopes of the chosen orders, matching the search, capped. */
  public TicketSearchResult searchTickets(String term, List<Long> selectedOrderIds, List<Long> selectedSuborderIds,
      int limit) {

    var visibility = visibilityService.anyTime();
    if (visibility.isEmpty()) return new TicketSearchResult(List.of(), List.of(), 0);

    var scopes = scopeSignsOf(selectedOrderIds, selectedSuborderIds);
    var search = term == null ? "" : term.trim().toLowerCase(java.util.Locale.ROOT);
    var byKey = new LinkedHashMap<String, TicketOption>();
    for (var ticket : jiraTicketService.getTickets(scopes)) {
      if (!matches(search, ticket.key(), ticket.summary())) continue;
      byKey.putIfAbsent(ticket.key(),
          new TicketOption(ticket.key(), ticket.summary(), ticket.issueType(), ticket.parentKey(), List.of()));
    }
    var types = byKey.values().stream()
        .map(TicketOption::issueType)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
    return new TicketSearchResult(byKey.values().stream().limit(limit).toList(), types, byKey.size());
  }

  /** Was versteckt ist, wird nicht angeboten — dieselbe Regel, die jede andere Auswahlliste befolgt. */
  private static <T> List<T> notHidden(List<T> entries, java.util.function.Predicate<T> hidden) {
    return entries.stream().filter(entry -> !hidden.test(entry)).toList();
  }

  private static boolean matches(String search, String... values) {
    if (search.isEmpty()) return true;
    for (var value : values) {
      if (value != null && value.toLowerCase(java.util.Locale.ROOT).contains(search)) return true;
    }
    return false;
  }

  private OrderOption toOption(Customerorder order) {
    return new OrderOption(order.getId(), order.getSign(), order.getShortdescription(),
        order.getCustomer().getId(), order.getCustomer().getShortname(), 0);
  }

  private static SuborderOption toOption(Suborder suborder) {
    return new SuborderOption(suborder.getId(), suborder.getCompleteOrderSign(), suborder.getShortdescription(),
        suborder.getCustomerorder().getId(),
        suborder.getParentorder() == null ? null : suborder.getParentorder().getId(),
        levelOf(suborder), suborder.getAllChildren().size() - 1);
  }

  /**
   * How many suborders a selection brings along. An order stands for every suborder below it, a suborder for its whole
   * branch — that is not a switch anybody could turn off, because bookings hang on the leaf.
   */
  private static int includedSuborderCount(List<Customerorder> orders, List<Suborder> suborders) {
    var included = new LinkedHashSet<Long>();
    orders.forEach(order -> order.getSuborders().forEach(suborder ->
        suborder.getAllChildren().forEach(child -> included.add(child.getId()))));
    suborders.forEach(suborder -> suborder.getAllChildren().forEach(child -> included.add(child.getId())));
    return included.size();
  }

  /** @param total how many the search found, of which only the first were rendered */
  public record OrderSearchResult(List<OrderOption> orders, List<SuborderOption> suborders, int total) {}

  /** @param total how many the search found, of which only the first were rendered */
  public record TicketSearchResult(List<TicketOption> tickets, List<String> types, int total) {}

  /**
   * A suborder means its whole branch, a ticket its whole descendant chain. Expanding here rather than in the query
   * keeps the condition simple and the walk cheap: both trees are master data and sit in the second level cache.
   */
  private TimereportListFilter expand(TimereportListFilter filter) {
    var suborderIds = expandSuborders(filter.suborderIds());
    var ticketKeys = filter.ticketKeys().isEmpty() || !filter.ticketDescendants()
        ? filter.ticketKeys()
        : List.copyOf(jiraTicketService.expandWithDescendants(filter.ticketKeys(),
            scopeSignsOf(filter.customerOrderIds(), filter.suborderIds())));
    return new TimereportListFilter(filter.employeeIds(), filter.customerIds(), filter.customerOrderIds(),
        suborderIds, ticketKeys, filter.ticketDescendants(), filter.from(), filter.until(), filter.billable(),
        filter.sort(), filter.descending(), filter.maxResults());
  }

  private List<Long> expandSuborders(List<Long> suborderIds) {
    if (suborderIds.isEmpty()) return List.of();
    var expanded = new LinkedHashSet<Long>();
    suborderService.getSubordersByIds(suborderIds)
        .forEach(suborder -> suborder.getAllChildren().forEach(child -> expanded.add(child.getId())));
    return List.copyOf(expanded);
  }

  /**
   * The replication scopes the ticket search runs over: those of the orders the filter names — and without any order
   * selection, those of every order the user may see bookings on.
   */
  private List<String> scopeSignsOf(List<Long> customerOrderIds, List<Long> suborderIds) {
    var orders = new ArrayList<Customerorder>();
    var suborders = new ArrayList<Suborder>();
    if (customerOrderIds.isEmpty() && suborderIds.isEmpty()) {
      var visibility = visibilityService.anyTime();
      if (visibility.unrestricted()) {
        orders.addAll(customerorderService.getVisibleCustomerorders());
        suborders.addAll(suborderService.getAllVisibleSuborders());
      } else {
        var values = timereportListDAO.findFilterValues(visibility);
        orders.addAll(customerorderService.getCustomerordersByIds(values.customerOrderIds()));
        suborders.addAll(suborderService.getSubordersByIds(values.suborderIds()));
      }
    } else {
      orders.addAll(customerorderService.getCustomerordersByIds(customerOrderIds));
      suborders.addAll(suborderService.getSubordersByIds(suborderIds));
      suborders.forEach(suborder -> orders.add(suborder.getCustomerorder()));
    }
    var scopes = new LinkedHashSet<String>();
    orders.forEach(order -> scopes.add(order.getSign()));
    suborders.forEach(suborder -> scopes.add(suborder.getCompleteOrderSign()));
    return List.copyOf(scopes);
  }

  private static int levelOf(Suborder suborder) {
    int level = 0;
    for (var parent = suborder.getParentorder(); parent != null; parent = parent.getParentorder()) {
      level++;
    }
    return level;
  }
}
