package org.tb.jira.service;

import org.tb.jira.domain.JiraApiFlavor;

/**
 * Writes worklogs into one flavour of the JIRA REST API (#1007). The only writing access SALAT has
 * to JIRA at all — everything else the jira module does is a read.
 */
public interface JiraWorklogClient {

  JiraApiFlavor flavor();

  /** @return the id JIRA gave the new worklog; it is what identifies it as ours from then on */
  String create(JiraWorklogTarget target, JiraWorklogEntry entry);

  /**
   * Overwrites a worklog SALAT wrote earlier.
   *
   * @throws JiraWorklogNotFoundException if it is gone from JIRA
   */
  void update(JiraWorklogTarget target, String worklogId, JiraWorklogEntry entry);

  /**
   * Removes a worklog SALAT wrote earlier, because all bookings behind it are gone.
   *
   * @throws JiraWorklogNotFoundException if it is already gone from JIRA
   */
  void delete(JiraWorklogTarget target, String worklogId);
}
