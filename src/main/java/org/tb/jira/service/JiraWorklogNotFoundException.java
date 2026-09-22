package org.tb.jira.service;

/**
 * The worklog SALAT remembers having written is no longer in JIRA — somebody deleted it there by
 * hand. Its own exception rather than an HTTP status, so the caller can react to the situation
 * without knowing how the clients talk to JIRA: the remembered row is dropped, and where the sum
 * still calls for a worklog a new one is created.
 */
public class JiraWorklogNotFoundException extends RuntimeException {

  public JiraWorklogNotFoundException(String issueKey, String worklogId, Throwable cause) {
    super("Worklog " + worklogId + " of issue " + issueKey + " does not exist in JIRA", cause);
  }
}
