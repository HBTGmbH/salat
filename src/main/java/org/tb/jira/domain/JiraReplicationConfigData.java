package org.tb.jira.domain;

/**
 * What a manager may write on a replication config (#984).
 *
 * <p>The watermark {@code lastMaxUpdated} is deliberately absent: it is written by the replication
 * itself, and a date entered by hand would permanently skip every ticket updated before it. The one
 * sensible operation on it is resetting it, which is its own service method.
 *
 * @param password the new password, or {@code null}/blank to keep the stored one. The form never
 *     shows what is stored, so an empty field means "unchanged", not "clear it".
 */
public record JiraReplicationConfigData(
    String name,
    String customerorderSign,
    String baseUrl,
    JiraApiFlavor apiFlavor,
    String username,
    String password,
    String jql,
    String parentFieldNames,
    Integer pageSize,
    boolean enabled
) {

}
