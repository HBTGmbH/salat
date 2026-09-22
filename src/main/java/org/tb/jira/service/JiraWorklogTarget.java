package org.tb.jira.service;

/**
 * Where a worklog operation goes: the JIRA instance, the account it is performed as, and the issue.
 *
 * <p>The account is the one stored on the replication config, and JIRA makes it the author of
 * everything written with it. That is deliberate (#1007): the person who booked the time in SALAT
 * never appears in JIRA.
 */
public record JiraWorklogTarget(
    String baseUrl,
    String username,
    String password,
    String issueKey
) {

}
