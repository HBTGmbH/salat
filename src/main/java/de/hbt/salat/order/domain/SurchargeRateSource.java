package de.hbt.salat.order.domain;

/**
 * An inherited surcharge rate of one kind and where it comes from (#1469) — what the suborder form shows
 * under the field, "Ohne eigenen Satz gilt: 25 %, vom Auftrag 4711". The same walk upwards as
 * {@link TicketReferencePolicySource}, but per kind: a suborder may set the night rate and inherit the
 * Sunday rate.
 *
 * @param rate {@code null} where neither a suborder above nor the order sets one
 * @param suborder the suborder above that carries the rate; {@code null} when it is the order's
 */
public record SurchargeRateSource(SurchargeKind kind, SurchargeRate rate, Suborder suborder, Customerorder customerorder) {

  /**
   * What a suborder below {@code parent} — or at the top of {@code customerorder} where {@code parent}
   * is {@code null} — inherits for {@code kind}: the rate of the nearest suborder from {@code parent}
   * upwards that has one, else the order's.
   */
  public static SurchargeRateSource inheritedBy(SurchargeKind kind, Customerorder customerorder, Suborder parent) {
    for (var current = parent; current != null; current = current.getParentorder()) {
      var own = current.getSurchargeRate(kind);
      if (own != null) {
        return new SurchargeRateSource(kind, own, current, customerorder);
      }
    }
    return new SurchargeRateSource(kind, customerorder.getSurchargeRate(kind), null, customerorder);
  }

  /** The complete sign of whatever carries the rate. */
  public String sourceSign() {
    return suborder != null ? suborder.getCompleteOrderSign() : customerorder.getSign();
  }

  public boolean fromOrder() {
    return suborder == null;
  }
}
