package de.hbt.salat.jira.viewhelper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.jira.auth.JiraTicketAuthorization;

/** Whether the menu offers the ticket page (#1386): to managers and to whoever is responsible for an order. */
@Component
@RequiredArgsConstructor
public class JiraTicketMenuViewHelper {

  private final JiraTicketAuthorization jiraTicketAuthorization;

  public boolean isTicketPageAvailable() {
    return jiraTicketAuthorization.isTicketPageAvailable();
  }
}
