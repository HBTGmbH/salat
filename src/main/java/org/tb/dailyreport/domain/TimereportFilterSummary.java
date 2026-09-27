package org.tb.dailyreport.domain;

import java.util.List;
import java.util.stream.Stream;
import org.tb.dailyreport.domain.TimereportFilterOptions.CustomerOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.EmployeeOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.OrderOption;
import org.tb.dailyreport.domain.TimereportFilterOptions.SuborderOption;

/**
 * What a filter of the booking list names, resolved to names and signs — the block a printout starts with (#1147).
 *
 * <p>Separate from {@link TimereportFilterOptions} because it has to be there on both roads: the fragment a filter
 * change swaps in does not compute the options, and a printed block that only knew the ids would be empty after the
 * first change of the filter.
 *
 * @param employees         the employees the filter names, as far as the user may see them
 * @param customers         the customers it names
 * @param orders            the orders it names
 * @param suborders         the suborders it names
 * @param includedSuborders how many suborders orders and suborders bring along, descendants included
 * @param tickets           the ticket keys it names, as they were picked
 * @param ticketDescendants whether the tickets stand for their descendants as well
 */
public record TimereportFilterSummary(
    List<EmployeeOption> employees,
    List<CustomerOption> customers,
    List<OrderOption> orders,
    List<SuborderOption> suborders,
    int includedSuborders,
    List<String> tickets,
    boolean ticketDescendants
) {

  /** Orders and suborders are one filter on the page — the dialog picks both — and they are counted as one. */
  public int orderCount() {
    return orders.size() + suborders.size();
  }

  /** The signs of both, orders first — the way they are listed in the dialog. */
  public List<String> orderSigns() {
    return Stream.concat(orders.stream().map(OrderOption::sign), suborders.stream().map(SuborderOption::completeSign))
        .toList();
  }
}
