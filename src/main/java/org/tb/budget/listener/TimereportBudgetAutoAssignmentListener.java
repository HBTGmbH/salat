package org.tb.budget.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.tb.budget.service.TimereportBudgetAssignmentService;
import org.tb.dailyreport.event.TimereportsCreatedOrUpdatedEvent;

/**
 * Assigns new and changed bookings to their budget plan, as far as it is unambiguous (#909).
 *
 * <p>Since #908 only the stored assignment counts, so without this nobody would notice that a
 * booking fell out of every evaluation until the numbers were wrong. The assignment nevertheless
 * stays a matter of the budget module: {@code dailyreport} only publishes that bookings were
 * written and knows nothing about budgets — the reverse import would close a module cycle.
 *
 * <p>The listener is synchronous and runs in the transaction of the booking, so a booking and its
 * assignment are written together or not at all.
 */
@Component
@RequiredArgsConstructor
public class TimereportBudgetAutoAssignmentListener {

    private final TimereportBudgetAssignmentService assignmentService;

    @EventListener
    public void onTimereportsCreatedOrUpdated(TimereportsCreatedOrUpdatedEvent event) {
        assignmentService.resolveAssignments(event.getIds());
    }

}
