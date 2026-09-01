package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory resolver for the pricing fallback hierarchy
 * (employee-specific → suborder-wide → order-wide).
 *
 * <p>Resolving a rate per time report through the repository produced up to three statements for
 * every single report — on a production-sized data set that dominated the budget dashboard with
 * ~37.000 of its ~39.000 statements. The pricing table is small (a few hundred rows), so the whole
 * relevant slice is loaded once and every lookup is answered from memory.
 *
 * <p>Overlapping validity ranges are rejected on save ({@code OrderPricingService.checkNoOverlap}),
 * so at most one row can match a key and date. Should overlaps exist anyway, the row with the
 * lowest id wins — that is what the repository queries returned as {@code get(0)}.
 */
public final class OrderPricingLookup {

    private record Key(String customerorderSign, String suborderSign, String employeeSign) {}

    private final Map<Key, List<OrderPricing>> byKey;

    private OrderPricingLookup(Map<Key, List<OrderPricing>> byKey) {
        this.byKey = byKey;
    }

    /** Builds a lookup over the given pricings. Iteration order of {@code pricings} defines precedence. */
    public static OrderPricingLookup of(Collection<OrderPricing> pricings) {
        Map<Key, List<OrderPricing>> byKey = new HashMap<>();
        for (var pricing : pricings) {
            byKey.computeIfAbsent(
                new Key(pricing.getCustomerorderSign(), pricing.getSuborderSign(), pricing.getEmployeeSign()),
                k -> new ArrayList<>()).add(pricing);
        }
        return new OrderPricingLookup(byKey);
    }

    public Optional<OrderPricing> findEffectiveRate(String customerorderSign, String suborderSign,
                                                    String employeeSign, LocalDate date) {
        if (suborderSign != null && employeeSign != null) {
            var rate = find(new Key(customerorderSign, suborderSign, employeeSign), date);
            if (rate.isPresent()) return rate;
        }
        if (suborderSign != null) {
            var rate = find(new Key(customerorderSign, suborderSign, null), date);
            if (rate.isPresent()) return rate;
        }
        return find(new Key(customerorderSign, null, null), date);
    }

    private Optional<OrderPricing> find(Key key, LocalDate date) {
        return byKey.getOrDefault(key, List.of()).stream()
            .filter(p -> !p.getValidFrom().isAfter(date) && !p.getValidUntil().isBefore(date))
            .findFirst();
    }

}
