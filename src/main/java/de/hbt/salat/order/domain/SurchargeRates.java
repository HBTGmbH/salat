package de.hbt.salat.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalTime;
import lombok.EqualsAndHashCode;

/**
 * The surcharge rates of an order or a suborder (#1469), the same six columns on both tables. On the
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

  @Column(name = "surcharge_saturday_percent")
  private Integer saturdayPercent;

  @Column(name = "surcharge_sunday_percent")
  private Integer sundayPercent;

  @Column(name = "surcharge_holiday_percent")
  private Integer holidayPercent;

  /** {@code null} where this order or suborder sets no rate of {@code kind}. */
  public SurchargeRate get(SurchargeKind kind) {
    return switch (kind) {
      case NIGHT -> nightPercent == null ? null : new SurchargeRate(nightPercent, nightFrom, nightUntil);
      case SATURDAY -> saturdayPercent == null ? null : SurchargeRate.of(saturdayPercent);
      case SUNDAY -> sundayPercent == null ? null : SurchargeRate.of(sundayPercent);
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
      case SATURDAY -> saturdayPercent = percent;
      case SUNDAY -> sundayPercent = percent;
      case HOLIDAY -> holidayPercent = percent;
    }
  }
}
