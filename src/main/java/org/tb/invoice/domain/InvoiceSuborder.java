package org.tb.invoice.domain;

import static java.time.Duration.ZERO;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.tb.invoice.service.InvoiceService.InvoiceOptions;
import org.tb.order.domain.Suborder;

@Data
public class InvoiceSuborder implements Serializable {

  private static final long serialVersionUID = 1L;

  private final long id;
  private final String orderDescription;
  private final List<InvoiceTimereport> timereports;
  private final Duration totalDuration;
  private final Duration budget;
  private boolean visible;

  public InvoiceSuborder(Suborder suborder, List<InvoiceTimereport> timereports, InvoiceOptions invoiceOptions) {
    this.id = suborder.getId();
    this.orderDescription = suborder.getCompleteOrderDescription(invoiceOptions.isShortDescriptions(), invoiceOptions.isUseCustomerDescriptions());
    this.timereports = timereports;
    this.visible = true;
    this.totalDuration = timereports.stream()
        .map(InvoiceTimereport::getDuration)
        .reduce(ZERO, Duration::plus);
    this.budget = suborder.getDebithours();
  }

  public BigDecimal getTotalHours() {
    return BigDecimal
        .valueOf(totalDuration.toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }

  public Duration getTotalDurationVisible() {
    if(!visible) return ZERO;
    return timereports.stream()
        .filter(InvoiceTimereport::isVisible)
        .map(InvoiceTimereport::getDuration)
        .reduce(ZERO, Duration::plus);
  }

  public BigDecimal getTotalHoursVisible() {
    if(!visible) return BigDecimal.ZERO;
    return BigDecimal
        .valueOf(getTotalDurationVisible().toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }

}
