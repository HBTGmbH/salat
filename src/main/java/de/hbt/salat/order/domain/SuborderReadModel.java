package de.hbt.salat.order.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A suborder as plain values, computed by this module's own rules (#1338): what another module
 * evaluates a customer order with, without the entity leaving this module (→ ADR-0021, Nachtrag
 * #1338). The path is logic of the entity, not a column; it is computed here so that no reader
 * rebuilds it. The complete order sign is stored with the suborder (#1342) and comes along.
 *
 * @param id                 the suborder
 * @param customerorderId    the customer order it belongs to
 * @param path               the ids of the suborders from the top level down to this one, itself
 *                           included
 * @param completeOrderSign  {@link Suborder#getCompleteOrderSign()}, {@code ORDER/01/02}
 * @param shortdescription   {@link Suborder#getShortdescription()}
 * @param debithours         the planned hours, {@code null} where none are set
 * @param invoiceable        {@link Suborder#isInvoiceable()}
 * @param hide               {@link Suborder#isHide()}
 */
public record SuborderReadModel(long id, long customerorderId, List<Long> path, String completeOrderSign,
    String shortdescription, Duration debithours, boolean invoiceable, boolean hide) {

  public SuborderReadModel {
    path = List.copyOf(path);
  }

  /**
   * Reads the values of a suborder whose whole order is at hand. The path is built from
   * {@code orderSuborders} by the ids of the parents rather than by walking the lazily fetched
   * parent chain, so summarizing every suborder of an order costs no statement beyond the one that
   * read them. A parent missing from the map — which the rule that a parent belongs to the same order
   * rules out — is walked through the entity instead.
   *
   * @param orderSuborders every suborder of the order by id, hidden ones included
   */
  public static SuborderReadModel of(Suborder suborder, Map<Long, Suborder> orderSuborders) {
    var path = new ArrayList<Long>();
    // a parent already on the path would be a cycle; the walk ends there instead of running forever
    for (var current = suborder; current != null && !path.contains(current.getId());
        current = parentOf(current, orderSuborders)) {
      path.add(current.getId());
    }
    Collections.reverse(path);
    return new SuborderReadModel(suborder.getId(), suborder.getCustomerorder().getId(), path,
        suborder.getCompleteOrderSign(),
        suborder.getShortdescription(), suborder.getDebithours(), suborder.isInvoiceable(), suborder.isHide());
  }

  private static Suborder parentOf(Suborder suborder, Map<Long, Suborder> orderSuborders) {
    var parent = suborder.getParentorder();
    return parent == null ? null : orderSuborders.getOrDefault(parent.getId(), parent);
  }

  /** Whether this suborder is the given one or lies anywhere below it. */
  public boolean liesWithin(long suborderId) {
    return path.contains(suborderId);
  }
}
