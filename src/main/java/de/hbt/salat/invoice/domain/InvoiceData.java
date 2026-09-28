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
  private final List<InvoiceSuborder> suborders;

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