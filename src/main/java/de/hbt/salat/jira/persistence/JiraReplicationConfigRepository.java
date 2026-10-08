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

  /** The replications of exactly this scope (#1386) — whose inherited fields the scope resolves. */
  @Query("""
      select c from JiraReplicationConfig c
      where c.customerorder.id = :customerorderId
        and (c.suborder.id = :suborderId or (c.suborder is null and :suborderId is null))
      """)
  List<JiraReplicationConfig> findInScope(long customerorderId, Long suborderId);

  /**
   * The replications that cover a scope (#1386): the order-wide one of the order and those of the
   * suborders on the path down to the scope, that suborder included. {@code pathSuborderIds} is empty
   * for the whole order.
   */
  @Query("""
      select c from JiraReplicationConfig c
      where c.customerorder.id = :customerorderId
        and (c.suborder is null or c.suborder.id in :pathSuborderIds)
      order by c.name
      """)
  List<JiraReplicationConfig> findCovering(long customerorderId, Collection<Long> pathSuborderIds);

  @Query("select c from JiraReplicationConfig c where c.suborder.id in :suborderIds")
  List<JiraReplicationConfig> findBySuborderIdIn(Collection<Long> suborderIds);

  @Query("select count(c) from JiraReplicationConfig c where c.customerorder.id = :customerorderId")
  long countByCustomerorderId(long customerorderId);

  @Query("select count(c) from JiraReplicationConfig c where c.suborder.id in :suborderIds")
  long countBySuborderIdIn(Collection<Long> suborderIds);

  /** The replications that still keep their secret in plain text (#1432), to be moved at start. */
  @Query("select c.id from JiraReplicationConfig c where c.secretId is null and c.legacyPassword is not null order by c.id")
  List<Long> findIdsWithPlaintextSecret();
}
