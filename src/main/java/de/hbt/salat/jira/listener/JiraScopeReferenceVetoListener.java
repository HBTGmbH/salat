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
 * Refuses to delete an order or a suborder that a JIRA replication still applies to (#1322).
 *
 * <p>The replication refers to both by id, with a foreign key. Without this veto the deletion would
 * fail at that key as a failed statement; with it the person deleting reads what is in the way — and
 * deletes the replication first or moves it to another scope.
 */
@Component
@RequiredArgsConstructor
public class JiraScopeReferenceVetoListener {

  private final JiraScopeReferenceService jiraScopeReferenceService;

  @EventListener
  public void onCustomerorderDelete(CustomerorderDeleteEvent event) {
    var replications = jiraScopeReferenceService.countReplicationsOfCustomerorder(event.getId());
    if (replications > 0) {
      event.veto(List.of(error(ErrorCode.JI_ORDER_HAS_REPLICATIONS, replications)));
    }
  }

  @EventListener
  public void onSuborderDelete(SuborderDeleteEvent event) {
    var replications = jiraScopeReferenceService.countReplicationsOfSuborder(event.getId());
    if (replications > 0) {
      event.veto(List.of(error(ErrorCode.JI_SUBORDER_HAS_REPLICATIONS, replications)));
    }
  }

}
