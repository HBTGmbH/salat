package de.hbt.salat.order.domain;

import java.util.List;

/**
 * Where a suborder sits in the order tree, as plain values (#1322): what another module needs that
 * refers to a suborder by id and has to decide which branch it lies in, without the entity leaving
 * this module (→ ADR-0021).
 *
 * @param id                the suborder
 * @param customerorderId   the customer order it belongs to
 * @param path              the ids of the suborders from the top level down to this one, itself
 *                          included
 * @param completeOrderSign {@link Suborder#getCompleteOrderSign()}, {@code ORDER/01/02}
 */
public record SuborderLocation(long id, long customerorderId, List<Long> path, String completeOrderSign) {

  public SuborderLocation {
    path = List.copyOf(path);
  }

  /** Reads the location from the current tree; walks the parent chain of the entity. */
  public static SuborderLocation of(Suborder suborder) {
    // withParents() starts at the suborder itself and climbs
    var path = suborder.withParents().reversed().stream().map(Suborder::getId).toList();
    return new SuborderLocation(suborder.getId(), suborder.getCustomerorder().getId(), path,
        suborder.getCompleteOrderSign());
  }

  /** Whether this suborder is the given one or lies anywhere below it. */
  public boolean liesWithin(long suborderId) {
    return path.contains(suborderId);
  }
}
