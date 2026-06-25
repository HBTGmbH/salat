package org.tb.common.util;

import static org.tb.common.GlobalConstants.DEFAULT_TIMEZONE_ID;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single source of truth for "the current time" across the application.
 *
 * <p>All business-logic reads of the current date/time funnel through this class (directly,
 * or via {@link DateUtils#today()} / {@link DateTimeUtils#now()}) instead of calling
 * {@code LocalDate.now()} / {@code LocalDateTime.now()} statically. In production the clock
 * is the system clock at the configured {@code DEFAULT_TIMEZONE_ID}; tests can pin it to a
 * fixed instant via {@link #useFixedClock(LocalDateTime)} / {@link #setClock(Clock)} so that
 * "now" becomes deterministic, and restore live time via {@link #reset()}.
 */
public final class ClockProvider {

  private static volatile Clock clock = systemClock();

  private ClockProvider() {
  }

  private static Clock systemClock() {
    return Clock.system(ZoneId.of(DEFAULT_TIMEZONE_ID));
  }

  public static Clock getClock() {
    return clock;
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public static LocalDate today() {
    return LocalDate.now(clock);
  }

  public static Instant instant() {
    return clock.instant();
  }

  /**
   * Replaces the clock. Intended for tests; production code should never call this.
   */
  public static void setClock(Clock clock) {
    ClockProvider.clock = clock;
  }

  /**
   * Pins "now" to the given local date-time (interpreted in {@code DEFAULT_TIMEZONE_ID}).
   * Intended for tests.
   */
  public static void useFixedClock(LocalDateTime fixed) {
    ZoneId zone = ZoneId.of(DEFAULT_TIMEZONE_ID);
    ClockProvider.clock = Clock.fixed(fixed.atZone(zone).toInstant(), zone);
  }

  /**
   * Restores the live system clock. Intended for tests, to undo {@link #setClock(Clock)} or
   * {@link #useFixedClock(LocalDateTime)}.
   */
  public static void reset() {
    ClockProvider.clock = systemClock();
  }
}
