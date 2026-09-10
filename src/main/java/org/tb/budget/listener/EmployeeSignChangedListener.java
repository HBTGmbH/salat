package org.tb.budget.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.tb.budget.service.EmployeeCostService;
import org.tb.budget.service.OrderPricingService;
import org.tb.employee.event.EmployeeSignChangedEvent;

/**
 * Carries cost assignments and customer rates over when an employee changes their sign (#966).
 *
 * <p>Both reference the person by sign, while the booking side reads its sign live off the employee
 * ({@code TimereportDAO}). A sign that changes on one side and not on the other makes
 * {@code EmployeeCostLookup} and {@code OrderPricingLookup} resolve nothing, and that shows up as
 * work costing 0 EUR and falling back to the order-wide rate — silently, which is the damage of
 * #922. Anonymizing an employee triggers it by design, and the past work of that person has to keep
 * counting: budgets reach back, and the person merely stops booking.
 *
 * <p>Following the sign is the interim measure. The reference belongs on {@code employee.id}, and
 * once it is there (#968) this listener goes away again.
 */
@Component
@RequiredArgsConstructor
public class EmployeeSignChangedListener {

    private final EmployeeCostService employeeCostService;
    private final OrderPricingService orderPricingService;

    @EventListener
    public void onEmployeeSignChanged(EmployeeSignChangedEvent event) {
        employeeCostService.moveAssignmentsToSign(event.getPreviousSign(), event.getNewSign());
        orderPricingService.movePricingsToSign(event.getPreviousSign(), event.getNewSign());
    }

}
