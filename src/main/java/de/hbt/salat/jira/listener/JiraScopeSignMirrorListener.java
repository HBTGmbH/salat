package de.hbt.salat.jira.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.jira.service.JiraScopeReferenceService;
import de.hbt.salat.order.event.CustomerorderUpdateEvent;
import de.hbt.salat.order.event.SuborderUpdateEvent;

/**
 * Keeps the scope sign of the JIRA replications in step with the order tree (#1322), as
 * {@code OrderSignMirrorListener} does for the budget module. What is rewritten is said at
 * {@link JiraScopeReferenceService}.
 */
@Component
@RequiredArgsConstructor
public class JiraScopeSignMirrorListener {

  private final JiraScopeReferenceService jiraScopeReferenceService;

  @EventListener
  public void onCustomerorderUpdate(CustomerorderUpdateEvent event) {
    jiraScopeReferenceService.followCustomerorder(event.getDomainObject());
  }

  @EventListener
  public void onSuborderUpdate(SuborderUpdateEvent event) {
    jiraScopeReferenceService.followSuborder(event.getDomainObject());
  }

}
