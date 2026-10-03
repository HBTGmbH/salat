package de.hbt.salat.order.domain;

/**
 * A customer order as a select offers it (#1283): what its option shows — sign, short description,
 * customer and the hidden marker — read in one query with the customer, so that neither the
 * suborders nor an association of the entity is loaded per row.
 *
 * <p>{@code shortdescription} and {@code description} are the stored columns; the option shows
 * {@link #shortdescriptionOrDescription()}, as the entity does.
 */
public record CustomerorderOption(long id, String sign, String shortdescription, String description,
    String customerShortname, String customerName, Boolean hide) {

  /** What {@link Customerorder#getShortdescription()} answers for the same values. */
  public String shortdescriptionOrDescription() {
    return Customerorder.shortdescriptionOf(shortdescription, description);
  }
}
