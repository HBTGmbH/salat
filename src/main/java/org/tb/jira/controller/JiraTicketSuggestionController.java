package org.tb.jira.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.tb.auth.domain.Authorized;
import org.tb.jira.domain.JiraTicketSuggestion;
import org.tb.jira.service.JiraTicketSuggestionService;

/**
 * Serves the ticket suggestions of the booking form (#982).
 *
 * <p>The booking form lives in {@code dailyreport}, which must not import {@code jira}. Rather than
 * routing the lookup through a command event — which removes the import but keeps the coupling —
 * the module answers the browser itself.
 *
 * <p>Deliberately not under {@code /api}: that path is a stateless filter chain for machine clients
 * and does not accept the session of a logged-in browser.
 */
@Controller
@RequestMapping("/jira/tickets")
@RequiredArgsConstructor
// no requireUnrestricted: externals and interns book their time like everyone else and need the
// suggestions while doing so
@Authorized
public class JiraTicketSuggestionController {

  private final JiraTicketSuggestionService jiraTicketSuggestionService;

  @GetMapping("/suggestions")
  @PreAuthorize("isAuthenticated()")
  @ResponseBody
  public List<JiraTicketSuggestion> suggestions(@RequestParam(required = false) String orderSign,
      @RequestParam(required = false) String q) {
    return jiraTicketSuggestionService.search(orderSign, q);
  }

}
