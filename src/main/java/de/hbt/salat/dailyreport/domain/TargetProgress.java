package de.hbt.salat.dailyreport.domain;

import java.time.Duration;

/**
 * How far the working time of a day, a week or a month has come towards its target, as the time account counts
 * it (#1469): the booked working time plus the time surcharges, which count at once. The bar shows both apart -
 * the surcharges as a piece of their own on top of the booked time -, the percent their sum.
 *
 * @param booked    the booked working time, standby left out (#463)
 * @param surcharge the time surcharge credit on it, standby left out
 * @param target    the target of the range; zero where there is none, then everything is zero
 */
public record TargetProgress(Duration booked, Duration surcharge, Duration target) {

  /** Booked and surcharges against the target, in percent, not capped: beyond 100 the target is exceeded. */
  public int percent() {
    return target.isZero() ? 0 : (int) (booked.plus(surcharge).toMinutes() * 100 / target.toMinutes());
  }

  /**
   * The width of the booked piece of the bar, in percent of the target with one decimal, at most the whole bar.
   * A decimal rather than whole percent: a credit of an hour on a month would otherwise round away.
   */
  public double bookedBar() {
    return target.isZero() ? 0 : Math.min(100, share(booked));
  }

  /** The width of the surcharge piece on top of it, at most what is left of the bar. */
  public double surchargeBar() {
    return target.isZero() ? 0 : Math.min(100 - bookedBar(), share(surcharge));
  }

  private double share(Duration part) {
    return Math.round(part.toMinutes() * 1000.0 / target.toMinutes()) / 10.0;
  }

  /** What is left to the target, negative where it is exceeded. */
  public Duration remaining() {
    return target.minus(booked).minus(surcharge);
  }
}
