package de.hbt.salat.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalTime;
import lombok.EqualsAndHashCode;

/**
 * The surcharge rates of an order or a suborder (#1469), the same five columns on both tables. On the
 * order a missing rate means no surcharge of that kind, on a suborder it means inherit — see
 * {@link SurchargeRateSource}. Read and written per kind through the entity.
 */
@Embeddable
@EqualsAndHashCode
public class SurchargeRates implements Serializable {

  @Column(name = "surcharge_night_percent")
  private Integer nightPercent;

  @Column(name = "surcharge_night_from")
  private LocalTime nightFrom;

  @Column(name = "surcharge_night_until")
  private LocalTime nightUntil;

  @Column(name = "surcharge_weekend_percent")
  private Integer weekendPercent;

  @Column(name = "surcharge_holiday_percent")
  private Integer holidayPercent;

  /** {@code null} where this order or suborder sets no rate of {@code kind}. */
  public SurchargeRate get(SurchargeKind kind) {
    return switch (kind) {
      case NIGHT -> nightPercent == null ? null : new SurchargeRate(nightPercent, nightFrom, nightUntil);
      case WEEKEND -> weekendPercent == null ? null : SurchargeRate.of(weekendPercent);
      case HOLIDAY -> holidayPercent == null ? null : SurchargeRate.of(holidayPercent);
    };
  }

  public void set(SurchargeKind kind, SurchargeRate rate) {
    var percent = rate != null ? rate.percent() : null;
    switch (kind) {
      case NIGHT -> {
        nightPercent = percent;
        nightFrom = rate != null ? rate.nightFrom() : null;
        nightUntil = rate != null ? rate.nightUntil() : null;
      }
      case WEEKEND -> weekendPercent = percent;
      case HOLIDAY -> holidayPercent = percent;
    }
  }
}
