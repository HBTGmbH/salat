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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One run of one replication (#1282) — what {@code etl_run_history} keeps for the ETL, kept here for
 * the replications.
 *
 * <p>Written when the run starts, not when it ends: a run that never comes to an end stays as
 * {@link Status#RUNNING} without {@code finishedAt}, and only that tells it apart from one that
 * never began. The {@code RUNNING} row is also the lock — while it stands, the same replication
 * does not start a second time (see {@code JiraReplicationRunService#startRun}).
 *
 * <p>A manual run reports back here and not to the page that started it: it goes on in the
 * background, and the page has returned long before it ends.
 */
@Entity
@Table(name = "jira_replication_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JiraReplicationRun {

  /** Limit of the column {@code message}. */
  public static final int MESSAGE_MAX_LENGTH = 4000;

  public enum Status {
    RUNNING,
    SUCCEEDED,
    /** Broken off by an exception — the run, one of its steps, or the worklog sync. */
    FAILED,
    /**
     * Gone through, but single tickets could not be stored or single worklogs could not be written
     * (#1325). Not {@link #SUCCEEDED}, because tickets or hours are missing; not {@link #FAILED}
     * either, because everything else went through and the next run tries the missing ones again —
     * for the tickets the watermark stops below them (#841). Stored as text in a {@code varchar(20)}
     * like every status, so the value needs no change to the schema.
     */
    INCOMPLETE,
    /**
     * Not begun at all, because the same replication was still running. A status of its own rather
     * than {@link #FAILED}: nothing ran that could have gone wrong. Without the row the hourly run
     * would leave a gap in the list that looks like an application that was switched off.
     *
     * <p>A step (#1325) carries it when the worklog sync is switched on but has nothing to work on —
     * no start date, a start date to come, no suborder in the scope.
     */
    SKIPPED
  }

  public enum Trigger {
    /** The hourly run. */
    SCHEDULED,
    /** Started by hand — from the list or through the REST interface. */
    MANUAL
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The replication the run belongs to, in the same module (#1349). */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "replication_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_jira_replication_run_config"))
  private JiraReplicationConfig replication;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  /** {@code null} while the run is going on — and for good, if it never came to an end. */
  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "triggered_by", length = 20, nullable = false)
  private Trigger triggeredBy;

  @Column(name = "message", length = MESSAGE_MAX_LENGTH)
  private String message;

}
