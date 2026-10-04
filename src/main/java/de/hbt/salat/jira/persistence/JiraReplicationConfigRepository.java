package de.hbt.salat.jira.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraReplicationConfig;

@Repository
public interface JiraReplicationConfigRepository extends JpaRepository<JiraReplicationConfig, Long> {

  List<JiraReplicationConfig> findByEnabledTrue();

  List<JiraReplicationConfig> findAllByOrderByNameAsc();

  List<JiraReplicationConfig> findByCustomerorderId(long customerorderId);

  List<JiraReplicationConfig> findBySuborderIdIn(Collection<Long> suborderIds);

  long countByCustomerorderId(long customerorderId);

  long countBySuborderIdIn(Collection<Long> suborderIds);
}
