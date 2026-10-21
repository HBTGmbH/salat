package de.hbt.salat.dailyreport.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Booked time in working days (#1512, #827): each booking divided by the daily working time of the
 * contract it was booked on, as with the vacation. A person may book on contracts with different
 * working times in one year, so the sum is kept as an exact fraction {@code numerator / denominator}
 * days: a sum of decimals would land at 0.9999 days where a full day was booked.
 */
public record WorkingDays(long numerator, long denominator) {

  public static final WorkingDays ZERO = new WorkingDays(0, 1);

  /** {@code minutes} booked on a contract with {@code dailyWorkingMinutes} a day; that must be positive. */
  public static WorkingDays of(long minutes, long dailyWorkingMinutes) {
    return ZERO.plus(minutes, dailyWorkingMinutes);
  }

  public WorkingDays plus(long minutes, long dailyWorkingMinutes) {
    if (dailyWorkingMinutes <= 0) {
      throw new IllegalArgumentException("a working day has a positive length: " + dailyWorkingMinutes);
    }
    long common = lcm(denominator, dailyWorkingMinutes);
    return new WorkingDays(numerator * (common / denominator) + minutes * (common / dailyWorkingMinutes), common);
  }

  /** Whether these are at least {@code days} working days. */
  public boolean reaches(int days) {
    return numerator >= days * denominator;
  }

  /** The working days still missing to {@code days}, rounded up to a tenth; zero once they are reached. */
  public BigDecimal missingTo(int days) {
    long missing = Math.max(0, days * denominator - numerator);
    return BigDecimal.valueOf(missing).divide(BigDecimal.valueOf(denominator), 1, RoundingMode.CEILING);
  }

  /** The working days as a decimal, for comparing and for showing. */
  public BigDecimal toDecimal() {
    return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
  }

  private static long lcm(long a, long b) {
    return a / gcd(a, b) * b;
  }

  private static long gcd(long a, long b) {
    return b == 0 ? a : gcd(b, a % b);
  }
}
