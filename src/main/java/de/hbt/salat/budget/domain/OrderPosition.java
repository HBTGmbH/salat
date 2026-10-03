package de.hbt.salat.budget.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import de.hbt.salat.order.domain.Suborder;

/**
 * Where something sits in the order tree, by id (#1205): the customer order, and the suborders from
 * the top level down to the one meant — empty for the order as a whole.
 *
 * <p>This is what {@link BudgetScope} compares, instead of the complete order sign it compared
 * before. A sign can be changed and a suborder can be moved to another parent; an id path read from
 * the current tree follows both, a stored sign followed neither.
 *
 * @param customerorderId the customer order
 * @param suborderPath    the ids of the suborders from the top level down to the one meant
 */
public record OrderPosition(long customerorderId, List<Long> suborderPath) {

    public OrderPosition {
        suborderPath = List.copyOf(suborderPath);
    }

    public static OrderPosition orderWide(long customerorderId) {
        return new OrderPosition(customerorderId, List.of());
    }

    /**
     * The position of a suborder, read by climbing its parents. That walks the lazily fetched parent
     * chain — the same walk {@code Suborder#getCompleteOrderSign()} did — so a caller asking for many
     * bookings of one suborder resolves the position once.
     */
    public static OrderPosition of(Suborder suborder) {
        var path = new ArrayList<Long>();
        for (var current = suborder; current != null; current = current.getParentorder()) {
            path.add(current.getId());
        }
        Collections.reverse(path);
        return new OrderPosition(suborder.getCustomerorder().getId(), path);
    }

    /** Whether the position is the customer order as a whole. */
    public boolean isOrderWide() {
        return suborderPath.isEmpty();
    }

    /**
     * Which level the position sits on: 0 for the order as a whole, 1 for a direct suborder of the
     * customer order, 2 for its children and so on (#1004).
     */
    public int level() {
        return suborderPath.size();
    }

    /** Whether the position is that suborder or lies anywhere below it. */
    public boolean liesWithin(long suborderId) {
        return suborderPath.contains(suborderId);
    }

}
