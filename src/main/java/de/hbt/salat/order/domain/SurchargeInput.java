package de.hbt.salat.order.domain;

import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The surcharge rates as the order or suborder form sends them (#1469). An empty percent means no
 * surcharge on the order and inherit on a suborder; the night times only count with a night percent.
 */
public record SurchargeInput(
    Integer nightPercent,
    LocalTime nightFrom,
    LocalTime nightUntil,
    Integer saturdayPercent,
    Integer sundayPercent,
    Integer holidayPercent
) {

  public static final SurchargeInput NONE = new SurchargeInput(null, null, null, null, null, null);

  /** The highest rate accepted, a typo guard rather than a business rule. */
  public static final int MAX_PERCENT = 1000;

  public Integer percent(SurchargeKind kind) {
    return switch (kind) {
      case NIGHT -> nightPercent;
      case SATURDAY -> saturdayPercent;
      case SUNDAY -> sundayPercent;
      case HOLIDAY -> holidayPercent;
    };
  }

  /** What an order or suborder carries now, read back for the form: {@code co::getSurchargeRate}. */
  public static SurchargeInput of(Function<SurchargeKind, SurchargeRate> rates) {
    var night = rates.apply(SurchargeKind.NIGHT);
    var saturday = rates.apply(SurchargeKind.SATURDAY);
    var sunday = rates.apply(SurchargeKind.SUNDAY);
    var holiday = rates.apply(SurchargeKind.HOLIDAY);
    return new SurchargeInput(
        night != null ? night.percent() : null,
        night != null ? night.nightFrom() : null,
        night != null ? night.nightUntil() : null,
        saturday != null ? saturday.percent() : null,
        sunday != null ? sunday.percent() : null,
        holiday != null ? holiday.percent() : null);
  }

  /** Whether every given percent lies between 0 and {@link #MAX_PERCENT}. */
  public boolean percentsInRange() {
    return Arrays.stream(SurchargeKind.values()).map(this::percent).allMatch(SurchargeInput::isPercentInRange);
  }

  /** Whether {@code percent} is empty or lies between 0 and {@link #MAX_PERCENT}. */
  public static boolean isPercentInRange(Integer percent) {
    return percent == null || (percent >= 0 && percent <= MAX_PERCENT);
  }

  /** Whether a night percent above zero comes with a beginning and an end that differ. */
  public boolean nightTimesComplete() {
    return nightPercent == null || nightPercent == 0
        || (nightFrom != null && nightUntil != null && !nightFrom.equals(nightUntil));
  }

  /**
   * Checks the input and hands the rate of every kind to {@code target}, {@code null} for an empty percent —
   * the order and the suborder store their rates alike and differ only in the error codes they refuse with.
   */
  public void applyTo(BiConsumer<SurchargeKind, SurchargeRate> target, ErrorCode percentInvalid, ErrorCode nightTimesMissing) {
    if (!percentsInRange()) {
      throw new InvalidDataException(percentInvalid, String.valueOf(MAX_PERCENT));
    }
    if (!nightTimesComplete()) {
      throw new InvalidDataException(nightTimesMissing);
    }
    for (var kind : SurchargeKind.values()) {
      target.accept(kind, rate(kind));
    }
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
