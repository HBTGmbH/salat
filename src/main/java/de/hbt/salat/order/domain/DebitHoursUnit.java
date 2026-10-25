package de.hbt.salat.order.domain;

import static de.hbt.salat.common.GlobalConstants.DEBITHOURS_UNIT_MONTH;
import static de.hbt.salat.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static de.hbt.salat.common.GlobalConstants.DEBITHOURS_UNIT_YEAR;

import java.util.Arrays;

/** What the debit hours of an order refer to, by name rather than by the code the column stores (#1534). */
public enum DebitHoursUnit {
  TOTAL(DEBITHOURS_UNIT_TOTALTIME), YEAR(DEBITHOURS_UNIT_YEAR), MONTH(DEBITHOURS_UNIT_MONTH);

  private final byte code;

  DebitHoursUnit(byte code) {
    this.code = code;
  }

  /** {@code null} for no unit, and for a code none of the forms know. */
  public static DebitHoursUnit of(Byte code) {
    return code == null ? null
        : Arrays.stream(values()).filter(unit -> unit.code == code).findFirst().orElse(null);
  }
}
