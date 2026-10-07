package de.hbt.salat.order.domain;

import java.time.LocalDate;

/**
 * A customer order as the command palette (#1157) and the order dialog of the booking list (#1331) find
 * it: plain values, read in one query with its customer, so that no association of the entity is loaded
 * per row.
 */
public record CustomerorderSearchRow(long id, String sign, String shortdescription, String description,
    long customerId, String customerShortname, String customerName, Boolean hide, LocalDate untilDate) {

  /** What {@link Customerorder#getShortdescription()} answers for the same values. */
  public String shortdescriptionOrDescription() {
    return Customerorder.shortdescriptionOf(shortdescription, description);
  }
}
