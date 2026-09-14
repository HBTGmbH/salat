package org.tb.budget.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.InvoicableBudget;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;

/**
 * The narrow read port other modules use to reach budget data (#915). Deliberately small: active
 * plans of one customer order as flat records, and the ids of the bookings assigned to a plan.
 * No entity leaves the module through here.
 *
 * <p>Authorization is the same as everywhere in the budget module (see {@link BudgetAuthorization}):
 * managers and the people responsible for the order. Backoffice has no budget access of its own, so
 * a backoffice employee who is not responsible for the order is offered no plans — the invoicing by
 * suborder is unaffected by that. Widening budget visibility through a side door would be the worse
 * mistake, so this port enforces the rule rather than trusting its callers.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class BudgetQueryService {

    private final OrderBudgetRepository orderBudgetRepository;
    private final TimereportBudgetAssignmentRepository assignmentRepository;
    private final BudgetAuthorization budgetAuthorization;

    /**
     * The active plans of the customer order, empty when the caller may not see its budget data —
     * an empty selection, not an error: the caller has a way to work without a plan.
     */
    public List<InvoicableBudget> getActivePlans(String customerorderSign) {
        if (!budgetAuthorization.isAuthorizedForCustomerorder(customerorderSign)) {
            return List.of();
        }
        return orderBudgetRepository.findByCustomerorderSignAndActive(customerorderSign, Boolean.TRUE)
            .stream()
            .map(InvoicableBudget::from)
            .toList();
    }

    /** One plan, empty when it does not exist or the caller may not see it. */
    public Optional<InvoicableBudget> getPlan(long orderBudgetId) {
        return orderBudgetRepository.findById(orderBudgetId)
            .filter(budgetAuthorization::isAuthorized)
            .map(InvoicableBudget::from);
    }

    /**
     * The ids of the bookings assigned to the plan — all of them, without a date restriction.
     *
     * <p>Narrowing by period is left to the caller, which already reads its bookings by date range
     * and only has to intersect. A period-aware query here would have to join the {@code Timereport}
     * entity in JPQL — permitted, as long as no entity leaves the query (→ ADR-0021, and
     * {@code TimereportBudgetAssignmentRepository.findAssignedBookings} does exactly that). It would
     * only buy a shorter id list here, next to nothing against the per-suborder booking reads the
     * caller does anyway, so it stays as it is for want of a reason, not for want of permission.
     */
    public List<Long> getAssignedTimereportIds(long orderBudgetId) {
        var plan = orderBudgetRepository.findById(orderBudgetId).orElse(null);
        if (plan == null || !budgetAuthorization.isAuthorized(plan)) {
            return List.of();
        }
        return assignmentRepository.findTimereportIdsByOrderBudgetId(orderBudgetId);
    }

}
