package de.hbt.salat.budget.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.budget.service.OrderReferenceService;
import de.hbt.salat.common.event.SignsRenamedEvent;

/**
 * Takes the suborder patterns of the customer rates along when an order or a suborder gets a new
 * complete sign (#1206). The patterns are the one place where the budget module names the order tree
 * by sign; everything else refers to it by id and needs nothing on a rename. What is rewritten is said
 * at {@link OrderReferenceService#followRename}.
 */
@Component
@RequiredArgsConstructor
public class OrderRenameListener {

    private final OrderReferenceService orderReferenceService;

    @EventListener
    public void onSignsRenamed(SignsRenamedEvent event) {
        orderReferenceService.followRename(event);
    }

}
