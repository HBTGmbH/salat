package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.domain.Authorized;
import org.tb.dailyreport.domain.TimereportListFilter;
import org.tb.dailyreport.domain.TimereportListFilter.Billable;
import org.tb.dailyreport.service.TimereportListExcelService;
import org.tb.dailyreport.service.TimereportListService;

/**
 * The booking list (#1092): one page that shows bookings across employees, orders and periods, and writes them to a
 * spreadsheet.
 *
 * <p>The period is always set, so every call searches — there is no state in which the page shows nothing but a
 * filter. Should that first call ever turn out to be too expensive, the way out is the one of #1009: a hidden field
 * tells a submit from a mere page call, never the presence of a filter parameter, which the UiState fallback supplies
 * on every request anyway.
 *
 * <p>Every filter travels as one request parameter, the multi-valued ones as a comma separated list. A list per value
 * would be the more usual form, but {@code UiState} remembers one string per key — and a filter that is forgotten the
 * moment somebody opens a booking is not a filter anybody would use.
 */
@Controller
@RequestMapping("/dailyreport/list")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class TimereportListController {

  /** Offered as the maximum number of rows; {@code 0} is the "all of them" entry. */
  static final List<Integer> LIMITS = List.of(50, 100, 500, 1000, 0);
  private static final int DEFAULT_LIMIT = 500;

  /** How many rows a dialog renders at once. More than this is not scanned, it is searched. */
  private static final int DIALOG_ROWS = 200;

  private final TimereportListService timereportListService;
  private final TimereportListExcelService excelService;
  private final MessageSourceAccessor messages;

  @GetMapping
  public String show(
      @RequestParam(required = false) String fBookingsEmployees,
      @RequestParam(required = false) String fBookingsCustomers,
      @RequestParam(required = false) String fBookingsOrders,
      @RequestParam(required = false) String fBookingsSuborders,
      @RequestParam(required = false) String fBookingsTickets,
      @RequestParam(required = false) String fBookingsTicketChildren,
      @RequestParam(required = false) String fBookingsFrom,
      @RequestParam(required = false) String fBookingsUntil,
      @RequestParam(required = false) String fBookingsBillable,
      @RequestParam(required = false) String fBookingsSort,
      @RequestParam(required = false) String fBookingsLimit,
      HttpServletRequest request,
      Model model) {

    var employeeIds = longs(fBookingsEmployees);
    var customerIds = longs(fBookingsCustomers);
    var orderIds = longs(fBookingsOrders);
    var suborderIds = longs(fBookingsSuborders);
    var ticketKeys = strings(fBookingsTickets);
    var period = period(fBookingsFrom, fBookingsUntil);
    var billable = billable(fBookingsBillable);
    var limit = limit(fBookingsLimit);

    var ticketChildren = ticketDescendants(fBookingsTicketChildren);
    var order = sortOrder(fBookingsSort);
    var filter = new TimereportListFilter(employeeIds, customerIds, orderIds, suborderIds, ticketKeys, ticketChildren,
        period.from(), period.until(), billable, order.sort(), order.descending(),
        limit == 0 ? TimereportListFilter.UNLIMITED : limit);

    model.addAttribute("result", timereportListService.search(filter));
    model.addAttribute("options", timereportListService.getFilterOptions(orderIds, suborderIds, ticketKeys));

    model.addAttribute("selectedEmployeeIds", employeeIds);
    model.addAttribute("selectedCustomerIds", customerIds);
    model.addAttribute("selectedOrderIds", orderIds);
    model.addAttribute("selectedSuborderIds", suborderIds);
    model.addAttribute("selectedTicketKeys", ticketKeys);
    model.addAttribute("ticketChildren", ticketChildren);
    model.addAttribute("yearMonth", period.yearMonth());
    model.addAttribute("wholeMonth", period.wholeMonth());
    model.addAttribute("from", period.from());
    model.addAttribute("until", period.until());
    model.addAttribute("billable", billable.name());
    model.addAttribute("sort", order.sort().name());
    model.addAttribute("sortDescending", order.descending());
    model.addAttribute("limits", LIMITS);
    model.addAttribute("limit", limit);

    model.addAttribute("section", "dailyreport");
    model.addAttribute("subSection", "list");
    model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
    model.addAttribute("pageTitle", messages.getMessage("main.timereportlist.title"));
    model.addAttribute("title", messages.getMessage("main.timereportlist.title"));

    // Ein Filterwechsel tauscht nur den Ergebnisbereich. Die Seite selbst bleibt stehen — mit ihr das
    // offene Modal, das ein Seitenwechsel mitgerissen haette.
    return "true".equals(request.getHeader("HX-Request"))
        ? "dailyreport/timereport-list :: results"
        : "dailyreport/timereport-list";
  }

  /**
   * The rows of the order dialog. It loads them when it opens and again on every search, rather than carrying them in
   * the page: in production this is a list of seven thousand, and a page that renders it is a page nobody can load.
   */
  @GetMapping("/orders")
  public String orders(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String levels,
      @RequestParam(required = false) String selectedCustomers,
      @RequestParam(required = false) String selectedOrders,
      @RequestParam(required = false) String selectedSuborders,
      Model model) {

    var chosenLevels = strings(levels);
    var includeOrders = chosenLevels.isEmpty() || chosenLevels.contains("order");
    var includeSuborders = chosenLevels.isEmpty() || chosenLevels.contains("suborder");
    var result = timereportListService.searchOrders(q, longs(selectedCustomers), includeOrders, includeSuborders,
        DIALOG_ROWS);

    model.addAttribute("orders", result.orders());
    model.addAttribute("suborders", result.suborders());
    model.addAttribute("total", result.total());
    model.addAttribute("shown", result.orders().size() + result.suborders().size());
    model.addAttribute("selectedOrderIds", longs(selectedOrders));
    model.addAttribute("selectedSuborderIds", longs(selectedSuborders));
    model.addAttribute("searching", q != null && !q.isBlank());
    return "dailyreport/timereport-list-fragments :: orderTree";
  }

  /** The rows of the ticket dialog — the tickets of the scopes of the chosen orders, for the same reason. */
  @GetMapping("/tickets")
  public String tickets(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String selectedCustomers,
      @RequestParam(required = false) String selectedOrders,
      @RequestParam(required = false) String selectedSuborders,
      @RequestParam(required = false) String selectedTickets,
      Model model) {

    var result = timereportListService.searchTickets(q, longs(selectedCustomers), longs(selectedOrders),
        longs(selectedSuborders), DIALOG_ROWS);
    model.addAttribute("tickets", result.tickets());
    model.addAttribute("ticketTypes", result.types());
    model.addAttribute("total", result.total());
    model.addAttribute("shown", result.tickets().size());
    model.addAttribute("selectedTicketKeys", strings(selectedTickets));
    return "dailyreport/timereport-list-fragments :: ticketTree";
  }

  /**
   * The spreadsheet of the same filter — every hit, also the ones the display limit cuts off. The limit is a property
   * of the screen, not of the answer.
   */
  @GetMapping("/export")
  public void export(
      @RequestParam(required = false) String fBookingsEmployees,
      @RequestParam(required = false) String fBookingsCustomers,
      @RequestParam(required = false) String fBookingsOrders,
      @RequestParam(required = false) String fBookingsSuborders,
      @RequestParam(required = false) String fBookingsTickets,
      @RequestParam(required = false) String fBookingsTicketChildren,
      @RequestParam(required = false) String fBookingsFrom,
      @RequestParam(required = false) String fBookingsUntil,
      @RequestParam(required = false) String fBookingsBillable,
      @RequestParam(required = false) String fBookingsSort,
      HttpServletResponse response) throws IOException {

    var period = period(fBookingsFrom, fBookingsUntil);
    var order = sortOrder(fBookingsSort);
    var filter = new TimereportListFilter(longs(fBookingsEmployees), longs(fBookingsCustomers),
        longs(fBookingsOrders), longs(fBookingsSuborders), strings(fBookingsTickets),
        ticketDescendants(fBookingsTicketChildren), period.from(), period.until(), billable(fBookingsBillable),
        order.sort(), order.descending(), TimereportListFilter.UNLIMITED);

    var bytes = excelService.export(timereportListService.searchAll(filter));
    var fileName = "buchungen_" + period.from() + "_" + period.until() + ".xlsx";
    response.setHeader("Content-disposition", "attachment; filename=" + fileName);
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setContentLength(bytes.length);
    response.getOutputStream().write(bytes);
  }

  /**
   * The period is a first and a last day, nothing else — whole months are a quick selection that sets the two, not a
   * second mode of the page (#1092). Whatever is missing or unreadable falls back to the current month, so a
   * hand-written URL cannot produce a page without a period.
   */
  private static Period period(String from, String until) {
    var begin = date(from);
    var end = date(until);
    if (begin == null || end == null || end.isBefore(begin)) {
      var month = YearMonth.from(today());
      return new Period(month.atDay(1), month.atEndOfMonth());
    }
    return new Period(begin, end);
  }

  /**
   * @param wholeMonth whether the period is exactly one month — then the month selection may say which one, otherwise
   *                   it would show a value the filter does not mean
   */
  private record Period(LocalDate from, LocalDate until) {

    YearMonth yearMonth() {
      return YearMonth.from(from);
    }

    boolean wholeMonth() {
      var month = YearMonth.from(from);
      return from.equals(month.atDay(1)) && until.equals(month.atEndOfMonth());
    }
  }

  private static LocalDate date(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * The sort as one parameter: the column, a leading minus for the reverse. Missing means chronological — the order a
   * list of bookings is read in, oldest day first.
   */
  private static SortOrder sortOrder(String value) {
    if (value == null || value.isBlank()) return new SortOrder(TimereportListFilter.Sort.DATE, false);
    var descending = value.startsWith("-");
    try {
      return new SortOrder(TimereportListFilter.Sort.valueOf(
          (descending ? value.substring(1) : value).trim().toUpperCase()), descending);
    } catch (IllegalArgumentException e) {
      return new SortOrder(TimereportListFilter.Sort.DATE, false);
    }
  }

  private record SortOrder(TimereportListFilter.Sort sort, boolean descending) {}

  /** Missing means on: whoever filters by an epic means its subtasks as well. */
  private static boolean ticketDescendants(String value) {
    return value == null || value.isBlank() || Boolean.parseBoolean(value.trim());
  }

  private static Billable billable(String value) {
    if (value == null || value.isBlank()) return Billable.ALL;
    try {
      return Billable.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return Billable.ALL;
    }
  }

  private static int limit(String value) {
    if (value == null || value.isBlank()) return DEFAULT_LIMIT;
    try {
      var limit = Integer.parseInt(value.trim());
      return LIMITS.contains(limit) ? limit : DEFAULT_LIMIT;
    } catch (NumberFormatException e) {
      return DEFAULT_LIMIT;
    }
  }

  private static List<Long> longs(String value) {
    return strings(value).stream()
        .map(TimereportListController::parseLong)
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private static Long parseLong(String value) {
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static List<String> strings(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(part -> !part.isEmpty())
        .distinct()
        .toList();
  }
}
