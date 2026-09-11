package org.tb.jira.domain;

import java.time.LocalDateTime;

/**
 * A replication config as the user interface gets to see it (#984).
 *
 * <p>The password is missing on purpose, and that is the whole reason this record exists: the list
 * and the form work on it instead of on the entity, so the stored token has no way of reaching a
 * template, a log line or an error message by accident.
 *
 * @param lastMaxUpdated the watermark the replication has reached — the one field that tells whether
 *     a replication is still running at all
 */
public record JiraReplicationConfigInfo(
    Long id,
    String name,
    String customerorderSign,
    String baseUrl,
    JiraApiFlavor apiFlavor,
    String username,
    String jql,
    String parentFieldNames,
    Integer pageSize,
    boolean enabled,
    LocalDateTime lastMaxUpdated
) {

  public static JiraReplicationConfigInfo from(JiraReplicationConfig config) {
    return new JiraReplicationConfigInfo(
        config.getId(),
        config.getName(),
        config.getCustomerorderSign(),
        config.getBaseUrl(),
        config.getApiFlavor(),
        config.getUsername(),
        config.getJql(),
        config.getParentFieldNames(),
        config.getPageSize(),
        Boolean.TRUE.equals(config.getEnabled()),
        config.getLastMaxUpdated()
    );
  }
}
