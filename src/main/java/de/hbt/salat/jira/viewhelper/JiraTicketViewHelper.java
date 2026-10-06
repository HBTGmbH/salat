package de.hbt.salat.jira.viewhelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import de.hbt.salat.jira.domain.JiraTicketDetail;

/**
 * How the details of a ticket name a moment (#1386, → ADR-0017): date and time, and who it was, where
 * that is known — {@code 06.10.2026 17:40 (kr)}. A dash where there is no moment at all.
 *
 * <p>Used from templates as {@code ${@jiraTicketViewHelper.stamp(when, who)}} and
 * {@code ${@jiraTicketViewHelper.importStamp(detail)}}.
 */
@Component
public class JiraTicketViewHelper {

  static final String NONE = "–";
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  public String stamp(LocalDateTime when, String who) {
    if (when == null) return NONE;
    return who == null || who.isBlank() ? DATE_TIME.format(when) : DATE_TIME.format(when) + " (" + who + ")";
  }

  /** The import that wrote the ticket last, with its file between moment and person. */
  public String importStamp(JiraTicketDetail detail) {
    if (detail.importedAt() == null) return NONE;
    var file = detail.importedFrom() == null ? "" : " · " + detail.importedFrom();
    var who = detail.importedBy() == null || detail.importedBy().isBlank() ? "" : " (" + detail.importedBy() + ")";
    return DATE_TIME.format(detail.importedAt()) + file + who;
  }
}
