package org.tb.jira.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tb.jira.domain.JiraReplicationConfig;

@Repository
public interface JiraReplicationConfigRepository extends JpaRepository<JiraReplicationConfig, Long> {

  List<JiraReplicationConfig> findByEnabledTrue();
}
