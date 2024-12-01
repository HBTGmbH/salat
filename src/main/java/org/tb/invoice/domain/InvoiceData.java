package org.tb.invoice.domain;

import static java.time.Duration.ZERO;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.tb.common.LocalDateRange;
import org.tb.customer.domain.Customer;
import org.tb.invoice.service.InvoiceService.InvoiceOptions;

@Data
public class InvoiceData {

  private final InvoiceOptions invoiceOptions;
  private final LocalDateRange invoiceDateRange;
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
        .filter(InvoiceSuborder::isVisible)
        .flatMap(invoiceSuborder -> invoiceSuborder.getTimereports().stream())
        .filter(InvoiceTimereport::isVisible)
        .map(InvoiceTimereport::getDuration)
        .reduce(ZERO, Duration::plus);
  }

  public BigDecimal getTotalHoursVisible() {
    return BigDecimal
        .valueOf(getTotalDurationVisible().toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }

}