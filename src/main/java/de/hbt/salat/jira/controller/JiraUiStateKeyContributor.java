package de.hbt.salat.jira.controller;

import static java.util.Map.of;

import java.util.Map;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

@Component
public class JiraUiStateKeyContributor implements UiStateKeyContributor {

  public static final UiStateKey JIRA_RUN_FAILED_ONLY = new UiStateKey("jira.Run.FailedOnly");
  public static final UiStateKey JIRA_TICKET_CUSTOMERORDER = new UiStateKey("jira.Ticket.CustomerorderId");
  public static final UiStateKey JIRA_TICKET_SUBORDER = new UiStateKey("jira.Ticket.SuborderId");

  @Override
  public Map<String, UiStateKey> getParamToKeyMappings() {
    return of("fJiraRunFailedOnly", JIRA_RUN_FAILED_ONLY,
        "fJiraTicketCustomerorderId", JIRA_TICKET_CUSTOMERORDER,
        "fJiraTicketSuborderId", JIRA_TICKET_SUBORDER);
  }
}
