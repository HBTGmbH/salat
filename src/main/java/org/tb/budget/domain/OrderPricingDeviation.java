package org.tb.budget.domain;

import java.time.LocalDate;
import org.tb.order.domain.Customerorder;

/**
 * Where the validity of a rate disagrees with the validity of its customer order (#957).
 *
 * <p>A rate references its order by sign and outlives it; nothing ties the two validities together,
 * neither when saving nor later. A rate is billing ground, so it is never silently adjusted — the
 * disagreement is reported instead of resolved.
 *
 * @param startsBeforeOrder    the rate is already in effect before the order starts
 * @param endsAfterOrder       the rate still runs after the order has ended. An open rate end is
 *                             this case as soon as the order has an end at all; against an open
 *                             order it is none.
 * @param uncoveredOrderPeriod the order period is not covered end to end by the order-wide rates.
 *                             Judged against those alone: a rate for one suborder or one person is
 *                             not meant to cover the order, so holding it to that would report a
 *                             gap for every specific rate that exists.
 */
public record OrderPricingDeviation(
    boolean startsBeforeOrder,
    boolean endsAfterOrder,
    boolean uncoveredOrderPeriod) {

    public static final OrderPricingDeviation NONE = new OrderPricingDeviation(false, false, false);

    /**
     * How this rate disagrees with its order. A rate whose order no longer exists has nothing to
     * disagree with — it keeps its place in the list either way, or it could not be reached at all.
     *
     * @param coverage all rates of the order, expired ones included: coverage is a property of the
     *                 stored rates, not of the ones the list currently shows.
     */
    public static OrderPricingDeviation of(OrderPricing pricing, Customerorder order,
                                           OrderPricingLookup coverage) {
        if (order == null) {
            return NONE;
        }
        return new OrderPricingDeviation(
            startsBefore(pricing.getValidFrom(), order.getFromDate()),
            endsAfter(pricing.getValidUntil(), order.getUntilDate()),
            pricing.isOrderWide()
                && coverage.hasUncoveredPeriod(pricing.getCustomerorderSign(),
                    order.getFromDate(), order.getUntilDate()));
    }

    public boolean any() {
        return startsBeforeOrder || endsAfterOrder || uncoveredOrderPeriod;
    }

    private static boolean startsBefore(LocalDate validFrom, LocalDate orderFrom) {
        return orderFrom != null && validFrom.isBefore(orderFrom);
    }

    private static boolean endsAfter(LocalDate validUntil, LocalDate orderUntil) {
        return orderUntil != null && validUntil.isAfter(orderUntil);
    }

}
