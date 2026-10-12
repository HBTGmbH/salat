package de.hbt.salat.order.domain;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalTime;

/**
 * The rate of one {@link SurchargeKind} as an order or suborder sets it (#1469). Zero is a rate of its
 * own: a suborder says with it that it pays no surcharge although the order does.
 *
 * @param nightFrom only for {@link SurchargeKind#NIGHT}: when the night begins, a hint for whoever
 *     books — a booking has no time of day, so nothing checks it
 * @param nightUntil only for {@link SurchargeKind#NIGHT}: when the night ends
 */
public record SurchargeRate(int percent, LocalTime nightFrom, LocalTime nightUntil) implements Serializable {

  public static SurchargeRate of(int percent) {
    return new SurchargeRate(percent, null, null);
  }

  /** Whether a booking can be flagged with this kind at all — not at a rate of zero. */
  public boolean offered() {
    return percent > 0;
  }

  /** The credit for {@code duration}: duration × rate, rounded to whole minutes. */
  public Duration creditFor(Duration duration) {
    return creditFor(duration, percent);
  }

  public static Duration creditFor(Duration duration, int percent) {
    return Duration.ofMinutes(Math.round(duration.toMinutes() * percent / 100.0));
  }
}
