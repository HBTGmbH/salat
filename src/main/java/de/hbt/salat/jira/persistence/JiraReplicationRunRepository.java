package de.hbt.salat.jira.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.jira.domain.JiraReplicationRun;
import de.hbt.salat.jira.domain.JiraReplicationRun.Status;

public interface JiraReplicationRunRepository extends JpaRepository<JiraReplicationRun, Long> {

  List<JiraReplicationRun> findByOrderByStartedAtDesc(Pageable pageable);

  List<JiraReplicationRun> findByStatusNotOrderByStartedAtDesc(Status status, Pageable pageable);

  List<JiraReplicationRun> findByStatus(Status status);

  /**
   * The running run of this replication, if there is one. {@code findFirst} because a second
   * {@code RUNNING} row may be left over from a crash, and the answer has to name the start of the
   * one that blocks.
   */
  Optional<JiraReplicationRun> findFirstByReplicationIdAndStatusOrderByStartedAtDesc(long replicationId,
                                                                                    Status status);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      DELETE FROM JiraReplicationRun r
      WHERE r.replicationId = :replicationId
      """)
  int deleteByReplicationId(@Param("replicationId") long replicationId);

  /**
   * @return the number of rows deleted
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      DELETE FROM JiraReplicationRun r
      WHERE r.startedAt < :cutoff
      """)
  int deleteStartedBefore(@Param("cutoff") LocalDateTime cutoff);

}
