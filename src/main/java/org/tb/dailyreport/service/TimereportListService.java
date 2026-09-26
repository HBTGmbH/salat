package org.tb.dailyreport.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
   * <p>Two roads to the employees and customers. A manager reads everything, so the answer is the master data.
   * Everybody else is offered what occurs in the bookings they may read — asked of the employee orders, because over
   * the bookings themselves the condition of anybody responsible for an order reads the whole table (#1127, see
   * {@link TimereportListDAO#findFilterValues}). Exactness matters: without it, whoever is responsible for a single
   * order would be offered the name of every employee in the house.
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
   *
   * <p>Inactive orders belong in here (#1106). This dialog narrows down what has already been booked, and an order
   * that has ended keeps its bookings — leaving it out would hide them behind a filter that cannot name them. That is
   * the opposite of what #1094 decided for the select boxes of the master data forms, and deliberately so: those pick
   * something new, and nothing new is booked onto an order that has ended. {@code hide} is the other question and is
   * answered the same on both sides — what somebody took out of the select boxes by hand stays out of this one too.
   *
   * <p>Both branches therefore filter by {@code hide} alone. Which of them runs decides how <em>much</em> somebody
   * sees, never by which rule.
   */
  public OrderSearchResult searchOrders(String term, List<Long> selectedCustomerIds, boolean includeOrders,
      boolean includeSuborders, int limit) {

    var visibility = visibilityService.anyTime();
    if (visibility.isEmpty()) return new OrderSearchResult(List.of(), List.of(), 0);

    List<Customerorder> orders;
    List<Suborder> suborders;
    if (visibility.unrestricted()) {
      orders = customerorderService.getNotHiddenCustomerorders();
      suborders = suborderService.getNotHiddenSuborders();
    } else {
      var values = timereportListDAO.findFilterValues(visibility);
      orders = notHidden(customerorderService.getCustomerordersByIds(values.customerOrderIds()),
          Customerorder::getHide);
      suborders = notHidden(suborderService.getSubordersByIds(values.suborderIds()), Suborder::isHide);
    }

    // Auf die gewaehlten Auftraggeber eingeschraenkt, aus demselben Grund wie die Ticketauswahl: ein
    // Eintrag, den der uebrige Filter ohnehin nicht durchliesse, gehoert nicht in die Liste.
    if (!selectedCustomerIds.isEmpty()) {
      var customers = new HashSet<>(selectedCustomerIds);
      orders = orders.stream().filter(order -> customers.contains(order.getCustomer().getId())).toList();
      var orderIds = orders.stream().map(Customerorder::getId).collect(Collectors.toSet());
      suborders = suborders.stream()
          .filter(suborder -> orderIds.contains(suborder.getCustomerorder().getId()))
          .toList();
    }

    var search = term == null ? "" : term.trim().toLowerCase(java.util.Locale.ROOT);
    var matchedOrders = includeOrders
        ? orders.stream().filter(order -> matches(search, order.getSign(), order.getShortdescription(),
              order.getCustomer().getShortname())).toList()
        : List.<Customerorder>of();
    var matchedSuborders = includeSuborders
        ? suborders.stream()
            .filter(suborder -> matches(search, suborder.getCompleteOrderSign(), suborder.getShortdescription()))
            .toList()
        : List.<Suborder>of();

    // Die Obergrenze gilt fuer beide zusammen: zweimal zweihundert Zeilen waeren keine Liste mehr, die
    // jemand ueberfliegt. Auftraege zuerst, Unterauftraege fuellen den Rest.
    var shownOrders = matchedOrders.stream().limit(limit).map(this::toOption).toList();
    var shownSuborders = matchedSuborders.stream()
        .limit(Math.max(0, limit - shownOrders.size()))
        .map(TimereportListService::toOption)
        .toList();
    return new OrderSearchResult(groupsOf(shownOrders, shownSuborders),
        orphansOf(shownOrders, shownSuborders), matchedOrders.size() + matchedSuborders.size());
  }

  /**
   * Der Baum, wie der Dialog ihn zeigt: jeder Auftrag mit seinen Unterauftraegen darunter, diese nach ihrem
   * vollstaendigen Kuerzel sortiert — damit steht ein Kind hinter seinem Elternteil, ohne dass die Vorlage den
   * Baum selbst laufen muss.
   */
  private static List<OrderGroup> groupsOf(List<OrderOption> orders, List<SuborderOption> suborders) {
    var byOrder = suborders.stream().collect(Collectors.groupingBy(SuborderOption::customerOrderId));
    return orders.stream()
        .map(order -> new OrderGroup(order, byOrder.getOrDefault(order.id(), List.of()).stream()
            .sorted(Comparator.comparing(SuborderOption::completeSign))
            .toList()))
        .toList();
  }

  /**
   * Unterauftraege, deren Auftrag nicht in der Trefferliste steht — etwa weil der Ebenenfilter die Auftraege
   * ausblendet. Sie stehen danach flach, und die Zeile nennt den Auftrag, zu dem sie gehoeren.
   */
  private static List<SuborderOption> orphansOf(List<OrderOption> orders, List<SuborderOption> suborders) {
    var known = orders.stream().map(OrderOption::id).collect(Collectors.toSet());
    return suborders.stream()
        .filter(suborder -> !known.contains(suborder.customerOrderId()))
        .sorted(Comparator.comparing(SuborderOption::completeSign))
        .toList();
  }

  /** Ein Auftrag mit den Unterauftraegen, die zu ihm gehoeren und die Suche ueberstanden haben. */
  public record OrderGroup(OrderOption order, List<SuborderOption> suborders) {}

  /**
   * The tickets a dialog shows: those replicated under the scopes the rest of the filter names, matching the search,
   * ordered as a tree and capped. Whoever has narrowed the list to one customer or one order is not looking for the
   * tickets of the others.
   */
  public TicketSearchResult searchTickets(String term, List<Long> selectedCustomerIds, List<Long> selectedOrderIds,
      List<Long> selectedSuborderIds, int limit) {

    var visibility = visibilityService.anyTime();
    if (visibility.isEmpty()) return new TicketSearchResult(List.of(), List.of(), 0);

    var scopes = scopeSignsOf(selectedCustomerIds, selectedOrderIds, selectedSuborderIds);
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
    var rows = treeOf(byKey);
    return new TicketSearchResult(rows.stream().limit(limit).toList(), types, rows.size());
  }

  /**
   * Die Treffer als Baum: jedes Ticket vor seinen Nachfahren, die Tiefe als Einrueckung. Ohne diese Ordnung stuende
   * ein Kind ueber seinem Elternteil und die Einrueckung behauptete eine Verwandtschaft zur falschen Zeile — die
   * Replikation liefert die Tickets in keiner fachlichen Reihenfolge.
   *
   * <p>Ein Ticket, dessen Elternteil nicht unter den Treffern ist, ist selbst eine Wurzel; die Zeile nennt dann den
   * Schluessel, unter dem es haengt.
   */
  private static List<TicketRow> treeOf(Map<String, TicketOption> byKey) {
    var children = new LinkedHashMap<String, List<TicketOption>>();
    var roots = new ArrayList<TicketOption>();
    byKey.values().forEach(ticket -> {
      if (ticket.parentKey() != null && byKey.containsKey(ticket.parentKey())) {
        children.computeIfAbsent(ticket.parentKey(), key -> new ArrayList<>()).add(ticket);
      } else {
        roots.add(ticket);
      }
    });

    var rows = new ArrayList<TicketRow>();
    roots.stream().sorted(Comparator.comparing(TicketOption::key)).forEach(root -> append(rows, root, 0, children));
    return rows;
  }

  private static void append(List<TicketRow> rows, TicketOption ticket, int level,
      Map<String, List<TicketOption>> children) {

    rows.add(new TicketRow(ticket, level));
    children.getOrDefault(ticket.key(), List.of()).stream()
        .sorted(Comparator.comparing(TicketOption::key))
        .forEach(child -> append(rows, child, level + 1, children));
  }

  /**
   * @param level wie tief im Baum, {@code 0} ganz oben — die Einrueckung des Dialogs
   */
  public record TicketRow(TicketOption ticket, int level) {}

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

  /**
   * @param groups  je Auftrag seine Unterauftraege, in der Reihenfolge des Baums
   * @param orphans Unterauftraege ohne ihren Auftrag in der Liste
   * @param total   wie viele die Suche gefunden hat, von denen nur die ersten gerendert wurden
   */
  public record OrderSearchResult(List<OrderGroup> groups, List<SuborderOption> orphans, int total) {

    public int shown() {
      return groups.stream().mapToInt(group -> 1 + group.suborders().size()).sum() + orphans.size();
    }
  }

  /** @param total wie viele die Suche gefunden hat, von denen nur die ersten gerendert wurden */
  public record TicketSearchResult(List<TicketRow> tickets, List<String> types, int total) {}

  /**
   * A suborder means its whole branch, a ticket its whole descendant chain. Expanding here rather than in the query
   * keeps the condition simple and the walk cheap: both trees are master data and sit in the second level cache.
   */
  private TimereportListFilter expand(TimereportListFilter filter) {
    var suborderIds = expandSuborders(filter.suborderIds());
    var ticketKeys = filter.ticketKeys().isEmpty() || !filter.ticketDescendants()
        ? filter.ticketKeys()
        : List.copyOf(jiraTicketService.expandWithDescendants(filter.ticketKeys(),
            scopeSignsOf(filter.customerIds(), filter.customerOrderIds(), filter.suborderIds())));
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
   * The replication scopes the ticket search runs over: those of the orders the filter names, of the suborders it
   * names, and of every order belonging to a customer it names. A ticket the rest of the filter could never hit does
   * not belong in the dialog. Without any of the three, those of every order the user may see bookings on.
   */
  private List<String> scopeSignsOf(List<Long> customerIds, List<Long> customerOrderIds, List<Long> suborderIds) {
    var orders = new ArrayList<Customerorder>();
    var suborders = new ArrayList<Suborder>();
    if (customerIds.isEmpty() && customerOrderIds.isEmpty() && suborderIds.isEmpty()) {
      var visibility = visibilityService.anyTime();
      if (visibility.unrestricted()) {
        orders.addAll(customerorderService.getNotHiddenCustomerorders());
        suborders.addAll(suborderService.getNotHiddenSuborders());
      } else {
        var values = timereportListDAO.findFilterValues(visibility);
        orders.addAll(customerorderService.getCustomerordersByIds(values.customerOrderIds()));
        suborders.addAll(suborderService.getSubordersByIds(values.suborderIds()));
      }
    } else {
      orders.addAll(customerorderService.getCustomerordersByIds(customerOrderIds));
      suborders.addAll(suborderService.getSubordersByIds(suborderIds));
      suborders.forEach(suborder -> orders.add(suborder.getCustomerorder()));
      if (!customerIds.isEmpty()) {
        var customers = new HashSet<>(customerIds);
        customerorderService.getNotHiddenCustomerorders().stream()
            .filter(order -> customers.contains(order.getCustomer().getId()))
            .forEach(orders::add);
      }
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
