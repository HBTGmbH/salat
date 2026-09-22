package org.tb.jira.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
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
 *
 * <p>Since #1025 the request names the suborder being booked on rather than an order sign, and the
 * service derives the scopes of the whole branch from it. Nothing the browser sends is used as a
 * scope any more.
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
  @Authorized
  @ResponseBody
  public List<JiraTicketSuggestion> suggestions(@RequestParam(required = false) Long suborderId,
      @RequestParam(required = false) String q) {
    return jiraTicketSuggestionService.search(suborderId, q);
  }

}
