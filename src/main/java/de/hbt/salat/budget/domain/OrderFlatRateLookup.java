package de.hbt.salat.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Objects;

/**
 * In-memory view of the flat rates of a set of customer orders (#972), for the same reason
 * {@link OrderPricingLookup} exists: the budget dashboard evaluates every plan of every order it
 * shows, and resolving the flat rates per plan by query would multiply the statements by the number
 * of plans.
 */
public final class OrderFlatRateLookup {

    private final Map<Long, List<OrderFlatRate>> byCustomerorderId;

    private OrderFlatRateLookup(Map<Long, List<OrderFlatRate>> byCustomerorderId) {
        this.byCustomerorderId = byCustomerorderId;
    }

    /**
     * Keyed by the id of the customer order (#1205).
     */
    public static OrderFlatRateLookup of(Collection<OrderFlatRate> flatRates) {
        Map<Long, List<OrderFlatRate>> byCustomerorderId = new HashMap<>();
        for (var flatRate : flatRates) {
            byCustomerorderId
                .computeIfAbsent(flatRate.getCustomerorderId(), id -> new ArrayList<>())
                .add(flatRate);
        }
        return new OrderFlatRateLookup(byCustomerorderId);
    }

    /** The suborders the flat rates of that customer order name, by id. */
    public Set<Long> suborderIds(long customerorderId) {
        return byCustomerorderId.getOrDefault(customerorderId, List.of()).stream()
            .map(OrderFlatRate::getSuborderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * The amounts of that customer order falling due between the two days, both boundaries
     * included, in due date order.
     */
    public List<FlatRateDueAmount> dueAmounts(long customerorderId, LocalDate from, LocalDate until) {
        if (from.isAfter(until)) {
            return List.of();
        }
        return byCustomerorderId.getOrDefault(customerorderId, List.of()).stream()
            .flatMap(flatRate -> flatRate.dueAmountsWithin(from, until).stream())
            .sorted(Comparator.comparing(FlatRateDueAmount::due))
            .toList();
    }

}
