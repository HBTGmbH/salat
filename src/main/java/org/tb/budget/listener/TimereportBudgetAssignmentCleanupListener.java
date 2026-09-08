package org.tb.budget.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.tb.budget.service.TimereportBudgetAssignmentService;
import org.tb.dailyreport.event.TimereportsDeletedEvent;
import org.tb.dailyreport.event.TimereportsDeletedEvent.TimereportDeleteId;

/**
 * Keeps the budget assignments free of rows pointing at deleted bookings.
 *
 * <p>The column does carry a foreign key with {@code ON DELETE CASCADE}, but that never fires for a
 * normal delete: {@code Timereport} is soft-deleted, so the row survives and the constraint stays
 * satisfied. The cleanup therefore has to happen here, on the event {@code dailyreport} already
 * publishes; the cascade only covers a future hard purge of soft-deleted rows.
 */
@Component
@RequiredArgsConstructor
public class TimereportBudgetAssignmentCleanupListener {

    private final TimereportBudgetAssignmentService assignmentService;

    @EventListener
    public void onTimereportsDeleted(TimereportsDeletedEvent event) {
        assignmentService.removeAssignmentsOfDeletedTimereports(
            event.getIds().stream().map(TimereportDeleteId::getTimereportId).toList());
    }

}
