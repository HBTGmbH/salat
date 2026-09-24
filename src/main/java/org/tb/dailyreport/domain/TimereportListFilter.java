package org.tb.dailyreport.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.tb.common.LocalDateRange;

/**
 * What the booking list is asked for (#1092). Every collection is an alternative — an empty one means "no restriction
 * in this dimension", never "nothing".
 *
 * <p>The period is the only mandatory part: it is always set, which is why the list searches on the first call.
 *
 * @param employeeIds      whose bookings, empty for everybody the user may see
 * @param customerIds      of which customers
 * @param customerOrderIds on which orders — an order always means its whole tree, bookings hang on the suborder
 * @param suborderIds      on which suborders, <em>including</em> their descendants; the caller expands them
 * @param ticketKeys       which ticket references, compared ignoring case
 * @param ticketDescendants whether a chosen ticket stands for its whole descendant chain — on by default, because
 *                          that is what somebody filtering by an epic means
 * @param from             first day, inclusive
 * @param until            last day, inclusive
 * @param billable         all bookings, only the billable ones, or only the others
 * @param maxResults       how many rows to show; the sums always count every hit
 */
public record TimereportListFilter(
    List<Long> employeeIds,
    List<Long> customerIds,
    List<Long> customerOrderIds,
    List<Long> suborderIds,
    List<String> ticketKeys,
    boolean ticketDescendants,
    LocalDate from,
    LocalDate until,
    Billable billable,
    int maxResults
) {

  /** No limit at all — what the "Alle" entry of the limit select means. */
  public static final int UNLIMITED = Integer.MAX_VALUE;

  public TimereportListFilter {
    employeeIds = List.copyOf(employeeIds);
    customerIds = List.copyOf(customerIds);
    customerOrderIds = List.copyOf(customerOrderIds);
    suborderIds = List.copyOf(suborderIds);
    // The reference in a booking is text somebody typed: nwp-110 and NWP-110 mean the same ticket, so both sides of
    // the comparison are normalised and the filter carries the normalised form.
    ticketKeys = ticketKeys.stream().map(key -> key.trim().toUpperCase(Locale.ROOT)).distinct().toList();
  }

  public LocalDateRange period() {
    return new LocalDateRange(from, until);
  }

  public boolean limited() {
    return maxResults != UNLIMITED;
  }

  public enum Billable {
    ALL, BILLABLE, NOT_BILLABLE
  }
}
