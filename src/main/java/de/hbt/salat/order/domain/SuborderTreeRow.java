package de.hbt.salat.order.domain;

/**
 * A suborder as a dialog shows it in the order tree (#1331): plain values, read in one query, and its place in the
 * tree, computed by this module from the edges of the order rather than by another module walking the entity.
 *
 * @param id                the suborder
 * @param completeOrderSign {@link Suborder#getCompleteOrderSign()}, {@code ORDER/01/02}
 * @param shortdescription  {@link Suborder#getShortdescription()} — the short description, or else the description,
 *                          cut to twenty characters
 * @param customerorderId   the customer order it belongs to
 * @param parentId          the suborder it hangs under, {@code null} at the top level
 * @param level             how deep in the tree, {@code 0} directly under the order
 * @param descendantCount   how many suborders lie below it, hidden ones included
 */
public record SuborderTreeRow(long id, String completeOrderSign, String shortdescription, long customerorderId,
    Long parentId, int level, int descendantCount) {

  /** What the query reads; the place in the tree is added by {@link #placed}. */
  public SuborderTreeRow(long id, String completeOrderSign, String shortdescription, long customerorderId,
      Long parentId) {
    this(id, completeOrderSign, shortdescription, customerorderId, parentId, 0, 0);
  }

  public SuborderTreeRow placed(int level, int descendantCount) {
    return new SuborderTreeRow(id, completeOrderSign, shortdescription, customerorderId, parentId, level,
        descendantCount);
  }
}
