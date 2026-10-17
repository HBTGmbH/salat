package de.hbt.salat.invoice.domain;

import static java.time.Duration.ZERO;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.invoice.service.InvoiceService.InvoiceOptions;

@Data
public class InvoiceData {

  private final InvoiceOptions invoiceOptions;
  private final LocalDateRange billingPeriod;
  private final String customerOrderSign;
  private final Customer customer;
  private final Duration totalDuration;
  /** Every suborder with its bookings — what is selected and deselected, whatever the grouping. */
  private final List<InvoiceSuborder> suborders;
  /**
   * The grouping the groups were built with. {@link InvoiceGrouping#SUBORDER} whenever no booking has a
   * ticket reference, whatever was asked for: then there is nothing to group by.
   */
  private final InvoiceGrouping grouping;
  /** What preview, print and export render (#1390). */
  private final List<InvoiceGroup> groups;
  /** The ticket selection as a tree; empty when no booking has a ticket reference. */
  private final List<InvoiceTicketOption> ticketOptions;
  private final List<InvoiceProjectKey> projectKeys;

  /** Whether at least one loaded booking has a ticket reference — only then is there anything to choose. */
  public boolean isTicketReferencesPresent() {
    return !ticketOptions.isEmpty();
  }

  /** Whether the suborders have rows of their own — every grouping but {@link InvoiceGrouping#TICKET}. */
  public boolean isSuborderRows() {
    return grouping != InvoiceGrouping.TICKET;
  }

  public BigDecimal getTotalHours() {
    return BigDecimal
        .valueOf(totalDuration.toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }

  public Duration getTotalDurationVisible() {
    return suborders.stream()
        .map(InvoiceSuborder::getTotalDurationVisible)
        .reduce(ZERO, Duration::plus);
  }

  public BigDecimal getTotalHoursVisible() {
    return BigDecimal
        .valueOf(getTotalDurationVisible().toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }

}
