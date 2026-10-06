package de.hbt.salat.jira.viewhelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import de.hbt.salat.jira.domain.JiraTicketDetail;

/**
 * How the details of a ticket name a moment (#1386, → ADR-0017): date and time, a dash where there is
 * none. Without the person: in a dialog about a ticket, the login sign distracts more than it tells.
 *
 * <p>Used from templates as {@code ${@jiraTicketViewHelper.stamp(when)}} and
 * {@code ${@jiraTicketViewHelper.importStamp(detail)}}.
 */
@Component
public class JiraTicketViewHelper {

  static final String NONE = "–";
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  public String stamp(LocalDateTime when) {
    return when == null ? NONE : DATE_TIME.format(when);
  }

  /** The import that wrote the ticket last: when, and from which file. */
  public String importStamp(JiraTicketDetail detail) {
    if (detail.importedAt() == null) return NONE;
    return detail.importedFrom() == null ? stamp(detail.importedAt())
        : stamp(detail.importedAt()) + " · " + detail.importedFrom();
  }
}
