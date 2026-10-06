package de.hbt.salat.jira.controller;

import static java.util.Map.entry;

import java.util.Map;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

@Component
public class JiraUiStateKeyContributor implements UiStateKeyContributor {

  public static final UiStateKey JIRA_RUN_FAILED_ONLY = new UiStateKey("jira.Run.FailedOnly");
  public static final UiStateKey JIRA_TICKET_CUSTOMERORDER = new UiStateKey("jira.Ticket.CustomerorderId");
  public static final UiStateKey JIRA_TICKET_SUBORDER = new UiStateKey("jira.Ticket.SuborderId");
  public static final UiStateKey JIRA_TICKET_KEYS = new UiStateKey("jira.Ticket.Keys");
  public static final UiStateKey JIRA_TICKET_CHILDREN = new UiStateKey("jira.Ticket.Children");
  public static final UiStateKey JIRA_TICKET_TITLE = new UiStateKey("jira.Ticket.Title");
  public static final UiStateKey JIRA_TICKET_TYPES = new UiStateKey("jira.Ticket.Types");
  public static final UiStateKey JIRA_TICKET_LIMIT = new UiStateKey("jira.Ticket.Limit");

  @Override
  public Map<String, UiStateKey> getParamToKeyMappings() {
    return Map.ofEntries(
        entry("fJiraRunFailedOnly", JIRA_RUN_FAILED_ONLY),
        entry("fJiraTicketCustomerorderId", JIRA_TICKET_CUSTOMERORDER),
        entry("fJiraTicketSuborderId", JIRA_TICKET_SUBORDER),
        entry("fJiraTicketKeys", JIRA_TICKET_KEYS),
        entry("fJiraTicketChildren", JIRA_TICKET_CHILDREN),
        entry("fJiraTicketTitle", JIRA_TICKET_TITLE),
        entry("fJiraTicketTypes", JIRA_TICKET_TYPES),
        entry("fJiraTicketLimit", JIRA_TICKET_LIMIT));
  }
}
