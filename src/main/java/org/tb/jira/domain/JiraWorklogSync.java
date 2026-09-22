package org.tb.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

/**
 * One worklog SALAT has written to JIRA (#1007): the sum of a day booked on a ticket, and the id
 * JIRA answered with.
 *
 * <p>This is the memory the whole sync rests on. Without it, two things are impossible: telling a
 * worklog SALAT wrote from one somebody entered in JIRA by hand — the second kind is never touched
 * — and noticing that a day/ticket pair has no bookings left at all, which is what has to delete
 * its worklog.
 *
 * <p>Keyed by the scope of the replication rather than by its id, like {@link JiraTicket}: a row
 * outlives the configuration that wrote it, and what was written to JIRA stays written whether or
 * not the config is still there.
 */
@Entity
@Table(name = "jira_worklog_sync")
@Getter
@Setter
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class JiraWorklogSync extends AuditedEntity {

  @Column(name = "scope_sign", nullable = false)
  private String scopeSign;

  @Column(name = "issue_key", nullable = false, length = 64)
  private String issueKey;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  /** The id JIRA gave the worklog — a string, because JIRA hands ids out as strings. */
  @Column(name = "worklog_id", nullable = false, length = 64)
  private String worklogId;

  /** What was last written to JIRA, so an unchanged sum can be recognised without asking JIRA. */
  @Column(name = "minutes", nullable = false)
  private int minutes;

  @Column(name = "last_synced")
  private LocalDateTime lastSynced;
}
