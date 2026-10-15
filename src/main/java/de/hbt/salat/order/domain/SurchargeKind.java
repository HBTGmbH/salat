package de.hbt.salat.order.domain;

import lombok.Getter;

/**
 * The kinds of assignment that earn a time surcharge on the overtime account (#1469). A booking carries
 * at most one of them; the rate per kind is set on the order and may differ per suborder.
 */
@Getter
public enum SurchargeKind {

  NIGHT("main.surcharge.kind.night"),
  SATURDAY("main.surcharge.kind.saturday"),
  SUNDAY("main.surcharge.kind.sunday"),
  HOLIDAY("main.surcharge.kind.holiday");

  private final String label;

  SurchargeKind(String label) {
    this.label = label;
  }

  /** Whether the kind belongs to a day of its own — Saturday, Sunday, holiday — rather than any day like the night. */
  public boolean isBoundToDay() {
    return this != NIGHT;
  }
}
