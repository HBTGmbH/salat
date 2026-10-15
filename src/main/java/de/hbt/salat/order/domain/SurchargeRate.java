package de.hbt.salat.order.domain;

import java.io.Serializable;
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

  /** The night an order form proposes until somebody sets another: 20:00 to 06:00. */
  public static final LocalTime USUAL_NIGHT_FROM = LocalTime.of(20, 0);

  public static final LocalTime USUAL_NIGHT_UNTIL = LocalTime.of(6, 0);

  public static SurchargeRate of(int percent) {
    return new SurchargeRate(percent, null, null);
  }

  /** Whether a booking can be flagged with this kind at all — not at a rate of zero. */
  public boolean offered() {
    return percent > 0;
  }
}
