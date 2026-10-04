package de.hbt.salat.jira.listener;

import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.jira.service.JiraScopeReferenceService;
import de.hbt.salat.order.event.CustomerorderDeleteEvent;
import de.hbt.salat.order.event.SuborderDeleteEvent;

/**
 * What deleting an order or a suborder means for the JIRA module (#1322, #1323).
 *
 * <p>A replication that still applies to it refuses the deletion. The replication refers to both by
 * id, with a foreign key; without this veto the deletion would fail at that key as a failed
 * statement. With it the person deleting reads what is in the way — and deletes the replication
 * first or moves it to another scope.
 *
 * <p>Tickets and worklog rows, by contrast, go with the scope: they outlive a replication so that a
 * new one on the same scope finds them again, and once the scope itself is gone none ever can.
 */
@Component
@RequiredArgsConstructor
public class JiraScopeDeleteListener {

  private final JiraScopeReferenceService jiraScopeReferenceService;

  @EventListener
  public void onCustomerorderDelete(CustomerorderDeleteEvent event) {
    var replications = jiraScopeReferenceService.countReplicationsOfCustomerorder(event.getId());
    if (replications > 0) {
      event.veto(List.of(error(ErrorCode.JI_ORDER_HAS_REPLICATIONS, replications)));
    }
    jiraScopeReferenceService.deleteScopeDataOfCustomerorder(event.getId());
  }

  @EventListener
  public void onSuborderDelete(SuborderDeleteEvent event) {
    var replications = jiraScopeReferenceService.countReplicationsOfSuborder(event.getId());
    if (replications > 0) {
      event.veto(List.of(error(ErrorCode.JI_SUBORDER_HAS_REPLICATIONS, replications)));
    }
    jiraScopeReferenceService.deleteScopeDataOfSuborder(event.getId());
  }

}
