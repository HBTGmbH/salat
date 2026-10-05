package de.hbt.salat.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * One worklog SALAT has written to JIRA (#1007): the sum of a day booked on a ticket, and the id
 * JIRA answered with.
 *
 * <p>This is the memory the whole sync rests on. Without it, two things are impossible: telling a
 * worklog SALAT wrote from one somebody entered in JIRA by hand — the second kind is never touched
 * — and noticing that a day/ticket pair has no bookings left at all, which is what has to delete
 * its worklog.
 *
 * <p>Keyed by the scope of the replication — order and suborder by id (#1323) — rather than by the
 * id of the replication, like {@link JiraTicket}: a row outlives the configuration that wrote it,
 * and what was written to JIRA stays written whether or not the config is still there. A new
 * replication on the same scope recognises the row as its own.
 *
 * <p>One row per scope, issue key and day. That key lives in the changelog only (changeset 150,
 * #1372), like the keys of {@link JiraTicket}: the suborder enters it as
 * {@code COALESCE(suborder_id, 0)}, because {@code NULL} — the whole order — is never equal to
 * {@code NULL} in a unique key.
 */
@Entity
@Table(name = "jira_worklog_sync")
@Getter
@Setter
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class JiraWorklogSync extends AuditedEntity {

  /**
   * The customer order of the scope that wrote the worklog (#1323) — master data of the order
   * module, read only (#1368, ADR-0036).
   */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customerorder_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_jira_worklog_sync_customerorder"))
  private Customerorder customerorder;

  /** The suborder of that scope, {@code null} for the whole order (#1323). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "suborder_id", foreignKey = @ForeignKey(name = "fk_jira_worklog_sync_suborder"))
  private Suborder suborder;

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

  /** The id of {@link #customerorder}, read off the reference without loading the order. */
  public Long getCustomerorderId() {
    return customerorder != null ? customerorder.getId() : null;
  }

  /** The id of {@link #suborder}, {@code null} for the whole order; the suborder is not loaded. */
  public Long getSuborderId() {
    return suborder != null ? suborder.getId() : null;
  }
}
