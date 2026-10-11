package de.hbt.salat.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import de.hbt.salat.jira.domain.JiraReplicationRun.Status;

/**
 * One step of a replication run (#1325): reading the tickets, and writing the worklogs where the
 * sync is switched on. The run keeps one message for all of it; the step keeps what it did in
 * numbers.
 *
 * <p>Written when the step ends, not when it begins: a step has no lock to hold, and a run that
 * breaks off before a step leaves no entry for it. A step that breaks off itself is caught and
 * written as {@link Status#FAILED}.
 *
 * <p>The counters are columns of their own rather than a document: each is known to the code, and a
 * report can read them like any other column. The ones that belong to the other step stay
 * {@code null}.
 *
 * <p>The foreign key deletes the steps with their run ({@code ON DELETE CASCADE}), so the cleanup of
 * the run history and the deletion of a replication need not know about them.
 */
@Entity
@Table(name = "jira_replication_run_step")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JiraReplicationRunStep {

  public enum Step {
    /** Fetching and storing the tickets, removing the ones no longer matched, deriving the parent chains. */
    READ_TICKETS,
    /** Writing the booked hours back to JIRA (#1007), only while the sync is switched on. */
    WRITE_WORKLOGS
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_jira_replication_run_step_run"))
  @OnDelete(action = OnDeleteAction.CASCADE)
  private JiraReplicationRun run;

  @Enumerated(EnumType.STRING)
  @Column(name = "step", length = 20, nullable = false)
  private Step step;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "finished_at", nullable = false)
  private LocalDateTime finishedAt;

  /**
   * The status values of the run: {@code SUCCEEDED}, {@code FAILED}, {@code INCOMPLETE}, and
   * {@code SKIPPED} for a sync that is switched on but has nothing to work on.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private Status status;

  @Column(name = "tickets_fetched")
  private Integer ticketsFetched;

  @Column(name = "tickets_written")
  private Integer ticketsWritten;

  /** Not processed; the watermark does not move past them (#841). */
  @Column(name = "tickets_failed")
  private Integer ticketsFailed;

  /** Maintained by another replication of the same scope (#1386). */
  @Column(name = "tickets_skipped")
  private Integer ticketsSkipped;

  /** No longer matched by the JQL (#1167) — only a run without watermark can tell. */
  @Column(name = "tickets_removed")
  private Integer ticketsRemoved;

  /** Tickets whose parent, top-level key or inherited fields changed and were written again. */
  @Column(name = "parent_chains_resolved")
  private Integer parentChainsResolved;

  /** First day of the period the worklogs were compared over. */
  @Column(name = "worklogs_from")
  private LocalDate worklogsFrom;

  /** Last day of that period — the day of the run. */
  @Column(name = "worklogs_until")
  private LocalDate worklogsUntil;

  @Column(name = "worklogs_created")
  private Integer worklogsCreated;

  @Column(name = "worklogs_updated")
  private Integer worklogsUpdated;

  @Column(name = "worklogs_deleted")
  private Integer worklogsDeleted;

  @Column(name = "worklogs_unchanged")
  private Integer worklogsUnchanged;

  /** Left in JIRA because their ticket is no longer replicated (#1167). */
  @Column(name = "worklogs_kept")
  private Integer worklogsKept;

  @Column(name = "worklogs_failed")
  private Integer worklogsFailed;

  @Column(name = "message", length = JiraReplicationRun.MESSAGE_MAX_LENGTH)
  private String message;

}
