package de.hbt.salat.jira.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.order.domain.Customerorder;

/**
 * The replicated tickets, found by the scope they were fetched under — the pair of customer order
 * and suborder ids (#1323). A {@code null} suborder means the whole order and is compared as such:
 * {@code t.suborder.id = :suborderId} alone would never match it.
 */
@Repository
public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
        and t.jiraId = :jiraId
      """)
  Optional<JiraTicket> findInScopeByJiraId(long customerorderId, Long suborderId, long jiraId);

  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
      """)
  List<JiraTicket> findInScope(long customerorderId, Long suborderId);

  /**
   * The ticket maintained by hand under this key in the scope (#1386) — the one a replication
   * covering the scope later takes over. Only without a JIRA id: one that has an id is found by it.
   */
  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
        and t.key = :key and t.jiraId is null
      """)
  Optional<JiraTicket> findManualInScopeByKey(long customerorderId, Long suborderId, String key);

  /** The ticket with this key in the scope, replicated or not — a key is unique per scope. */
  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
        and t.key = :key
      """)
  Optional<JiraTicket> findInScopeByKey(long customerorderId, Long suborderId, String key);

  /** Every ticket of a customer order, of the whole order and of all its suborders, for the ticket page (#1386). */
  @Query("""
      select t from JiraTicket t left join fetch t.replication
      where t.customerorder.id = :customerorderId
      order by t.key
      """)
  List<JiraTicket> findAllOfCustomerorder(long customerorderId);

  /** The tickets of these suborders, for the ticket page narrowed to a branch (#1386). */
  @Query("""
      select t from JiraTicket t left join fetch t.replication
      where t.suborder.id in :suborderIds
      order by t.key
      """)
  List<JiraTicket> findAllOfSuborders(Collection<Long> suborderIds);

  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
        and t.key in :keys
      """)
  List<JiraTicket> findInScopeByKeyIn(long customerorderId, Long suborderId, Collection<String> keys);

  /**
   * The tickets of several scopes at once (#1092): the order-wide ones of these orders and those of
   * these suborders. The booking list offers the tickets of every order it may show, and a ticket
   * tree that stopped at a scope boundary would be a tree with holes.
   */
  @Query("""
      select t from JiraTicket t
      where (t.suborder is null and t.customerorder.id in :customerorderIds)
         or t.suborder.id in :suborderIds
      """)
  List<JiraTicket> findInScopes(Collection<Long> customerorderIds, Collection<Long> suborderIds);

  /**
   * Suggestions for the booking form (#982). Matches the typed text against key and summary alike —
   * whoever books remembers either the number or what the ticket was about. Most recently updated
   * first, because that is what someone is booking on today.
   *
   * <p>Several scopes at once (#1025): booking on a suborder offers the tickets of its whole branch,
   * so the caller passes the order and the suborders from the top level down to the one booked on.
   * The limit applies to the branch as a whole, not per scope, and the same key may well arrive from
   * two of them — de-duplicating is the caller's job.
   */
  @Query("""
      select t from JiraTicket t
      where ((t.suborder is null and t.customerorder.id = :customerorderId) or t.suborder.id in :suborderIds)
        and (lower(t.key) like lower(concat('%', :searchTerm, '%'))
             or lower(t.summary) like lower(concat('%', :searchTerm, '%')))
      order by t.updatedTs desc nulls last, t.key asc
      """)
  List<JiraTicket> search(long customerorderId, Collection<Long> suborderIds, String searchTerm, Pageable pageable);

  /**
   * Takes the tickets of a suborder branch along to the customer order it was moved to (#1323).
   * Their scope is the pair of order and suborder, and a moved suborder means its new place in the
   * tree. Rows already on that order are left alone, so a save that moves nothing writes nothing.
   */
  @Modifying
  @Query("""
      update JiraTicket t set t.customerorder = :customerorder
      where t.suborder.id in :suborderIds and t.customerorder <> :customerorder
      """)
  int moveBranchToCustomerorder(Collection<Long> suborderIds, Customerorder customerorder);

  @Modifying
  @Query("delete from JiraTicket t where t.customerorder.id = :customerorderId")
  int deleteByCustomerorderId(long customerorderId);

  @Modifying
  @Query("delete from JiraTicket t where t.suborder.id in :suborderIds")
  int deleteBySuborderIdIn(Collection<Long> suborderIds);

}
