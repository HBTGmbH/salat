package de.hbt.salat.order.domain;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.function.Function;

/**
 * The surcharge rates as the order or suborder form sends them (#1469). An empty percent means no
 * surcharge on the order and inherit on a suborder; the night times only count with a night percent.
 */
public record SurchargeInput(
    Integer nightPercent,
    LocalTime nightFrom,
    LocalTime nightUntil,
    Integer weekendPercent,
    Integer holidayPercent
) {

  public static final SurchargeInput NONE = new SurchargeInput(null, null, null, null, null);

  /** The highest rate accepted, a typo guard rather than a business rule. */
  public static final int MAX_PERCENT = 1000;

  public Integer percent(SurchargeKind kind) {
    return switch (kind) {
      case NIGHT -> nightPercent;
      case WEEKEND -> weekendPercent;
      case HOLIDAY -> holidayPercent;
    };
  }

  /** What an order or suborder carries now, read back for the form: {@code co::getSurchargeRate}. */
  public static SurchargeInput of(Function<SurchargeKind, SurchargeRate> rates) {
    var night = rates.apply(SurchargeKind.NIGHT);
    var weekend = rates.apply(SurchargeKind.WEEKEND);
    var holiday = rates.apply(SurchargeKind.HOLIDAY);
    return new SurchargeInput(
        night != null ? night.percent() : null,
        night != null ? night.nightFrom() : null,
        night != null ? night.nightUntil() : null,
        weekend != null ? weekend.percent() : null,
        holiday != null ? holiday.percent() : null);
  }

  /** Whether every given percent lies between 0 and {@link #MAX_PERCENT}. */
  public boolean percentsInRange() {
    return Arrays.stream(SurchargeKind.values())
        .map(this::percent)
        .allMatch(p -> p == null || (p >= 0 && p <= MAX_PERCENT));
  }

  /** Whether a night percent above zero comes with a beginning and an end that differ. */
  public boolean nightTimesComplete() {
    return nightPercent == null || nightPercent == 0
        || (nightFrom != null && nightUntil != null && !nightFrom.equals(nightUntil));
  }

  /** The rate of {@code kind} to store, {@code null} for an empty percent; check the input first. */
  public SurchargeRate rate(SurchargeKind kind) {
    var percent = percent(kind);
    if (percent == null) {
      return null;
    }
    return kind == SurchargeKind.NIGHT && percent > 0
        ? new SurchargeRate(percent, nightFrom, nightUntil)
        : SurchargeRate.of(percent);
  }
}
