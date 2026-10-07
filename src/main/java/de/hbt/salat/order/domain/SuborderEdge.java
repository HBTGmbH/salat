package de.hbt.salat.order.domain;

/**
 * One edge of the order tree, as plain values (#1331): a suborder and the suborder it hangs under, {@code null} at the
 * top level of its order.
 */
public record SuborderEdge(long id, Long parentId) {
}
