package de.hbt.salat.invoice.domain;

import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import de.hbt.salat.order.domain.SurchargeKind;

/**
 * One position of time surcharges on an invoice (#1470): the flagged bookings of one kind at one rate,
 * how long they were and what the surcharge on them comes to. The rate is the one stored with the
 * booking — the customer is charged what the person is credited with.
 *
 * @param basis  the booked duration of the flagged bookings
 * @param credit the surcharge on them, the sum of their credits
 */
public record InvoiceSurcharge(SurchargeKind kind, int percent, Duration basis, Duration credit) {

  /** What the preview script finds the position by. */
  public String getKey() {
    return kind.name() + "-" + percent;
  }

  public BigDecimal getHours() {
    return BigDecimal
        .valueOf(credit.toMinutes())
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
  }
}
