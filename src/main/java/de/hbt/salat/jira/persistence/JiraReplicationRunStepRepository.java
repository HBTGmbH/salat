package de.hbt.salat.jira.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import de.hbt.salat.jira.domain.JiraReplicationRunStep;

/**
 * The steps of the replication runs (#1325). Nothing here deletes: the foreign key takes the steps
 * along with their run.
 */
public interface JiraReplicationRunStepRepository extends JpaRepository<JiraReplicationRunStep, Long> {

  /** The steps of a run in the order they ran; the id decides between two of the same second. */
  List<JiraReplicationRunStep> findByRunIdOrderByStartedAtAscIdAsc(long runId);

}
