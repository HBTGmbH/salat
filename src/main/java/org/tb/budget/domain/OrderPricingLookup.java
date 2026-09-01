package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.tb.common.util.SqlLikePattern;

/**
 * In-memory resolver for the pricing hierarchy.
 *
 * <p>Resolving a rate per time report through the repository produced up to three statements for
 * every single report — on a production-sized data set that dominated the budget dashboard with
 * ~37.000 of its ~39.000 statements. The pricing table is small (a few hundred rows), so the whole
 * relevant slice is loaded once and every lookup is answered from memory.
 *
 * <h2>Matching</h2>
 *
 * <p>{@code suborderSign} is not a key but an SQL {@code LIKE} pattern, matched against the complete
 * order sign of the suborder <em>with a trailing slash</em> — the shape the reporting SQL builds via
 * its {@code suborder_fqs} view. A pattern ending in a slash therefore covers a suborder and its
 * whole subtree and cannot spill over into a sibling whose sign merely starts with the same
 * characters; {@code %} and {@code _} may be used as wildcards. An empty pattern covers the whole
 * customer order. {@code employeeSign} in contrast is compared for equality, {@code null} meaning
 * "any employee" — the report prefix-matches it too, but stored signs exist that are a prefix of a
 * different employee's sign, so copying that would attach rates to the wrong people.
 *
 * <p>Several patterns can cover the same suborder, so matches are ranked: employee-specific before
 * employee-agnostic, then the longest (most specific) pattern, then the lowest id. The last of those
 * reproduces the {@code get(0)} of the repository queries this class replaced, which mattered when
 * validity ranges overlap — {@code OrderPricingService.checkNoOverlap} rejects that for identical
 * patterns, but deliberately allows a specific pattern to be layered over a general one.
 */
public final class OrderPricingLookup {

    /** A pricing row with its pattern compiled once, rather than per time report. */
    private record Candidate(OrderPricing pricing, SqlLikePattern suborderPattern) {

        boolean covers(String suborderSignWithSlash, String employeeSign) {
            var ownEmployee = pricing.getEmployeeSign();
            return (ownEmployee == null || ownEmployee.equals(employeeSign))
                && suborderPattern.matches(suborderSignWithSlash);
        }
    }

    private record MemoKey(String customerorderSign, String suborderSign, String employeeSign) {}

    private final Map<String, List<Candidate>> byCustomerorderSign;
    private final Map<MemoKey, List<OrderPricing>> covering = new HashMap<>();

    private OrderPricingLookup(Map<String, List<Candidate>> byCustomerorderSign) {
        this.byCustomerorderSign = byCustomerorderSign;
    }

    /** Builds a lookup over the given pricings. */
    public static OrderPricingLookup of(Collection<OrderPricing> pricings) {
        Map<String, List<Candidate>> byCustomerorderSign = new HashMap<>();
        for (var pricing : pricings) {
            byCustomerorderSign
                .computeIfAbsent(pricing.getCustomerorderSign(), k -> new ArrayList<>())
                .add(new Candidate(pricing, SqlLikePattern.startingWith(pricing.getSuborderSign())));
        }
        byCustomerorderSign.values().forEach(candidates -> candidates.sort(bySpecificity()));
        return new OrderPricingLookup(byCustomerorderSign);
    }

    private static Comparator<Candidate> bySpecificity() {
        return Comparator
            .comparing((Candidate c) -> c.pricing().getEmployeeSign() == null)
            .thenComparing(c -> c.suborderPattern().length(), Comparator.reverseOrder())
            .thenComparing(c -> c.pricing().getId(), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    public Optional<OrderPricing> findEffectiveRate(String customerorderSign, String suborderSign,
                                                    String employeeSign, LocalDate date) {
        return covering(customerorderSign, suborderSign, employeeSign).stream()
            .filter(p -> !p.getValidFrom().isAfter(date) && !p.getValidUntil().isBefore(date))
            .findFirst();
    }

    /**
     * The pricings covering this suborder and employee, most specific first. Memoized because the
     * date is the only part that varies per time report, and there are far fewer distinct
     * suborder/employee pairs than time reports.
     */
    private List<OrderPricing> covering(String customerorderSign, String suborderSign, String employeeSign) {
        return covering.computeIfAbsent(new MemoKey(customerorderSign, suborderSign, employeeSign), key -> {
            var withSlash = withTrailingSlash(key.suborderSign());
            return byCustomerorderSign.getOrDefault(key.customerorderSign(), List.of()).stream()
                .filter(c -> c.covers(withSlash, key.employeeSign()))
                .map(Candidate::pricing)
                .toList();
        });
    }

    /**
     * Callers pass the complete order sign as {@code Suborder#getCompleteOrderSign()} returns it. The
     * slash is appended here rather than at the call sites, which share the value with the employee
     * cost lookup, where signs are still compared for equality.
     */
    private static String withTrailingSlash(String suborderSign) {
        if (suborderSign == null || suborderSign.isBlank()) {
            return "";
        }
        return suborderSign.endsWith("/") ? suborderSign : suborderSign + "/";
    }

}
