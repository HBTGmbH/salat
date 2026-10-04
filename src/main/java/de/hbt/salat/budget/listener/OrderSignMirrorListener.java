package de.hbt.salat.budget.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.budget.service.OrderReferenceService;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.order.event.CustomerorderUpdateEvent;
import de.hbt.salat.order.event.SuborderUpdateEvent;

/**
 * Keeps the sign columns of budget plans, flat rates, cost assignments and customer rates in step with the order
 * tree (#1205) — the counterpart of {@link EmployeeSignChangedListener} for orders. What is
 * rewritten, and why the columns exist at all, is said at {@link OrderReferenceService#followOrderTree}.
 */
@Component
@RequiredArgsConstructor
public class OrderSignMirrorListener {

    private final OrderReferenceService orderReferenceService;

    @EventListener
    public void onCustomerorderUpdate(CustomerorderUpdateEvent event) {
        orderReferenceService.followOrderTree(event.getDomainObject());
    }

    @EventListener
    public void onSuborderUpdate(SuborderUpdateEvent event) {
        orderReferenceService.followOrderTree(event.getDomainObject().getCustomerorder());
    }

    /** The suborder patterns of the customer rates, which name the order tree by sign (#1206). */
    @EventListener
    public void onSignsRenamed(SignsRenamedEvent event) {
        orderReferenceService.followRename(event);
    }

}
