package de.hbt.salat.jira.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * What a worklog says: a day, a number of minutes, and a comment naming how they are made up
 * (#1007, #1408). The minutes are the sum over everybody who booked on that ticket that day; the
 * comment names each of them by sign with their share. Nothing else — no name, no task
 * description.
 *
 * @param comment the text of the comment; each client shapes it the way its JIRA expects
 */
public record JiraWorklogEntry(LocalDate workDate, int minutes, String comment) {

  /**
   * The first line of every comment SALAT writes, followed by how the time is made up: one line per
   * person with the sign and the share, sorted by sign (#1408), the lines separated by {@code \n}.
   * <pre>
   * Von HBT protokollierte Stunden übertragen:
   * abc 4h
   * xyz 2h 30m
   * </pre>
   * A constant, not a message from the bundles: the reader is a foreign system with no locale of ours.
   *
   * <p>The sign is allowed where a name is not. It is how people are known in the projects SALAT
   * books on anyway, and it answers the one question a worklog of several people raises — who stands
   * behind which part of the number — without sending anything else along. Without it the question
   * went to the people responsible for the project, who had to look the split up in SALAT. The name,
   * the task description and everything else a booking says about who and what stay in SALAT.
   */
  static final String WORKLOG_COMMENT = "Von HBT protokollierte Stunden übertragen:";

  /** Between the lines of a comment; the Cloud client turns it into a hard break of its document. */
  static final String LINE_BREAK = "\n";

  /**
   * The worklog of one day out of the shares per sign: the time is their sum, the comment names
   * them. A share of zero minutes is left out of the comment — after the split of a booking with
   * several references a person can be left with nothing on a ticket, and {@code abc 0m} would only
   * raise a question.
   */
  static JiraWorklogEntry of(LocalDate workDate, Map<String, Long> minutesBySign) {
    var minutes = minutesBySign.values().stream().mapToLong(Long::longValue).sum();
    return new JiraWorklogEntry(workDate, Math.toIntExact(minutes), commentOf(minutesBySign));
  }

  private static String commentOf(Map<String, Long> minutesBySign) {
    return new TreeMap<>(minutesBySign).entrySet().stream()
        .filter(share -> share.getValue() > 0)
        .map(share -> share.getKey() + " " + duration(share.getValue()))
        .collect(Collectors.joining(LINE_BREAK, WORKLOG_COMMENT + LINE_BREAK, ""));
  }

  /** {@code 4h}, {@code 2h 30m} or {@code 45m} — the notation JIRA itself uses for logged time. */
  static String duration(long minutes) {
    var hours = minutes / 60;
    var rest = minutes % 60;
    if (hours == 0) {
      return rest + "m";
    }
    return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
  }
}
