package de.hbt.salat.budget.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.budget.service.EmployeeCostService;
import de.hbt.salat.budget.service.OrderPricingService;
import de.hbt.salat.employee.event.EmployeeSignChangedEvent;

/**
 * Keeps the sign column of cost assignments and customer rates in step when an employee changes
 * their sign (#966, #968).
 *
 * <p>Both reference the person by {@code employee_id} now, and the application resolves by that id
 * alone — a changed sign no longer affects what work costs or earns. The sign column stays next to
 * the id only because views, ETL definitions and reports still join on it; a stale sign there would
 * let the reports lose the rows of an anonymized person, which is the damage of #922 moved outside
 * the application.
 *
 * <p>Once those readers have moved to {@code employee_id}, the column is dropped and this listener
 * goes away with it.
 */
@Component
@RequiredArgsConstructor
public class EmployeeSignChangedListener {

    private final EmployeeCostService employeeCostService;
    private final OrderPricingService orderPricingService;

    @EventListener
    public void onEmployeeSignChanged(EmployeeSignChangedEvent event) {
        employeeCostService.followSignChange(event.getEmployeeId(), event.getNewSign());
        orderPricingService.followSignChange(event.getEmployeeId(), event.getNewSign());
    }

}
