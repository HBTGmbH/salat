package de.hbt.salat.budget.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.budget.service.OrderReferenceService;
import de.hbt.salat.order.event.CustomerorderUpdateEvent;
import de.hbt.salat.order.event.SuborderUpdateEvent;

/**
 * Keeps the sign columns of budget plans, flat rates and cost assignments in step with the order
 * tree (#1205) — the counterpart of {@link EmployeeSignChangedListener} for orders. What is
 * rewritten, and why the columns exist at all, is said at {@link OrderReferenceService#followOrderTree}.
 */
@Component
@RequiredArgsConstructor
public class OrderSignMirrorListener {

    private final OrderReferenceService orderReferenceService;

    @EventListener
    public void onCustomerorderUpdate(CustomerorderUpdateEvent event) {
        orderReferenceService.followOrderTree(event.getDomainObject(), event.getPreviousSign());
    }

    @EventListener
    public void onSuborderUpdate(SuborderUpdateEvent event) {
        orderReferenceService.followOrderTree(event.getDomainObject().getCustomerorder(), null);
    }

}
