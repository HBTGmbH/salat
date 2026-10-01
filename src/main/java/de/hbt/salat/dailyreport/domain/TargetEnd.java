package de.hbt.salat.dailyreport.domain;

import static de.hbt.salat.common.GlobalConstants.BREAK_MINUTES_AFTER_NINE_HOURS;
import static de.hbt.salat.common.GlobalConstants.BREAK_MINUTES_AFTER_SIX_HOURS;
import static de.hbt.salat.common.GlobalConstants.NINE_HOURS_IN_MINUTES;
import static de.hbt.salat.common.GlobalConstants.SIX_HOURS_IN_MINUTES;

import java.time.Duration;
import java.time.LocalTime;

/**
 * When the contract target of a day is fulfilled, the "Vertrag-Soll" of the daily view (#1236).
 *
 * <p>Start, break and the target of the day add up. With the mandatory break considered, a booked
 * break shorter than the one the target requires counts as the required one: whoever books no break
 * yet still has to take it before the day is done. A longer booked break counts in full. The
 * thresholds are those of the check on the booked time ({@code WD-0006}, {@code WD-0007}), so a
 * target of exactly six or nine hours stays below the next step there and here alike.
 *
 * @param time                   the time of day the target is fulfilled
 * @param mandatoryBreak         the break that counted instead of the booked one, {@code null} while
 *                               the booked break counts - the view marks the time while it is set
 */
public record TargetEnd(LocalTime time, Duration mandatoryBreak) {

  public static TargetEnd of(LocalTime start, Duration bookedBreak, Duration dayTarget, boolean considerMandatoryBreak) {
    var required = considerMandatoryBreak ? mandatoryBreakFor(dayTarget) : Duration.ZERO;
    if (required.compareTo(bookedBreak) > 0) {
      return new TargetEnd(start.plus(required).plus(dayTarget), required);
    }
    return new TargetEnd(start.plus(bookedBreak).plus(dayTarget), null);
  }

  static Duration mandatoryBreakFor(Duration workingTime) {
    if (workingTime.toMinutes() > NINE_HOURS_IN_MINUTES) {
      return Duration.ofMinutes(BREAK_MINUTES_AFTER_NINE_HOURS);
    }
    if (workingTime.toMinutes() > SIX_HOURS_IN_MINUTES) {
      return Duration.ofMinutes(BREAK_MINUTES_AFTER_SIX_HOURS);
    }
    return Duration.ZERO;
  }

}
