package de.hbt.salat.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
    FAILED,
    /**
     * Not begun at all, because the same replication was still running. A status of its own rather
     * than {@link #FAILED}: nothing ran that could have gone wrong. Without the row the hourly run
     * would leave a gap in the list that looks like an application that was switched off.
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

  /** The replication — an id rather than an association, the list looks the name up itself. */
  @Column(name = "replication_id", nullable = false)
  private Long replicationId;

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
