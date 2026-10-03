package de.hbt.salat.budget.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.order.service.SuborderService;

/**
 * Reads where a plan or a flat rate sits in the order tree from the ids it stores (#1205), so that
 * {@code BudgetScope} compares the current tree rather than a sign written when the record was saved.
 *
 * <p>Empty for a suborder that no longer exists — it covers nothing.
 */
@Component
@RequiredArgsConstructor
public class OrderPositions {

    private final SuborderService suborderService;

    public Optional<OrderPosition> of(OrderBudget plan) {
        return of(plan.getCustomerorderId(), plan.getSuborderId());
    }

    public Optional<OrderPosition> of(OrderFlatRate flatRate) {
        return of(flatRate.getCustomerorderId(), flatRate.getSuborderId());
    }

    /**
     * @param suborderId {@code null} for the order as a whole
     */
    public Optional<OrderPosition> of(long customerorderId, Long suborderId) {
        if (suborderId == null) {
            return Optional.of(OrderPosition.orderWide(customerorderId));
        }
        var suborder = suborderService.getSuborderById(suborderId);
        return suborder == null ? Optional.empty() : Optional.of(OrderPosition.of(suborder));
    }

}
