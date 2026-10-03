package de.hbt.salat.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderFlatRateRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.SuborderService;

/**
 * What the budget module does when the order tree changes (#1205): it keeps the sign columns of its
 * records in step, and it says what still refers to an order or a suborder that is about to go.
 *
 * <p>Plans, flat rates and cost assignments refer to their order and suborder by id now, and the
 * application resolves by that id alone: a renamed order or a moved suborder no longer changes what
 * counts where. The sign columns stay next to the ids only because reports, views and ETL definitions
 * still read them. A stale sign there would let a report lose the plans of a renamed order.
 *
 * <p>The customer rates refer to their order by id as well (#1212). Their suborder is a {@code LIKE}
 * pattern, no reference, and stays as typed.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderReferenceService {

    private final OrderBudgetRepository orderBudgetRepository;
    private final OrderFlatRateRepository orderFlatRateRepository;
    private final EmployeeCostAssignmentRepository assignmentRepository;
    private final OrderPricingRepository orderPricingRepository;
    private final SuborderService suborderService;

    /**
     * Rewrites the sign columns of the budget data of the order from the current tree.
     *
     * <p>Called while the order or one of its suborders is being updated, before it is saved and
     * inside the same transaction. The suborders read here are the managed instances of that
     * transaction, so a complete order sign is already the new one — for a suborder and for
     * everything below it. The whole order is refreshed every time: a renamed or moved suborder
     * changes the complete sign of its subtree, and an order has a handful of such records at most.
     */
    @Authorized(requiresManager = true)
    public void followOrderTree(Customerorder customerorder) {
        var customerorderId = customerorder.getId();
        for (var plan : orderBudgetRepository.findByCustomerorderId(customerorderId)) {
            plan.setCustomerorderSign(customerorder.getSign());
            if (plan.getSuborderId() != null) {
                plan.setSuborderSign(completeOrderSignOf(plan.getSuborderId(), plan.getSuborderSign()));
            }
        }
        for (var flatRate : orderFlatRateRepository.findByCustomerorderIdOrderByValidFromAsc(customerorderId)) {
            flatRate.setCustomerorderSign(customerorder.getSign());
            if (flatRate.getSuborderId() != null) {
                flatRate.setSuborderSign(completeOrderSignOf(flatRate.getSuborderId(), flatRate.getSuborderSign()));
            }
        }
        var suborderIds = suborderService.getSubordersByCustomerorderId(customerorderId).stream()
            .map(Suborder::getId)
            .toList();
        if (!suborderIds.isEmpty()) {
            for (var assignment : assignmentRepository.findBySuborderIdIn(suborderIds)) {
                assignment.setSuborderSign(completeOrderSignOf(assignment.getSuborderId(), assignment.getSuborderSign()));
            }
        }
        orderPricingRepository.updateCustomerorderSign(customerorderId, customerorder.getSign());
    }

    /** How many plans, flat rates and customer rates still refer to the customer order. */
    @Transactional(readOnly = true)
    public References referencesToCustomerorder(long customerorderId) {
        return new References(orderBudgetRepository.countByCustomerorderId(customerorderId),
            orderFlatRateRepository.countByCustomerorderId(customerorderId), 0,
            orderPricingRepository.countByCustomerorderId(customerorderId));
    }

    /** How many plans, flat rates and cost assignments still refer to the suborder. */
    @Transactional(readOnly = true)
    public References referencesToSuborder(long suborderId) {
        return new References(orderBudgetRepository.countBySuborderId(suborderId),
            orderFlatRateRepository.countBySuborderId(suborderId), assignmentRepository.countBySuborderId(suborderId), 0);
    }

    /**
     * What still refers to an order or a suborder. Customer rates name a suborder only by pattern, so
     * they count for the order alone.
     */
    public record References(long plans, long flatRates, long costAssignments, long rates) {

        public boolean any() {
            return plans + flatRates + costAssignments + rates > 0;
        }
    }

    private String completeOrderSignOf(long suborderId, String current) {
        var suborder = suborderService.getSuborderById(suborderId);
        return suborder == null ? current : suborder.getCompleteOrderSign();
    }

}
