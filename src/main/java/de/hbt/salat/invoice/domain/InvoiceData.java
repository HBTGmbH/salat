package de.hbt.salat.invoice.domain;

import static java.time.Duration.ZERO;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.Data;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.invoice.service.InvoiceService.InvoiceOptions;
import de.hbt.salat.order.domain.SurchargeKind;

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

  /** Whether a loaded booking earns a time surcharge (#1470) — only then is there anything to choose. */
  public boolean isSurchargesPresent() {
    return bookings().anyMatch(InvoiceTimereport::isSurcharged);
  }

  /** Whether the surcharges stand as positions of their own below the total. */
  public boolean isSurchargesSeparate() {
    return invoiceOptions.isSeparateSurcharges() && isSurchargesPresent();
  }

  /**
   * The time surcharges as positions of their own (#1470), one per kind and rate, of the bookings that
   * are in the invoice. Empty when they are part of the hours instead.
   */
  public List<InvoiceSurcharge> getSurcharges() {
    return surcharges(true);
  }

  /** {@link #getSurcharges}, but of every loaded booking — the positions the preview can show at all. */
  public List<InvoiceSurcharge> getSurchargesLoaded() {
    return surcharges(false);
  }

  private List<InvoiceSurcharge> surcharges(boolean visibleOnly) {
    if (!isSurchargesSeparate()) {
      return List.of();
    }
    var byKindAndRate = new TreeMap<SurchargeKey, InvoiceSurcharge>();
    suborders.stream()
        .filter(suborder -> !visibleOnly || suborder.isVisible())
        .flatMap(suborder -> suborder.getTimereports().stream())
        .filter(InvoiceTimereport::isSurcharged)
        .filter(booking -> !visibleOnly || booking.isVisible())
        .forEach(booking -> {
          var key = new SurchargeKey(booking.getSurchargeKind(), booking.getSurchargePercent());
          var basis = visibleOnly ? booking.getBookedDuration() : ZERO;
          var credit = visibleOnly ? booking.getSurchargeCredit() : ZERO;
          byKindAndRate.merge(key, new InvoiceSurcharge(key.kind(), key.percent(), basis, credit),
              (a, b) -> new InvoiceSurcharge(a.kind(), a.percent(), a.basis().plus(b.basis()), a.credit().plus(b.credit())));
        });
    return List.copyOf(byKindAndRate.values());
  }

  /** In the order of the kinds, then by rate. */
  private record SurchargeKey(SurchargeKind kind, int percent) implements Comparable<SurchargeKey> {
    @Override
    public int compareTo(SurchargeKey other) {
      return kind != other.kind ? kind.compareTo(other.kind) : Integer.compare(percent, other.percent);
    }
  }

  /** The total of the invoice with the surcharges listed apart added to it. */
  public Duration getTotalDurationVisibleWithSurcharges() {
    return getSurcharges().stream().map(InvoiceSurcharge::credit).reduce(getTotalDurationVisible(), Duration::plus);
  }

  public BigDecimal getTotalHoursVisibleWithSurcharges() {
    return BigDecimal
        .valueOf(getTotalDurationVisibleWithSurcharges().toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }

  private Stream<InvoiceTimereport> bookings() {
    return suborders.stream().flatMap(suborder -> suborder.getTimereports().stream());
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
