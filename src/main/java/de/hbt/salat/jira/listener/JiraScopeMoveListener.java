package de.hbt.salat.jira.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.jira.service.JiraScopeReferenceService;
import de.hbt.salat.order.event.SuborderUpdateEvent;

/**
 * Takes the JIRA replications, tickets and worklogs of a suborder along when it is moved to another
 * customer order (#1322, #1323). What is rewritten is said at {@link JiraScopeReferenceService}.
 *
 * <p>A renamed order or suborder needs nothing: replications, tickets and worklogs refer to both as
 * references (#1368), and nothing in the module keeps a sign of its own any more (#1372).
 */
@Component
@RequiredArgsConstructor
public class JiraScopeMoveListener {

  private final JiraScopeReferenceService jiraScopeReferenceService;

  @EventListener
  public void onSuborderUpdate(SuborderUpdateEvent event) {
    jiraScopeReferenceService.followSuborder(event.getDomainObject());
  }

}
