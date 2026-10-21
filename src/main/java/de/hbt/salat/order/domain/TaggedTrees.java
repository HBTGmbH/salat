package de.hbt.salat.order.domain;

import java.util.List;

/**
 * Where tags sit in the order trees, as ids (#847): the orders carrying one of them, and the suborders carrying one
 * together with every suborder below them. A booking on a suborder of one of the orders, or on one of the suborders,
 * is in the trees of the tags. Empty when no order and no suborder carries any of them.
 */
public record TaggedTrees(List<Long> customerorderIds, List<Long> suborderIds) {

  public TaggedTrees {
    customerorderIds = List.copyOf(customerorderIds);
    suborderIds = List.copyOf(suborderIds);
  }

  public boolean isEmpty() {
    return customerorderIds.isEmpty() && suborderIds.isEmpty();
  }
}
