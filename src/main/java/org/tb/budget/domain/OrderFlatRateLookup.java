package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory view of the flat rates of a set of customer orders (#972), for the same reason
 * {@link OrderPricingLookup} exists: the budget dashboard evaluates every plan of every order it
 * shows, and resolving the flat rates per plan by query would multiply the statements by the number
 * of plans.
 */
public final class OrderFlatRateLookup {

    private final Map<String, List<OrderFlatRate>> byCustomerorderSign;

    private OrderFlatRateLookup(Map<String, List<OrderFlatRate>> byCustomerorderSign) {
        this.byCustomerorderSign = byCustomerorderSign;
    }

    public static OrderFlatRateLookup of(Collection<OrderFlatRate> flatRates) {
        Map<String, List<OrderFlatRate>> byCustomerorderSign = new HashMap<>();
        for (var flatRate : flatRates) {
            byCustomerorderSign
                .computeIfAbsent(flatRate.getCustomerorderSign(), sign -> new ArrayList<>())
                .add(flatRate);
        }
        return new OrderFlatRateLookup(byCustomerorderSign);
    }

    /**
     * The amounts of that customer order falling due between the two days, both boundaries
     * included, in due date order.
     */
    public List<FlatRateDueAmount> dueAmounts(String customerorderSign, LocalDate from, LocalDate until) {
        if (from.isAfter(until)) {
            return List.of();
        }
        return byCustomerorderSign.getOrDefault(customerorderSign, List.of()).stream()
            .flatMap(flatRate -> flatRate.dueAmountsWithin(from, until).stream())
            .sorted(Comparator.comparing(FlatRateDueAmount::due))
            .toList();
    }

}
