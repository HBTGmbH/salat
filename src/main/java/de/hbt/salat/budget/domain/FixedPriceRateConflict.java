package de.hbt.salat.budget.domain;

import java.time.LocalDate;
import java.util.Collection;
import de.hbt.salat.budget.domain.OrderBudgetBinding.PositionedSuborder;

/**
 * Whether a customer rate earns hourly revenue where a fixed-price plan already earns through its
 * flat rates (#1404). The controlling would then count the work twice: once as the fixed price, once
 * as hours times rate. Nothing is refused — the rate may be meant for work outside the fixed price —
 * but the person saving it, and everybody looking at the plan or the rate list, is told.
 *
 * <p>The rate meets the plan where it could price one of its bookings, by the same conditions the
 * binding of a rate to a plan is judged by (→ {@link OrderBudgetBinding}): same order, intersecting
 * scopes, overlapping validities. A rate bound to another plan prices only that plan's bookings and
 * therefore never meets this one. A rate of 0 EUR is a deliberate statement and earns nothing, so it
 * is no conflict; {@link AppliedRate} keeps that apart from "no rate" for the same reason.
 */
public final class FixedPriceRateConflict {

    private FixedPriceRateConflict() {
    }

    /**
     * @param customerorderId the order of the rate
     * @param suborderPattern the suborder pattern of the rate, {@code null} for the whole order
     * @param ratePlanId      the plan the rate is bound to, {@code null} for a rate without plan
     * @param validUntil      the end of the rate; an open end is the sentinel 31.12.2999
     * @param suborders       the suborders of the order with their sign and position
     */
    public static boolean conflicts(OrderBudget plan, Long customerorderId, String suborderPattern,
                                    Long ratePlanId, Integer priceCentsPerHour,
                                    LocalDate validFrom, LocalDate validUntil,
                                    Collection<PositionedSuborder> suborders) {
        if (!plan.isFixedPrice() || !earnsSomething(priceCentsPerHour)) {
            return false;
        }
        if (isBoundToAnotherPlan(plan, ratePlanId)) {
            return false;
        }
        return OrderBudgetBinding.periodsOverlap(plan, validFrom, validUntil)
            && OrderBudgetBinding.scopeMeetsPattern(plan, customerorderId, suborderPattern, suborders);
    }

    /** The same question for a stored rate. */
    public static boolean conflicts(OrderBudget plan, OrderPricing rate, Collection<PositionedSuborder> suborders) {
        return conflicts(plan, rate.getCustomerorderId(), rate.getSuborderSign(), rate.getOrderBudgetId(),
            rate.getPriceCentsPerHour(), rate.getValidFrom(), rate.getValidUntil(), suborders);
    }

    private static boolean earnsSomething(Integer priceCentsPerHour) {
        return priceCentsPerHour != null && priceCentsPerHour > 0;
    }

    private static boolean isBoundToAnotherPlan(OrderBudget plan, Long ratePlanId) {
        return ratePlanId != null && !ratePlanId.equals(plan.getId());
    }

}
