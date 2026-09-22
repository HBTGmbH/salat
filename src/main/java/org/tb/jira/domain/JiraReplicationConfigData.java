package org.tb.jira.domain;

import java.time.LocalDate;

/**
 * What a manager may write on a replication config (#984).
 *
 * <p>The watermark {@code lastMaxUpdated} is deliberately absent: it is written by the replication
 * itself, and a date entered by hand would permanently skip every ticket updated before it. The one
 * sensible operation on it is resetting it, which is its own service method.
 *
 * @param password the new password, or {@code null}/blank to keep the stored one. The form never
 *     shows what is stored, so an empty field means "unchanged", not "clear it".
 * @param scopeSign where the replication applies (#1025) — a customer order sign for the whole
 *     order, or the fully qualified sign of one suborder, {@code AUFTRAG/01/02}
 * @param additionalFieldNames comma separated JIRA response keys to replicate in addition (#881)
 * @param inheritedFieldNames comma separated response keys resolved along the parent chain (#881)
 * @param worklogSyncEnabled whether the run writes the booked hours back as worklogs (#1007)
 * @param worklogSyncFrom first day the worklog sync covers; filled with today when the switch is
 *     turned on without one, so switching it on never writes the whole history at once
 */
public record JiraReplicationConfigData(
    String name,
    String scopeSign,
    String baseUrl,
    JiraApiFlavor apiFlavor,
    String username,
    String password,
    String jql,
    String parentFieldNames,
    String additionalFieldNames,
    String inheritedFieldNames,
    Integer pageSize,
    boolean enabled,
    boolean worklogSyncEnabled,
    LocalDate worklogSyncFrom
) {

}
