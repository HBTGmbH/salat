package org.tb.budget.domain;

import java.util.List;
import java.util.Map;

/**
 * What one pass of {@link AppliedRateLookup} produced for the budget detail page (#964): the
 * "Mitarbeitende" card and the rates of the bookings the page renders.
 *
 * <p>The two travel together because they must not be able to disagree. They are built from the
 * same lookup in one go — the card over <em>all</em> bookings of the period, the map over the ones
 * the capped list shows. Resolving the rates of bookings nobody sees would cost nothing, since the
 * lookup answers from memory, but it would be work for nothing all the same.
 */
public record AppliedRates(BudgetEmployees employees, Map<Long, AppliedRate> byBookingId) {

    /** A plan without a single booking in the period. */
    public static AppliedRates none(boolean costsIncluded) {
        return new AppliedRates(BudgetEmployees.of(List.of(), costsIncluded), Map.of());
    }

    /** The rates of one booking — never {@code null}, so the view needs no case of its own. */
    public AppliedRate of(long timereportId) {
        return byBookingId.getOrDefault(timereportId, AppliedRate.none(employees.costsIncluded()));
    }

}
