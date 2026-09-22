package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.tb.common.LocalDateRange;
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
 * <p>A rate may also be bound to a budget plan (#1065). Such a rate applies only to bookings
 * assigned to that plan; every other booking falls back to the plan-less rate, exactly as work by
 * somebody without their own rate falls back to the employee-agnostic one. The plan is therefore
 * both a filter and a rank, and it has to be both: as a filter alone it would let a foreign plan's
 * rate win, as a rank alone it would price every booking with it.
 *
 * <p>Several patterns can cover the same suborder, so matches are ranked: employee-specific before
 * employee-agnostic, then plan-bound before plan-less, then the longest (most specific) pattern,
 * then the lowest id. The last of those reproduces the {@code get(0)} of the repository queries this
 * class replaced, which mattered when validity ranges overlap —
 * {@code OrderPricingService.checkNoOverlap} rejects that for identical patterns, but deliberately
 * allows a specific pattern to be layered over a general one.
 *
 * <p>The plan sits <em>below</em> the employee and <em>above</em> the pattern, and that position is
 * the one insertion that leaves every existing figure alone: all stored rates are plan-less, so they
 * rank equal on the new step and keep their order among themselves. Swapping the first two steps
 * would reprice history.
 *
 * <p><strong>A second formulation of this rule lives in the user interface</strong> — the help text
 * of the rate form (message key {@code main.pricing.help.selection.*}). Changing
 * {@link #bySpecificity()} means changing that text too, or the form explains a rule the
 * application no longer follows.
 */
public final class OrderPricingLookup {

    /**
     * A pricing row with its pattern compiled once, rather than per time report. The plan id is read
     * once here as well — off the lazy proxy, which does not load the plan.
     */
    private record Candidate(OrderPricing pricing, SqlLikePattern suborderPattern, Long orderBudgetId) {

        boolean covers(String suborderSignWithSlash, String employeeSign, Long bookingPlanId) {
            var ownEmployee = pricing.getEmployeeSign();
            return (ownEmployee == null || ownEmployee.equals(employeeSign))
                // A plan-bound rate is for its own plan only; a plan-less one takes any booking.
                && (orderBudgetId == null || orderBudgetId.equals(bookingPlanId))
                && suborderPattern.matches(suborderSignWithSlash);
        }
    }

    private record MemoKey(String customerorderSign, String suborderSign, String employeeSign,
                           Long orderBudgetId) {}

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
                .add(new Candidate(pricing, SqlLikePattern.startingWith(pricing.getSuborderSign()),
                    pricing.getOrderBudgetId()));
        }
        byCustomerorderSign.values().forEach(candidates -> candidates.sort(bySpecificity()));
        return new OrderPricingLookup(byCustomerorderSign);
    }

    /**
     * The whole rule, in the order its steps apply. Each {@code false} sorts first, so "has an
     * employee" and "has a plan" come before the ones that have none.
     */
    private static Comparator<Candidate> bySpecificity() {
        return Comparator
            .comparing((Candidate c) -> c.pricing().getEmployeeSign() == null)
            .thenComparing(c -> c.orderBudgetId() == null)
            .thenComparing(c -> c.suborderPattern().length(), Comparator.reverseOrder())
            .thenComparing(c -> c.pricing().getId(), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * The rate that applies to that work on that day.
     *
     * @param orderBudgetId the plan the booking is assigned to, or {@code null} for a booking that
     *                      belongs to none. It is read from the stored assignment (#913), never
     *                      derived: deriving it here would resolve a rate against a plan nobody
     *                      picked.
     */
    public Optional<OrderPricing> findEffectiveRate(String customerorderSign, String suborderSign,
                                                    String employeeSign, Long orderBudgetId,
                                                    LocalDate date) {
        return covering(customerorderSign, suborderSign, employeeSign, orderBudgetId).stream()
            .filter(p -> !p.getValidFrom().isAfter(date) && !p.getValidUntil().isBefore(date))
            .findFirst();
    }

    /**
     * Whether the given period is not covered end to end by the order-wide rates of that customer
     * order (#957) — the rates without a suborder pattern and without an employee, the ones that
     * apply to the order as a whole.
     *
     * <p>An order without rates of that kind makes no claim to cover its period, so it reports no
     * gap: the order may well be priced per suborder or per person, and calling that a gap would
     * report one for every order that is. An open order end has to be met by an open rate end,
     * otherwise the order runs on beyond its last rate — which is a gap like any other.
     */
    public boolean hasUncoveredPeriod(String customerorderSign, LocalDate from, LocalDate until) {
        if (from == null) {
            return false;
        }
        var end = until != null ? until : LocalDateRange.FINIT_UNTIL_BOUNDARY;
        var orderWide = byCustomerorderSign.getOrDefault(customerorderSign, List.of()).stream()
            .map(Candidate::pricing)
            .filter(OrderPricing::isOrderWide)
            .sorted(Comparator.comparing(OrderPricing::getValidFrom))
            .toList();
        if (orderWide.isEmpty()) {
            return false;
        }
        // The first day not covered yet; the rates are walked in order, so a rate starting after it
        // leaves a gap behind, and one ending later moves it on.
        var uncovered = from;
        for (var pricing : orderWide) {
            if (pricing.getValidFrom().isAfter(uncovered)) {
                return true;
            }
            if (!pricing.getValidUntil().isBefore(uncovered)) {
                uncovered = pricing.getValidUntil().plusDays(1);
            }
        }
        return !uncovered.isAfter(end);
    }

    /**
     * The pricings covering this suborder, employee and plan, most specific first. Memoized because
     * the date is the only part that varies per time report, and there are far fewer distinct
     * suborder/employee/plan combinations than time reports — the plan adds a dimension to the key
     * but not an order of magnitude, since a booking of an order belongs to one of a handful of
     * plans.
     */
    private List<OrderPricing> covering(String customerorderSign, String suborderSign,
                                        String employeeSign, Long orderBudgetId) {
        var memoKey = new MemoKey(customerorderSign, suborderSign, employeeSign, orderBudgetId);
        return covering.computeIfAbsent(memoKey, key -> {
            var withSlash = withTrailingSlash(key.suborderSign());
            return byCustomerorderSign.getOrDefault(key.customerorderSign(), List.of()).stream()
                .filter(c -> c.covers(withSlash, key.employeeSign(), key.orderBudgetId()))
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
