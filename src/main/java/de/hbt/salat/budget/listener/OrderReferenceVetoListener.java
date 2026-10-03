package de.hbt.salat.budget.listener;

import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.budget.service.OrderReferenceService;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.order.event.CustomerorderDeleteEvent;
import de.hbt.salat.order.event.SuborderDeleteEvent;

/**
 * Refuses to delete an order or a suborder that budget data still refers to (#1205).
 *
 * <p>Plans, flat rates, cost assignments and customer rates (#1212) refer to their order by id, with
 * a foreign key. Without this veto the deletion would fail at that key as a failed statement; with it
 * the person deleting reads what is in the way. Before #1205 the deletion went through and left the budget data pointing
 * at a sign nobody carried any more — the plan vanished from the controlling without a word.
 */
@Component
@RequiredArgsConstructor
public class OrderReferenceVetoListener {

    private final OrderReferenceService orderReferenceService;

    @EventListener
    public void onCustomerorderDelete(CustomerorderDeleteEvent event) {
        var references = orderReferenceService.referencesToCustomerorder(event.getId());
        if (references.any()) {
            event.veto(List.of(error(ErrorCode.BU_ORDER_HAS_BUDGET_REFERENCES,
                references.plans(), references.flatRates(), references.rates())));
        }
    }

    @EventListener
    public void onSuborderDelete(SuborderDeleteEvent event) {
        var references = orderReferenceService.referencesToSuborder(event.getId());
        if (references.any()) {
            event.veto(List.of(error(ErrorCode.BU_SUBORDER_HAS_BUDGET_REFERENCES,
                references.plans(), references.flatRates(), references.costAssignments())));
        }
    }

}
