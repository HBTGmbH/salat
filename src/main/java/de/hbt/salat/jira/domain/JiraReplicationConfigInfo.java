package de.hbt.salat.jira.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A replication config as the user interface gets to see it (#984).
 *
 * <p>The password is missing on purpose, and that is the whole reason this record exists: the list
 * and the form work on it instead of on the entity, so the stored token has no way of reaching a
 * template, a log line or an error message by accident.
 *
 * @param customerorderId the customer order the replication applies to (#1322)
 * @param suborderId the suborder it is narrowed to, or {@code null} for the whole order
 * @param scopeSign the scope as the list shows it — the order sign or the complete order sign of
 *     the suborder, {@code AUFTRAG/01/02}, read from the current order tree rather than from the
 *     mirror column
 * @param worklogSyncEnabled whether the run writes the booked hours back as worklogs (#1007)
 * @param worklogSyncFrom first day the worklog sync covers
 * @param worklogSyncInvoiceableOnly whether only bookings on invoiceable suborders are written (#1218)
 * @param lastMaxUpdated the watermark the replication has reached — the one field that tells whether
 *     a replication is still running at all
 */
public record JiraReplicationConfigInfo(
    Long id,
    String name,
    Long customerorderId,
    Long suborderId,
    String scopeSign,
    String baseUrl,
    JiraApiFlavor apiFlavor,
    String username,
    String jql,
    String parentFieldNames,
    String additionalFieldNames,
    String inheritedFieldNames,
    Integer pageSize,
    boolean enabled,
    boolean worklogSyncEnabled,
    LocalDate worklogSyncFrom,
    boolean worklogSyncInvoiceableOnly,
    LocalDateTime lastMaxUpdated
) {

  /**
   * @param scopeSign the sign of order or suborder as the order tree carries it now — see
   *     {@code JiraScopes#signOf}
   */
  public static JiraReplicationConfigInfo from(JiraReplicationConfig config, String scopeSign) {
    return new JiraReplicationConfigInfo(
        config.getId(),
        config.getName(),
        config.getCustomerorderId(),
        config.getSuborderId(),
        scopeSign,
        config.getBaseUrl(),
        config.getApiFlavor(),
        config.getUsername(),
        config.getJql(),
        config.getParentFieldNames(),
        config.getAdditionalFieldNames(),
        config.getInheritedFieldNames(),
        config.getPageSize(),
        Boolean.TRUE.equals(config.getEnabled()),
        Boolean.TRUE.equals(config.getWorklogSyncEnabled()),
        config.getWorklogSyncFrom(),
        Boolean.TRUE.equals(config.getWorklogSyncInvoiceableOnly()),
        config.getLastMaxUpdated()
    );
  }
}
