package org.tb.budget.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.BudgetControllingColumns;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.SegmentControllingGroup;
import org.tb.budget.domain.SegmentControllingOrder;
import org.tb.budget.domain.SegmentControllingResult;
import org.tb.common.LocalDateRange;
import org.tb.customer.domain.Customer;
import org.tb.customer.domain.CustomerSegment;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

/**
 * The controlling of every customer order at once, grouped by customer segment (#779).
 *
 * <p>Every order that earned or cost something in the window is listed, budgeted or not
 * (→ {@link #candidateSigns}). Profit and margin do not need a plan, and they are what a segment is
 * read for.
 *
 * <p>Each order is evaluated by {@link BudgetControllingService#compute} exactly as its own page
 * evaluates it, and the line this view shows is that evaluation's total over all its sections. The
 * aggregation is therefore not a second calculation: whoever follows the button from a line lands on
 * the page the line was taken from and finds the same figures, including the window semantics of
 * #916 — hours before the window reported apart, amounts over the whole plan.
 *
 * <p>That costs one evaluation per order, and an evaluation reads back to the start of the earliest
 * plan it touches. This is the reason the page is bound to a period and reserved for managers rather
 * than offered as a landing page: it is a report, and it is priced like one. Recomputing the same
 * figures cheaply and differently was the alternative, and it is exactly the drift the single
 * calculation is meant to avoid.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class BudgetSegmentControllingService {

    private final OrderBudgetService orderBudgetService;
    private final OrderFlatRateService orderFlatRateService;
    private final BudgetControllingService budgetControllingService;
    private final CustomerorderService customerorderService;
    private final TimereportService timereportService;

    /**
     * @param from  first day of the evaluated window
     * @param until last day of the evaluated window
     */
    public SegmentControllingResult compute(LocalDate from, LocalDate until) {
        var signs = candidateSigns(from, until);
        var customerorders = customerordersBySign(signs);

        var ordersBySegment = new LinkedHashMap<SegmentKey, List<SegmentControllingOrder>>();
        for (var sign : signs) {
            var customerorder = customerorders.get(sign);
            if (customerorder == null) {
                // A plan whose order has been deleted has nobody to report for. It cannot be placed
                // in a segment either, so it is left out rather than grouped under "no segment".
                continue;
            }
            var evaluation = budgetControllingService.compute(sign, from, until, true);
            if (evaluation.isEmpty()) {
                // Nothing booked, nothing due, nothing planned in the window — the order has no line.
                continue;
            }
            var customer = customerorder.getCustomer();
            ordersBySegment.computeIfAbsent(segmentKeyOf(customer), key -> new ArrayList<>())
                .add(SegmentControllingOrder.of(sign, customerorder.getShortdescription(),
                    customer == null ? null : customer.getShortname(), evaluation.total()));
        }

        var segments = ordersBySegment.entrySet().stream()
            .map(entry -> group(entry.getKey(), entry.getValue()))
            // By segment name, and the orders without one last: a group that stands for missing
            // master data belongs at the end, not sorted in among the real segments.
            .sorted(Comparator.comparing((SegmentControllingGroup g) -> !g.hasSegment())
                .thenComparing(g -> g.segmentName() == null ? "" : g.segmentName(),
                    String.CASE_INSENSITIVE_ORDER))
            .toList();

        return new SegmentControllingResult(new LocalDateRange(from, until), segments,
            columnsOf(segments));
    }

    /**
     * The orders this page reports on: everything that earned or cost something in the window, not
     * only what is budgeted.
     *
     * <p>Profit and margin are the point of the segment view, and both exist without a plan — an
     * order billed by the hour without a budget still earns and still costs. Listing only budgeted
     * orders would have left exactly those out and made a segment look more or less profitable than
     * it is, depending on how completely it happens to be planned.
     *
     * <p>Three sources, because each can be the only one: bookings in the window; an active plan,
     * which may carry a flat rate falling due without anybody booking; and a flat rate on an order
     * that has neither. Orders that turn out to have nothing to report drop out below, when their
     * evaluation comes back empty.
     */
    private List<String> candidateSigns(LocalDate from, LocalDate until) {
        var signs = new LinkedHashSet<String>();
        signs.addAll(timereportService.getCustomerorderSignsWithReportsBetween(from, until));
        orderBudgetService.getAllActiveVisible().stream()
            .map(OrderBudget::getCustomerorderSign)
            .forEach(signs::add);
        signs.addAll(orderFlatRateService.getCustomerorderSignsWithFlatRate());
        return signs.stream().sorted().toList();
    }

    /** Segment identity of an order — {@code null} id and name for a customer without a segment. */
    private record SegmentKey(Long id, String name) {}

    private static SegmentKey segmentKeyOf(Customer customer) {
        CustomerSegment segment = customer == null ? null : customer.getSegment();
        return segment == null ? new SegmentKey(null, null)
            : new SegmentKey(segment.getId(), segment.getName());
    }

    private static SegmentControllingGroup group(SegmentKey key, List<SegmentControllingOrder> orders) {
        var sorted = orders.stream()
            .sorted(Comparator.comparing(SegmentControllingOrder::customerorderSign))
            .toList();
        var totals = sorted.stream().map(SegmentControllingOrder::total).toList();
        // Costs are part of every line here — the page is managers only — so the segment total
        // reports them as well. No budget: the orders of a segment answer to different plans, and
        // some to none at all (→ BudgetControllingColumns#withoutBudget).
        var total = BudgetControllingRow.sum(null, null, totals, null, true);
        return new SegmentControllingGroup(key.id(), key.name(), sorted, total);
    }

    /**
     * The columns of the whole page, decided over every line it shows, the segment totals included.
     * One set for all tables, so that lines in different segments stay comparable — and without
     * anything a plan answers for (→ {@link BudgetControllingColumns#withoutPlan()}): this page
     * reports orders, and some of them have no plan at all.
     */
    private static BudgetControllingColumns columnsOf(List<SegmentControllingGroup> segments) {
        var rows = new ArrayList<BudgetControllingRow>();
        for (var segment : segments) {
            segment.orders().forEach(order -> rows.add(order.total()));
            rows.add(segment.total());
        }
        return BudgetControllingColumns.of(rows).withoutPlan();
    }

    private Map<String, Customerorder> customerordersBySign(List<String> signs) {
        var bySign = new LinkedHashMap<String, Customerorder>();
        for (var customerorder : customerorderService.getCustomerordersBySigns(signs)) {
            bySign.putIfAbsent(customerorder.getSign(), customerorder);
        }
        return bySign;
    }
}
