package de.hbt.salat.order.domain;

/**
 * An inherited ticket reference setting and where it comes from (#1326) — what the suborder form shows
 * under its field, "geerbt: höchstens 1, vom Auftrag 4711".
 *
 * @param suborder the suborder above that carries the setting; {@code null} when it is the order's
 */
public record TicketReferencePolicySource(TicketReferencePolicy policy, Suborder suborder, Customerorder customerorder) {

  /**
   * What a suborder below {@code parent} — or at the top of {@code customerorder} where {@code parent}
   * is {@code null} — inherits: the setting of the nearest suborder from {@code parent} upwards that
   * has one, else the order's.
   */
  public static TicketReferencePolicySource inheritedBy(Customerorder customerorder, Suborder parent) {
    for (var current = parent; current != null; current = current.getParentorder()) {
      var own = current.getTicketReferencePolicy();
      if (own != null) {
        return new TicketReferencePolicySource(own, current, customerorder);
      }
    }
    return new TicketReferencePolicySource(customerorder.getTicketReferencePolicy(), null, customerorder);
  }

  /** The complete sign of whatever carries the setting. */
  public String sourceSign() {
    return suborder != null ? suborder.getCompleteOrderSign() : customerorder.getSign();
  }

  public boolean fromOrder() {
    return suborder == null;
  }
}
