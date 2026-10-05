package de.hbt.salat.jira.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraReplicationConfig;

/**
 * The replication configs. Order and suborder are references (#1368); the queries by their id spell
 * out the path, because a derived name like {@code countByCustomerorderId} would resolve to the
 * convenience getter of the entity rather than to the foreign key and fail when it runs.
 */
@Repository
public interface JiraReplicationConfigRepository extends JpaRepository<JiraReplicationConfig, Long> {

  List<JiraReplicationConfig> findByEnabledTrue();

  List<JiraReplicationConfig> findAllByOrderByNameAsc();

  @Query("select c from JiraReplicationConfig c where c.suborder.id in :suborderIds")
  List<JiraReplicationConfig> findBySuborderIdIn(Collection<Long> suborderIds);

  @Query("select count(c) from JiraReplicationConfig c where c.customerorder.id = :customerorderId")
  long countByCustomerorderId(long customerorderId);

  @Query("select count(c) from JiraReplicationConfig c where c.suborder.id in :suborderIds")
  long countBySuborderIdIn(Collection<Long> suborderIds);
}
