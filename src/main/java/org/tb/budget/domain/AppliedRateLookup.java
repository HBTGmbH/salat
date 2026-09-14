package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;

/**
 * The one resolution behind the "Mitarbeitende" card and the rate columns of the booking list
 * (#964): given a person, a suborder and a day, which cost category and which condition apply.
 *
 * <p>Card and rows are two views of this resolution, not two resolutions. If they named different
 * rates one of them would be wrong, so both ask this class — built once per request and then
 * answered from memory, like the two lookups it composes.
 *
 * <p>Each suborder is flattened into plain values <em>here</em>, once. {@code getCompleteOrderSign()}
 * climbs the chain of parent suborders and {@code getEffectiveOrderType()} reaches for the customer
 * order, and doing either per booking is the pattern {@code docs/performance-tips.md} exists to
 * prevent. Both rate lookups take the same complete order sign and only treat it differently —
 * {@link EmployeeCostLookup} compares it for equality, {@link OrderPricingLookup} as a LIKE pattern.
 *
 * <p>A {@code null} cost lookup means costs are not reported at all: they are managers-only, and
 * {@code EmployeeCostService.lookup()} must not even be called for anybody else. That is why the
 * flag travels into every {@link AppliedRate} rather than being weighed up by each caller.
 */
public final class AppliedRateLookup {

    /** What the resolution needs of a suborder — read once, not once per booking. */
    private record SuborderRates(String completeOrderSign, boolean invoiceable, OrderType orderType) {}

    private final String customerorderSign;
    private final Map<Long, SuborderRates> subordersById;
    private final EmployeeCostLookup costLookup;
    private final OrderPricingLookup pricingLookup;

    private AppliedRateLookup(String customerorderSign, Map<Long, SuborderRates> subordersById,
                              EmployeeCostLookup costLookup, OrderPricingLookup pricingLookup) {
        this.customerorderSign = customerorderSign;
        this.subordersById = subordersById;
        this.costLookup = costLookup;
        this.pricingLookup = pricingLookup;
    }

    /**
     * @param costLookup {@code null} where costs are not reported — see the class comment
     */
    public static AppliedRateLookup of(String customerorderSign, Collection<Suborder> suborders,
                                       EmployeeCostLookup costLookup, OrderPricingLookup pricingLookup) {
        Map<Long, SuborderRates> subordersById = new HashMap<>();
        for (var suborder : suborders) {
            subordersById.put(suborder.getId(), new SuborderRates(
                suborder.getCompleteOrderSign(), suborder.isInvoiceable(),
                suborder.getEffectiveOrderType()));
        }
        return new AppliedRateLookup(customerorderSign, subordersById, costLookup, pricingLookup);
    }

    /** Whether the cost side is resolved at all. */
    public boolean includesCosts() {
        return costLookup != null;
    }

    /**
     * What applies to the work of that person on that suborder on that day.
     *
     * <p>A suborder this lookup does not know resolves to no rate on either side and counts as
     * invoiceable. That is a booking whose suborder could not be read at all — the caller already
     * says so in the log; inventing a rate for it would be the worse answer.
     */
    public AppliedRate resolve(String employeeSign, long suborderId, LocalDate day) {
        var suborder = subordersById.get(suborderId);
        if (suborder == null) {
            return AppliedRate.none(includesCosts());
        }
        var cost = costLookup == null ? null : costLookup
            .findEffectiveCost(employeeSign, suborder.completeOrderSign(), suborder.orderType(), day)
            .orElse(null);
        var price = pricingLookup
            .findEffectiveRate(customerorderSign, suborder.completeOrderSign(), employeeSign, day)
            .orElse(null);
        return new AppliedRate(
            cost == null ? null : cost.getName(),
            cost == null ? null : cost.getCostCentsPerHour(),
            price == null ? null : price.getPriceCentsPerHour(),
            suborder.invoiceable(),
            includesCosts());
    }

}
